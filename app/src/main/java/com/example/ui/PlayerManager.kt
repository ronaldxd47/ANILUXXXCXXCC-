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
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
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
 * Dedicated Singleton PlayerManager untuk mengelola instance Media3 ExoPlayer.
 * Menjamin single active player, meneruskan header stream granular ke MediaSource,
 * menerapkan BandwidthMeter nyata, pemilihan track kualitas dinamis, opsi Data Saver / Hardware Accel,
 * serta menyimpan/memulihkan posisi pemutaran.
 */
@OptIn(UnstableApi::class)
class PlayerManager private constructor(context: Context) {

    private val appContext: Context = context.applicationContext

    private var exoPlayer: ExoPlayer? = null
    private var currentListener: Player.Listener? = null
    private var externalPlayerListener: Player.Listener? = null
    private var currentLifecycleOwner: LifecycleOwner? = null
    private var lifecycleObserver: DefaultLifecycleObserver? = null

    private var hardwareAccelEnabled: Boolean = true
    private var dataSaverEnabled: Boolean = false
    private var currentPlaybackSpeed: Float = 1.0f

    private val bandwidthMeter: DefaultBandwidthMeter by lazy {
        DefaultBandwidthMeter.Builder(appContext).build()
    }

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

    private val _availableQualities = MutableStateFlow(listOf("Auto", "1080p", "720p", "480p", "360p"))
    val availableQualities: StateFlow<List<String>> = _availableQualities.asStateFlow()

    private var activeStream: ResolvedStream? = null

    val player: ExoPlayer?
        get() = exoPlayer

