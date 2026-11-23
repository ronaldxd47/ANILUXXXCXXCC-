package com.example.data.stream.extractor

import com.example.data.stream.model.RequestPolicy
import com.example.data.stream.model.StreamCandidate

/**
 * Extractor untuk hoster streaming yang menggunakan JavaScript obfuscation (Dean Edwards packer)
 * atau pola JSON player populer (Streamwish, Vidhide, Filemoon, Dood, Mp4upload, Upbolt, Mixdrop, OK.ru).
 */
class PackedHosterExtractor : HosterExtractor {
    override val name: String = "PackedHoster"
    override val priority: Int = 20

    private val hostKeywords = listOf(
        "streamwish", "vidhide", "filemoon", "dood", "mp4upload",
        "lulustream", "upbolt", "mixdrop", "ok.ru", "krakenfiles", "pixeldrain"
    )

    override fun canHandle(url: String, html: String): Boolean {
        val lower = url.lowercase()
        return hostKeywords.any { lower.contains(it) } ||
            html.contains("eval(function(p,a,c,k,e,d)")
    }

    override fun extract(
        pageUrl: String,
        html: String,
        serverName: String,
        requestPolicy: RequestPolicy
    ): List<StreamCandidate> {
        val unpacked = ExtractorUtils.unpackPackedJs(html)
        val combined = if (unpacked.isNotEmpty()) "$html\n$unpacked" else html
        val subtitles = ExtractorUtils.extractSubtitles(combined, pageUrl)

        val foundUrls = linkedSetOf<String>()

        val directRegex = Regex(
            """(https?(?::|\\/\\/)[^"'<>\s]+?\.(?:m3u8|mp4|mpd|webm|mkv)(?:\?[^"'<>\s]*)?)""",
            RegexOption.IGNORE_CASE
        )
        directRegex.findAll(combined).forEach { match ->
            val resolved = ExtractorUtils.resolveAbsoluteUrl(pageUrl, match.value)
            if (resolved.startsWith("http") && !ExtractorUtils.isAdOrTrackingUrl(resolved)) {
                foundUrls.add(resolved)
            }
        }

        val propRegex = Regex(
            """["']?(?:file|src|url|hls|mp4)["']?\s*:\s*["']([^"']+\.(?:m3u8|mp4|mpd|webm)[^"']*)["']""",
            RegexOption.IGNORE_CASE
        )
        propRegex.findAll(combined).forEach { match ->
            val resolved = ExtractorUtils.resolveAbsoluteUrl(pageUrl, match.groupValues[1])
            if (resolved.startsWith("http") && !ExtractorUtils.isAdOrTrackingUrl(resolved)) {
                foundUrls.add(resolved)
            }
        }

        return foundUrls.map { mediaUrl ->
            ExtractorUtils.buildCandidate(
                url = mediaUrl,
                serverName = serverName,
                pageUrl = pageUrl,
                requestPolicy = requestPolicy,
                subtitles = subtitles
            )
        }
    }
}
