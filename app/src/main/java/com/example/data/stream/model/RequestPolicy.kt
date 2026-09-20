package com.example.data.stream.model

/**
 * Kebijakan header jaringan granular per tahap request (manifest, media segment, encryption key, dan subtitle).
 * Menyediakan perlindungan terhadap error 403 Forbidden di level CDN serta sanitasi logging.
 */
data class RequestPolicy(
    val manifestHeaders: Map<String, String> = emptyMap(),
    val segmentHeaders: Map<String, String> = emptyMap(),
    val keyHeaders: Map<String, String> = emptyMap(),
    val subtitleHeaders: Map<String, String> = emptyMap(),
    val referer: String? = null,
    val origin: String? = null,
    val cookie: String? = null,
    val userAgent: String? = null
) {
    /**
     * Menggabungkan seluruh header default yang aman untuk MediaSource DataSource ExoPlayer.
     */
    fun toSafeHeaderMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        map["Accept"] = "*/*"
        
        // Gabungkan manifest headers
        manifestHeaders.forEach { (k, v) ->
            if (isSafeHeaderKey(k)) map[k] = v
        }

        referer?.takeIf { it.isNotBlank() }?.let { map["Referer"] = it }
        origin?.takeIf { it.isNotBlank() }?.let { map["Origin"] = it }
        cookie?.takeIf { it.isNotBlank() }?.let { map["Cookie"] = it }
        userAgent?.takeIf { it.isNotBlank() }?.let { map["User-Agent"] = it }

        return map
    }

    /**
     * Menghasilkan representasi header yang disamarkan (redacted) untuk keperluan logging diagnostik tanpa membocorkan token.
     */
    fun toRedactedString(): String {
        val safe = toSafeHeaderMap().mapValues { (k, v) ->
            if (k.contains("cookie", ignoreCase = true) ||
                k.contains("auth", ignoreCase = true) ||
                k.contains("token", ignoreCase = true)
            ) {
                "[REDACTED_SECRET]"
            } else {
                v
            }
        }
        return safe.toString()
    }

    companion object {
        fun fromMap(headers: Map<String, String>, sourcePage: String? = null): RequestPolicy {
            val referer = headers["Referer"] ?: headers["referer"] ?: sourcePage
            val origin = headers["Origin"] ?: headers["origin"]
            val cookie = headers["Cookie"] ?: headers["cookie"]
            val userAgent = headers["User-Agent"] ?: headers["user-agent"]
            return RequestPolicy(
                manifestHeaders = headers,
                segmentHeaders = headers,
                referer = referer,
                origin = origin,
                cookie = cookie,
                userAgent = userAgent
            )
        }

        private fun isSafeHeaderKey(key: String): Boolean {
            val lower = key.lowercase().trim()
            return lower.isNotEmpty() &&
                    !lower.startsWith("sec-fetch-") &&
                    lower != "host" &&
                    lower != "content-length"
        }
    }
}
