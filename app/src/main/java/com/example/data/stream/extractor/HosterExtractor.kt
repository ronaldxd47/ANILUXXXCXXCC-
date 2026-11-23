package com.example.data.stream.extractor

import android.net.Uri
import com.example.data.stream.detector.ProtocolDetector
import com.example.data.stream.model.RequestPolicy
import com.example.data.stream.model.StreamCandidate
import com.example.data.stream.model.SubtitleTrack
import org.jsoup.Jsoup
import java.net.URI
import java.util.regex.Pattern

/**
 * Kontrak modular extractor hoster video (terinspirasi pola plugin video extractor Mangayomi).
 * Memisahkan logika parsing spesifik per penyedia agar mudah ditambah, diuji, dan dipelihara.
 */
interface HosterExtractor {
    val name: String
    val priority: Int get() = 100

    fun canHandle(url: String, html: String = ""): Boolean

    fun extract(
        pageUrl: String,
        html: String,
        serverName: String,
        requestPolicy: RequestPolicy
    ): List<StreamCandidate>
}

object ExtractorUtils {
    private val AD_DOMAINS = listOf(
        "googleads", "analytics", "doubleclick", "googlesyndication", "popads", "adservice"
    )

    fun isAdOrTrackingUrl(url: String): Boolean {
        val lower = url.lowercase()
        return AD_DOMAINS.any { lower.contains(it) }
    }

    fun resolveAbsoluteUrl(baseUrl: String, candidate: String): String {
        val clean = candidate.trim().replace("\\/", "/")
        if (clean.isEmpty()) return ""
        if (clean.startsWith("//")) return "https:$clean"
        if (clean.startsWith("http://") || clean.startsWith("https://")) return clean

        return try {
            URI(baseUrl).resolve(clean).toString()
        } catch (_: Exception) {
            try {
                val uri = Uri.parse(baseUrl)
                val origin = if (uri.scheme != null && uri.host != null) "${uri.scheme}://${uri.host}" else ""
                if (clean.startsWith("/") && origin.isNotEmpty()) "$origin$clean" else clean
            } catch (_: Exception) {
                clean
            }
        }
    }

    fun unpackPackedJs(html: String): String {
        val packedPattern = Pattern.compile(
            "eval\\(function\\(p,a,c,k,e,d\\).*?\\}\\('(.*?)'\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*'([^']+)'\\.split\\('\\|'\\)",
            Pattern.DOTALL
        )
        val matcher = packedPattern.matcher(html)
        val results = StringBuilder()

        while (matcher.find()) {
            try {
                val p = matcher.group(1) ?: continue
                val a = matcher.group(2)?.toIntOrNull() ?: 62
                val k = matcher.group(4)?.split("|") ?: continue

                var unpacked = p
                for (i in (k.size - 1) downTo 0) {
                    val word = k[i]
                    if (word.isNotEmpty()) {
                        val token = toRadix(i, a)
                        unpacked = unpacked.replace(Regex("\\b$token\\b"), word)
                    }
                }
                results.append(unpacked).append("\n")
            } catch (_: Exception) {
            }
        }
        return results.toString()
    }

    /**
     * Mengekstrak subtitle eksternal (.vtt, .srt, .ass) dari HTML5 <track> tag
     * maupun konfigurasi JSON player (JWPlayer/Plyr/ArtPlayer).
     */
    fun extractSubtitles(html: String, baseUrl: String): List<SubtitleTrack> {
        val found = linkedMapOf<String, SubtitleTrack>()

        // 1. HTML5 <track kind="subtitles|captions" src="...">
        try {
            val doc = Jsoup.parse(html, baseUrl)
            doc.select("track[src]").forEach { el ->
                val rawSrc = el.attr("src").trim()
                val kind = el.attr("kind").lowercase()
                if (rawSrc.isNotEmpty() && (kind.isEmpty() || kind == "subtitles" || kind == "captions")) {
                    val fullUrl = resolveAbsoluteUrl(baseUrl, rawSrc)
                    if (fullUrl.startsWith("http")) {
                        val label = el.attr("label").ifBlank { el.attr("srclang").uppercase().ifBlank { "Subtitle" } }
                        val lang = el.attr("srclang").ifBlank { guessLangCode(label) }
                        found[fullUrl] = SubtitleTrack(
                            url = fullUrl,
                            label = label,
                            language = lang,
                            mimeType = inferSubtitleMime(fullUrl)
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }

        // 2. Regex JSON tracks/subtitles: "file":"https://.../sub.vtt" atau "src":".../sub.srt"
        val subRegex = Regex(
            """(https?(?::|\\/\\/)[^"'<>\s]+?\.(?:vtt|srt|ass|ssa)(?:\?[^"'<>\s]*)?)""",
            RegexOption.IGNORE_CASE
        )
        for (match in subRegex.findAll(html)) {
            val fullUrl = resolveAbsoluteUrl(baseUrl, match.value)
            if (fullUrl.startsWith("http") && !found.containsKey(fullUrl)) {
                val lang = guessLangCode(fullUrl)
                val label = when (lang) {
                    "id" -> "Indonesia"
                    "en" -> "English"
                    else -> "Subtitle"
                }
                found[fullUrl] = SubtitleTrack(
                    url = fullUrl,
                    label = label,
                    language = lang,
                    mimeType = inferSubtitleMime(fullUrl)
                )
            }
        }

        return found.values.toList()
    }

    fun inferSubtitleMime(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains(".vtt") -> "text/vtt"
            lower.contains(".srt") -> "application/x-subrip"
            lower.contains(".ass") || lower.contains(".ssa") -> "text/x-ssa"
            else -> "text/vtt"
        }
    }

    private fun guessLangCode(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("indo") || lower.contains("_id") || lower.contains("-id") || lower == "id" -> "id"
            lower.contains("eng") || lower.contains("_en") || lower.contains("-en") || lower == "en" -> "en"
            else -> "id"
        }
    }

    fun buildCandidate(
        url: String,
        serverName: String,
        pageUrl: String,
        requestPolicy: RequestPolicy,
        subtitles: List<SubtitleTrack>,
        mimeHint: String = ""
    ): StreamCandidate {
        val protocol = ProtocolDetector.detect(url, mimeHint, "")
        return StreamCandidate(
            url = url,
            protocol = protocol,
            mimeType = mimeHint.ifEmpty { null },
            requestPolicy = requestPolicy,
            subtitles = subtitles,
            isDirectVideo = true,
            providerName = serverName,
            originalPageUrl = pageUrl
        )
    }

    private fun toRadix(num: Int, radix: Int): String {
        val alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        if (num == 0) return "0"
        var n = num
        val sb = StringBuilder()
        while (n > 0) {
            sb.append(alphabet[n % radix])
            n /= radix
        }
        return sb.reverse().toString()
    }
}
