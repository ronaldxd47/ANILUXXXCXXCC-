package com.example.data.stream.model

/**
 * Protokol streaming media yang didukung oleh sistem Stream Intelligence.
 */
enum class StreamProtocol {
    PROGRESSIVE,       // Progressive download (MP4, MKV, WebM, TS, etc.)
    HLS,               // HTTP Live Streaming (.m3u8, application/vnd.apple.mpegurl)
    DASH,              // Dynamic Adaptive Streaming over HTTP (.mpd)
    SMOOTH_STREAMING,  // Microsoft Smooth Streaming (.ism, .isml)
    RTSP,              // Real-Time Streaming Protocol (rtsp://)
    WEB_EMBED,         // Browser-only / sandbox iframe player
    UNKNOWN;

    val isAdaptive: Boolean
        get() = this == HLS || this == DASH || this == SMOOTH_STREAMING

    val isNativeExoCompatible: Boolean
        get() = this == PROGRESSIVE || this == HLS || this == DASH || this == SMOOTH_STREAMING || this == RTSP
}

/**
 * Format container binary media.
 */
enum class ContainerFormat(val extension: String, val defaultMime: String) {
    MP4("mp4", "video/mp4"),
    FMP4("m4s", "video/mp4"),
    WEBM("webm", "video/webm"),
    MATROSKA("mkv", "video/x-matroska"),
    MPEG_TS("ts", "video/mp2t"),
    MPEG_PS("mpg", "video/mp2p"),
    FLV("flv", "video/x-flv"),
    OGG("ogg", "video/ogg"),
    WAV("wav", "audio/wav"),
    ADTS("aac", "audio/aac"),
    MP3("mp3", "audio/mpeg"),
    FLAC("flac", "audio/flac"),
    AMR("amr", "audio/amr"),
    UNKNOWN("", "");

    val isDirectVideo: Boolean
        get() = this == MP4 || this == FMP4 || this == WEBM || this == MATROSKA || this == MPEG_TS || this == FLV
}
