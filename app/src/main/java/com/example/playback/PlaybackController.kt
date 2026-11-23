package com.example.playback

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import com.example.data.stream.ResolvedStream
import com.example.data.stream.StreamResolver
import com.example.data.stream.model.StreamCandidate
import com.example.playback.router.PlaybackRouter
import com.example.ui.PlaybackAction
import com.example.ui.PlaybackEngine
import com.example.ui.PlaybackErrorClassifier
import com.example.ui.PlaybackSession
import com.example.ui.PlayerManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * State tunggal yang terkoordinasi untuk mengendalikan siklus resolusi,
 * routing (ExoPlayer vs WebView), serta error recovery tanpa race condition dengan Compose lifecycle.
 */
data class PlaybackUiState(
    val activeEmbedUrl: String = "",
    val currentServerName: String = "Server",
    val isResolving: Boolean = false,
    val activeEngine: PlaybackEngine = PlaybackEngine.EXO,
    val resolvedStream: ResolvedStream? = null,
    val session: PlaybackSession = PlaybackSession(streamUrl = "", serverName = ""),
    val hasPlaybackError: Boolean = false,
    val playbackErrorMessage: String = "",
    val fallbackPreferIframe: Boolean = false
)

@OptIn(UnstableApi::class)
class PlaybackController(
    private val context: Context,
    private val playerManager: PlayerManager,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "PlaybackController"
    }

    private val generationCounter = AtomicLong(0L)
    private var activeTask: Job? = null

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    fun startStream(
        embedUrl: String,
        serverName: String,
        resumePositionMs: Long = 0L
    ) {
        val gen = generationCounter.incrementAndGet()
        activeTask?.cancel()

        _state.value = PlaybackUiState(
            activeEmbedUrl = embedUrl,
            currentServerName = serverName,
            isResolving = true,
            activeEngine = PlaybackEngine.EXO,
            session = PlaybackSession(
                streamUrl = embedUrl,
                serverName = serverName,
                activeEngine = PlaybackEngine.EXO
            )
        )

        activeTask = scope.launch {
            try {
                val candidates = StreamResolver.resolveCandidates(context, embedUrl, serverName)
                if (generationCounter.get() != gen) return@launch

                val topCandidate = candidates.firstOrNull()
                val resolved = topCandidate?.toResolvedStream()
                    ?: StreamResolver.resolve(context, embedUrl, serverName)
                if (generationCounter.get() != gen) return@launch

                val targetEngine = topCandidate?.let { PlaybackRouter.route(it) }
                    ?: PlaybackRouter.route(resolved)

                val initialSession = PlaybackSession(
                    streamUrl = embedUrl,
                    serverName = serverName,
                    activeEngine = targetEngine,
                    resolvedStream = resolved,
                    candidates = candidates,
                    currentCandidateIndex = 0
                )

                if (targetEngine == PlaybackEngine.EXO) {
                    _state.value = _state.value.copy(
                        isResolving = false,
                        activeEngine = PlaybackEngine.EXO,
                        resolvedStream = resolved,
                        session = initialSession,
                        hasPlaybackError = false,
                        playbackErrorMessage = "",
                        fallbackPreferIframe = false
                    )
                    playerManager.prepareStream(resolved, playWhenReady = true, startPositionMs = resumePositionMs)
                } else {
                    playerManager.pause()
                    _state.value = _state.value.copy(
                        isResolving = false,
                        activeEngine = PlaybackEngine.WEBVIEW,
                        resolvedStream = resolved,
                        session = initialSession.switchToWeb(),
                        hasPlaybackError = false,
                        playbackErrorMessage = "",
                        fallbackPreferIframe = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generationCounter.get() != gen) return@launch
                Log.e(TAG, "Stream resolution failed: ${e.message}")
                playerManager.pause()
                _state.value = _state.value.copy(
                    isResolving = false,
                    activeEngine = PlaybackEngine.WEBVIEW,
                    session = _state.value.session.switchToWeb(),
                    hasPlaybackError = false,
                    fallbackPreferIframe = true
                )
            }
        }
    }

    fun handlePlayerError(error: PlaybackException, currentPositionMs: Long) {
        val currentState = _state.value
        val gen = generationCounter.get()
        val action = PlaybackErrorClassifier.classify(error, currentState.session)

        when (action) {
            is PlaybackAction.NextCandidate -> {
                val safeCandUrl = PlayerManager.redactUrl(action.candidate.url)
                Log.i(TAG, "Recovery NextCandidate: ${action.reason} -> $safeCandUrl")
                val nextSession = currentState.session.advanceToNextCandidate()
                val nextStream = action.candidate.toResolvedStream()
                val targetEngine = PlaybackRouter.route(action.candidate)

                if (targetEngine == PlaybackEngine.EXO) {
                    _state.value = currentState.copy(
                        activeEngine = PlaybackEngine.EXO,
                        resolvedStream = nextStream,
                        session = nextSession,
                        hasPlaybackError = false,
                        playbackErrorMessage = ""
                    )
                    playerManager.prepareCandidate(action.candidate, playWhenReady = true, startPositionMs = currentPositionMs)
                } else {
                    playerManager.pause()
                    _state.value = currentState.copy(
                        activeEngine = PlaybackEngine.WEBVIEW,
                        resolvedStream = nextStream,
                        session = nextSession.switchToWeb(),
                        hasPlaybackError = false,
                        playbackErrorMessage = "",
                        fallbackPreferIframe = false
                    )
                }
            }

            is PlaybackAction.ReResolve -> {
                Log.i(TAG, "Recovery ReResolve: ${action.reason}")
                activeTask?.cancel()
                activeTask = scope.launch {
                    _state.value = currentState.copy(isResolving = true, hasPlaybackError = false)
                    try {
                        val candidates = StreamResolver.resolveCandidates(
                            context,
                            currentState.activeEmbedUrl,
                            currentState.currentServerName
                        )
                        if (generationCounter.get() != gen) return@launch

                        val fresh = candidates.firstOrNull()?.toResolvedStream()
                            ?: StreamResolver.resolve(context, currentState.activeEmbedUrl, currentState.currentServerName)
                        if (generationCounter.get() != gen) return@launch

                        val updatedSession = currentState.session.recordReResolve(fresh).copy(
                            candidates = candidates.ifEmpty { currentState.session.candidates },
                            currentCandidateIndex = 0
                        )
                        val targetEngine = PlaybackRouter.route(fresh)
                        if (targetEngine == PlaybackEngine.EXO) {
                            _state.value = _state.value.copy(
                                isResolving = false,
                                activeEngine = PlaybackEngine.EXO,
                                resolvedStream = fresh,
                                session = updatedSession,
                                hasPlaybackError = false
                            )
                            playerManager.prepareStream(fresh, playWhenReady = true, startPositionMs = currentPositionMs)
                        } else {
                            playerManager.pause()
                            _state.value = _state.value.copy(
                                isResolving = false,
                                activeEngine = PlaybackEngine.WEBVIEW,
                                resolvedStream = fresh,
                                session = updatedSession.switchToWeb(),
                                fallbackPreferIframe = false
                            )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (generationCounter.get() != gen) return@launch
                        Log.e(TAG, "Re-resolve failed: ${e.message}")
                        playerManager.pause()
                        _state.value = _state.value.copy(
                            isResolving = false,
                            activeEngine = PlaybackEngine.WEBVIEW,
                            session = currentState.session.switchToWeb(),
                            fallbackPreferIframe = true
                        )
                    }
                }
            }

            is PlaybackAction.RetryExo -> {
                Log.i(TAG, "Recovery RetryExo after ${action.delayMs}ms")
                val updatedSession = currentState.session.recordExoAttempt()
                _state.value = currentState.copy(session = updatedSession, hasPlaybackError = false)
                activeTask?.cancel()
                activeTask = scope.launch {
                    delay(action.delayMs)
                    if (generationCounter.get() != gen) return@launch
                    currentState.resolvedStream?.let { stream ->
                        playerManager.prepareStream(stream, playWhenReady = true, startPositionMs = currentPositionMs)
                    }
                }
            }

            is PlaybackAction.FallbackToWeb -> {
                Log.i(TAG, "Recovery FallbackToWeb: ${action.reason}")
                val preferIframe = action.reason.contains("404") || action.reason.contains("410")
                playerManager.pause()
                _state.value = currentState.copy(
                    activeEngine = PlaybackEngine.WEBVIEW,
                    session = currentState.session.switchToWeb(),
                    hasPlaybackError = false,
                    playbackErrorMessage = "",
                    fallbackPreferIframe = preferIframe
                )
            }

            is PlaybackAction.FatalError -> {
                Log.e(TAG, "Fatal playback error: ${action.userFriendlyMessage}")
                _state.value = currentState.copy(
                    hasPlaybackError = true,
                    playbackErrorMessage = action.userFriendlyMessage
                )
            }
        }
    }

    fun retryManual(resumePositionMs: Long = 0L) {
        val current = _state.value
        val freshSession = PlaybackSession(
            streamUrl = current.activeEmbedUrl,
            serverName = current.currentServerName,
            activeEngine = PlaybackEngine.EXO,
            resolvedStream = current.resolvedStream,
            candidates = current.session.candidates
        )
        _state.value = current.copy(
            hasPlaybackError = false,
            playbackErrorMessage = "",
            activeEngine = PlaybackEngine.EXO,
            session = freshSession
        )
        startStream(current.activeEmbedUrl, current.currentServerName, resumePositionMs)
    }

    fun switchToWebManual() {
        playerManager.pause()
        val current = _state.value
        _state.value = current.copy(
            activeEngine = PlaybackEngine.WEBVIEW,
            session = current.session.switchToWeb(),
            hasPlaybackError = false,
            playbackErrorMessage = "",
            fallbackPreferIframe = true
        )
    }

    fun switchToExoManual(resumePositionMs: Long = 0L) {
        startStream(_state.value.activeEmbedUrl, _state.value.currentServerName, resumePositionMs)
    }
}
