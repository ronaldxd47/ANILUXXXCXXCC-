package com.example.data.stream.detector

import com.example.data.stream.model.ContainerFormat
import com.example.data.stream.model.StreamProtocol

/**
 * Komponen cerdas untuk mendeteksi protokol streaming media
 * dari kombinasi URL, Content-Type header, dan isi manifest bytes.
 */
object ProtocolDetector {

    /**
     * Mendeteksi protokol stream dari URL, MIME type, dan optional sample content.
     */
    fun detect(
        url: String,
        contentType: String = "",
        sampleContent: String = ""
    ): StreamProtocol {
        val lowerUrl = url.lowercase().trim()
        val lowerMime = contentType.lowercase().trim()
        val sample = sampleContent.trim()

        // 1. RTSP check
        if (lowerUrl.startsWith("rtsp://") || lowerUrl.startsWith("rtsps://")) {
            return StreamProtocol.RTSP
        }

        // 2. Manifest signature inspection (Paling akurat)
        if (sample.isNotEmpty()) {
            if (sample.contains("#EXTM3U") ||
                sample.contains("#EXT-X-STREAM-INF") ||
                sample.contains("#EXT-X-TARGETDURATION") ||
                sample.contains("#EXT-X-VERSION")
            ) {
                return StreamProtocol.HLS
            }
            if (sample.contains("<MPD", ignoreCase = true) ||
                (sample.contains("xmlns=\"urn:mpeg:dash:schema:mpd:2011\"", ignoreCase = true))
            ) {
                return StreamProtocol.DASH
            }
            if (sample.contains("<SmoothStreamingMedia", ignoreCase = true)) {
                return StreamProtocol.SMOOTH_STREAMING
            }
            if (sample.contains("<!DOCTYPE html", ignoreCase = true) ||
                sample.contains("<html", ignoreCase = true)
            ) {
                return StreamProtocol.WEB_EMBED
            }
        }

        // 3. Content-Type inspection
        if (lowerMime.isNotEmpty()) {
            when {
                lowerMime.contains("application/vnd.apple.mpegurl") ||
                lowerMime.contains("application/x-mpegurl") ||
                lowerMime.contains("audio/x-mpegurl") -> return StreamProtocol.HLS

                lowerMime.contains("application/dash+xml") -> return StreamProtocol.DASH
                lowerMime.contains("application/vnd.ms-sstr+xml") -> return StreamProtocol.SMOOTH_STREAMING

                lowerMime.contains("video/mp4") ||
                lowerMime.contains("video/webm") ||
                lowerMime.contains("video/x-matroska") ||
                lowerMime.contains("video/mp2t") ||
                lowerMime.contains("video/x-flv") -> return StreamProtocol.PROGRESSIVE

                lowerMime.contains("text/html") -> return StreamProtocol.WEB_EMBED
            }
        }

        // 4. URL Extension & Path heuristics
        return when {
            lowerUrl.contains(".m3u8") || lowerUrl.contains("/hls/") -> StreamProtocol.HLS
            lowerUrl.contains(".mpd") || lowerUrl.contains("/dash/") -> StreamProtocol.DASH
            lowerUrl.contains(".isml") || lowerUrl.contains(".ism/manifest") -> StreamProtocol.SMOOTH_STREAMING
            lowerUrl.contains(".mp4") || lowerUrl.contains(".webm") ||
            lowerUrl.contains(".mkv") || lowerUrl.contains(".ts") ||
            lowerUrl.contains("videoplayback") -> StreamProtocol.PROGRESSIVE
            lowerUrl.contains("/embed/") || lowerUrl.contains("/player/") || lowerUrl.contains("/watch/") -> StreamProtocol.WEB_EMBED
            else -> StreamProtocol.UNKNOWN
        }
    }

    /**
     * Memetakan protokol ke MIME type standar Android Media3.
     */
    fun toMedia3MimeType(protocol: StreamProtocol, container: ContainerFormat): String? {
        return when (protocol) {
            StreamProtocol.HLS -> "application/x-mpegURL"
            StreamProtocol.DASH -> "application/dash+xml"
            StreamProtocol.SMOOTH_STREAMING -> "application/vnd.ms-sstr+xml"
            StreamProtocol.RTSP -> "application/x-rtsp"
            StreamProtocol.PROGRESSIVE -> {
                when (container) {
                    ContainerFormat.WEBM -> "video/webm"
                    ContainerFormat.MATROSKA -> "video/x-matroska"
                    ContainerFormat.MPEG_TS -> "video/mp2t"
                    ContainerFormat.FLV -> "video/x-flv"
                    ContainerFormat.MP4, ContainerFormat.FMP4 -> "video/mp4"
                    else -> null // Auto-sniff by DefaultMediaSourceFactory
                }
            }
            else -> null
        }
    }
}
