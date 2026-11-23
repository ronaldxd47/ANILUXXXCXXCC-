package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.BatteryManager
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import com.example.playback.PlaybackController
import com.example.ui.player.WebPlayerView
import com.example.ui.player.controls.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Komponen Utama Video Player (Thin Orchestrator).
 * Seluruh resolusi stream, routing PlaybackRouter, dan error recovery dikendalikan oleh
 * PlaybackController (bebas race-condition dengan Compose lifecycle dan bebas double-prepare).
 */
@OptIn(UnstableApi::class)
@Composable
fun ExoVideoPlayer(
    title: String,
    streamUrl: String,
    streamEmbeds: List<StreamEmbed>,
    currentEpisodeIndex: Int,
    totalEpisodes: Int,
    hasPrevEpisode: Boolean = currentEpisodeIndex > 0,
    hasNextEpisode: Boolean = currentEpisodeIndex in 0 until (totalEpisodes - 1),
    nextEpisodeTitle: String? = null,
    onPrevEpisode: () -> Unit = { if (currentEpisodeIndex > 0) Unit },
    onNextEpisode: () -> Unit = {},
    onEpisodeChange: (Int) -> Unit,
    onBack: () -> Unit,
    initialPositionMs: Long = 0L,
    hardwareAccel: Boolean = true,
    dataSaver: Boolean = false,
    autoPlayNext: Boolean = true,
    defaultQuality: String = "Auto",
    onSaveProgress: (positionMs: Long, durationMs: Long) -> Unit = { _, _ -> },
    onPlaybackEnded: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var currentServerName by remember(streamUrl) {
        val embed = streamEmbeds.find { it.iframeUrl == streamUrl }
        mutableStateOf(embed?.serverName ?: "Server Default")
    }
    var activeStreamUrl by remember(streamUrl) { mutableStateOf(streamUrl) }

    val playerManager = remember(context) { PlayerManager.getInstance(context) }
    val controller = remember(context, playerManager) { PlaybackController(context, playerManager, scope) }
    val controllerState by controller.state.collectAsState()
    val availableQualities by playerManager.availableQualities.collectAsState()
    val currentQuality by playerManager.currentQuality.collectAsState()

    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var isPlayingState by remember { mutableStateOf(false) }
    var playbackState by remember { mutableStateOf(Player.STATE_IDLE) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isBuffering by remember { mutableStateOf(false) }

    var showControls by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var isSeeking by remember { mutableStateOf(false) }
    var controlsInteractionTrigger by remember { mutableLongStateOf(0L) }
    val resetControlsTimer: () -> Unit = { controlsInteractionTrigger = System.currentTimeMillis() }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var isFullscreen by remember { mutableStateOf(false) }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showServerSelector by remember { mutableStateOf(false) }

    var doubleTapSeekSide by remember { mutableStateOf<String?>(null) }
    var doubleTapSeekAmount by remember { mutableIntStateOf(0) }
    var doubleTapAnimTrigger by remember { mutableLongStateOf(0L) }
    var bandwidthSpeedText by remember { mutableStateOf("Auto") }
    var showNextCountdown by remember(streamUrl) { mutableStateOf(false) }
    var nextCountdownSeconds by remember(streamUrl) { mutableIntStateOf(5) }

    // Terapkan preferensi pemutaran pengguna (hardware accel, data saver, kualitas default)
    LaunchedEffect(hardwareAccel, dataSaver, defaultQuality) {
        playerManager.configureOptions(
            hardwareAccel = hardwareAccel,
            dataSaver = dataSaver,
            defaultQuality = defaultQuality
        )
    }

    // Lifecycle ExoPlayer tunggal selama player aktif (reuses codec across server switches)
    DisposableEffect(lifecycleOwner, playerManager) {
        playerManager.attachLifecycle(lifecycleOwner)

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isPlayingState = isPlaying
                if (!isPlaying && currentPosition > 0L) {
                    onSaveProgress(currentPosition, duration)
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                isPlayingState = playWhenReady && playbackState != Player.STATE_ENDED
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    exoPlayer?.let {
                        duration = it.duration.coerceAtLeast(0L)
                        currentPosition = it.currentPosition.coerceAtLeast(0L)
                    }
                } else if (state == Player.STATE_ENDED) {
                    isPlayingState = false
                    onSaveProgress(duration, duration)
                    if (autoPlayNext && hasNextEpisode) {
                        nextCountdownSeconds = 5
                        showNextCountdown = true
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val pos = exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: currentPosition
                controller.handlePlayerError(error, pos)
            }
        }

        val instance = playerManager.initializePlayer(
            refererUrl = streamUrl,
            externalListener = listener
        )
        exoPlayer = instance

        onDispose {
            val lastPos = exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: currentPosition
            val lastDur = exoPlayer?.duration?.coerceAtLeast(0L) ?: duration
            if (lastPos > 0L) {
                onSaveProgress(lastPos, lastDur)
            }
            playerManager.detachLifecycle()
            playerManager.releasePlayer()
            exoPlayer = null
        }
    }

    // Sinkronkan reference exoPlayer jika PlayerManager merebuild engine (mis. toggle Hardware Accel)
    LaunchedEffect(playbackState, controllerState.resolvedStream) {
        val currentInstance = playerManager.player
        if (currentInstance != null && currentInstance !== exoPlayer) {
            exoPlayer = currentInstance
        }
    }

    // Trigger resolusi & prepare tunggal lewat PlaybackController (zero double-prepare)
    LaunchedEffect(activeStreamUrl, currentServerName) {
        val resumePos = if (activeStreamUrl == streamUrl) initialPositionMs else currentPosition
        controller.startStream(
            embedUrl = activeStreamUrl,
            serverName = currentServerName,
            resumePositionMs = resumePos
        )
    }

    LaunchedEffect(doubleTapAnimTrigger) {
        if (doubleTapAnimTrigger > 0L) {
            delay(750)
            doubleTapSeekSide = null
            doubleTapSeekAmount = 0
        }
    }

    LaunchedEffect(showNextCountdown) {
        if (showNextCountdown) {
            for (sec in 5 downTo 1) {
                nextCountdownSeconds = sec
                delay(1000)
                if (!showNextCountdown) return@LaunchedEffect
            }
            showNextCountdown = false
            onPlaybackEnded()
        }
    }

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

    LaunchedEffect(showControls, isPlayingState, isSeeking, controlsInteractionTrigger, isLocked) {
        if (showControls && isPlayingState && !isLocked && !isSeeking) {
            delay(3000)
            showControls = false
        }
    }

    LaunchedEffect(isPlayingState) {
        var ticks = 0
        while (isPlayingState) {
            exoPlayer?.let { p ->
                currentPosition = p.currentPosition.coerceAtLeast(0L)
                duration = p.duration.coerceAtLeast(0L)
                bandwidthSpeedText = playerManager.getFormattedBandwidthSpeed()
                ticks++
                if (ticks % 5 == 0 && currentPosition > 0L) {
                    onSaveProgress(currentPosition, duration)
                }
            }
            delay(1000)
        }
    }

    @Composable
    fun PlayerContent(
        fullScreenMode: Boolean,
        modifier: Modifier = Modifier
    ) {
        val isWebMode = controllerState.activeEngine == PlaybackEngine.WEBVIEW ||
            (controllerState.resolvedStream != null && !controllerState.resolvedStream!!.isDirect)

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (isWebMode && !controllerState.isResolving) {
                val stream = controllerState.resolvedStream
                // Jika fallbackPreferIframe = false dan URL .m3u8 tersedia, biarkan WebPlayerView memakai HLS.js runtime
                val webUrl = if (!controllerState.fallbackPreferIframe && stream?.url?.isNotBlank() == true) {
                    stream.url
                } else {
                    stream?.originalIframeUrl?.takeIf { it.isNotBlank() && !it.contains(".m3u8", ignoreCase = true) }
                        ?: activeStreamUrl
                }

                WebPlayerView(
                    url = webUrl,
                    title = title,
                    headers = stream?.headers ?: emptyMap(),
                    baseUrl = stream?.originalIframeUrl
                        ?: stream?.headers?.get("Referer")
                        ?: activeStreamUrl,
                    onBack = onBack,
                    onSwitchToExo = { controller.switchToExoManual(currentPosition) },
                    onOpenServerSelector = { showServerSelector = true },
                    onToggleFullscreen = { isFullscreen = !isFullscreen },
                    isFullscreen = fullScreenMode
                )
            } else {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            this.resizeMode = resizeMode
                            this.player = playerManager.player ?: exoPlayer
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { view ->
                        view.resizeMode = resizeMode
                        val activeInstance = playerManager.player ?: exoPlayer
                        if (view.player != activeInstance) {
                            view.player = activeInstance
                        }
                    },
                    onReset = { view -> view.player = null },
                    onRelease = { view -> view.player = null },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("exo_player_surface")
                        .playerGestureHandler(
                            onTap = {
                                showControls = !showControls
                                if (showControls) resetControlsTimer()
                            },
                            onDoubleTapSeek = { isRight ->
                                (playerManager.player ?: exoPlayer)?.let { p ->
                                    val side = if (isRight) "right" else "left"
                                    if (doubleTapSeekSide == side) {
                                        doubleTapSeekAmount += 10
                                    } else {
                                        doubleTapSeekSide = side
                                        doubleTapSeekAmount = 10
                                    }
                                    doubleTapAnimTrigger = System.currentTimeMillis()
                                    val delta = if (isRight) 10000L else -10000L
                                    val maxDur = p.duration.coerceAtLeast(duration)
                                    val newPos = (p.currentPosition + delta).coerceIn(0L, maxDur)
                                    p.seekTo(newPos)
                                    currentPosition = newPos
                                    showControls = true
                                    resetControlsTimer()
                                }
                            }
                        )
                )

                if (controllerState.isResolving) {
                    ResolvingOverlay()
                }

                if (isBuffering && !controllerState.isResolving && !controllerState.hasPlaybackError) {
                    BufferingOverlay()
                }

                if (controllerState.hasPlaybackError) {
                    PlaybackErrorOverlay(
                        errorMessage = controllerState.playbackErrorMessage,
                        onRetry = { controller.retryManual(currentPosition) },
                        onSwitchToWeb = { controller.switchToWebManual() },
                        onOpenServerSelector = { showServerSelector = true }
                    )
                }

                if (showNextCountdown) {
                    AutoPlayCountdownOverlay(
                        nextEpisodeTitle = nextEpisodeTitle.orEmpty(),
                        secondsRemaining = nextCountdownSeconds,
                        onPlayNow = {
                            showNextCountdown = false
                            onPlaybackEnded()
                        },
                        onCancel = { showNextCountdown = false }
                    )
                }

                DoubleTapSeekFeedback(
                    seekSide = doubleTapSeekSide,
                    seekAmount = doubleTapSeekAmount,
                    isFullscreen = fullScreenMode
                )

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
                        hasPrevEpisode = hasPrevEpisode,
                        hasNextEpisode = hasNextEpisode,
                        showSkipIntro = duration > 120_000L && currentPosition < 360_000L,
                        onPlayPauseToggle = {
                            val activeInstance = playerManager.player ?: exoPlayer
                            activeInstance?.let {
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
                            (playerManager.player ?: exoPlayer)?.seekTo(targetMs)
                            isSeeking = false
                            resetControlsTimer()
                        },
                        onRewind10 = {
                            val p = playerManager.player ?: exoPlayer
                            val pos = p?.currentPosition ?: 0L
                            val target = (pos - 10000).coerceAtLeast(0L)
                            p?.seekTo(target)
                            currentPosition = target
                            resetControlsTimer()
                        },
                        onForward10 = {
                            val p = playerManager.player ?: exoPlayer
                            val pos = p?.currentPosition ?: 0L
                            val maxDur = p?.duration?.coerceAtLeast(duration) ?: duration
                            val target = (pos + 10000).coerceAtMost(maxDur)
                            p?.seekTo(target)
                            currentPosition = target
                            resetControlsTimer()
                        },
                        onSkipIntro = {
                            val p = playerManager.player ?: exoPlayer
                            val pos = p?.currentPosition ?: currentPosition
                            val maxDur = p?.duration?.coerceAtLeast(duration) ?: duration
                            val target = (pos + 85_000L).coerceAtMost(maxDur)
                            p?.seekTo(target)
                            currentPosition = target
                            resetControlsTimer()
                        },
                        onPrevEpisode = {
                            onSaveProgress(currentPosition, duration)
                            onPrevEpisode()
                            resetControlsTimer()
                        },
                        onNextEpisode = {
                            onSaveProgress(currentPosition, duration)
                            onNextEpisode()
                            resetControlsTimer()
                        },
                        onEpisodeChange = { newEpIndex ->
                            onSaveProgress(currentPosition, duration)
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
                            playerManager.setPlaybackSpeed(playbackSpeed)
                            resetControlsTimer()
                        },
                        onLockToggle = { isLocked = !isLocked },
                        onSettingsClick = { showSettingsDialog = true },
                        onServerSelectorClick = { showServerSelector = true },
                        onSwitchToWeb = { controller.switchToWebManual() },
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
                    val controllerInsets = WindowCompat.getInsetsController(win, win.decorView)
                    controllerInsets.hide(WindowInsetsCompat.Type.systemBars())
                    controllerInsets.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
                onDispose {
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    activity?.window?.let { win ->
                        val controllerInsets = WindowCompat.getInsetsController(win, win.decorView)
                        controllerInsets.show(WindowInsetsCompat.Type.systemBars())
                    }
                }
            }
            PlayerContent(fullScreenMode = true, modifier = Modifier.fillMaxSize())
        }
    } else {
        PlayerContent(fullScreenMode = false, modifier = modifier)
    }

    if (showSettingsDialog) {
        PlayerSettingsDialog(
            playbackSpeed = playbackSpeed,
            currentQuality = currentQuality,
            availableQualities = availableQualities,
            onSpeedSelected = { speed ->
                playbackSpeed = speed
                playerManager.setPlaybackSpeed(speed)
            },
            onQualitySelected = { quality ->
                playerManager.setVideoQuality(quality)
                showSettingsDialog = false
            },
            onDismissRequest = { showSettingsDialog = false }
        )
    }

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
