package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.BatteryManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.StreamEmbed
import com.example.data.stream.ResolvedStream
import com.example.data.stream.StreamMediaType
import com.example.data.stream.StreamResolver
import com.example.utils.FormatUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Formatter
import java.util.Locale

@OptIn(UnstableApi::class)
@Composable
fun ExoVideoPlayer(
    title: String,
    streamUrl: String,
    streamEmbeds: List<StreamEmbed>,
    currentEpisodeIndex: Int,
    totalEpisodes: Int,
    onEpisodeChange: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    
    // Playback Engine Mode: True = Native ExoPlayer (HLS/MP4), False = Embedded Web Player Sandbox
    var useNativeExo by remember(streamUrl) { mutableStateOf(true) }
    var resolvedStreamState by remember(streamUrl) { mutableStateOf<ResolvedStream?>(null) }
    var isResolving by remember(streamUrl) { mutableStateOf(true) }
    var resolveErrorMessage by remember(streamUrl) { mutableStateOf<String?>(null) }
    var hasPlaybackError by remember(streamUrl) { mutableStateOf(false) }
    var playbackErrorMessage by remember(streamUrl) { mutableStateOf("") }
    
    // Auto-selected server name & active iframe URL
    var currentServerName by remember(streamUrl) {
        val embed = streamEmbeds.find { it.iframeUrl == streamUrl }
        mutableStateOf(embed?.serverName ?: "Server Default")
    }
    var activeStreamUrl by remember(streamUrl) { mutableStateOf(streamUrl) }
    
    // Stateful playback session to coordinate retries, re-resolutions, and fallbacks cleanly
    var playbackSession by remember(activeStreamUrl, currentServerName) {
        mutableStateOf(
            PlaybackSession(
                streamUrl = activeStreamUrl,
                serverName = currentServerName,
                activeEngine = PlaybackEngine.EXO
            )
        )
    }

    // Player Lifecycle Manager (Singleton)
    val playerManager = remember(context) { PlayerManager.getInstance(context) }
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    // Bind lifecycle so playback pauses on background and cleans up on screen destroy
    DisposableEffect(lifecycleOwner, playerManager) {
        playerManager.attachLifecycle(lifecycleOwner)
        onDispose {
            playerManager.detachLifecycle()
        }
    }

    // Resolve direct streaming URL with full header preservation, validation, and MIME classification
    LaunchedEffect(activeStreamUrl, currentServerName) {
        isResolving = true
        resolveErrorMessage = null
        hasPlaybackError = false
        playbackErrorMessage = ""
        useNativeExo = true
        
        try {
            val resolved = StreamResolver.resolve(context, activeStreamUrl, currentServerName)
            resolvedStreamState = resolved
            playbackSession = playbackSession.copy(resolvedStream = resolved)
            if (resolved.isDirect) {
                useNativeExo = true
            } else {
                // If stream cannot be resolved to direct media, fallback to Web Player sandbox
                playbackSession = playbackSession.switchToWeb()
                useNativeExo = false
            }
        } catch (e: Exception) {
            Log.e("ExoVideoPlayer", "Stream resolution failed", e)
            resolveErrorMessage = e.message
            playbackSession = playbackSession.switchToWeb()
            useNativeExo = false
        } finally {
            isResolving = false
        }
    }
    
    // Player UI States
    var isPlayingState by remember { mutableStateOf(false) }
    var playbackState by remember { mutableStateOf(Player.STATE_IDLE) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var isBuffering by remember { mutableStateOf(false) }
    
    // Control States
    var showControls by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var isSeeking by remember { mutableStateOf(false) }
    var controlsInteractionTrigger by remember { mutableLongStateOf(0L) }
    val resetControlsTimer: () -> Unit = { controlsInteractionTrigger = System.currentTimeMillis() }
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var currentQuality by remember { mutableStateOf("Auto") }
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var isFullscreen by remember { mutableStateOf(false) }
    
    // Dialogs
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showServerSelector by remember { mutableStateOf(false) }
    
    // Gestures & Live Bitrate
    var gestureSeekPreview by remember { mutableStateOf(-1L) }
    var bandwidthSpeedText by remember { mutableStateOf("HD Auto") }

    // Double-Tap Seek Feedback Animation
    var doubleTapSeekSide by remember { mutableStateOf<String?>(null) }
    var doubleTapSeekAmount by remember { mutableIntStateOf(0) }
    var doubleTapAnimTrigger by remember { mutableLongStateOf(0L) }

    LaunchedEffect(doubleTapAnimTrigger) {
        if (doubleTapAnimTrigger > 0L) {
            delay(750)
            doubleTapSeekSide = null
            doubleTapSeekAmount = 0
        }
    }

    // Fullscreen Battery & Clock Status
    var currentTimeString by remember { mutableStateOf("") }
    var batteryLevel by remember { mutableIntStateOf(-1) }

    LaunchedEffect(isFullscreen, showControls) {
        if (isFullscreen && showControls) {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            batteryLevel = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            currentTimeString = sdf.format(Date())
        }
    }

    // Initialize ExoPlayer strictly when direct native playback is active
    DisposableEffect(activeStreamUrl, resolvedStreamState, useNativeExo, playerManager) {
        val stream = resolvedStreamState
        val isDirectReady = stream != null && stream.isDirect && useNativeExo

        if (isDirectReady) {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    isPlayingState = isPlaying
                }
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    isPlayingState = playWhenReady && playbackState != Player.STATE_ENDED
                }
                override fun onPlaybackStateChanged(state: Int) {
                    playbackState = state
                    isBuffering = state == Player.STATE_BUFFERING
                    if (state == Player.STATE_READY) {
                        exoPlayer?.let { duration = it.duration }
                        hasPlaybackError = false
                    }
                    if (state == Player.STATE_ENDED) {
                        isPlayingState = false
                    }
                }
                override fun onPlayerError(error: PlaybackException) {
                    Log.w("ExoVideoPlayer", "Player error: [${error.errorCodeName}] ${error.message}")
                    val action = PlaybackErrorClassifier.classify(error, playbackSession)
                    when (action) {
                        is PlaybackAction.ReResolve -> {
                            Log.i("ExoVideoPlayer", "Action ReResolve triggered: ${action.reason}")
                            scope.launch {
                                isResolving = true
                                try {
                                    val fresh = StreamResolver.resolve(context, activeStreamUrl, currentServerName)
                                    playbackSession = playbackSession.recordReResolve(fresh)
                                    resolvedStreamState = fresh
                                    if (fresh.isDirect) {
                                        useNativeExo = true
                                        playerManager.prepareStream(fresh, playWhenReady = true)
                                    } else {
                                        playbackSession = playbackSession.switchToWeb()
                                        useNativeExo = false
                                    }
                                } catch (e: Exception) {
                                    Log.e("ExoVideoPlayer", "Re-resolve failed", e)
                                    playbackSession = playbackSession.switchToWeb()
                                    useNativeExo = false
                                } finally {
                                    isResolving = false
                                }
                            }
                        }
                        is PlaybackAction.RetryExo -> {
                            Log.i("ExoVideoPlayer", "Action RetryExo with backoff ${action.delayMs}ms")
                            playbackSession = playbackSession.recordExoAttempt()
                            scope.launch {
                                delay(action.delayMs)
                                resolvedStreamState?.let { s ->
                                    playerManager.prepareStream(s, playWhenReady = true)
                                }
                            }
                        }
                        is PlaybackAction.FallbackToWeb -> {
                            Log.i("ExoVideoPlayer", "Action FallbackToWeb: ${action.reason}")
                            playbackSession = playbackSession.switchToWeb()
                            hasPlaybackError = false
                            playbackErrorMessage = ""
                            useNativeExo = false
                        }
                        is PlaybackAction.FatalError -> {
                            Log.e("ExoVideoPlayer", "Action FatalError: ${action.userFriendlyMessage}")
                            hasPlaybackError = true
                            playbackErrorMessage = action.userFriendlyMessage
                        }
                    }
                }
            }

            val instance = playerManager.initializePlayer(
                customHeaders = stream.headers,
                externalListener = listener
            )
            exoPlayer = instance
            playerManager.prepareStream(stream)
        } else {
            // Ensure any previous native player instance is released when in Web Player mode
            playerManager.releasePlayer()
            exoPlayer = null
        }

        onDispose {
            playerManager.releasePlayer()
            exoPlayer = null
        }
    }

    // Auto-hide controls ticker & live position tracker
    LaunchedEffect(showControls, isPlayingState, isSeeking, controlsInteractionTrigger, isLocked) {
        if (showControls && isPlayingState && !isLocked && !isSeeking) {
            delay(3000)
            showControls = false
        }
    }
    
    LaunchedEffect(isPlayingState) {
        while (isPlayingState) {
            exoPlayer?.let { p ->
                currentPosition = p.currentPosition
                duration = p.duration.coerceAtLeast(0L)
                bandwidthSpeedText = playerManager.getFormattedBandwidthSpeed()
            }
            delay(1000)
        }
    }

    // Main Player Composables
    @Composable
    fun PlayerContent(
        fullScreenMode: Boolean,
        modifier: Modifier = Modifier
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (!useNativeExo || (resolvedStreamState != null && !resolvedStreamState!!.isDirect)) {
                // Embedded Sandboxed Web Engine
                val webUrl = if (resolvedStreamState != null && resolvedStreamState!!.isDirect && resolvedStreamState!!.url.isNotEmpty()) {
                    resolvedStreamState!!.url
                } else {
                    resolvedStreamState?.originalIframeUrl ?: activeStreamUrl
                }
                WebPlayerView(
                    url = webUrl,
                    title = title,
                    headers = resolvedStreamState?.headers ?: emptyMap(),
                    baseUrl = resolvedStreamState?.originalIframeUrl ?: resolvedStreamState?.headers?.get("Referer") ?: activeStreamUrl,
                    onBack = onBack,
                    onSwitchToExo = {
                        scope.launch {
                            isResolving = true
                            try {
                                val freshResolved = StreamResolver.resolve(context, activeStreamUrl, currentServerName)
                                resolvedStreamState = freshResolved
                                if (freshResolved.isDirect) {
                                    useNativeExo = true
                                }
                            } catch (e: Exception) {
                                Log.e("ExoVideoPlayer", "Manual re-resolve failed", e)
                            } finally {
                                isResolving = false
                            }
                        }
                    },
                    onOpenServerSelector = { showServerSelector = true },
                    onToggleFullscreen = { isFullscreen = !isFullscreen },
                    isFullscreen = fullScreenMode
                )
            } else {
                // Native ExoPlayer Surface (Hardware Accelerated)
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            this.resizeMode = resizeMode
                            this.player = exoPlayer
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { view ->
                        view.resizeMode = resizeMode
                        if (view.player != exoPlayer) {
                            view.player = exoPlayer
                        }
                    },
                    onReset = { view ->
                        view.player = null
                    },
                    onRelease = { view ->
                        view.player = null
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    showControls = !showControls
                                    if (showControls) resetControlsTimer()
                                },
                                onDoubleTap = { offset ->
                                    val width = size.width
                                    val isRight = offset.x > width / 2
                                    exoPlayer?.let { p ->
                                        val side = if (isRight) "right" else "left"
                                        if (doubleTapSeekSide == side) {
                                            doubleTapSeekAmount += 10
                                        } else {
                                            doubleTapSeekSide = side
                                            doubleTapSeekAmount = 10
                                        }
                                        doubleTapAnimTrigger = System.currentTimeMillis()
                                        val delta = if (isRight) 10000L else -10000L
                                        val newPos = (p.currentPosition + delta).coerceIn(0L, duration)
                                        p.seekTo(newPos)
                                        currentPosition = newPos
                                        showControls = true
                                        resetControlsTimer()
                                    }
                                }
                            )
                        }
                )
                
                // Loading Resolver Indicator
                if (isResolving) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFF00F2FE), strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Mengekstrak stream HLS video...", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                
                // Buffering Indicator
                if (isBuffering && !isResolving && !hasPlaybackError) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF7000FF), strokeWidth = 3.dp, modifier = Modifier.size(44.dp))
                    }
                }

                // Error Overlay with Recoverable Fallbacks
                if (hasPlaybackError) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.88f))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = "Error",
                                tint = Color(0xFFFF2A55),
                                modifier = Modifier.size(42.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Gagal Memutar Native ExoPlayer",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = playbackErrorMessage,
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        hasPlaybackError = false
                                        playbackSession = PlaybackSession(
                                            streamUrl = activeStreamUrl,
                                            serverName = currentServerName,
                                            activeEngine = PlaybackEngine.EXO,
                                            resolvedStream = resolvedStreamState
                                        )
                                        playerManager.retryPlayback(onReResolveRequired = {
                                            scope.launch {
                                                isResolving = true
                                                try {
                                                    val fresh = StreamResolver.resolve(context, activeStreamUrl, currentServerName)
                                                    playbackSession = playbackSession.recordReResolve(fresh)
                                                    resolvedStreamState = fresh
                                                    if (fresh.isDirect) {
                                                        useNativeExo = true
                                                        playerManager.prepareStream(fresh, playWhenReady = true)
                                                    } else {
                                                        playbackSession = playbackSession.switchToWeb()
                                                        useNativeExo = false
                                                    }
                                                } catch (e: Exception) {
                                                    hasPlaybackError = true
                                                    playbackErrorMessage = "Gagal memuat ulang: ${e.message}"
                                                } finally {
                                                    isResolving = false
                                                }
                                            }
                                        })
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7000FF)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = "Coba Ulang", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Coba Ulang", fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        hasPlaybackError = false
                                        useNativeExo = false
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00F2FE))
                                ) {
                                    Text("Web Player", fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = { showServerSelector = true },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                ) {
                                    Text("Ganti Server", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                
                // Double-Tap Seek Ripple Feedback Overlay (YouTube-style)
                AnimatedVisibility(
                    visible = doubleTapSeekSide != null,
                    enter = fadeIn() + scaleIn(initialScale = 0.85f),
                    exit = fadeOut() + scaleOut(targetScale = 0.95f),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = if (doubleTapSeekSide == "left") Alignment.CenterStart else Alignment.CenterEnd
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = if (fullScreenMode) 44.dp else 20.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.65f))
                                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (doubleTapSeekSide == "left") {
                                    Icon(
                                        imageVector = Icons.Filled.FastRewind,
                                        contentDescription = "Rewind",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "-${doubleTapSeekAmount}s",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    Text(
                                        text = "+${doubleTapSeekAmount}s",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.FastForward,
                                        contentDescription = "Forward",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Controls Overlay
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.75f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.85f)
                                    )
                                )
                            )
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        showControls = false
                                    }
                                )
                            }
                    ) {
                        if (isLocked) {
                            // Locked State: Show only unlock button
                            IconButton(
                                onClick = { isLocked = false },
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(16.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    .size(42.dp)
                            ) {
                                Icon(imageVector = Icons.Filled.Lock, contentDescription = "Unlock", tint = Color.White)
                            }
                        } else {
                            // Unlocked: Full Controls Bar
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Top Controls Bar
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                                            )
                                        )
                                        .padding(
                                            horizontal = if (fullScreenMode) 14.dp else 10.dp,
                                            vertical = if (fullScreenMode) 8.dp else 4.dp
                                        ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (fullScreenMode) {
                                        IconButton(
                                            onClick = onBack,
                                            modifier = Modifier
                                                .background(Color.White.copy(alpha = 0.15f), CircleShape)
                                                .border(0.5.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                                .size(28.dp)
                                        ) {
                                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(15.dp))
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    
                                    val formattedEpTitle = remember(title) { FormatUtils.formatEpisodeTitle(title) }
                                    Text(
                                        text = formattedEpTitle,
                                        color = Color.White,
                                        fontSize = if (fullScreenMode) 13.sp else 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    
                                    if (fullScreenMode) {
                                        // Battery & Clock Display
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color.White.copy(alpha = 0.12f))
                                                .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.5.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (batteryLevel in 0..20) Icons.Filled.BatteryAlert else Icons.Filled.BatteryFull,
                                                contentDescription = "Battery",
                                                tint = if (batteryLevel in 0..20) Color(0xFFFF5252) else Color.White.copy(alpha = 0.85f),
                                                modifier = Modifier.size(12.dp)
                                            )
                                            if (batteryLevel >= 0) {
                                                Spacer(modifier = Modifier.width(3.dp))
                                                Text(
                                                    text = "$batteryLevel%",
                                                    color = Color.White.copy(alpha = 0.85f),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                            if (currentTimeString.isNotEmpty()) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = currentTimeString,
                                                    color = Color.White.copy(alpha = 0.85f),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        // Quality / Speed Badge
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color.White.copy(alpha = 0.12f))
                                                .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(text = if (bandwidthSpeedText.isNotEmpty()) bandwidthSpeedText else "HD", color = Color(0xFF00F2FE), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))
                                    }

                                    // Switch to Web Engine
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color.White.copy(alpha = 0.12f))
                                            .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                            .clickable { useNativeExo = false }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("Web", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    IconButton(
                                        onClick = { showSettingsDialog = true },
                                        modifier = Modifier
                                            .background(Color.White.copy(alpha = 0.15f), CircleShape)
                                            .border(0.5.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                            .size(if (fullScreenMode) 28.dp else 24.dp)
                                    ) {
                                        Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White, modifier = Modifier.size(if (fullScreenMode) 15.dp else 13.dp))
                                    }
                                    
                                    if (fullScreenMode) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(
                                            onClick = { isLocked = true },
                                            modifier = Modifier
                                                .background(Color.White.copy(alpha = 0.15f), CircleShape)
                                                .border(0.5.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                                .size(28.dp)
                                        ) {
                                            Icon(imageVector = Icons.Filled.LockOpen, contentDescription = "Lock", tint = Color.White, modifier = Modifier.size(15.dp))
                                        }
                                    }
                                }
                                
                                // Center Playback Controls
                                Row(
                                    modifier = Modifier.align(Alignment.CenterHorizontally),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(if (fullScreenMode) 14.dp else 10.dp)
                                ) {
                                    // Previous Episode (if available, fullscreen only)
                                    if (fullScreenMode && totalEpisodes > 1 && currentEpisodeIndex > 0) {
                                        IconButton(
                                            onClick = {
                                                onEpisodeChange(currentEpisodeIndex - 1)
                                                resetControlsTimer()
                                            },
                                            modifier = Modifier
                                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                                .border(0.5.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                                .size(28.dp)
                                        ) {
                                            Icon(imageVector = Icons.Filled.SkipPrevious, contentDescription = "Episode Sebelumnya", tint = Color.White, modifier = Modifier.size(15.dp))
                                        }
                                    }

                                    // Rewind 10s
                                    IconButton(
                                        onClick = {
                                            val pos = exoPlayer?.currentPosition ?: 0L
                                            val target = (pos - 10000).coerceAtLeast(0)
                                            exoPlayer?.seekTo(target)
                                            currentPosition = target
                                            resetControlsTimer()
                                        },
                                        modifier = Modifier
                                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                            .border(0.5.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                            .size(if (fullScreenMode) 32.dp else 26.dp)
                                    ) {
                                        Icon(imageVector = Icons.Filled.Replay10, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(if (fullScreenMode) 16.dp else 14.dp))
                                    }
                                    
                                    // Play / Pause (Modern Radiant Accent)
                                    IconButton(
                                        onClick = {
                                            exoPlayer?.let {
                                                if (it.isPlaying) {
                                                    it.pause()
                                                    isPlayingState = false
                                                } else {
                                                    it.play()
                                                    isPlayingState = true
                                                }
                                            }
                                            resetControlsTimer()
                                        },
                                        modifier = Modifier
                                            .background(
                                                Brush.radialGradient(
                                                    listOf(Color(0xFFE50914), Color(0xFFB00610))
                                                ),
                                                CircleShape
                                            )
                                            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                            .size(if (fullScreenMode) 40.dp else 34.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isPlayingState) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            contentDescription = "Play/Pause",
                                            tint = Color.White,
                                            modifier = Modifier.size(if (fullScreenMode) 22.dp else 18.dp)
                                        )
                                    }
                                    
                                    // Forward 10s
                                    IconButton(
                                        onClick = {
                                            val pos = exoPlayer?.currentPosition ?: 0L
                                            val target = (pos + 10000).coerceAtMost(duration)
                                            exoPlayer?.seekTo(target)
                                            currentPosition = target
                                            resetControlsTimer()
                                        },
                                        modifier = Modifier
                                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                            .border(0.5.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                            .size(if (fullScreenMode) 32.dp else 26.dp)
                                    ) {
                                        Icon(imageVector = Icons.Filled.Forward10, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(if (fullScreenMode) 16.dp else 14.dp))
                                    }

                                    // Next Episode (if available, fullscreen only)
                                    if (fullScreenMode && totalEpisodes > 1 && currentEpisodeIndex < totalEpisodes - 1) {
                                        IconButton(
                                            onClick = {
                                                onEpisodeChange(currentEpisodeIndex + 1)
                                                resetControlsTimer()
                                            },
                                            modifier = Modifier
                                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                                .border(0.5.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                                .size(28.dp)
                                        ) {
                                            Icon(imageVector = Icons.Filled.SkipNext, contentDescription = "Episode Selanjutnya", tint = Color.White, modifier = Modifier.size(15.dp))
                                        }
                                    }
                                }
                                
                                // Bottom Controls Bar
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                                            )
                                        )
                                        .padding(
                                            horizontal = if (fullScreenMode) 14.dp else 8.dp,
                                            vertical = if (fullScreenMode) 8.dp else 4.dp
                                        )
                                ) {
                                    // Custom Sleek Slider
                                    Slider(
                                        value = if (duration > 0) (currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                                        onValueChange = { frac ->
                                            isSeeking = true
                                            val target = (frac * duration).toLong()
                                            currentPosition = target
                                        },
                                        onValueChangeFinished = {
                                            exoPlayer?.seekTo(currentPosition)
                                            isSeeking = false
                                            resetControlsTimer()
                                        },
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color(0xFFE50914),
                                            activeTrackColor = Color(0xFFE50914),
                                            inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                                        ),
                                        modifier = Modifier.fillMaxWidth().height(if (fullScreenMode) 16.dp else 12.dp)
                                    )
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${formatTime(currentPosition)} / ${formatTime(duration)}",
                                            color = Color.White,
                                            fontSize = if (fullScreenMode) 11.sp else 9.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            // Playback Speed quick pill
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color.White.copy(alpha = 0.12f))
                                                    .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                    .clickable {
                                                        playbackSpeed = when (playbackSpeed) {
                                                            1.0f -> 1.25f
                                                            1.25f -> 1.5f
                                                            1.5f -> 2.0f
                                                            2.0f -> 0.75f
                                                            else -> 1.0f
                                                        }
                                                        exoPlayer?.setPlaybackSpeed(playbackSpeed)
                                                        resetControlsTimer()
                                                    }
                                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                                            ) {
                                                Text(text = "${playbackSpeed}x", color = Color.White, fontSize = if (fullScreenMode) 10.sp else 8.sp, fontWeight = FontWeight.Bold)
                                            }

                                            if (fullScreenMode) {
                                                Spacer(modifier = Modifier.width(6.dp))

                                                // Server selector
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color.White.copy(alpha = 0.12f))
                                                        .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                        .clickable {
                                                            showServerSelector = true
                                                            resetControlsTimer()
                                                        }
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(imageVector = Icons.Filled.Dns, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(10.dp))
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Text(text = currentServerName, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                                
                                                Spacer(modifier = Modifier.width(6.dp))
                                                
                                                // Aspect Ratio Toggle
                                                IconButton(
                                                    onClick = {
                                                        resizeMode = when (resizeMode) {
                                                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                                        }
                                                        resetControlsTimer()
                                                    },
                                                    modifier = Modifier
                                                        .background(Color.White.copy(alpha = 0.12f), CircleShape)
                                                        .size(26.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = when (resizeMode) {
                                                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> Icons.Filled.ZoomOutMap
                                                            AspectRatioFrameLayout.RESIZE_MODE_FILL -> Icons.Filled.FitScreen
                                                            else -> Icons.Filled.AspectRatio
                                                        },
                                                        contentDescription = "Aspect",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(4.dp))

                                            // Fullscreen Toggle
                                            IconButton(
                                                onClick = { isFullscreen = !isFullscreen },
                                                modifier = Modifier
                                                    .background(Color.White.copy(alpha = 0.12f), CircleShape)
                                                    .size(if (fullScreenMode) 26.dp else 22.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (fullScreenMode) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                                    contentDescription = "Fullscreen",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(if (fullScreenMode) 14.dp else 13.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Fullscreen Dialog Handler
    if (isFullscreen) {
        Dialog(
            onDismissRequest = { isFullscreen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            val activity = context.findActivity()
            DisposableEffect(Unit) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                activity?.window?.let { win ->
                    val controller = WindowCompat.getInsetsController(win, win.decorView)
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
                onDispose {
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    activity?.window?.let { win ->
                        val controller = WindowCompat.getInsetsController(win, win.decorView)
                        controller.show(WindowInsetsCompat.Type.systemBars())
                    }
                }
            }
            PlayerContent(fullScreenMode = true, modifier = Modifier.fillMaxSize())
        }
    } else {
        PlayerContent(fullScreenMode = false, modifier = modifier)
    }
    
    // Settings Dialog (Speed & Real Quality Track Selection)
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            containerColor = Color(0xFF161822),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings", tint = Color(0xFFE50914))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Pengaturan Putar", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text("Kecepatan Putar", color = Color(0xFF94A3B8), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                            val isSelected = playbackSpeed == speed
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFFE50914) else Color(0xFF202330))
                                    .border(1.dp, if (isSelected) Color(0xFFFF5252) else Color.Transparent, RoundedCornerShape(8.dp))
                                    .clickable {
                                        playbackSpeed = speed
                                        exoPlayer?.setPlaybackSpeed(speed)
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "${speed}x", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Text("Kualitas Resolusi (HLS Track Selection)", color = Color(0xFF94A3B8), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Auto", "360p", "480p", "720p", "1080p").forEach { res ->
                            val isSelected = currentQuality == res
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFFE50914) else Color(0xFF202330))
                                    .border(1.dp, if (isSelected) Color(0xFFFF5252) else Color.Transparent, RoundedCornerShape(8.dp))
                                    .clickable {
                                        currentQuality = res
                                        playerManager.setVideoQuality(res)
                                        showSettingsDialog = false
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = res, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showSettingsDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Selesai", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
    
    // Server Selector Dialog
    if (showServerSelector) {
        AlertDialog(
            onDismissRequest = { showServerSelector = false },
            containerColor = Color(0xFF161822),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.Dns, contentDescription = "Servers", tint = Color(0xFFE50914))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Pilih Server Putar", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Box(modifier = Modifier.heightIn(max = 240.dp)) {
                    androidx.compose.foundation.lazy.LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(streamEmbeds.size) { index ->
                            val embed = streamEmbeds[index]
                            val isSelected = currentServerName == embed.serverName
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFFE50914) else Color(0xFF202330))
                                    .border(1.dp, if (isSelected) Color(0xFFFF5252) else Color.Transparent, RoundedCornerShape(8.dp))
                                    .clickable {
                                        currentServerName = embed.serverName
                                        activeStreamUrl = embed.iframeUrl
                                        showServerSelector = false
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(text = embed.serverName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showServerSelector = false }) {
                    Text("Tutup", color = Color.White)
                }
            }
        )
    }
}

/**
 * Sandboxed, GPU-Accelerated Web Player with HLS Awareness, Anti-Ad filters, and custom overlay controls.
 */
@Composable
fun WebPlayerView(
    url: String,
    title: String,
    onBack: () -> Unit,
    onSwitchToExo: () -> Unit,
    onOpenServerSelector: () -> Unit,
    onToggleFullscreen: () -> Unit,
    isFullscreen: Boolean,
    headers: Map<String, String> = emptyMap(),
    baseUrl: String? = null,
    modifier: Modifier = Modifier
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember(url) { mutableStateOf(true) }
    var loadProgress by remember(url) { mutableStateOf(0) }

    DisposableEffect(url) {
        onDispose {
            try {
                webViewInstance?.let { wv ->
                    webViewInstance = null
                    wv.stopLoading()
                    wv.webChromeClient = null
                    wv.webViewClient = object : WebViewClient() {}
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    wv.onPause()
                    wv.postDelayed({
                        try {
                            wv.destroy()
                        } catch (e: Exception) {}
                    }, 200)
                }
            } catch (e: Exception) {
                Log.w("WebPlayerView", "Error disposing WebView: ${e.message}")
            }
            webViewInstance = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(AndroidColor.BLACK)
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        allowFileAccess = true
                        allowContentAccess = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        setSupportZoom(false)
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        userAgentString = headers["User-Agent"]
                            ?: "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                    }
                    
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            loadProgress = newProgress
                            if (newProgress >= 85) {
                                isLoading = false
                            }
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val targetUrl = request?.url?.toString() ?: ""
                            val lower = targetUrl.lowercase()
                            // Block external app intent navigation / non-http schemes
                            if (lower.startsWith("intent://") || lower.startsWith("market://") || 
                                lower.startsWith("whatsapp://") || lower.startsWith("tg://") ||
                                lower.startsWith("about:blank")) {
                                return true
                            }
                            return false
                        }

                        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                            Log.w("WebVideoPlayer", "Render process gone in WebVideoPlayer (didCrash=${detail?.didCrash()})")
                            isLoading = false
                            webViewInstance = null
                            return true
                        }

                        override fun onPageFinished(view: WebView?, pageUrl: String?) {
                            super.onPageFinished(view, pageUrl)
                            isLoading = false
                            
                            // Inject CSS to make video/iframe fill the entire screen cleanly
                            val injectScript = """
                                (function() {
                                    try {
                                        var style = document.createElement('style');
                                        style.innerHTML = 'body, html { margin:0; padding:0; background:#000!important; overflow:hidden!important; width:100%!important; height:100%!important; } video, iframe, #player, .player, .jwplayer, .video-js { position:absolute!important; top:0!important; left:0!important; width:100%!important; height:100%!important; object-fit:contain!important; } .header, .footer, .sidebar, .comments, #disqus_thread, [class*="ad"], [id*="ad"], [class*="banner"] { display:none!important; }';
                                        document.head.appendChild(style);

                                        setTimeout(function() {
                                            var v = document.querySelector('video');
                                            if (v) { v.play().catch(function(){}); }
                                            var btns = document.querySelectorAll('.jw-display-icon-container, .vjs-big-play-button, .play-button, [aria-label="Play"], #play');
                                            btns.forEach(function(b) { b.click(); });
                                        }, 600);
                                    } catch(e){}
                                })();
                            """.trimIndent()
                            view?.evaluateJavascript(injectScript, null)
                        }
                    }

                    var cleanUrl = url.trim()
                    if (cleanUrl.startsWith("//")) cleanUrl = "https:$cleanUrl"
                    
                    // If the URL is a raw direct stream, generate a proper HTML5 player with local/cdn HLS.js and headers
                    if (cleanUrl.contains(".m3u8", ignoreCase = true)) {
                        val headersJson = org.json.JSONObject(headers as Map<*, *>).toString()
                        val hlsHtml = """
                            <!DOCTYPE html>
                            <html>
                            <head>
                                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                                <style>
                                    html, body { margin:0; padding:0; width:100%; height:100%; background:#000; overflow:hidden; display:flex; justify-content:center; align-items:center; }
                                    video { width:100%; height:100%; object-fit:contain; }
                                </style>
                                <script src="file:///android_asset/hls.min.js"></script>
                                <script>
                                    if (typeof Hls === 'undefined') {
                                        var cdnScript = document.createElement('script');
                                        cdnScript.src = 'https://cdn.jsdelivr.net/npm/hls.js@1.5.17/dist/hls.min.js';
                                        document.head.appendChild(cdnScript);
                                    }
                                </script>
                            </head>
                            <body>
                                <video id="video" controls autoplay playsinline></video>
                                <script>
                                    function initHls() {
                                        var video = document.getElementById('video');
                                        var videoSrc = '$cleanUrl';
                                        var customHeaders = $headersJson;
                                        if (typeof Hls !== 'undefined' && Hls.isSupported()) {
                                            var hls = new Hls({
                                                xhrSetup: function(xhr, url) {
                                                    xhr.withCredentials = false;
                                                    for (var k in customHeaders) {
                                                        if (customHeaders.hasOwnProperty(k)) {
                                                            var lk = k.toLowerCase();
                                                            if (lk !== 'referer' && lk !== 'user-agent' && lk !== 'origin' && lk !== 'host') {
                                                                try { xhr.setRequestHeader(k, customHeaders[k]); } catch(e){}
                                                            }
                                                        }
                                                    }
                                                }
                                            });
                                            hls.loadSource(videoSrc);
                                            hls.attachMedia(video);
                                            hls.on(Hls.Events.MANIFEST_PARSED, function() {
                                                video.play().catch(function(){});
                                            });
                                        } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
                                            video.src = videoSrc;
                                            video.addEventListener('loadedmetadata', function() {
                                                video.play().catch(function(){});
                                            });
                                        }
                                    }
                                    if (typeof Hls !== 'undefined') {
                                        initHls();
                                    } else {
                                        window.addEventListener('load', function() { setTimeout(initHls, 300); });
                                    }
                                </script>
                            </body>
                            </html>
                        """.trimIndent()

                        val effectiveBaseUrl = when {
                            !baseUrl.isNullOrBlank() && (baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) -> baseUrl
                            headers["Referer"] != null && headers["Referer"]!!.startsWith("http") -> headers["Referer"]!!
                            headers["Origin"] != null && headers["Origin"]!!.startsWith("http") -> headers["Origin"]!!
                            cleanUrl.startsWith("http") -> {
                                try {
                                    val uri = Uri.parse(cleanUrl)
                                    if (uri.scheme != null && uri.host != null) "${uri.scheme}://${uri.host}" else cleanUrl
                                } catch (e: Exception) { cleanUrl }
                            }
                            else -> "https://anichin.vip"
                        }
                        loadDataWithBaseURL(effectiveBaseUrl, hlsHtml, "text/html", "UTF-8", null)
                    } else {
                        if (headers.isNotEmpty()) {
                            loadUrl(cleanUrl, headers)
                        } else {
                            loadUrl(cleanUrl)
                        }
                    }
                    
                    webViewInstance = this
                }
            },
            update = { webView ->
                var cleanUrl = url.trim()
                if (cleanUrl.startsWith("//")) cleanUrl = "https:$cleanUrl"
                if (webView.url != cleanUrl && cleanUrl.isNotEmpty() && !cleanUrl.contains(".m3u8")) {
                    webView.loadUrl(cleanUrl)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Web Player Top Control Overlay
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                    )
                )
                .padding(
                    horizontal = if (isFullscreen) 12.dp else 8.dp,
                    vertical = if (isFullscreen) 8.dp else 4.dp
                )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isFullscreen) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .size(28.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(15.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF00F2FE).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .border(0.5.dp, Color(0xFF00F2FE).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(if (isFullscreen) "WEB ENGINE" else "WEB", color = Color(0xFF00F2FE), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Switch to ExoPlayer button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF7000FF))
                            .clickable { onSwitchToExo() }
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.FlashOn, contentDescription = "Exo", tint = Color.White, modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Exo", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Reload button
                    IconButton(
                        onClick = { webViewInstance?.reload() },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .size(if (isFullscreen) 28.dp else 22.dp)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Reload", tint = Color.White, modifier = Modifier.size(if (isFullscreen) 15.dp else 12.dp))
                    }

                    if (isFullscreen) {
                        // Server switch button
                        IconButton(
                            onClick = onOpenServerSelector,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .size(28.dp)
                        ) {
                            Icon(Icons.Filled.Dns, contentDescription = "Server", tint = Color(0xFFFFC107), modifier = Modifier.size(15.dp))
                        }
                    }

                    // Fullscreen toggle
                    IconButton(
                        onClick = onToggleFullscreen,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .size(if (isFullscreen) 28.dp else 22.dp)
                    ) {
                        Icon(
                            imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                            contentDescription = "Fullscreen",
                            tint = Color.White,
                            modifier = Modifier.size(if (isFullscreen) 15.dp else 13.dp)
                        )
                    }
                }
            }
        }

        // Loading Progress Indicator
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF00F2FE), strokeWidth = 3.dp, modifier = Modifier.size(38.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Memuat Pemutar Web ($loadProgress%)...", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).toInt()
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    val mFormatter = Formatter(StringBuilder(), Locale.getDefault())
    return if (hours > 0) {
        mFormatter.format("%d:%02d:%02d", hours, minutes, seconds).toString()
    } else {
        mFormatter.format("%02d:%02d", minutes, seconds).toString()
    }
}
