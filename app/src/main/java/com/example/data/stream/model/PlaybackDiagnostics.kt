package com.example.data.stream.model

/**
 * Model metrik telemetri dan diagnostik playback.
 * Menyimpan informasi performa (startup latency, buffer count, candidate switch)
 * dengan sanitasi otomatis terhadap URL token/cookie.
 */
data class PlaybackDiagnostics(
    val episodeTitle: String = "",
    val providerName: String = "",
    val resolveDurationMs: Long = 0L,
    val candidateCount: Int = 0,
    val selectedCandidateId: String = "",
    val protocol: StreamProtocol = StreamProtocol.UNKNOWN,
    val container: ContainerFormat = ContainerFormat.UNKNOWN,
    val mimeType: String = "",
    val videoCodec: String = "",
    val audioCodec: String = "",
    val activeEngine: String = "EXO",
    val httpStatus: Int = 200,
    val firstFrameLatencyMs: Long = 0L,
    val bufferCount: Int = 0,
    val rebufferDurationMs: Long = 0L,
    val lastErrorClass: String = "",
    val lastErrorMessage: String = "",
    val recoveryCount: Int = 0
) {
    fun toSummaryString(): String {
        return buildString {
            append("PROVIDER: ").append(providerName.ifEmpty { "Default" }).append(" | ")
            append("PROTOCOL: ").append(protocol.name).append(" | ")
            append("CONTAINER: ").append(container.name).append(" | ")
            append("ENGINE: ").append(activeEngine).append(" | ")
            append("RESOLVE: ").append(resolveDurationMs).append("ms | ")
            if (firstFrameLatencyMs > 0) {
                append("1ST_FRAME: ").append(firstFrameLatencyMs).append("ms | ")
            }
            append("BUFFERS: ").append(bufferCount)
            if (lastErrorClass.isNotEmpty()) {
                append(" | ERROR: ").append(lastErrorClass)
            }
        }
    }

    companion object {
        /**
         * Membersihkan query parameter sensitif (token, sign, auth, key) dari URL untuk logging aman.
         */
        fun redactUrl(rawUrl: String): String {
            if (rawUrl.isBlank()) return ""
            return try {
                val uri = android.net.Uri.parse(rawUrl)
                val cleanBuilder = uri.buildUpon().clearQuery()
                for (paramName in uri.queryParameterNames) {
                    val lower = paramName.lowercase()
                    if (lower.contains("token") || lower.contains("auth") ||
                        lower.contains("sign") || lower.contains("key") || lower.contains("pass")
                    ) {
                        cleanBuilder.appendQueryParameter(paramName, "[REDACTED]")
                    } else {
                        cleanBuilder.appendQueryParameter(paramName, uri.getQueryParameter(paramName) ?: "")
                    }
                }
                cleanBuilder.build().toString()
            } catch (e: Exception) {
                // Fallback regex jika parse gagal
                rawUrl.replace(Regex("(token|auth|sign|signature|key)=([^&]+)", RegexOption.IGNORE_CASE), "$1=[REDACTED]")
            }
        }
    }
}
