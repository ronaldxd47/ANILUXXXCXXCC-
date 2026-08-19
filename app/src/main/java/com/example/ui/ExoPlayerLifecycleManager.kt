package com.example.ui

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

/**
 * Robust lifecycle manager for ExoPlayer instances in Jetpack Compose.
 * Provides clear separation between 'initializePlayer' and 'releasePlayer' methods
 * to prevent player doubling, audio overlap, and memory leaks.
 */
@OptIn(UnstableApi::class)
class ExoPlayerLifecycleManager(
    private val context: Context
) {
    private var exoPlayer: ExoPlayer? = null
    private var activeListener: Player.Listener? = null

    val player: ExoPlayer?
        get() = exoPlayer

    /**
     * Initializes a new ExoPlayer instance with custom stream HTTP headers.
     * Guarantees any previously active instance is safely released first to prevent player doubling.
     */
    @Synchronized
    fun initializePlayer(
        streamUrl: String,
        listener: Player.Listener? = null
    ): ExoPlayer {
        // Prevent player doubling: safely release existing player instance first
        releasePlayer()

        val domainOrigin = try {
            val uri = android.net.Uri.parse(streamUrl)
            if (uri.scheme != null && uri.host != null) "${uri.scheme}://${uri.host}" else streamUrl
        } catch (e: Exception) {
            streamUrl
        }

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20000)
            .setReadTimeoutMs(20000)
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to streamUrl,
                    "Origin" to domainOrigin,
                    "Accept" to "*/*",
                    "Sec-Fetch-Mode" to "cors",
                    "Sec-Fetch-Site" to "cross-site"
                )
            )

        val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)

        val newPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                playWhenReady = true
            }

        listener?.let {
            newPlayer.addListener(it)
            activeListener = it
        }

        exoPlayer = newPlayer
        Log.d("ExoPlayerManager", "ExoPlayer initialized successfully")
        return newPlayer
    }

    /**
     * Prepares and starts playback of a resolved media source URL.
     */
    fun prepareMedia(mediaUrl: String) {
        exoPlayer?.let { p ->
            try {
                p.stop()
                p.clearMediaItems()
                val uri = android.net.Uri.parse(mediaUrl)
                val mediaItemBuilder = MediaItem.Builder().setUri(uri)
                
                val lower = mediaUrl.lowercase()
                if (lower.contains(".m3u8")) {
                    mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                } else if (lower.contains(".mpd")) {
                    mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_MPD)
                } else if (lower.endsWith(".mp4") || lower.contains(".mp4?")) {
                    mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.VIDEO_MP4)
                }

                p.setMediaItem(mediaItemBuilder.build())
                p.prepare()
                p.play()
                Log.d("ExoPlayerManager", "Media prepared and playing: $mediaUrl")
            } catch (e: Exception) {
                Log.e("ExoPlayerManager", "Error preparing media source: ${e.message}", e)
            }
        }
    }

    /**
     * Pauses player playback safely during lifecycle background events.
     */
    fun pausePlayer() {
        try {
            exoPlayer?.pause()
        } catch (e: Exception) {
            Log.e("ExoPlayerManager", "Error pausing player: ${e.message}", e)
        }
    }

    /**
     * Cleans up listeners, stops, clears media items, releases the ExoPlayer instance,
     * and resets references to prevent memory leaks and player doubling.
     */
    @Synchronized
    fun releasePlayer() {
        exoPlayer?.let { p ->
            try {
                activeListener?.let { l -> p.removeListener(l) }
                activeListener = null
                p.stop()
                p.clearMediaItems()
                p.release()
                Log.d("ExoPlayerManager", "ExoPlayer cleanly released")
            } catch (e: Exception) {
                Log.e("ExoPlayerManager", "Error releasing ExoPlayer: ${e.message}", e)
            } finally {
                exoPlayer = null
            }
        }
    }
}
