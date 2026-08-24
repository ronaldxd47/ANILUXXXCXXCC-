package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
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
import com.example.data.StreamEmbed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    val scope = rememberCoroutineScope()
    
    // Playback Engine Mode: True = Native ExoPlayer, False = Embedded Web Player Sandbox
    var useNativeExo by remember(streamUrl) { mutableStateOf(true) }
    var resolvedUrl by remember(streamUrl) { mutableStateOf("") }
    var isResolving by remember(streamUrl) { mutableStateOf(true) }
    var resolveErrorMessage by remember(streamUrl) { mutableStateOf<String?>(null) }
    
    // Auto-selected server name
    var currentServerName by remember(streamUrl) {
        val embed = streamEmbeds.find { it.iframeUrl == streamUrl }
        mutableStateOf(embed?.serverName ?: "Server Default")
    }
    
    // Player Lifecycle Manager (Singleton)
    val playerManager = remember(context) { PlayerManager.getInstance(context) }
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    // Resolve direct streaming URL from embed iframe
    LaunchedEffect(streamUrl) {
        isResolving = true
        resolveErrorMessage = null
        useNativeExo = true
        
        try {
            val directUrl = VideoUrlResolver.resolve(context, streamUrl)
            if (directUrl.isNotEmpty()) {
                resolvedUrl = directUrl
                useNativeExo = true
            } else {
                // If direct video stream not extractable, switch seamlessly to Web Player Engine
                resolvedUrl = ""
                useNativeExo = false
            }
        } catch (e: Exception) {
            resolvedUrl = ""
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
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var currentQuality by remember { mutableStateOf("720p") }
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var isFullscreen by remember { mutableStateOf(false) }
    
    // Dialogs
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showServerSelector by remember { mutableStateOf(false) }
    
    // Gestures
    var gestureSeekPreview by remember { mutableStateOf(-1L) }
    var bufferingSpeedText by remember { mutableStateOf("8.5 Mbps") }

    // Initialize ExoPlayer and attach listener safely
    DisposableEffect(streamUrl, playerManager) {
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
                }
                if (state == Player.STATE_ENDED) {
                    isPlayingState = false
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                Log.e("ExoVideoPlayer", "Player error: ${error.message}, switching to web player fallback")
                useNativeExo = false
            }
        }

        val instance = playerManager.initializePlayer(streamUrl, listener)
        exoPlayer = instance

        onDispose {
            playerManager.releasePlayer()
            exoPlayer = null
        }
    }
    
    // Prepare media on resolvedUrl change
    LaunchedEffect(resolvedUrl, useNativeExo) {
        if (resolvedUrl.isNotEmpty() && useNativeExo) {
            try {
                playerManager.prepareMedia(resolvedUrl)
            } catch (e: Exception) {
                Log.e("ExoVideoPlayer", "Error preparing media, fallback to web player", e)
                useNativeExo = false
            }
        }
    }
    
    // Lifecycle-aware Player Pause management on backgrounding
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE,
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    playerManager.pausePlayer()
                }
                androidx.lifecycle.Lifecycle.Event.ON_DESTROY -> {
                    playerManager.releasePlayer()
                    exoPlayer = null
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Download speed simulation on buffering
    LaunchedEffect(isBuffering) {
        while (isBuffering) {
            val randomSpeed = (3..14).random() + (0..9).random() / 10.0
            bufferingSpeedText = String.format(Locale.US, "%.1f Mbps", randomSpeed)
            delay(1000)
        }
    }
    
    // Track play position
    LaunchedEffect(isPlayingState, playbackState) {
        while (isPlayingState && playbackState == Player.STATE_READY) {
            currentPosition = exoPlayer?.currentPosition ?: 0L
            delay(500)
        }
    }
    
    // Auto-hide controls
    LaunchedEffect(showControls, isPlayingState) {
        if (showControls && isPlayingState) {
            delay(3500)
            showControls = false
        }
    }
    
    val stringBuilder = remember { StringBuilder() }
    val formatter = remember { Formatter(stringBuilder, Locale.getDefault()) }
    
    fun formatTime(timeMs: Long): String {
        if (timeMs <= 0) return "00:00"
        val totalSeconds = (timeMs + 500) / 1000
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        stringBuilder.setLength(0)
        return if (hours > 0) {
            formatter.format("%d:%02d:%02d", hours, minutes, seconds).toString()
        } else {
            formatter.format("%02d:%02d", minutes, seconds).toString()
        }
    }
    
    // Core Player Component rendered in normal view OR Fullscreen Dialog
    @Composable
    fun PlayerContent(fullScreenMode: Boolean, modifier: Modifier = Modifier) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (!useNativeExo) {
                // EMBEDDED WEB PLAYER ENGINE (Anti-Ad, Sandboxed, High-Performance)
                WebPlayerView(
                    url = streamUrl,
                    title = title,
                    onBack = {
                        if (fullScreenMode) isFullscreen = false else onBack()
                    },
                    onSwitchToExo = {
                        if (resolvedUrl.isNotEmpty()) {
                            useNativeExo = true
                            playerManager.prepareMedia(resolvedUrl)
                        } else {
                            scope.launch {
                                isResolving = true
                                val direct = VideoUrlResolver.resolve(context, streamUrl)
                                if (direct.isNotEmpty()) {
                                    resolvedUrl = direct
                                    useNativeExo = true
                                    playerManager.prepareMedia(direct)
                                }
                                isResolving = false
                            }
                        }
                    },
                    onOpenServerSelector = { showServerSelector = true },
                    onToggleFullscreen = { isFullscreen = !isFullscreen },
                    isFullscreen = fullScreenMode
                )
            } else {
                // NATIVE EXOPLAYER ENGINE
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = { offset ->
                                    val midX = size.width / 2
                                    val pos = exoPlayer?.currentPosition ?: 0L
                                    if (offset.x < midX) {
                                        val target = (pos - 10000).coerceAtLeast(0)
                                        exoPlayer?.seekTo(target)
                                        currentPosition = target
                                    } else {
                                        val target = (pos + 10000).coerceAtMost(duration)
                                        exoPlayer?.seekTo(target)
                                        currentPosition = target
                                    }
                                },
                                onTap = { showControls = !showControls }
                            )
                        }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = { if (!isLocked) gestureSeekPreview = currentPosition },
                                onDragEnd = {
                                    if (gestureSeekPreview != -1L) {
                                        exoPlayer?.seekTo(gestureSeekPreview)
                                        currentPosition = gestureSeekPreview
                                        gestureSeekPreview = -1L
                                    }
                                },
                                onDragCancel = { gestureSeekPreview = -1L },
                                onHorizontalDrag = { change, dragAmount ->
                                    if (!isLocked) {
                                        change.consume()
                                        val addedSeek = (dragAmount * 150L).toLong()
                                        gestureSeekPreview = (gestureSeekPreview + addedSeek).coerceIn(0, duration)
                                    }
                                }
                            )
                        }
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                this.resizeMode = resizeMode
                                player = exoPlayer
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        update = { playerView ->
                            if (playerView.player != exoPlayer) {
                                playerView.player = exoPlayer
                            }
                            playerView.resizeMode = resizeMode
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Seek gesture overlay
                    if (gestureSeekPreview != -1L) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.8f))
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = if (gestureSeekPreview > currentPosition) Icons.Filled.FastForward else Icons.Filled.FastRewind,
                                    contentDescription = "Seek",
                                    tint = Color(0xFF7000FF),
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${formatTime(gestureSeekPreview)} / ${formatTime(duration)}",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    
                    // Buffering overlay
                    if (isBuffering || isResolving) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    color = Color(0xFF7000FF),
                                    strokeWidth = 4.dp,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(text = bufferingSpeedText, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(text = if (isResolving) "Menyiapkan Stream..." else "Loading Video...", color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                    }
                    
                    // Custom Player Controls
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
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.8f),
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.8f)
                                        )
                                    )
                                )
                        ) {
                            if (isLocked) {
                                IconButton(
                                    onClick = { isLocked = false },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(24.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                        .size(48.dp)
                                        .statusBarsPadding()
                                ) {
                                    Icon(imageVector = Icons.Filled.Lock, contentDescription = "Unlock", tint = Color(0xFF7000FF))
                                }
                            } else {
                                // Top Bar
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .fillMaxWidth()
                                        .padding(top = 12.dp, start = 12.dp, end = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (fullScreenMode) {
                                                isFullscreen = false
                                            } else {
                                                onBack()
                                            }
                                        },
                                        modifier = Modifier
                                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                            .size(36.dp)
                                    ) {
                                        Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = title,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    
                                    // Switch to Web Engine
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF23283A))
                                            .clickable { useNativeExo = false }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("Web Player", color = Color(0xFF00F2FE), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    IconButton(
                                        onClick = { showSettingsDialog = true },
                                        modifier = Modifier
                                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                            .size(36.dp)
                                    ) {
                                        Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    IconButton(
                                        onClick = { isLocked = true },
                                        modifier = Modifier
                                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                            .size(36.dp)
                                    ) {
                                        Icon(imageVector = Icons.Filled.LockOpen, contentDescription = "Lock", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                                
                                // Center Playback Buttons
                                Row(
                                    modifier = Modifier.align(Alignment.Center),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                                ) {
                                    // Rewind 10s
                                    IconButton(
                                        onClick = {
                                            val pos = exoPlayer?.currentPosition ?: 0L
                                            val target = (pos - 10000).coerceAtLeast(0)
                                            exoPlayer?.seekTo(target)
                                            currentPosition = target
                                        },
                                        modifier = Modifier
                                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                            .size(44.dp)
                                    ) {
                                        Icon(imageVector = Icons.Filled.Replay10, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(26.dp))
                                    }
                                    
                                    // Play / Pause
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
                                        },
                                        modifier = Modifier
                                            .background(Color(0xFF7000FF), CircleShape)
                                            .size(56.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isPlayingState) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            contentDescription = "Play/Pause",
                                            tint = Color.White,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                    
                                    // Forward 10s
                                    IconButton(
                                        onClick = {
                                            val pos = exoPlayer?.currentPosition ?: 0L
                                            val target = (pos + 10000).coerceAtMost(duration)
                                            exoPlayer?.seekTo(target)
                                            currentPosition = target
                                        },
                                        modifier = Modifier
                                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                            .size(44.dp)
                                    ) {
                                        Icon(imageVector = Icons.Filled.Forward10, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(26.dp))
                                    }
                                }
                                
                                // Bottom Controls Bar
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    // Custom Slider
                                    Slider(
                                        value = if (duration > 0) (currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                                        onValueChange = { frac ->
                                            val target = (frac * duration).toLong()
                                            currentPosition = target
                                        },
                                        onValueChangeFinished = {
                                            exoPlayer?.seekTo(currentPosition)
                                        },
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color(0xFF7000FF),
                                            activeTrackColor = Color(0xFF7000FF),
                                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                        ),
                                        modifier = Modifier.fillMaxWidth().height(18.dp)
                                    )
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${formatTime(currentPosition)} / ${formatTime(duration)}",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            // Server selector
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color.Black.copy(alpha = 0.5f))
                                                    .clickable { showServerSelector = true }
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(text = currentServerName, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                            
                                            Spacer(modifier = Modifier.width(8.dp))
                                            
                                            // Real System Fullscreen Toggle
                                            IconButton(
                                                onClick = { isFullscreen = !isFullscreen },
                                                modifier = Modifier
                                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                                    .size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (fullScreenMode) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                                    contentDescription = "Fullscreen",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            
                                            Spacer(modifier = Modifier.width(6.dp))
                                            
                                            IconButton(
                                                onClick = {
                                                    resizeMode = when (resizeMode) {
                                                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                                    }
                                                },
                                                modifier = Modifier
                                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                                    .size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = when (resizeMode) {
                                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> Icons.Filled.ZoomOutMap
                                                        AspectRatioFrameLayout.RESIZE_MODE_FILL -> Icons.Filled.FitScreen
                                                        else -> Icons.Filled.AspectRatio
                                                    },
                                                    contentDescription = "Aspect",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(18.dp)
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
    
    // Check if in Fullscreen Mode
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
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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
    
    // Settings Dialog
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            containerColor = Color(0xFF131520),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings", tint = Color(0xFF7000FF))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Pengaturan Putar", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text("Kecepatan Putar", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.5f, 1.0f, 1.5f, 2.0f).forEach { speed ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (playbackSpeed == speed) Color(0xFF7000FF) else Color.Black.copy(alpha = 0.3f))
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
                    
                    Text("Kualitas Resolusi", color = Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("360p", "480p", "720p", "1080p").forEach { res ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (currentQuality == res) Color(0xFF7000FF) else Color.Black.copy(alpha = 0.3f))
                                    .clickable {
                                        currentQuality = res
                                        showSettingsDialog = false
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = res, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showSettingsDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7000FF))
                ) {
                    Text("Simpan", color = Color.White)
                }
            }
        )
    }
    
    // Server Selector Dialog
    if (showServerSelector) {
        AlertDialog(
            onDismissRequest = { showServerSelector = false },
            containerColor = Color(0xFF131520),
            titleContentColor = Color.White,
            textContentColor = Color.White,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Filled.Dns, contentDescription = "Servers", tint = Color(0xFF7000FF))
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
                                    .background(if (isSelected) Color(0xFF7000FF) else Color.Black.copy(alpha = 0.3f))
                                    .clickable {
                                        currentServerName = embed.serverName
                                        showServerSelector = false
                                        onEpisodeChange(currentEpisodeIndex)
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
 * Sandboxed, GPU-Accelerated Web Player with Anti-Ad filters and custom overlay controls.
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember(url) { mutableStateOf(true) }
    var loadProgress by remember(url) { mutableStateOf(0) }

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
                        databaseEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        allowFileAccess = true
                        allowContentAccess = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        setSupportZoom(false)
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
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
                            // Block ad popups, redirects, deep links
                            if (lower.startsWith("intent://") || lower.startsWith("market://") || 
                                lower.contains("shopee") || lower.contains("lazada") || 
                                lower.contains("tokopedia") || lower.contains("adsterra") || 
                                lower.contains("popcash") || lower.contains("bet") || lower.contains("casino")) {
                                return true // Block
                            }
                            return false
                        }

                        override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                            Log.w("WebVideoPlayer", "Render process gone in WebVideoPlayer (didCrash=${detail?.didCrash()})")
                            try {
                                view?.let {
                                    it.stopLoading()
                                    (it.parent as? ViewGroup)?.removeView(it)
                                    it.destroy()
                                }
                            } catch (e: Exception) {}
                            isLoading = false
                            return true
                        }

                        override fun onPageFinished(view: WebView?, pageUrl: String?) {
                            super.onPageFinished(view, pageUrl)
                            isLoading = false
                            
                            // Inject CSS to make video/iframe fill the entire screen cleanly without outside clutter
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
                    loadUrl(cleanUrl)
                    webViewInstance = this
                }
            },
            update = { webView ->
                var cleanUrl = url.trim()
                if (cleanUrl.startsWith("//")) cleanUrl = "https:$cleanUrl"
                if (webView.url != cleanUrl && cleanUrl.isNotEmpty()) {
                    webView.loadUrl(cleanUrl)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Web Player Control Bar Overlay
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                    )
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF00F2FE).copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0xFF00F2FE).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("WEB ENGINE", color = Color(0xFF00F2FE), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Switch to ExoPlayer button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF7000FF))
                            .clickable { onSwitchToExo() }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.FlashOn, contentDescription = "Exo", tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ExoPlayer", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Reload button
                    IconButton(
                        onClick = { webViewInstance?.reload() },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Reload", tint = Color.White, modifier = Modifier.size(18.dp))
                    }

                    // Server switch button
                    IconButton(
                        onClick = onOpenServerSelector,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(Icons.Filled.Dns, contentDescription = "Server", tint = Color(0xFFFFC107), modifier = Modifier.size(18.dp))
                    }

                    // Fullscreen toggle
                    IconButton(
                        onClick = onToggleFullscreen,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                            contentDescription = "Fullscreen",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
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

object HeadlessStreamExtractor {
    suspend fun extractMediaUrl(context: Context, pageUrl: String, timeoutMs: Long = 4000L): String {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                var isDone = false
                val handler = android.os.Handler(android.os.Looper.getMainLooper())
                var webView: WebView? = null

                fun cleanup() {
                    if (!isDone) {
                        isDone = true
                        handler.removeCallbacksAndMessages(null)
                        try {
                            webView?.stopLoading()
                            webView?.loadUrl("about:blank")
                            webView?.onPause()
                            webView?.removeAllViews()
                            webView?.destroy()
                        } catch (e: Exception) {}
                        webView = null
                    }
                }

                val timeoutRunnable = Runnable {
                    if (continuation.isActive && !isDone) {
                        cleanup()
                        continuation.resumeWith(Result.success(""))
                    }
                }
                handler.postDelayed(timeoutRunnable, timeoutMs)

                try {
                    webView = WebView(context).apply {
                        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

                        webViewClient = object : WebViewClient() {
                            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                                Log.w("HeadlessStreamExtractor", "Render process gone in HeadlessStreamExtractor (didCrash=${detail?.didCrash()})")
                                if (continuation.isActive && !isDone) {
                                    handler.post {
                                        if (continuation.isActive && !isDone) {
                                            cleanup()
                                            continuation.resumeWith(Result.success(""))
                                        }
                                    }
                                }
                                return true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                val autoPlayJs = """
                                    (function() {
                                        try {
                                            var playBtns = document.querySelectorAll('.jw-video, .jw-button-color, .plyr__control--overlaid, .vjs-big-play-button, .play-button, [aria-label="Play"], #player, iframe, .video-js');
                                            playBtns.forEach(function(btn) { btn.click(); });
                                            var v = document.querySelector('video');
                                            if (v) { v.play().catch(function(e){}); }
                                        } catch(e){}
                                    })();
                                """.trimIndent()
                                view?.evaluateJavascript(autoPlayJs, null)
                            }
                            
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val reqUrl = request?.url?.toString() ?: ""
                                if (isMediaStreamUrl(reqUrl)) {
                                    if (continuation.isActive && !isDone) {
                                        handler.post {
                                            if (continuation.isActive && !isDone) {
                                                val foundMediaUrl = reqUrl
                                                cleanup()
                                                continuation.resumeWith(Result.success(foundMediaUrl))
                                            }
                                        }
                                    }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                        }
                        var cleanUrl = pageUrl.trim()
                        if (cleanUrl.startsWith("//")) cleanUrl = "https:$cleanUrl"
                        loadUrl(cleanUrl)
                    }
                } catch (e: Exception) {
                    if (continuation.isActive && !isDone) {
                        cleanup()
                        continuation.resumeWith(Result.success(""))
                    }
                }

                continuation.invokeOnCancellation {
                    cleanup()
                }
            }
        }
    }

    private fun isMediaStreamUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (lower.contains("googleads") || lower.contains("analytics") || lower.contains("doubleclick") || lower.contains("favicon")) {
            return false
        }
        return lower.contains(".m3u8") ||
               lower.contains(".mp4") ||
               lower.contains("videoplayback") ||
               (lower.contains(".m4s") && !lower.contains("audio"))
    }
}

object VideoUrlResolver {
    suspend fun resolve(context: Context, iframeUrl: String): String {
        return com.example.data.AnichinStreamExtractor.extractStreamUrl(context, iframeUrl)
    }
}
