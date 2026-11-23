package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.BatteryManager
import android.util.Log
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.StreamEmbed
import com.example.data.stream.ResolvedStream
import com.example.data.stream.StreamResolver
import com.example.ui.player.WebPlayerView
import com.example.ui.player.controls.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Komponen Utama Video Player (Orchestrator).
 * Mengintegrasikan Media3 ExoPlayer (Hardware Accelerated), sandboxed WebView fallback,
 * adaptive stream routing, lifecycle management, serta kontrol UI modular.
 */
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

    // Status Engine: Native ExoPlayer vs Sandboxed Web Engine
    var useNativeExo by remember(streamUrl) { mutableStateOf(true) }
    var resolvedStreamState by remember(streamUrl) { mutableStateOf<ResolvedStream?>(null) }
    var isResolving by remember(streamUrl) { mutableStateOf(true) }
    var resolveErrorMessage by remember(streamUrl) { mutableStateOf<String?>(null) }
    var hasPlaybackError by remember(streamUrl) { mutableStateOf(false) }
    var playbackErrorMessage by remember(streamUrl) { mutableStateOf("") }

    // Server aktif
    var currentServerName by remember(streamUrl) {
        val embed = streamEmbeds.find { it.iframeUrl == streamUrl }
        mutableStateOf(embed?.serverName ?: "Server Default")
    }
    var activeStreamUrl by remember(streamUrl) { mutableStateOf(streamUrl) }

    // Session playback untuk menangani recovery terkoordinasi
    var playbackSession by remember(activeStreamUrl, currentServerName) {
        mutableStateOf(
            PlaybackSession(
                streamUrl = activeStreamUrl,
                serverName = currentServerName,
                activeEngine = PlaybackEngine.EXO
            )
        )
    }

    // Player Manager Singleton
    val playerManager = remember(context) { PlayerManager.getInstance(context) }
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    // Bind lifecycle ke PlayerManager
    DisposableEffect(lifecycleOwner, playerManager) {
        playerManager.attachLifecycle(lifecycleOwner)
        onDispose {
            playerManager.detachLifecycle()
        }
    }

    // Ekstraksi dan resolusi kandidat stream
    LaunchedEffect(activeStreamUrl, currentServerName) {
        isResolving = true
        resolveErrorMessage = null
        hasPlaybackError = false
        playbackErrorMessage = ""
        useNativeExo = true

        try {
            val candidates = StreamResolver.resolveCandidates(context, activeStreamUrl, currentServerName)
            val topCandidate = candidates.firstOrNull()
            val resolved = topCandidate?.toResolvedStream()
                ?: StreamResolver.resolve(context, activeStreamUrl, currentServerName)
            resolvedStreamState = resolved
            playbackSession = PlaybackSession(
                streamUrl = activeStreamUrl,
                serverName = currentServerName,
                activeEngine = if (resolved.isDirect) PlaybackEngine.EXO else PlaybackEngine.WEBVIEW,
                resolvedStream = resolved,
                candidates = candidates,
                currentCandidateIndex = 0
            )
            if (resolved.isDirect) {
                useNativeExo = true
            } else {
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

    // State Playback & UI
    var isPlayingState by remember { mutableStateOf(false) }
    var playbackState by remember { mutableStateOf(Player.STATE_IDLE) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var isBuffering by remember { mutableStateOf(false) }

    // State Kontrol
    var showControls by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var isSeeking by remember { mutableStateOf(false) }
    var controlsInteractionTrigger by remember { mutableLongStateOf(0L) }
    val resetControlsTimer: () -> Unit = { controlsInteractionTrigger = System.currentTimeMillis() }
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var currentQuality by remember { mutableStateOf("Auto") }
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var isFullscreen by remember { mutableStateOf(false) }

    // State Dialog
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showServerSelector by remember { mutableStateOf(false) }

    // Double-Tap Seek Animation
    var doubleTapSeekSide by remember { mutableStateOf<String?>(null) }
    var doubleTapSeekAmount by remember { mutableIntStateOf(0) }
    var doubleTapAnimTrigger by remember { mutableLongStateOf(0L) }
    var bandwidthSpeedText by remember { mutableStateOf("HD Auto") }

    LaunchedEffect(doubleTapAnimTrigger) {
        if (doubleTapAnimTrigger > 0L) {
            delay(750)
            doubleTapSeekSide = null
            doubleTapSeekAmount = 0
        }
    }

    // Fullscreen Status (Baterai & Jam)
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

    // Lifecycle listener ExoPlayer
    DisposableEffect(activeStreamUrl, currentServerName, playerManager) {
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
                    is PlaybackAction.NextCandidate -> {
                        val safeCandUrl = PlayerManager.redactUrl(action.candidate.url)
                        Log.i("ExoVideoPlayer", "Action NextCandidate: ${action.reason} -> $safeCandUrl")
                        playbackSession = playbackSession.advanceToNextCandidate()
                        val nextStream = action.candidate.toResolvedStream()
                        resolvedStreamState = nextStream
                        if (nextStream.isDirect) {
                            useNativeExo = true
                            playerManager.prepareCandidate(action.candidate, playWhenReady = true)
                        } else {
                            playbackSession = playbackSession.switchToWeb()
                            useNativeExo = false
                        }
                    }
                    is PlaybackAction.ReResolve -> {
                        Log.i("ExoVideoPlayer", "Action ReResolve: ${action.reason}")
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
                        Log.i("ExoVideoPlayer", "Action RetryExo backoff ${action.delayMs}ms")
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
                        playerManager.pause()
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
            refererUrl = activeStreamUrl,
            externalListener = listener
        )
        exoPlayer = instance

        onDispose {
            playerManager.releasePlayer()
            exoPlayer = null
        }
    }

    // Effect saat stream ter-resolusi atau berubah
    LaunchedEffect(resolvedStreamState, useNativeExo) {
        val stream = resolvedStreamState
        if (stream != null && stream.isDirect && useNativeExo) {
            playerManager.prepareStream(stream)
        } else if (!useNativeExo) {
            playerManager.pause()
        }
    }

    // Auto-hide controls
    LaunchedEffect(showControls, isPlayingState, isSeeking, controlsInteractionTrigger, isLocked) {
        if (showControls && isPlayingState && !isLocked && !isSeeking) {
            delay(3000)
            showControls = false
        }
    }

    // Polling posisi & bandwidth saat memutar
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

    // Konten Player Utama
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
                // Web Engine Sandbox
                val webUrl = resolvedStreamState?.originalIframeUrl?.takeIf { it.isNotBlank() && !it.contains(".m3u8") }
                    ?: activeStreamUrl
                WebPlayerView(
                    url = webUrl,
                    title = title,
                    headers = resolvedStreamState?.headers ?: emptyMap(),
                    baseUrl = resolvedStreamState?.originalIframeUrl
                        ?: resolvedStreamState?.headers?.get("Referer")
                        ?: activeStreamUrl,
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
                // Native ExoPlayer Surface
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
                    onReset = { view -> view.player = null },
                    onRelease = { view -> view.player = null },
                    modifier = Modifier
                        .fillMaxSize()
                        .playerGestureHandler(
                            onTap = {
                                showControls = !showControls
                                if (showControls) resetControlsTimer()
                            },
                            onDoubleTapSeek = { isRight ->
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
                )

                // Indikator Resolving
                if (isResolving) {
                    ResolvingOverlay()
                }

                // Indikator Buffering
                if (isBuffering && !isResolving && !hasPlaybackError) {
                    BufferingOverlay()
                }

                // Overlay Error Playback
                if (hasPlaybackError) {
                    PlaybackErrorOverlay(
                        errorMessage = playbackErrorMessage,
                        onRetry = {
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
                        onSwitchToWeb = {
                            hasPlaybackError = false
                            useNativeExo = false
                        },
                        onOpenServerSelector = { showServerSelector = true }
                    )
                }

                // Visual Feedback Double-Tap Seek
                DoubleTapSeekFeedback(
                    seekSide = doubleTapSeekSide,
                    seekAmount = doubleTapSeekAmount,
                    isFullscreen = fullScreenMode
                )

                // Overlay Kontrol (Top Bar, Center Controls, Bottom Bar)
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    PlayerControlsOverlay(
                        title = title,
                        isPlaying = isPlayingState,
                        currentPosition = currentPosition,
                        duration = duration,
                        playbackSpeed = playbackSpeed,
                        bandwidthSpeedText = bandwidthSpeedText,
                        currentServerName = currentServerName,
                        resizeMode = resizeMode,
                        isLocked = isLocked,
                        isFullscreen = fullScreenMode,
                        batteryLevel = batteryLevel,
                        currentTimeString = currentTimeString,
                        currentEpisodeIndex = currentEpisodeIndex,
                        totalEpisodes = totalEpisodes,
                        onPlayPauseToggle = {
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
                        onSeek = { targetMs ->
                            isSeeking = true
                            currentPosition = targetMs
                            exoPlayer?.seekTo(targetMs)
                            isSeeking = false
                            resetControlsTimer()
                        },
                        onRewind10 = {
                            val pos = exoPlayer?.currentPosition ?: 0L
                            val target = (pos - 10000).coerceAtLeast(0)
                            exoPlayer?.seekTo(target)
                            currentPosition = target
                            resetControlsTimer()
                        },
                        onForward10 = {
                            val pos = exoPlayer?.currentPosition ?: 0L
                            val target = (pos + 10000).coerceAtMost(duration)
                            exoPlayer?.seekTo(target)
                            currentPosition = target
                            resetControlsTimer()
                        },
                        onEpisodeChange = { newEpIndex ->
                            onEpisodeChange(newEpIndex)
                            resetControlsTimer()
                        },
                        onPlaybackSpeedCycle = {
                            playbackSpeed = when (playbackSpeed) {
                                1.0f -> 1.25f
                                1.25f -> 1.5f
                                1.5f -> 2.0f
                                2.0f -> 0.75f
                                else -> 1.0f
                            }
                            exoPlayer?.setPlaybackSpeed(playbackSpeed)
                            resetControlsTimer()
                        },
                        onLockToggle = { isLocked = !isLocked },
                        onSettingsClick = { showSettingsDialog = true },
                        onServerSelectorClick = { showServerSelector = true },
                        onSwitchToWeb = { useNativeExo = false },
                        onToggleAspectRatio = {
                            resizeMode = when (resizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                            resetControlsTimer()
                        },
                        onToggleFullscreen = { isFullscreen = !isFullscreen },
                        onBack = onBack,
                        onDismissControls = { showControls = false }
                    )
                }
            }
        }
    }

    // Penanganan Dialog Fullscreen (Landscape Orientation & System Insets)
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

    // Dialog Pengaturan Kecepatan & Kualitas
    if (showSettingsDialog) {
        PlayerSettingsDialog(
            playbackSpeed = playbackSpeed,
            currentQuality = currentQuality,
            onSpeedSelected = { speed ->
                playbackSpeed = speed
                exoPlayer?.setPlaybackSpeed(speed)
            },
            onQualitySelected = { quality ->
                currentQuality = quality
                playerManager.setVideoQuality(quality)
                showSettingsDialog = false
            },
            onDismissRequest = { showSettingsDialog = false }
        )
    }

    // Dialog Pemilihan Server
    if (showServerSelector) {
        ServerSelectorDialog(
            streamEmbeds = streamEmbeds,
            currentServerName = currentServerName,
            onServerSelected = { embed ->
                currentServerName = embed.serverName
                activeStreamUrl = embed.iframeUrl
                showServerSelector = false
            },
            onDismissRequest = { showServerSelector = false }
        )
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
