package com.example.data.stream.extractor

import com.example.data.stream.detector.ProtocolDetector
import com.example.data.stream.model.QualityInfo
import com.example.data.stream.model.RequestPolicy
import com.example.data.stream.model.StreamCandidate
import org.json.JSONObject
import java.util.regex.Pattern

/**
 * Extractor khusus untuk player Blogger / Blogspot (VIDEO_CONFIG streams).
 */
class BloggerExtractor : HosterExtractor {
    override val name: String = "Blogger"
    override val priority: Int = 10

    override fun canHandle(url: String, html: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("blogger.com") ||
            lower.contains("blogspot.com") ||
            html.contains("VIDEO_CONFIG")
    }

    override fun extract(
        pageUrl: String,
        html: String,
        serverName: String,
        requestPolicy: RequestPolicy
    ): List<StreamCandidate> {
        val results = mutableListOf<StreamCandidate>()
        try {
            val jsonPattern = Pattern.compile("var\\s+VIDEO_CONFIG\\s*=\\s*(\\{.+?\\});", Pattern.DOTALL)
            val matcher = jsonPattern.matcher(html)
            if (!matcher.find()) return emptyList()

            val jsonString = matcher.group(1) ?: return emptyList()
            val json = JSONObject(jsonString)
            val streams = json.optJSONArray("streams") ?: return emptyList()
            val subtitles = ExtractorUtils.extractSubtitles(html, pageUrl)

            val parsed = mutableListOf<Pair<Int, String>>()
            for (i in 0 until streams.length()) {
                val streamObj = streams.optJSONObject(i) ?: continue
                val playUrl = streamObj.optString("play_url", "").trim()
                val formatId = streamObj.optInt("format_id", 0)
                if (playUrl.isNotEmpty()) {
                    parsed.add(formatId to playUrl)
                }
            }

            parsed.sortedByDescending { it.first }.forEach { (formatId, rawPlayUrl) ->
                val fullUrl = ExtractorUtils.resolveAbsoluteUrl(pageUrl, rawPlayUrl)
                if (fullUrl.startsWith("http")) {
                    val qualityLabel = when (formatId) {
                        37 -> "1080p"
                        22 -> "720p"
                        59 -> "480p"
                        18 -> "360p"
                        else -> "Auto"
                    }
                    results.add(
                        StreamCandidate(
                            url = fullUrl,
                            protocol = ProtocolDetector.detect(fullUrl, "", ""),
                            requestPolicy = requestPolicy,
                            quality = QualityInfo(label = qualityLabel),
                            subtitles = subtitles,
                            isDirectVideo = true,
                            providerName = serverName,
                            originalPageUrl = pageUrl
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return results
    }
}
