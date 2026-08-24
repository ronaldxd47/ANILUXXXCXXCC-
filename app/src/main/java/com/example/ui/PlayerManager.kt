package com.example.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Dedicated Singleton PlayerManager for managing the ExoPlayer instance.
 * Ensures a single active player across the application lifecycle,
 * prevents player doubling / ghost audio, handles automatic resource cleanup,
 * and provides a robust, clean interface.
 */
@OptIn(UnstableApi::class)
class PlayerManager private constructor(context: Context) {

    private val appContext: Context = context.applicationContext

    // Internal active ExoPlayer instance
    private var exoPlayer: ExoPlayer? = null
    private var currentListener: Player.Listener? = null
    private var currentLifecycleOwner: LifecycleOwner? = null
    private var lifecycleObserver: DefaultLifecycleObserver? = null

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

    val player: ExoPlayer?
        get() = exoPlayer

    companion object {
        private const val TAG = "PlayerManager"
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

        @Volatile
        private var INSTANCE: PlayerManager? = null

        /**
         * Returns the Singleton instance of PlayerManager.
         * Thread-safe double-checked locking prevents concurrent instantiation.
         */
        fun getInstance(context: Context): PlayerManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PlayerManager(context).also { INSTANCE = it }
            }
        }
    }

    /**
     * Initializes or re-initializes the ExoPlayer with proper HTTP DataSource & media headers.
     * Guarantees that any existing active instance is cleanly stopped and released first.
     */
    @Synchronized
    fun initializePlayer(
        refererUrl: String = "",
        externalListener: Player.Listener? = null
    ): ExoPlayer {
        // Step 1: Explicitly release previous instance to prevent player doubling & leaks
        releasePlayer()

        Log.d(TAG, "Initializing new ExoPlayer instance for referer: $refererUrl")

        // Step 2: Configure Origin and Referer headers for protected streams
        val domainOrigin = try {
            val uri = Uri.parse(refererUrl)
            if (uri.scheme != null && uri.host != null) "${uri.scheme}://${uri.host}" else refererUrl
        } catch (e: Exception) {
            refererUrl
        }

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(DEFAULT_USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(25000)
            .setReadTimeoutMs(25000)
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to refererUrl,
                    "Origin" to domainOrigin,
                    "Accept" to "*/*",
                    "Sec-Fetch-Mode" to "cors",
                    "Sec-Fetch-Site" to "cross-site"
                )
            )

        val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)

        // Step 3: Build ExoPlayer with movie-optimized audio attributes and noisy audio handling
        val newPlayer = ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true // handleAudioFocus = true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                playWhenReady = true
            }

        // Step 4: Register internal state listener & optional external listener
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
                Log.e(TAG, "ExoPlayer Error encountered: ${error.errorCodeName} - ${error.message}", error)
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
     * Prepares and starts playback of a resolved media source URL (HLS/m3u8, DASH/mpd, or MP4).
     */
    @Synchronized
    fun prepareMedia(mediaUrl: String, playWhenReady: Boolean = true) {
        val p = exoPlayer ?: return
        try {
            Log.d(TAG, "Preparing media URL: $mediaUrl")
            p.stop()
            p.clearMediaItems()

            val uri = Uri.parse(mediaUrl)
            val mediaItemBuilder = MediaItem.Builder().setUri(uri)

            // Auto-detect MIME type based on file extension / streaming protocol
            val lower = mediaUrl.lowercase()
            when {
                lower.contains(".m3u8") -> {
                    mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
                }
                lower.contains(".mpd") -> {
                    mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
                }
                lower.endsWith(".mp4") || lower.contains(".mp4?") -> {
                    mediaItemBuilder.setMimeType(MimeTypes.VIDEO_MP4)
                }
            }

            p.setMediaItem(mediaItemBuilder.build())
            p.prepare()
            p.playWhenReady = playWhenReady
            if (playWhenReady) {
                p.play()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing media item: ${e.message}", e)
        }
    }

    /**
     * Toggles play / pause state.
     */
    fun togglePlayPause() {
        exoPlayer?.let { p ->
            if (p.isPlaying) {
                p.pause()
            } else {
                p.play()
            }
        }
    }

    /**
     * Pauses playback safely.
     */
    fun pausePlayer() {
        try {
            exoPlayer?.pause()
            _isPlaying.value = false
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing player: ${e.message}", e)
        }
    }

    /**
     * Resumes playback.
     */
    fun playPlayer() {
        try {
            exoPlayer?.play()
            _isPlaying.value = true
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming player: ${e.message}", e)
        }
    }

    /**
     * Seeks to a specified position in milliseconds.
     */
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

    /**
     * Adjusts playback speed (0.25x to 2.0x).
     */
    fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.let { p ->
            try {
                p.setPlaybackSpeed(speed.coerceIn(0.25f, 2.0f))
            } catch (e: Exception) {
                Log.e(TAG, "Error setting playback speed: ${e.message}", e)
            }
        }
    }

    /**
     * Updates the current playback position.
     */
    fun updatePosition(): Long {
        val pos = exoPlayer?.currentPosition ?: 0L
        _currentPosition.value = pos
        return pos
    }

    /**
     * Attaches lifecycle management to automatically pause on background and release on destroy.
     */
    fun attachLifecycle(lifecycleOwner: LifecycleOwner) {
        detachLifecycle()
        currentLifecycleOwner = lifecycleOwner

        val observer = object : DefaultLifecycleObserver {
            override fun onPause(owner: LifecycleOwner) {
                pausePlayer()
            }

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

    /**
     * Detaches the lifecycle observer.
     */
    fun detachLifecycle() {
        currentLifecycleOwner?.let { owner ->
            lifecycleObserver?.let { obs ->
                owner.lifecycle.removeObserver(obs)
            }
        }
        currentLifecycleOwner = null
        lifecycleObserver = null
    }

    /**
     * Fully and explicitly stops, releases, and nullifies the ExoPlayer instance.
     * Clears all listeners and media items to guarantee 0% memory leakage and no duplicate audio.
     */
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
