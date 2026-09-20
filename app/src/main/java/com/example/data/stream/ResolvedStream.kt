package com.example.data.stream

import com.example.data.stream.model.ContainerFormat
import com.example.data.stream.model.StreamProtocol

/**
 * Representasi tipe stream media yang didukung pemutar.
 */
enum class StreamMediaType {
    HLS,          // HTTP Live Streaming (.m3u8)
    DASH,         // Dynamic Adaptive Streaming (.mpd)
    PROGRESSIVE,  // Progressive direct stream (.mp4, .webm, .mkv, .ts, .flv)
    MP4,          // Deprecated alias for PROGRESSIVE
    IFRAME,       // Web Embed Sandbox fallback
    UNKNOWN
}

/**
 * Model data stream video terstruktur yang mempertahankan metadata,
 * protocol, container format, dan header request (Referer, Origin, User-Agent, Cookie)
 * untuk mencegah 403 Forbidden dan degradasi container format pada ExoPlayer.
 */
data class ResolvedStream(
    val url: String,
    val mediaType: StreamMediaType = StreamMediaType.UNKNOWN,
    val protocol: StreamProtocol = StreamProtocol.UNKNOWN,
    val container: ContainerFormat = ContainerFormat.UNKNOWN,
    val headers: Map<String, String> = emptyMap(),
    val isDirectVideo: Boolean = false,
    val serverName: String = "",
    val originalIframeUrl: String = "",
    val quality: String = "Auto",
    val contentType: String = "",
    val isValidated: Boolean = false,
    val resolvedTimestamp: Long = System.currentTimeMillis()
) {
    val isHls: Boolean
        get() = mediaType == StreamMediaType.HLS || protocol == StreamProtocol.HLS || url.lowercase().contains(".m3u8")

    val isDash: Boolean
        get() = mediaType == StreamMediaType.DASH || protocol == StreamProtocol.DASH || url.lowercase().contains(".mpd")

    val isProgressive: Boolean
        get() = mediaType == StreamMediaType.PROGRESSIVE || mediaType == StreamMediaType.MP4 ||
                protocol == StreamProtocol.PROGRESSIVE || container.isDirectVideo

    val isDirect: Boolean
        get() = isDirectVideo || isHls || isDash || isProgressive ||
                url.lowercase().endsWith(".mp4") || url.lowercase().contains(".mp4?")
}