    companion object {
        private const val TAG = "PlayerManager"
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

        @Volatile
        private var INSTANCE: PlayerManager? = null

        fun getInstance(context: Context): PlayerManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PlayerManager(context).also { INSTANCE = it }
            }
        }

        fun redactUrl(url: String): String {
            return url.replace(
                Regex("([?&](?:token|sig|signature|expires|auth|key|tkn)=)[^&]+", RegexOption.IGNORE_CASE)
            ) { "${it.groupValues[1]}REDACTED" }
        }
    }

    fun initializePlayer(
        refererUrl: String,
        externalListener: Player.Listener? = null
    ): ExoPlayer {
        val domainOrigin = try {
            val uri = Uri.parse(refererUrl)
            if (uri.scheme != null && uri.host != null) "${uri.scheme}://${uri.host}" else refererUrl
        } catch (_: Exception) {
            refererUrl
        }
        return initializePlayer(
            customHeaders = mapOf("Referer" to refererUrl, "Origin" to domainOrigin),
            externalListener = externalListener
        )
    }

    @Synchronized
    fun initializePlayer(
        customHeaders: Map<String, String> = emptyMap(),
        externalListener: Player.Listener? = null,
        hardwareAccel: Boolean = hardwareAccelEnabled
    ): ExoPlayer {
        val needsRebuildForAccel = exoPlayer != null && hardwareAccel != hardwareAccelEnabled
        hardwareAccelEnabled = hardwareAccel

        if (needsRebuildForAccel) {
            releasePlayer()
        }

        externalPlayerListener = externalListener

        exoPlayer?.let { existing ->
            Log.d(TAG, "Reusing existing healthy ExoPlayer instance")
            return existing
        }

        Log.d(TAG, "Initializing fresh ExoPlayer instance (hwAccel=$hardwareAccelEnabled)...")

        val safeHeaders = sanitizeHeaders(customHeaders)
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

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15000, // minBufferMs
                50000, // maxBufferMs
                2500,  // bufferForPlaybackMs
                5000   // bufferForPlaybackAfterRebufferMs
            )
            .build()

        val renderersFactory = DefaultRenderersFactory(appContext)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(
                if (hardwareAccelEnabled) DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                else DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
            )

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

        val internalListener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                externalPlayerListener?.onIsPlayingChanged(playing)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                _isPlaying.value = playWhenReady && _playbackState.value != Player.STATE_ENDED
                externalPlayerListener?.onPlayWhenReadyChanged(playWhenReady, reason)
            }

            override fun onPlaybackStateChanged(state: Int) {
                _playbackState.value = state
                _isBuffering.value = (state == Player.STATE_BUFFERING)
                if (state == Player.STATE_READY) {
                    _duration.value = newPlayer.duration.coerceAtLeast(0L)
                } else if (state == Player.STATE_ENDED) {
                    _isPlaying.value = false
                }
                externalPlayerListener?.onPlaybackStateChanged(state)
            }

            override fun onTracksChanged(tracks: Tracks) {
                val detectedHeights = mutableSetOf<Int>()
                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_VIDEO) {
                        for (i in 0 until group.length) {
                            val h = group.getTrackFormat(i).height
                            if (h > 0) detectedHeights.add(h)
                        }
                    }
                }
                if (detectedHeights.isNotEmpty()) {
                    val mapped = detectedHeights
                        .sortedDescending()
                        .map { h ->
                            when {
                                h >= 1000 -> "1080p"
                                h >= 700 -> "720p"
                                h >= 460 -> "480p"
                                else -> "360p"
                            }
                        }
                        .distinct()
                    _availableQualities.value = listOf("Auto") + mapped
                }
                externalPlayerListener?.onTracksChanged(tracks)
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, "ExoPlayer error (${error.errorCodeName}): ${error.message}")
                _lastError.value = error
                _isPlaying.value = false
                _isBuffering.value = false
                externalPlayerListener?.onPlayerError(error)
            }
        }

        newPlayer.addListener(internalListener)
        currentListener = internalListener
        exoPlayer = newPlayer
        _lastError.value = null
        applyTrackConstraints()

        return newPlayer
    }

    @Synchronized
    fun configureOptions(
        hardwareAccel: Boolean,
        dataSaver: Boolean,
        defaultQuality: String
    ) {
        val accelChanged = hardwareAccel != hardwareAccelEnabled
        dataSaverEnabled = dataSaver
        if (_currentQuality.value == "Auto" && defaultQuality.isNotBlank()) {
            _currentQuality.value = normalizeQualityLabel(defaultQuality)
        }
        if (accelChanged && exoPlayer != null) {
            val savedStream = activeStream
            val savedPos = exoPlayer?.currentPosition ?: 0L
            val wasPlaying = exoPlayer?.playWhenReady ?: true
            val listener = externalPlayerListener
            releasePlayer()
            initializePlayer(emptyMap(), listener, hardwareAccel)
            if (savedStream != null) {
                prepareStream(savedStream, playWhenReady = wasPlaying, startPositionMs = savedPos)
            }
        } else {
            hardwareAccelEnabled = hardwareAccel
            applyTrackConstraints()
        }
    }

    @Synchronized
    fun prepareStream(
        stream: ResolvedStream,
        playWhenReady: Boolean = true,
        startPositionMs: Long = 0L
    ) {
        val p = exoPlayer ?: return
        activeStream = stream
        _lastError.value = null

        try {
            val safeLogUrl = redactUrl(stream.url)
            Log.d(
                TAG,
                "Preparing ResolvedStream: $safeLogUrl (protocol=${stream.protocol}, container=${stream.container}, startMs=$startPositionMs)"
            )
            p.stop()
            p.clearMediaItems()

            val uri = Uri.parse(stream.url)
            val mediaItemBuilder = MediaItem.Builder().setUri(uri)

            val detectedProtocol = if (stream.protocol != StreamProtocol.UNKNOWN) {
                stream.protocol
            } else when (stream.mediaType) {
                StreamMediaType.HLS -> StreamProtocol.HLS
                StreamMediaType.DASH -> StreamProtocol.DASH
                StreamMediaType.PROGRESSIVE, StreamMediaType.MP4 -> StreamProtocol.PROGRESSIVE
                else -> ProtocolDetector.detect(stream.url, stream.contentType, "")
            }

            val lower = stream.url.lowercase()
            val mimeType: String? = when (detectedProtocol) {
                StreamProtocol.HLS -> MimeTypes.APPLICATION_M3U8
                StreamProtocol.DASH -> MimeTypes.APPLICATION_MPD
                StreamProtocol.SMOOTH_STREAMING -> "application/vnd.ms-sstr+xml"
                StreamProtocol.RTSP -> "application/x-rtsp"
                StreamProtocol.PROGRESSIVE -> when (stream.container) {
                    ContainerFormat.WEBM -> MimeTypes.VIDEO_WEBM
                    ContainerFormat.MATROSKA -> MimeTypes.VIDEO_MATROSKA
                    ContainerFormat.MPEG_TS -> MimeTypes.VIDEO_MP2T
                    ContainerFormat.MP4, ContainerFormat.FMP4 -> MimeTypes.VIDEO_MP4
                    ContainerFormat.FLV -> "video/x-flv"
                    else -> when {
                        lower.contains(".webm") || stream.contentType.contains("webm", ignoreCase = true) -> MimeTypes.VIDEO_WEBM
                        lower.contains(".mkv") || stream.contentType.contains("matroska", ignoreCase = true) -> MimeTypes.VIDEO_MATROSKA
                        lower.contains(".ts") || stream.contentType.contains("mp2t", ignoreCase = true) -> MimeTypes.VIDEO_MP2T
                        lower.endsWith(".mp4") || lower.contains(".mp4?") || stream.contentType.contains("mp4", ignoreCase = true) -> MimeTypes.VIDEO_MP4
                        else -> null
                    }
                }
                else -> null
            }

            if (mimeType != null) {
                mediaItemBuilder.setMimeType(mimeType)
            }
            if (stream.subtitles.isNotEmpty()) {
                val subtitleConfigs = stream.subtitles.mapIndexed { idx, sub ->
                    val subMime = sub.mimeType ?: when {
                        sub.url.contains(".srt", ignoreCase = true) -> MimeTypes.APPLICATION_SUBRIP
                        sub.url.contains(".ass", ignoreCase = true) || sub.url.contains(".ssa", ignoreCase = true) -> MimeTypes.TEXT_SSA
                        else -> MimeTypes.TEXT_VTT
                    }
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                        .setMimeType(subMime)
                        .setLanguage(sub.language)
                        .setLabel(sub.label)
                        .setSelectionFlags(if (idx == 0) C.SELECTION_FLAG_DEFAULT else 0)
                        .build()
                }
                mediaItemBuilder.setSubtitleConfigurations(subtitleConfigs)
            }
            val mediaItem = mediaItemBuilder.build()

            val safeHeaders = sanitizeHeaders(stream.headers)
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

            p.setMediaSource(mediaSource, startPositionMs.coerceAtLeast(0L))
            p.prepare()
            p.setPlaybackSpeed(currentPlaybackSpeed)
            applyTrackConstraints()
            p.playWhenReady = playWhenReady
            if (playWhenReady) {
                p.play()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing media source: ${e.message}", e)
        }
    }

    @Synchronized
    fun prepareCandidate(
        candidate: StreamCandidate,
        playWhenReady: Boolean = true,
        startPositionMs: Long = 0L
    ) {
        prepareStream(candidate.toResolvedStream(), playWhenReady, startPositionMs)
    }

    fun prepareMedia(mediaUrl: String, playWhenReady: Boolean = true) {
        val protocol = ProtocolDetector.detect(mediaUrl, "", "")
        val mediaType = when (protocol) {
            StreamProtocol.HLS -> StreamMediaType.HLS
            StreamProtocol.DASH -> StreamMediaType.DASH
            StreamProtocol.PROGRESSIVE -> StreamMediaType.PROGRESSIVE
            else -> StreamMediaType.UNKNOWN
        }
        val stream = ResolvedStream(
            url = mediaUrl,
            mediaType = mediaType,
            protocol = protocol,
            isDirectVideo = true
        )
        prepareStream(stream, playWhenReady)
    }

    fun setVideoQuality(quality: String) {
        _currentQuality.value = normalizeQualityLabel(quality)
        applyTrackConstraints()
        Log.d(TAG, "Applied video quality constraint: ${_currentQuality.value} (dataSaver=$dataSaverEnabled)")
    }

    private fun applyTrackConstraints() {
        val p = exoPlayer ?: return
        val builder = p.trackSelectionParameters.buildUpon()
        builder.clearVideoSizeConstraints()
        builder.setMaxVideoBitrate(Int.MAX_VALUE)

        when (_currentQuality.value.lowercase()) {
            "1080p" -> builder.setMaxVideoSize(1920, 1080)
            "720p" -> builder.setMaxVideoSize(1280, 720)
            "480p" -> builder.setMaxVideoSize(854, 480)
            "360p" -> builder.setMaxVideoSize(640, 360)
            else -> {
                if (dataSaverEnabled) {
                    builder.setMaxVideoSize(854, 480)
                    builder.setMaxVideoBitrate(1_200_000)
                }
            }
        }
        p.trackSelectionParameters = builder.build()
    }

    private fun normalizeQualityLabel(raw: String): String = when {
        raw.contains("1080") -> "1080p"
        raw.contains("720") -> "720p"
        raw.contains("480") -> "480p"
        raw.contains("360") -> "360p"
        else -> "Auto"
    }

    private fun sanitizeHeaders(customHeaders: Map<String, String>): MutableMap<String, String> {
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
        return safeHeaders
    }

    fun getEstimatedBitrateMbps(): Float {
        val bps = bandwidthMeter.bitrateEstimate
        if (bps <= 0L) return 0f
        return bps.toFloat() / 1_000_000f
    }

    fun getFormattedBandwidthSpeed(): String {
        val mbps = getEstimatedBitrateMbps()
        return if (mbps > 0.05f) {
            String.format(java.util.Locale.US, "%.1f Mbps", mbps)
        } else {
            _currentQuality.value
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
            val pos = exoPlayer?.currentPosition ?: 0L
            prepareStream(current, playWhenReady = true, startPositionMs = pos)
        } else {
            exoPlayer?.prepare()
            exoPlayer?.play()
        }
    }

    fun togglePlayPause() {
        exoPlayer?.let { p ->
            if (p.isPlaying) p.pause() else p.play()
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

    fun pause() = pausePlayer()

    fun playPlayer() {
        try {
            exoPlayer?.play()
            _isPlaying.value = true
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming player: ${e.message}", e)
        }
    }

    fun play() = playPlayer()

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
        currentPlaybackSpeed = speed.coerceIn(0.25f, 2.0f)
        exoPlayer?.let { p ->
            try {
                p.setPlaybackSpeed(currentPlaybackSpeed)
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
                pausePlayer()
            }

            override fun onDestroy(owner: LifecycleOwner) {
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
                activeStream = null
                _isPlaying.value = false
                _isBuffering.value = false
                _playbackState.value = Player.STATE_IDLE
            }
        }
    }
}
