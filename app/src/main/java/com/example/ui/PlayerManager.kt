package com.example.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import com.example.data.stream.ResolvedStream
import com.example.data.stream.StreamMediaType
import com.example.data.stream.detector.ProtocolDetector
import com.example.data.stream.model.ContainerFormat
import com.example.data.stream.model.StreamCandidate
import com.example.data.stream.model.StreamProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Dedicated Singleton PlayerManager for managing the ExoPlayer instance.
 * Ensures a single active player across the application lifecycle,
 * preserves stream headers, implements real BandwidthMeter, Media3 Track Selection for quality,
 * and robust error classification.
 */
@OptIn(UnstableApi::class)
class PlayerManager private constructor(context: Context) {

    private val appContext: Context = context.applicationContext

    // Internal active ExoPlayer instance
    private var exoPlayer: ExoPlayer? = null
    private var currentListener: Player.Listener? = null
    private var currentLifecycleOwner: LifecycleOwner? = null
    private var lifecycleObserver: DefaultLifecycleObserver? = null

    // Real Bandwidth Meter for live speed calculation
    private val bandwidthMeter: DefaultBandwidthMeter by lazy {
        DefaultBandwidthMeter.Builder(appContext).build()
    }

    // Reactive Player State Flows for Compose & UI observation
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackState = MutableStateFlow(Player.STATE_IDLE)
    val playbackState: StateFlow<Int> = _playbackState.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _lastError = MutableStateFlow<PlaybackException?>(null)
    val lastError: StateFlow<PlaybackException?> = _lastError.asStateFlow()

    private val _currentQuality = MutableStateFlow("Auto")
    val currentQuality: StateFlow<String> = _currentQuality.asStateFlow()

    // Current prepared stream
    private var activeStream: ResolvedStream? = null

    val player: ExoPlayer?
        get() = exoPlayer

