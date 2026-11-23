package com.example.data.stream.extractor

import com.example.data.stream.model.RequestPolicy
import com.example.data.stream.model.StreamCandidate

/**
 * Registry terpusat seluruh HosterExtractor.
 * Menjalankan extractor spesifik sesuai prioritas, lalu fallback ke GenericHtmlMediaExtractor,
 * serta melakukan deduplikasi kandidat berdasarkan URL.
 */
object HosterExtractorRegistry {

    private val extractors: List<HosterExtractor> = listOf(
        BloggerExtractor(),
        PackedHosterExtractor(),
        GenericHtmlMediaExtractor()
    ).sortedBy { it.priority }

    fun extractAll(
        pageUrl: String,
        html: String,
        serverName: String,
        requestPolicy: RequestPolicy
    ): List<StreamCandidate> {
        val collected = linkedMapOf<String, StreamCandidate>()

        for (extractor in extractors) {
            if (!extractor.canHandle(pageUrl, html)) continue
            val extracted = extractor.extract(pageUrl, html, serverName, requestPolicy)
            for (candidate in extracted) {
                if (!collected.containsKey(candidate.url)) {
                    collected[candidate.url] = candidate
                }
            }
        }

        return collected.values.toList()
    }
}
