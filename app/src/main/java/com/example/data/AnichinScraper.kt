package com.example.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

object AnichinScraper : BaseAnimestreamScraper(
    tag = "AnichinScraper",
    sourceName = "Anichin",
    domains = listOf(
        "https://anichin.co",
        "https://anichin.moe",
        "https://anichin.vip"
    ),
    domainKeywords = listOf("anichin")
) {
    var appContext: Context? = null
    val DOMAINS: List<String> get() = domains
    val BASE_URL: String get() = baseUrl

    suspend fun getDocWithFallback(
        urlPath: String,
        allowWebViewFallback: Boolean = true
    ): org.jsoup.nodes.Document? {
        return super.getDocWithFallback(urlPath, appContext, allowWebViewFallback)
    }

    suspend fun fetchLatestUpdates(page: Int = 1): List<ScrapedAnime> {
        val path = if (page > 1) "/page/$page/" else "/"
        val doc = getDocWithFallback(path) ?: return emptyList()
        return parseAnimeListFromDoc(doc, defaultType = "Donghua")
    }

    suspend fun fetchDonghuaList(page: Int = 1): List<ScrapedAnime> {
        val path = if (page > 1) "/donghua-list/page/$page/" else "/donghua-list/"
        val doc = getDocWithFallback(path)
        val list = if (doc != null) parseAnimeListFromDoc(doc, defaultType = "Donghua") else emptyList()
        if (list.isNotEmpty()) return list

        val altPath = if (page > 1) "/page/$page/" else "/"
        val altDoc = getDocWithFallback(altPath) ?: return emptyList()
        return parseAnimeListFromDoc(altDoc, defaultType = "Donghua")
    }

    suspend fun searchAnime(query: String, page: Int = 1): List<ScrapedAnime> {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val path = if (page > 1) "/page/$page/?s=$encoded&post_type=post" else "/?s=$encoded&post_type=post"
        val doc = getDocWithFallback(path) ?: return emptyList()
        return parseAnimeListFromDoc(doc, defaultType = "Donghua")
    }

    suspend fun fetchAnimeDetail(animeUrl: String): AnimeDetail? = withContext(Dispatchers.IO) {
        try {
            val initialDoc = getDocWithFallback(animeUrl) ?: return@withContext null
            val seriesDoc = resolveSeriesDocumentIfEpisode(initialDoc, animeUrl, appContext)
            parseAnimeDetailFromDoc(seriesDoc, cleanSeoBoilerplate = false)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Failed fetchAnimeDetail: ${e.message}")
            null
        }
    }

    suspend fun fetchEpisodeDetail(episodeUrl: String): EpisodeDetail? = withContext(Dispatchers.IO) {
        try {
            val doc = getDocWithFallback(episodeUrl) ?: return@withContext null
            parseEpisodeDetailFromDoc(doc, episodeUrl)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Failed fetchEpisodeDetail: ${e.message}")
            null
        }
    }

    suspend fun fetchSchedule(): List<ScheduleDay> = withContext(Dispatchers.IO) {
        try {
            val doc = getDocWithFallback("/jadwal-rilis/", allowWebViewFallback = false)
                ?: getDocWithFallback("/schedule/", allowWebViewFallback = false)
                ?: return@withContext emptyList()
            parseScheduleFromDoc(doc)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Error parsing schedule", e)
            emptyList()
        }
    }
}

data class ScheduleDay(
    val day: String,
    val animeList: List<ScheduleAnime>
)

data class ScheduleAnime(
    val title: String,
    val link: String,
    val episode: String = "",
    val releaseDay: String = "",
    val imageUrl: String = "",
    val source: String = "Anichin"
)
