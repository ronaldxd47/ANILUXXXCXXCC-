package com.example.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

object DonghubScraper : BaseAnimestreamScraper(
    tag = "DonghubScraper",
    sourceName = "Donghub",
    domains = listOf(
        "https://donghive.vip",
        "https://donghub.vip"
    ),
    domainKeywords = listOf("donghive", "donghub")
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
            val doc = getDocWithFallback("/schedule/", allowWebViewFallback = false)
                ?: getDocWithFallback("/jadwal-rilis/", allowWebViewFallback = false)
                ?: return@withContext emptyList()
            parseScheduleFromDoc(doc)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Error parsing Donghub/Donghive schedule", e)
            emptyList()
        }
    }
}
