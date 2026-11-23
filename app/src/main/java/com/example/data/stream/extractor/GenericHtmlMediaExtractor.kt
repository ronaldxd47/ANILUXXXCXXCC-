package com.example.data.stream.extractor

import com.example.data.stream.model.RequestPolicy
import com.example.data.stream.model.StreamCandidate
import org.jsoup.Jsoup

/**
 * Extractor generik untuk halaman HTML5 (<video>, <source>), konfigurasi player JWPlayer/Plyr,
 * serta tautan media absolut (.m3u8, .mpd, .mp4, .webm, .mkv) beserta subtitle eksternalnya.
 */
class GenericHtmlMediaExtractor : HosterExtractor {
    override val name: String = "GenericHtml"
    override val priority: Int = 100

    override fun canHandle(url: String, html: String): Boolean = html.isNotBlank()

    override fun extract(
        pageUrl: String,
        html: String,
        serverName: String,
        requestPolicy: RequestPolicy
    ): List<StreamCandidate> {
        val candidates = mutableListOf<StreamCandidate>()
        val seenUrls = hashSetOf<String>()
        val subtitles = ExtractorUtils.extractSubtitles(html, pageUrl)

        fun addIfNew(url: String, mimeHint: String = "") {
            if (url.startsWith("http") && !ExtractorUtils.isAdOrTrackingUrl(url) && seenUrls.add(url)) {
                candidates.add(
                    ExtractorUtils.buildCandidate(
                        url = url,
                        serverName = serverName,
                        pageUrl = pageUrl,
                        requestPolicy = requestPolicy,
                        subtitles = subtitles,
                        mimeHint = mimeHint
                    )
                )
            }
        }

        // 1. Tag HTML5 <video> / <source>
        try {
            val doc = Jsoup.parse(html, pageUrl)
            doc.select("video source, video").forEach { videoEl ->
                val src = videoEl.attr("src").trim()
                if (src.isNotEmpty() && !src.startsWith("blob:")) {
                    addIfNew(ExtractorUtils.resolveAbsoluteUrl(pageUrl, src), videoEl.attr("type"))
                }
            }
        } catch (_: Exception) {
        }

        // 2. Tautan langsung (.m3u8, .mpd, .mp4, .webm, .mkv)
        val directRegex = Regex(
            """(https?(?::|\\/\\/)[^"'<>\s]+?\.(?:m3u8|mp4|mpd|webm|mkv)(?:\?[^"'<>\s]*)?)""",
            RegexOption.IGNORE_CASE
        )
        directRegex.findAll(html).forEach { match ->
            addIfNew(ExtractorUtils.resolveAbsoluteUrl(pageUrl, match.value))
        }

        // 3. Properti JS player (file / src / url)
        val fileRegex = Regex(
            """["']?(?:file|src|url)["']?\s*:\s*["']([^"']+\.(?:m3u8|mp4|mpd|webm)[^"']*)["']""",
            RegexOption.IGNORE_CASE
        )
        fileRegex.findAll(html).forEach { match ->
            addIfNew(ExtractorUtils.resolveAbsoluteUrl(pageUrl, match.groupValues[1]))
        }

        return candidates
    }
}
