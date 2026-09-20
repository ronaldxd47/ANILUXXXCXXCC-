package com.example.playback.router

import com.example.data.stream.model.StreamCandidate
import com.example.data.stream.model.StreamProtocol
import com.example.ui.PlaybackEngine

/**
 * Router cerdas untuk menentukan engine playback terbaik (Media3 ExoPlayer vs WebView Sandbox)
 * berdasarkan spesifikasi protokol, container, dan kapabilitas stream candidate.
 */
object PlaybackRouter {

    /**
     * Memutuskan engine yang tepat untuk memutar [StreamCandidate].
     */
    fun route(candidate: StreamCandidate): PlaybackEngine {
        val protocol = candidate.protocol
        val url = candidate.url.lowercase()

        // 1. Browser-only atau Sandbox iframe selalu menggunakan WebView
        if (protocol == StreamProtocol.WEB_EMBED) {
            return PlaybackEngine.WEBVIEW
        }

        if (url.startsWith("blob:") ||
            url.contains("/embed/") ||
            url.contains("/player/") ||
            url.contains("youtube.com") ||
            url.contains("blogger.com/video")
        ) {
            return if (candidate.isDirectVideo) PlaybackEngine.EXO else PlaybackEngine.WEBVIEW
        }

        // 2. Direct adaptive & progressive media yang didukung Media3
        if (protocol == StreamProtocol.HLS ||
            protocol == StreamProtocol.DASH ||
            protocol == StreamProtocol.SMOOTH_STREAMING ||
            protocol == StreamProtocol.RTSP ||
            protocol == StreamProtocol.PROGRESSIVE ||
            candidate.container.isDirectVideo
        ) {
            return PlaybackEngine.EXO
        }

        // 3. Fallback default
        return if (candidate.isDirectVideo) PlaybackEngine.EXO else PlaybackEngine.WEBVIEW
    }
}
