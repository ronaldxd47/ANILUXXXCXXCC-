package com.example.data.stream.model

import com.example.data.stream.ResolvedStream
import com.example.data.stream.StreamMediaType
import java.util.UUID

/**
 * Metadata codec video atau audio.
 */
data class CodecInfo(
    val codecName: String,
    val profile: String? = null,
    val level: String? = null,
    val bitDepth: Int = 8,
    val isHdr: Boolean = false
)

/**
 * Metadata resolusi / track stream.
 */
data class QualityInfo(
    val label: String = "Auto",
    val width: Int = 0,
    val height: Int = 0,
    val bitrate: Long = 0L,
    val frameRate: Float = 0f
)

/**
 * Representasi multi-layer candidate stream.
 * Digunakan oleh Stream Intelligence Platform untuk candidate ranking,
 * playability scoring, dan fallback routing.
 */
data class StreamCandidate(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val protocol: StreamProtocol = StreamProtocol.UNKNOWN,
    val container: ContainerFormat = ContainerFormat.UNKNOWN,
    val mimeType: String? = null,
    val videoCodec: CodecInfo? = null,
    val audioCodec: CodecInfo? = null,
    val requestPolicy: RequestPolicy = RequestPolicy(),
    val quality: QualityInfo = QualityInfo(),
    val score: Int = 0,
    val confidence: Float = 0.5f,
    val isDirectVideo: Boolean = false,
    val providerName: String = "",
    val originalPageUrl: String = "",
    val iframeChain: List<String> = emptyList(),
    val isLive: Boolean = false,
    val isValidated: Boolean = false
) {
    /**
     * Konversi ke model ResolvedStream untuk interoperabilitas dengan UI dan player eksisting.
     */
    fun toResolvedStream(): ResolvedStream {
        val mappedMediaType = when (protocol) {
            StreamProtocol.HLS -> StreamMediaType.HLS
            StreamProtocol.DASH -> StreamMediaType.DASH
            StreamProtocol.PROGRESSIVE -> StreamMediaType.MP4
            StreamProtocol.WEB_EMBED -> StreamMediaType.IFRAME
            else -> {
                if (container.isDirectVideo) StreamMediaType.MP4 else StreamMediaType.UNKNOWN
            }
        }

        return ResolvedStream(
            url = url,
            mediaType = mappedMediaType,
            headers = requestPolicy.toSafeHeaderMap(),
            isDirectVideo = isDirectVideo || protocol.isAdaptive || container.isDirectVideo,
            serverName = providerName,
            originalIframeUrl = originalPageUrl.ifEmpty { url },
            quality = quality.label,
            contentType = mimeType ?: container.defaultMime,
            isValidated = isValidated
        )
    }

    companion object {
        fun fromResolvedStream(stream: ResolvedStream): StreamCandidate {
            val protocol = when (stream.mediaType) {
                StreamMediaType.HLS -> StreamProtocol.HLS
                StreamMediaType.DASH -> StreamProtocol.DASH
                StreamMediaType.MP4 -> StreamProtocol.PROGRESSIVE
                StreamMediaType.IFRAME -> StreamProtocol.WEB_EMBED
                StreamMediaType.UNKNOWN -> {
                    val lower = stream.url.lowercase()
                    when {
                        lower.contains(".m3u8") -> StreamProtocol.HLS
                        lower.contains(".mpd") -> StreamProtocol.DASH
                        lower.endsWith(".mp4") || lower.contains(".mp4?") -> StreamProtocol.PROGRESSIVE
                        else -> StreamProtocol.UNKNOWN
                    }
                }
            }

            val container = when (protocol) {
                StreamProtocol.PROGRESSIVE -> {
                    val lower = stream.url.lowercase()
                    when {
                        lower.contains(".webm") -> ContainerFormat.WEBM
                        lower.contains(".mkv") -> ContainerFormat.MATROSKA
                        lower.contains(".ts") -> ContainerFormat.MPEG_TS
                        lower.contains(".flv") -> ContainerFormat.FLV
                        else -> ContainerFormat.MP4
                    }
                }
                StreamProtocol.HLS -> ContainerFormat.MPEG_TS
                StreamProtocol.DASH -> ContainerFormat.FMP4
                else -> ContainerFormat.UNKNOWN
            }

            return StreamCandidate(
                url = stream.url,
                protocol = protocol,
                container = container,
                mimeType = stream.contentType.ifEmpty { null },
                requestPolicy = RequestPolicy.fromMap(stream.headers, stream.originalIframeUrl),
                quality = QualityInfo(label = stream.quality),
                isDirectVideo = stream.isDirect,
                providerName = stream.serverName,
                originalPageUrl = stream.originalIframeUrl,
                isValidated = stream.isValidated,
                score = if (stream.isDirect) 80 else 30,
                confidence = if (stream.isValidated) 0.9f else 0.5f
            )
        }
    }
}
