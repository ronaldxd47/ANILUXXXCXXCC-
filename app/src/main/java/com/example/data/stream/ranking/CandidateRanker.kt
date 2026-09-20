package com.example.data.stream.ranking

import com.example.data.stream.model.ContainerFormat
import com.example.data.stream.model.StreamCandidate
import com.example.data.stream.model.StreamProtocol

/**
 * Engine penilai dan perangking candidate stream berdasarkan skor kelayakan (playability score).
 * Mengeliminasi tracking, ads, thumbnail, dan mengutamakan stream direct video (HLS/DASH/MP4).
 */
object CandidateRanker {

    /**
     * Memberikan score (0..100) kepada candidate stream.
     */
    fun calculateScore(candidate: StreamCandidate): Int {
        var score = 0
        val lowerUrl = candidate.url.lowercase()

        // 1. Hard Penalties: Ads, tracking, and web challenge pages
        if (lowerUrl.contains("googleads") ||
            lowerUrl.contains("doubleclick") ||
            lowerUrl.contains("adservice") ||
            lowerUrl.contains("/pagead/") ||
            lowerUrl.contains("popunder") ||
            lowerUrl.contains("adsterra")
        ) {
            return -100 // Blacklist Ads
        }

        if (lowerUrl.contains("analytics") ||
            lowerUrl.contains("/collect?") ||
            lowerUrl.contains("/beacon") ||
            lowerUrl.contains("histats")
        ) {
            return -90 // Blacklist Analytics/tracking
        }

        if (lowerUrl.endsWith(".jpg") || lowerUrl.endsWith(".png") ||
            lowerUrl.endsWith(".jpeg") || lowerUrl.endsWith(".webp") ||
            lowerUrl.contains("thumbnail") || lowerUrl.contains("/poster/")
        ) {
            return -80 // Blacklist Images/Posters
        }

        // 2. Base Playability Points
        when (candidate.protocol) {
            StreamProtocol.HLS -> score += 45 // Prioritas utama untuk streaming anime adaptive
            StreamProtocol.DASH -> score += 40
            StreamProtocol.PROGRESSIVE -> {
                score += when (candidate.container) {
                    ContainerFormat.MP4, ContainerFormat.FMP4 -> 35
                    ContainerFormat.WEBM, ContainerFormat.MATROSKA -> 30
                    ContainerFormat.MPEG_TS -> 25
                    else -> 20
                }
            }
            StreamProtocol.SMOOTH_STREAMING -> score += 25
            StreamProtocol.RTSP -> score += 20
            StreamProtocol.WEB_EMBED -> score += 15 // Sandbox fallback
            StreamProtocol.UNKNOWN -> score += 5
        }

        // 3. Validation Confidence
        if (candidate.isValidated) {
            score += 25
        }

        // 4. MIME Type Validity
        if (!candidate.mimeType.isNullOrBlank()) {
            if (candidate.mimeType.contains("mpegurl", ignoreCase = true) ||
                candidate.mimeType.contains("video/", ignoreCase = true) ||
                candidate.mimeType.contains("dash+xml", ignoreCase = true)
            ) {
                score += 15
            }
        }

        // 5. Header Policies (Referer, Origin, User-Agent tersedia)
        if (candidate.requestPolicy.referer != null) score += 5
        if (candidate.requestPolicy.userAgent != null) score += 5

        // 6. Direct Video Indicator
        if (candidate.isDirectVideo) {
            score += 15
        }

        return score.coerceIn(-100, 100)
    }

    /**
     * Mengurutkan candidate dari score tertinggi ke terendah, memfilter blacklist.
     */
    fun rankCandidates(candidates: List<StreamCandidate>): List<StreamCandidate> {
        return candidates
            .map { it.copy(score = calculateScore(it)) }
            .filter { it.score > 0 }
            .sortedWith(
                compareByDescending<StreamCandidate> { it.score }
                    .thenByDescending { it.confidence }
            )
    }
}
