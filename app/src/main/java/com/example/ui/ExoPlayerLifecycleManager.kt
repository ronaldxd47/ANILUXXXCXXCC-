package com.example.ui

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * Lifecycle compatibility wrapper delegating to singleton [PlayerManager].
 * Ensures backward compatibility while preventing multiple player instances.
 */
@OptIn(UnstableApi::class)
class ExoPlayerLifecycleManager(
    context: Context
) {
    private val playerManager: PlayerManager = PlayerManager.getInstance(context)

    val player: ExoPlayer?
        get() = playerManager.player

    fun initializePlayer(
        streamUrl: String,
        listener: Player.Listener? = null
    ): ExoPlayer {
        return playerManager.initializePlayer(streamUrl, listener)
    }

    fun prepareMedia(mediaUrl: String) {
        playerManager.prepareMedia(mediaUrl)
    }

    fun pausePlayer() {
        playerManager.pausePlayer()
    }

    fun releasePlayer() {
        playerManager.releasePlayer()
    }
}
