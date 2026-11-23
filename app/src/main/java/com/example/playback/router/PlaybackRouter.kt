package com.example.playback.router

import com.example.data.stream.ResolvedStream
import com.example.data.stream.StreamMediaType
import com.example.data.stream.model.StreamCandidate
import com.example.data.stream.model.StreamProtocol
import com.example.ui.PlaybackEngine

/**
 * Router terpusat untuk menentukan engine playback terbaik (Media3 ExoPlayer vs WebView Sandbox)
 * berdasarkan protokol, container format, dan kapabilitas stream.
 */
object PlaybackRouter {

    fun route(candidate: StreamCandidate): PlaybackEngine {
        val protocol = candidate.protocol
        val url = candidate.url.lowercase()

        if (protocol == StreamProtocol.WEB_EMBED) {
            return PlaybackEngine.WEBVIEW
        }

        if (url.startsWith("blob:") ||
            url.contains("/embed/") ||
            url.contains("/player/") ||
            url.contains("youtube.com") ||
            url.contains("youtu.be") ||
            url.contains("blogger.com/video")
        ) {
            return if (candidate.isDirectVideo) PlaybackEngine.EXO else PlaybackEngine.WEBVIEW
        }

        if (protocol == StreamProtocol.HLS ||
            protocol == StreamProtocol.DASH ||
            protocol == StreamProtocol.SMOOTH_STREAMING ||
            protocol == StreamProtocol.RTSP ||
            protocol == StreamProtocol.PROGRESSIVE ||
            candidate.container.isDirectVideo
        ) {
            return PlaybackEngine.EXO
        }

        return if (candidate.isDirectVideo) PlaybackEngine.EXO else PlaybackEngine.WEBVIEW
    }

    fun route(stream: ResolvedStream): PlaybackEngine {
        if (stream.mediaType == StreamMediaType.IFRAME || stream.protocol == StreamProtocol.WEB_EMBED) {
            return PlaybackEngine.WEBVIEW
        }
        return if (stream.isDirect) PlaybackEngine.EXO else PlaybackEngine.WEBVIEW
    }
}
