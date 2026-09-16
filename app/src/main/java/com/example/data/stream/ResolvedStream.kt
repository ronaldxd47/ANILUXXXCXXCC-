package com.example.data.stream

/**
 * Representasi tipe stream media yang didukung pemutar.
 */
enum class StreamMediaType {
    HLS,      // HTTP Live Streaming (.m3u8)
    DASH,     // Dynamic Adaptive Streaming (.mpd)
    MP4,      // Progressive download (.mp4, .mkv, .webm)
    IFRAME,   // Web Embed Sandbox fallback
    UNKNOWN
}

/**
 * Model data stream video terstruktur yang mempertahankan metadata,
 * header request (Referer, Origin, User-Agent, Cookie) untuk mencegah 403 Forbidden pada ExoPlayer.
 */
data class ResolvedStream(
    val url: String,
    val mediaType: StreamMediaType = StreamMediaType.UNKNOWN,
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
        get() = mediaType == StreamMediaType.HLS || url.lowercase().contains(".m3u8")

    val isDash: Boolean
        get() = mediaType == StreamMediaType.DASH || url.lowercase().contains(".mpd")

    val isDirect: Boolean
        get() = isDirectVideo || isHls || isDash || url.lowercase().endsWith(".mp4") || url.lowercase().contains(".mp4?")
}
