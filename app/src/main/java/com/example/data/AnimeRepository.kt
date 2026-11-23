package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.cache.TtlMemoryCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Interface Repository untuk abstraksi data anime & donghua.
 * Memenuhi arsitektur Clean Code & Repository Pattern, siap dimigrasi
 * ke backend REST API (NestJS/Express + PostgreSQL/MongoDB) di masa depan.
 */
interface AnimeRepository {
    /** Menginisialisasi konteks runtime yang diperlukan oleh data source (seperti WebView fallback). */
    fun initialize(context: Context)

    /** Mengambil update episode anime & donghua terbaru (Fase 1 Fast Load). */
    suspend fun getLatestUpdates(
        phase1Timeout: Long = 9000L,
        forceRefresh: Boolean = false
    ): List<ScrapedAnime>

    /** Mengambil data halaman tambahan / background pagination. */
    suspend fun getPageUpdates(
        page: Int,
        timeout: Long = 12000L,
        forceRefresh: Boolean = false
    ): List<ScrapedAnime>

    /** Mencari anime / donghua berdasarkan kata kunci. */
    suspend fun searchAnime(query: String, timeout: Long = 6000L): List<ScrapedAnime>

    /** Mengambil detail anime (deskripsi, episode list, genre, rating). */
    suspend fun getAnimeDetail(
        url: String,
        timeout: Long = 20000L,
        forceRefresh: Boolean = false
    ): AnimeDetail?

    /** Mengambil opsi server streaming dan link download episode. */
    suspend fun getEpisodeDetail(
        url: String,
        timeout: Long = 20000L,
        forceRefresh: Boolean = false
    ): EpisodeDetail?

    /** Menghapus seluruh cache in-memory repository. */
    fun invalidateCache()
}

/**
 * Implementasi MultiSource Repository yang menggabungkan dan mengagregasi
 * data dari Samehadaku, Anichin, dan Donghub/Donghive secara asynchronous
 * dengan TTL caching dan in-flight request deduplication.
 */
