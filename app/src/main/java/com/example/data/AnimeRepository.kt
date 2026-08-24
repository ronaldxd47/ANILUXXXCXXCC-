package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Interface Repository untuk abstraksi data anime/donghua.
 * Memenuhi arsitektur Clean Code & Repository Pattern, siap dimigrasi
 * ke backend REST API (NestJS/Express + PostgreSQL/MongoDB) di masa depan.
 */
interface AnimeRepository {
    /** Mengambil update episode anime & donghua terbaru (Fase 1 Fast Load). */
    suspend fun getLatestUpdates(phase1Timeout: Long = 9000L): List<ScrapedAnime>

    /** Mengambil data halaman tambahan / background pagination. */
    suspend fun getPageUpdates(page: Int, timeout: Long = 12000L): List<ScrapedAnime>

    /** Mencari anime / donghua berdasarkan kata kunci. */
    suspend fun searchAnime(query: String, timeout: Long = 6000L): List<ScrapedAnime>

    /** Mengambil detail anime (deskripsi, episode list, genre, rating). */
    suspend fun getAnimeDetail(url: String, timeout: Long = 20000L): AnimeDetail?

    /** Mengambil opsi server streaming dan link download episode. */
    suspend fun getEpisodeDetail(url: String, timeout: Long = 20000L): EpisodeDetail?
}

/**
 * Implementasi MultiSource Repository yang menggabungkan dan mengagregasi
 * data dari Samehadaku, Anichin, dan Donghub secara asynchronous.
 */
class MultiSourceAnimeRepository : AnimeRepository {
    private val TAG = "AnimeRepository"

    override suspend fun getLatestUpdates(phase1Timeout: Long): List<ScrapedAnime> = withContext(Dispatchers.IO) {
        var s1 = emptyList<ScrapedAnime>()
        var s2 = emptyList<ScrapedAnime>()
        var s3 = emptyList<ScrapedAnime>()

        supervisorScope {
            val d1 = async { try { withTimeoutOrNull(phase1Timeout) { SamehadakuScraper.fetchLatestUpdates(1) } ?: emptyList() } catch (e: Exception) { emptyList() } }
            val d2 = async { try { withTimeoutOrNull(phase1Timeout) { AnichinScraper.fetchLatestUpdates(1) } ?: emptyList() } catch (e: Exception) { emptyList() } }
            val d3 = async { try { withTimeoutOrNull(phase1Timeout) { DonghubScraper.fetchLatestUpdates(1) } ?: emptyList() } catch (e: Exception) { emptyList() } }

            s1 = d1.await()
            s2 = d2.await()
            s3 = d3.await()
        }

        val fastInterleaved = interleaveLists(s1, s2, s3)
        deduplicateAnimeList(fastInterleaved)
    }

    override suspend fun getPageUpdates(page: Int, timeout: Long): List<ScrapedAnime> = withContext(Dispatchers.IO) {
        var b1 = emptyList<ScrapedAnime>()
        var b2 = emptyList<ScrapedAnime>()
        var b3 = emptyList<ScrapedAnime>()

        supervisorScope {
            val bg1 = async { try { withTimeoutOrNull(timeout) { SamehadakuScraper.fetchLatestUpdates(page) } ?: emptyList() } catch (e: Exception) { emptyList() } }
            val bg2 = async { try { withTimeoutOrNull(timeout) { AnichinScraper.fetchLatestUpdates(page) } ?: emptyList() } catch (e: Exception) { emptyList() } }
            val bg3 = async { try { withTimeoutOrNull(timeout) { DonghubScraper.fetchLatestUpdates(page) } ?: emptyList() } catch (e: Exception) { emptyList() } }

            b1 = bg1.await()
            b2 = bg2.await()
            b3 = bg3.await()
        }

        val fullInterleaved = interleaveLists(b1, b2, b3)
        deduplicateAnimeList(fullInterleaved)
    }

    override suspend fun searchAnime(query: String, timeout: Long): List<ScrapedAnime> = withContext(Dispatchers.IO) {
        val rawScraped = mutableListOf<ScrapedAnime>()
        try {
            supervisorScope {
                val d1 = async { try { SamehadakuScraper.searchAnime(query) } catch (e: Exception) { emptyList() } }
                val d2 = async { try { AnichinScraper.searchAnime(query) } catch (e: Exception) { emptyList() } }
                val d3 = async { try { DonghubScraper.searchAnime(query) } catch (e: Exception) { emptyList() } }

                val results = withTimeoutOrNull(timeout) {
                    awaitAll(d1, d2, d3)
                } ?: emptyList()

                for (res in results) {
                    rawScraped.addAll(res)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search supervisor error: ${e.message}")
        }
        deduplicateAnimeList(rawScraped)
    }

    override suspend fun getAnimeDetail(url: String, timeout: Long): AnimeDetail? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(timeout) {
            when {
                url.contains("anichin") -> AnichinScraper.fetchAnimeDetail(url)
                url.contains("donghub") -> DonghubScraper.fetchAnimeDetail(url)
                else -> SamehadakuScraper.fetchAnimeDetail(url)
            }
        }
    }

    override suspend fun getEpisodeDetail(url: String, timeout: Long): EpisodeDetail? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(timeout) {
            when {
                url.contains("anichin") -> AnichinScraper.fetchEpisodeDetail(url)
                url.contains("donghub") -> DonghubScraper.fetchEpisodeDetail(url)
                else -> SamehadakuScraper.fetchEpisodeDetail(url)
            }
        }
    }

    private fun deduplicateAnimeList(list: List<ScrapedAnime>): List<ScrapedAnime> {
        val result = mutableListOf<ScrapedAnime>()
        val seenLinks = mutableSetOf<String>()
        val seenTitleKeys = mutableSetOf<String>()

        for (item in list) {
            val cleanLink = item.link.trim().trimEnd('/')
            if (cleanLink.isEmpty()) continue
            if (seenLinks.contains(cleanLink)) continue

            val rawTitle = item.title
            val cleanTitle = rawTitle
                .replace("[SVIP]", "", ignoreCase = true)
                .replace("Sub Indo", "", ignoreCase = true)
                .replace("Subtitle Indonesia", "", ignoreCase = true)
                .trim()

            val titleKey = cleanTitle
                .lowercase()
                .replace(Regex("[^a-z0-9]"), "")
                .trim()

            if (titleKey.isNotEmpty() && seenTitleKeys.contains(titleKey)) {
                continue
            }

            seenLinks.add(cleanLink)
            if (titleKey.isNotEmpty()) {
                seenTitleKeys.add(titleKey)
            }
            result.add(item.copy(title = cleanTitle.ifEmpty { rawTitle }, link = cleanLink))
        }

        return result
    }

    private fun interleaveLists(vararg lists: List<ScrapedAnime>): List<ScrapedAnime> {
        val result = mutableListOf<ScrapedAnime>()
        val maxSize = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until maxSize) {
            for (list in lists) {
                if (i < list.size) {
                    result.add(list[i])
                }
            }
        }
        return result
    }
}