    companion object {
        private const val TAG = "PlayerManager"
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        @Volatile
        private var INSTANCE: PlayerManager? = null

        fun getInstance(context: Context): PlayerManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PlayerManager(context).also { INSTANCE = it }
            }
        }
    }

    /**
     * Initializes player with referer URL string.
     */
    fun initializePlayer(
        refererUrl: String,
        externalListener: Player.Listener? = null
    ): ExoPlayer {
        val domainOrigin = try {
            val uri = Uri.parse(refererUrl)
            if (uri.scheme != null && uri.host != null) "${uri.scheme}://${uri.host}" else refererUrl
        } catch (e: Exception) {
            refererUrl
        }
        val headers = mapOf(
            "Referer" to refererUrl,
            "Origin" to domainOrigin
        )
        return initializePlayer(headers, externalListener)
    }

    /**
     * Initializes or gets the existing ExoPlayer instance. Reuses player instance when possible
     * to avoid heavy codec/audio hardware teardown across episode transitions.
     */
    @Synchronized
    fun initializePlayer(
        customHeaders: Map<String, String> = emptyMap(),
        externalListener: Player.Listener? = null
    ): ExoPlayer {
        // Reuse healthy player if already instantiated
        exoPlayer?.let { existing ->
            Log.d(TAG, "Reusing existing healthy ExoPlayer instance")
            currentListener?.let { existing.removeListener(it) }
            externalListener?.let { existing.addListener(it) }
            currentListener = externalListener
            return existing
        }

        Log.d(TAG, "Initializing fresh ExoPlayer instance...")

        // Build Default HTTP DataSource with sanitized stream headers
        val safeHeaders = mutableMapOf<String, String>()
        safeHeaders["Accept"] = "*/*"
        customHeaders.forEach { (key, value) ->
            val k = key.trim()
            val v = value.trim()
            if (k.isNotEmpty() && v.isNotEmpty() &&
                !k.startsWith("Sec-Fetch-", ignoreCase = true) &&
                !k.equals("Host", ignoreCase = true) &&
                !k.equals("Content-Length", ignoreCase = true) &&
                !k.equals("User-Agent", ignoreCase = true)
            ) {
                safeHeaders[k] = v
            }
        }

        val userAgent = customHeaders["User-Agent"]?.takeIf { it.isNotBlank() } ?: DEFAULT_USER_AGENT

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(25000)
            .setReadTimeoutMs(25000)
            .setTransferListener(bandwidthMeter)
            .setDefaultRequestProperties(safeHeaders)

        val mediaSourceFactory = DefaultMediaSourceFactory(appContext)
            .setDataSourceFactory(httpDataSourceFactory)

        // Custom load control for fast buffering
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15000, // minBufferMs
                50000, // maxBufferMs
                2500,  // bufferForPlaybackMs
                5000   // bufferForPlaybackAfterRebufferMs
            )
            .build()

        // Step 3: Build ExoPlayer with decoder fallback for emulator compatibility
        val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(appContext)
            .setEnableDecoderFallback(true)

        val newPlayer = ExoPlayer.Builder(appContext, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setBandwidthMeter(bandwidthMeter)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                playWhenReady = true
            }

        // Step 4: Register listener
        val internalListener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                externalListener?.onIsPlayingChanged(playing)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                _isPlaying.value = playWhenReady && _playbackState.value != Player.STATE_ENDED
                externalListener?.onPlayWhenReadyChanged(playWhenReady, reason)
            }

            override fun onPlaybackStateChanged(state: Int) {
                _playbackState.value = state
                _isBuffering.value = (state == Player.STATE_BUFFERING)
                if (state == Player.STATE_READY) {
                    _duration.value = newPlayer.duration.coerceAtLeast(0L)
                } else if (state == Player.STATE_ENDED) {
                    _isPlaying.value = false
                }
                externalListener?.onPlaybackStateChanged(state)
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, "ExoPlayer error (${error.errorCodeName}): ${error.message}")
                _lastError.value = error
                _isPlaying.value = false
                _isBuffering.value = false
                externalListener?.onPlayerError(error)
            }
        }

        newPlayer.addListener(internalListener)
        currentListener = internalListener
        exoPlayer = newPlayer
        _lastError.value = null

        return newPlayer
    }

    /**
     * Prepares and starts playback of a [ResolvedStream] with dedicated stream-specific request headers
     * applied directly to the MediaSource. This guarantees that master playlist, variant playlists,
     * encryption keys, and video segments all receive the stream's custom Referer, Origin, Cookie, and User-Agent.
     */
    @Synchronized
    fun prepareStream(stream: ResolvedStream, playWhenReady: Boolean = true) {
        val p = exoPlayer ?: return
        activeStream = stream

        try {
            Log.d(TAG, "Preparing ResolvedStream with stream-specific headers: ${stream.url} (type=${stream.mediaType}, headers=${stream.headers.keys})")
            p.stop()
            p.clearMediaItems()

            val uri = Uri.parse(stream.url)
            val mediaItemBuilder = MediaItem.Builder().setUri(uri)

            val lower = stream.url.lowercase()
            val detectedProtocol = when (stream.mediaType) {
                StreamMediaType.HLS -> StreamProtocol.HLS
                StreamMediaType.DASH -> StreamProtocol.DASH
                StreamMediaType.MP4 -> StreamProtocol.PROGRESSIVE
                else -> ProtocolDetector.detect(stream.url, stream.contentType, "")
            }

            val mimeType: String? = when (detectedProtocol) {
                StreamProtocol.HLS -> MimeTypes.APPLICATION_M3U8
                StreamProtocol.DASH -> MimeTypes.APPLICATION_MPD
                StreamProtocol.SMOOTH_STREAMING -> "application/vnd.ms-sstr+xml"
                StreamProtocol.RTSP -> "application/x-rtsp"
                StreamProtocol.PROGRESSIVE -> {
                    when {
                        lower.contains(".webm") || stream.contentType.contains("webm", ignoreCase = true) -> MimeTypes.VIDEO_WEBM
                        lower.contains(".mkv") || stream.contentType.contains("matroska", ignoreCase = true) -> MimeTypes.VIDEO_MATROSKA
                        lower.contains(".ts") || stream.contentType.contains("mp2t", ignoreCase = true) -> MimeTypes.VIDEO_MP2T
                        lower.endsWith(".mp4") || lower.contains(".mp4?") || stream.contentType.contains("mp4", ignoreCase = true) -> MimeTypes.VIDEO_MP4
                        else -> null // Auto-detection by Media3 extractors
                    }
                }
                else -> null
            }
            if (mimeType != null) {
                mediaItemBuilder.setMimeType(mimeType)
            }
            val mediaItem = mediaItemBuilder.build()

            // Build stream-specific HTTP DataSource Factory with headers
            val safeHeaders = mutableMapOf<String, String>()
            safeHeaders["Accept"] = "*/*"
            stream.headers.forEach { (key, value) ->
                val k = key.trim()
                val v = value.trim()
                if (k.isNotEmpty() && v.isNotEmpty() &&
                    !k.startsWith("Sec-Fetch-", ignoreCase = true) &&
                    !k.equals("Host", ignoreCase = true) &&
                    !k.equals("Content-Length", ignoreCase = true) &&
                    !k.equals("User-Agent", ignoreCase = true)
                ) {
                    safeHeaders[k] = v
                }
            }

            val userAgent = stream.headers["User-Agent"]?.takeIf { it.isNotBlank() } ?: DEFAULT_USER_AGENT

            val streamHttpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(userAgent)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(25000)
                .setReadTimeoutMs(25000)
                .setTransferListener(bandwidthMeter)
                .setDefaultRequestProperties(safeHeaders)

            val mediaSourceFactory = DefaultMediaSourceFactory(appContext)
                .setDataSourceFactory(streamHttpDataSourceFactory)

            val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)

            p.setMediaSource(mediaSource)
            p.prepare()
            p.playWhenReady = playWhenReady
            if (playWhenReady) {
                p.play()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing media source: ${e.message}", e)
        }
    }

    /**
     * Mempersiapkan media langsung dari [StreamCandidate] dengan kebijakan header granular.
     */
    @Synchronized
    fun prepareCandidate(candidate: StreamCandidate, playWhenReady: Boolean = true) {
        prepareStream(candidate.toResolvedStream(), playWhenReady)
    }

    /**
     * Overload for raw URL string using intelligent ProtocolDetector instead of guessing MP4.
     */
    fun prepareMedia(mediaUrl: String, playWhenReady: Boolean = true) {
        val protocol = ProtocolDetector.detect(mediaUrl, "", "")
        val mediaType = when (protocol) {
            StreamProtocol.HLS -> StreamMediaType.HLS
            StreamProtocol.DASH -> StreamMediaType.DASH
            StreamProtocol.PROGRESSIVE -> StreamMediaType.MP4
            else -> StreamMediaType.UNKNOWN
        }
        val stream = ResolvedStream(
            url = mediaUrl,
            mediaType = mediaType,
            isDirectVideo = true
        )
        prepareStream(stream, playWhenReady)
    }

    /**
     * Changes video quality using real Media3 TrackSelectionParameters.
     * Sets ceiling constraints without rigid min-size boundaries that could cause 0 track matches.
     * Supports: "Auto", "1080p", "720p", "480p", "360p".
     */
    fun setVideoQuality(quality: String) {
        val p = exoPlayer ?: return
        _currentQuality.value = quality

        val builder = p.trackSelectionParameters.buildUpon()
        builder.clearVideoSizeConstraints()

        when (quality.lowercase()) {
            "1080p" -> {
                builder.setMaxVideoSize(1920, 1080)
            }
            "720p" -> {
                builder.setMaxVideoSize(1280, 720)
            }
            "480p" -> {
                builder.setMaxVideoSize(854, 480)
            }
            "360p" -> {
                builder.setMaxVideoSize(640, 360)
            }
            else -> {
                // Auto: Unconstrained adaptive bitrate
            }
        }

        p.trackSelectionParameters = builder.build()
        Log.d(TAG, "Applied video quality constraint: $quality")
    }

    /**
     * Gets real estimated bitrate in Mbps from DefaultBandwidthMeter.
     */
    fun getEstimatedBitrateMbps(): Float {
        val bps = bandwidthMeter.bitrateEstimate
        if (bps <= 0L) return 0f
        return (bps.toFloat() / 1_000_000f)
    }

    /**
     * Formats real bandwidth for UI display.
     */
    fun getFormattedBandwidthSpeed(): String {
        val mbps = getEstimatedBitrateMbps()
        return if (mbps > 0.05f) {
            String.format(java.util.Locale.US, "%.1f Mbps", mbps)
        } else {
            "HD Auto"
        }
    }

    fun retryPlayback(onReResolveRequired: (() -> Unit)? = null) {
        val current = activeStream
        val lastErr = _lastError.value
        val isAuthError = lastErr?.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
        
        if (isAuthError && onReResolveRequired != null) {
            Log.i(TAG, "Auth error detected on retry: triggering fresh re-resolve...")
            onReResolveRequired()
        } else if (current != null) {
            prepareStream(current, playWhenReady = true)
        } else {
            exoPlayer?.prepare()
            exoPlayer?.play()
        }
    }

    fun togglePlayPause() {
        exoPlayer?.let { p ->
            if (p.isPlaying) {
                p.pause()
            } else {
                p.play()
            }
        }
    }

    fun pausePlayer() {
        try {
            exoPlayer?.pause()
            _isPlaying.value = false
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing player: ${e.message}", e)
        }
    }

    fun playPlayer() {
        try {
            exoPlayer?.play()
            _isPlaying.value = true
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming player: ${e.message}", e)
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.let { p ->
            try {
                val clampedPosition = positionMs.coerceIn(0L, p.duration.coerceAtLeast(0L))
                p.seekTo(clampedPosition)
                _currentPosition.value = clampedPosition
            } catch (e: Exception) {
                Log.e(TAG, "Error seeking player: ${e.message}", e)
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.let { p ->
            try {
                p.setPlaybackSpeed(speed.coerceIn(0.25f, 2.0f))
            } catch (e: Exception) {
                Log.e(TAG, "Error setting playback speed: ${e.message}", e)
            }
        }
    }

    fun updatePosition(): Long {
        val pos = exoPlayer?.currentPosition ?: 0L
        _currentPosition.value = pos
        return pos
    }

    fun attachLifecycle(lifecycleOwner: LifecycleOwner) {
        detachLifecycle()
        currentLifecycleOwner = lifecycleOwner

        val observer = object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                // Pause playback when app or screen moves to background
                pausePlayer()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                // Release player resources when screen lifecycle is completely destroyed
                releasePlayer()
                detachLifecycle()
            }
        }

        lifecycleObserver = observer
        lifecycleOwner.lifecycle.addObserver(observer)
    }

    fun detachLifecycle() {
        currentLifecycleOwner?.let { owner ->
            lifecycleObserver?.let { obs ->
                owner.lifecycle.removeObserver(obs)
            }
        }
        currentLifecycleOwner = null
        lifecycleObserver = null
    }

    @Synchronized
    fun releasePlayer() {
        exoPlayer?.let { p ->
            try {
                Log.d(TAG, "Explicitly releasing ExoPlayer instance...")
                currentListener?.let { l -> p.removeListener(l) }
                currentListener = null
                p.stop()
                p.clearMediaItems()
                p.release()
                Log.d(TAG, "ExoPlayer instance cleanly released")
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing ExoPlayer: ${e.message}", e)
            } finally {
                exoPlayer = null
                _isPlaying.value = false
                _isBuffering.value = false
                _playbackState.value = Player.STATE_IDLE
            }
        }
    }
}