class MultiSourceAnimeRepository(
    context: Context? = null
) : AnimeRepository {

    companion object {
        private const val TAG = "AnimeRepository"
        private const val FEED_CACHE_TTL_MS = 3 * 60 * 1000L
        private const val DETAIL_CACHE_TTL_MS = 10 * 60 * 1000L
        private const val EPISODE_CACHE_TTL_MS = 2 * 60 * 1000L
    }

    init {
        context?.let { initialize(it) }
    }

    private val pageCache = TtlMemoryCache<Int, List<ScrapedAnime>>(defaultTtlMs = FEED_CACHE_TTL_MS, maxEntries = 16)
    private val detailCache = TtlMemoryCache<String, AnimeDetail>(defaultTtlMs = DETAIL_CACHE_TTL_MS, maxEntries = 64)
    private val episodeCache = TtlMemoryCache<String, EpisodeDetail>(defaultTtlMs = EPISODE_CACHE_TTL_MS, maxEntries = 64)

    override fun initialize(context: Context) {
        val appCtx = context.applicationContext
        SamehadakuScraper.appContext = appCtx
        AnichinScraper.appContext = appCtx
        DonghubScraper.appContext = appCtx
    }

    override suspend fun getLatestUpdates(
        phase1Timeout: Long,
        forceRefresh: Boolean
    ): List<ScrapedAnime> = getPageUpdates(page = 1, timeout = phase1Timeout, forceRefresh = forceRefresh)

    override suspend fun getPageUpdates(
        page: Int,
        timeout: Long,
        forceRefresh: Boolean
    ): List<ScrapedAnime> = withContext(Dispatchers.IO) {
        pageCache.getOrFetch(
            key = page,
            forceRefresh = forceRefresh,
            shouldCache = { !it.isNullOrEmpty() }
        ) {
            var b1 = emptyList<ScrapedAnime>()
            var b2 = emptyList<ScrapedAnime>()
            var b3 = emptyList<ScrapedAnime>()

            supervisorScope {
                val bg1 = async { safeFetch("Samehadaku-p$page") { withTimeoutOrNull(timeout) { SamehadakuScraper.fetchLatestUpdates(page) } } ?: emptyList() }
                val bg2 = async { safeFetch("Anichin-p$page") { withTimeoutOrNull(timeout) { AnichinScraper.fetchLatestUpdates(page) } } ?: emptyList() }
                val bg3 = async { safeFetch("Donghub-p$page") { withTimeoutOrNull(timeout) { DonghubScraper.fetchLatestUpdates(page) } } ?: emptyList() }

                b1 = bg1.await()
                b2 = bg2.await()
                b3 = bg3.await()
            }

            deduplicateAnimeList(interleaveLists(b1, b2, b3))
        } ?: emptyList()
    }

    override suspend fun searchAnime(query: String, timeout: Long): List<ScrapedAnime> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return@withContext emptyList()

        val rawScraped = mutableListOf<ScrapedAnime>()
        try {
            supervisorScope {
                val d1 = async { safeFetch("Search-Samehadaku") { SamehadakuScraper.searchAnime(cleanQuery) } ?: emptyList() }
                val d2 = async { safeFetch("Search-Anichin") { AnichinScraper.searchAnime(cleanQuery) } ?: emptyList() }
                val d3 = async { safeFetch("Search-Donghub") { DonghubScraper.searchAnime(cleanQuery) } ?: emptyList() }

                val results = withTimeoutOrNull(timeout) {
                    awaitAll(d1, d2, d3)
                } ?: emptyList()

                for (res in results) {
                    rawScraped.addAll(res)
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Search supervisor error: ${e.message}")
        }
        deduplicateAnimeList(rawScraped)
    }

    override suspend fun getAnimeDetail(
        url: String,
        timeout: Long,
        forceRefresh: Boolean
    ): AnimeDetail? = withContext(Dispatchers.IO) {
        val normalizedKey = url.trim()
        if (normalizedKey.isEmpty()) return@withContext null

        detailCache.getOrFetch(
            key = normalizedKey,
            forceRefresh = forceRefresh,
            shouldCache = { it != null && it.title.isNotBlank() && it.title != "Title Missing" }
        ) {
            withTimeoutOrNull(timeout) {
                when (AnimeSource.fromUrl(normalizedKey)) {
                    AnimeSource.ANICHIN -> AnichinScraper.fetchAnimeDetail(normalizedKey)
                    AnimeSource.DONGHUB -> DonghubScraper.fetchAnimeDetail(normalizedKey)
                    else -> SamehadakuScraper.fetchAnimeDetail(normalizedKey)
                }
            }
        }
    }

    override suspend fun getEpisodeDetail(
        url: String,
        timeout: Long,
        forceRefresh: Boolean
    ): EpisodeDetail? = withContext(Dispatchers.IO) {
        val normalizedKey = url.trim()
        if (normalizedKey.isEmpty()) return@withContext null

        episodeCache.getOrFetch(
            key = normalizedKey,
            forceRefresh = forceRefresh,
            shouldCache = { it != null && (it.streamEmbeds.isNotEmpty() || it.downloads.isNotEmpty()) }
        ) {
            withTimeoutOrNull(timeout) {
                when (AnimeSource.fromUrl(normalizedKey)) {
                    AnimeSource.ANICHIN -> AnichinScraper.fetchEpisodeDetail(normalizedKey)
                    AnimeSource.DONGHUB -> DonghubScraper.fetchEpisodeDetail(normalizedKey)
                    else -> SamehadakuScraper.fetchEpisodeDetail(normalizedKey)
                }
            }
        }
    }

    override fun invalidateCache() {
        pageCache.clear()
        detailCache.clear()
        episodeCache.clear()
    }

    private inline fun <T> safeFetch(label: String, block: () -> T?): T? {
        return try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "$label failed: ${e.message}")
            null
        }
    }

    private fun interleaveLists(
        l1: List<ScrapedAnime>,
        l2: List<ScrapedAnime>,
        l3: List<ScrapedAnime>
    ): List<ScrapedAnime> {
        val result = mutableListOf<ScrapedAnime>()
        val maxLen = maxOf(l1.size, maxOf(l2.size, l3.size))
        for (i in 0 until maxLen) {
            if (i < l1.size) result.add(l1[i])
            if (i < l2.size) result.add(l2[i])
            if (i < l3.size) result.add(l3[i])
        }
        return result
    }

    private fun deduplicateAnimeList(list: List<ScrapedAnime>): List<ScrapedAnime> {
        val seenLinks = HashSet<String>()
        val seenNormalizedTitles = HashSet<String>()
        val result = mutableListOf<ScrapedAnime>()

        for (item in list) {
            if (seenLinks.contains(item.link)) continue

            val normTitle = item.title
                .replace(Regex("(?i)\\s*subtitle\\s*indonesia.*"), "")
                .replace(Regex("(?i)\\s*sub\\s*indo.*"), "")
                .replace(Regex("(?i)\\s*episode\\s*\\d+.*"), "")
                .replace(Regex("[^a-zA-Z0-9]"), "")
                .lowercase()

            val uniqueKey = if (normTitle.length > 4) normTitle + "_" + item.episode else item.link
            if (seenNormalizedTitles.contains(uniqueKey)) continue

            seenLinks.add(item.link)
            seenNormalizedTitles.add(uniqueKey)
            result.add(item)
        }
        return result
    }
}
