package com.example.ui

import com.example.data.stream.ResolvedStream
import com.example.data.stream.model.StreamCandidate

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
     * Beralih ke candidate alternatif berikutnya dalam daftar hasil ranking resolver.
     */
    data class NextCandidate(val candidate: StreamCandidate, val reason: String) : PlaybackAction()

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
 * State machine untuk melacak sesi pemutaran, mencegah infinite loop switching antara Exo dan Web,
 * serta mengelola candidate fallback traversal.
 */
data class PlaybackSession(
    val streamUrl: String,
    val serverName: String,
    val activeEngine: PlaybackEngine = PlaybackEngine.EXO,
    val resolvedStream: ResolvedStream? = null,
    val candidates: List<StreamCandidate> = emptyList(),
    val currentCandidateIndex: Int = 0,
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

    val hasNextCandidate: Boolean
        get() = currentCandidateIndex + 1 < candidates.size

    val nextCandidate: StreamCandidate?
        get() = if (hasNextCandidate) candidates[currentCandidateIndex + 1] else null

    fun recordExoAttempt(): PlaybackSession = copy(exoAttempts = exoAttempts + 1)

    fun recordWebAttempt(): PlaybackSession = copy(webAttempts = webAttempts + 1, activeEngine = PlaybackEngine.WEBVIEW)

    fun advanceToNextCandidate(): PlaybackSession {
        if (!hasNextCandidate) return this
        val nextIdx = currentCandidateIndex + 1
        val cand = candidates[nextIdx]
        return copy(
            currentCandidateIndex = nextIdx,
            resolvedStream = cand.toResolvedStream(),
            streamUrl = cand.url,
            exoAttempts = 0
        )
    }

    fun recordReResolve(newStream: ResolvedStream): PlaybackSession = copy(
        resolvedStream = newStream,
        streamUrl = newStream.url,
        reResolveCount = reResolveCount + 1,
        exoAttempts = 0 // Reset attempt counter for new fresh stream
    )

    fun switchToWeb(): PlaybackSession = copy(
        activeEngine = PlaybackEngine.WEBVIEW,
        webAttempts = webAttempts + 1
    )
}
