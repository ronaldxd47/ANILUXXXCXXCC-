package com.example.ui

import com.example.data.stream.ResolvedStream

/**
 * Representasi engine playback aktif.
 */
enum class PlaybackEngine {
    EXO,
    WEBVIEW
}

/**
 * Aksi rekomendasi dari PlaybackErrorClassifier.
 */
sealed class PlaybackAction {
    /**
     * Meminta ViewModel/Resolver untuk melakukan resolve ulang (fresh token / signed URL)
     */
    data class ReResolve(val reason: String) : PlaybackAction()

    /**
     * Mengulang pemutaran ExoPlayer dengan backoff singkat
     */
    data class RetryExo(val delayMs: Long = 1000L) : PlaybackAction()

    /**
     * Alihkan pemutaran ke Web Sandbox Player (HLS.js / iframe)
     */
    data class FallbackToWeb(val reason: String) : PlaybackAction()

    /**
     * Error fatal yang tidak bisa dipulihkan (tampilkan UI error ke pengguna)
     */
    data class FatalError(val userFriendlyMessage: String) : PlaybackAction()
}

/**
 * State machine untuk melacak sesi pemutaran, mencegah infinite loop switching antara Exo dan Web.
 */
data class PlaybackSession(
    val streamUrl: String,
    val serverName: String,
    val activeEngine: PlaybackEngine = PlaybackEngine.EXO,
    val resolvedStream: ResolvedStream? = null,
    val exoAttempts: Int = 0,
    val webAttempts: Int = 0,
    val reResolveCount: Int = 0,
    val maxReResolves: Int = 1,
    val maxExoRetries: Int = 2
) {
    val canReResolve: Boolean
        get() = reResolveCount < maxReResolves

    val canRetryExo: Boolean
        get() = exoAttempts < maxExoRetries

    val canFallbackToWeb: Boolean
        get() = activeEngine == PlaybackEngine.EXO && webAttempts == 0

    fun recordExoAttempt(): PlaybackSession = copy(exoAttempts = exoAttempts + 1)

    fun recordWebAttempt(): PlaybackSession = copy(webAttempts = webAttempts + 1, activeEngine = PlaybackEngine.WEBVIEW)

    fun recordReResolve(newStream: ResolvedStream): PlaybackSession = copy(
        resolvedStream = newStream,
        reResolveCount = reResolveCount + 1,
        exoAttempts = 0 // Reset attempt counter for new fresh stream
    )

    fun switchToWeb(): PlaybackSession = copy(
        activeEngine = PlaybackEngine.WEBVIEW,
        webAttempts = webAttempts + 1
    )
}
