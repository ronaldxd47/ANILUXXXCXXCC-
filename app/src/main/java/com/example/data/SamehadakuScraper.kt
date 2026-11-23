package com.example.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

data class ScrapedAnime(
    val title: String,
    val link: String,
    val imageUrl: String,
    val status: String = "",
    val episode: String = "",
    val score: String = "",
    val type: String = ""
)

data class AnimeDetail(
    val title: String,
    val posterUrl: String,
    val synopsis: String,
    val rating: String = "N/A",
    val status: String = "Unknown",
    val studio: String = "Unknown",
    val released: String = "Unknown",
    val duration: String = "Unknown",
    val genres: List<String> = emptyList(),
    val episodes: List<EpisodeItem> = emptyList()
)

data class EpisodeItem(
    val title: String,
    val link: String,
    val date: String = ""
)

data class EpisodeDetail(
    val title: String,
    val streamEmbeds: List<StreamEmbed> = emptyList(),
    val downloads: List<DownloadGroup> = emptyList()
)

data class StreamEmbed(
    val serverName: String,
    val iframeUrl: String
)

data class DownloadGroup(
    val resolution: String,
    val format: String = "MP4/MKV",
    val links: List<DownloadLink> = emptyList()
)

data class DownloadLink(
    val host: String,
    val url: String
)

object SamehadakuScraper : BaseAnimestreamScraper(
    tag = "SamehadakuScraper",
    sourceName = "Samehadaku",
    domains = listOf(
        "https://samehadaku.li",
        "https://samehadaku.ac",
        "https://v2.samehadaku.how",
        "https://samehadaku.how"
    ),
    domainKeywords = listOf("samehadaku"),
    useDoh = true
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

    /**
     * Active Samehadaku mirrors (samehadaku.li / samehadaku.ac) use Animestream root `/` and `/page/{page}/`,
     * while legacy Eastplay mirrors used `/anime-terbaru/`. Try root first, then fall back to `/anime-terbaru/`.
     */
    suspend fun fetchLatestUpdates(page: Int = 1): List<ScrapedAnime> {
        val primaryPath = if (page > 1) "/page/$page/" else "/"
        val primaryDoc = getDocWithFallback(primaryPath)
        val primaryList = if (primaryDoc != null) parseAnimeListFromDoc(primaryDoc, defaultType = "TV") else emptyList()
        if (primaryList.isNotEmpty()) return primaryList

        val legacyPath = if (page > 1) "/anime-terbaru/page/$page/" else "/anime-terbaru/"
        val legacyDoc = getDocWithFallback(legacyPath) ?: return emptyList()
        return parseAnimeListFromDoc(legacyDoc, defaultType = "TV")
    }

    suspend fun fetchAllAnime(page: Int = 1): List<ScrapedAnime> {
        val primaryPath = if (page > 1) "/anime/page/$page/" else "/anime/"
        val doc = getDocWithFallback(primaryPath)
            ?: getDocWithFallback(if (page > 1) "/daftar-anime-2/page/$page/" else "/daftar-anime-2/")
            ?: return emptyList()
        return parseAnimeListFromDoc(doc, defaultType = "TV")
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
            Log.e(tag, "Error parsing Samehadaku schedule", e)
            emptyList()
        }
    }

    suspend fun searchAnime(query: String, page: Int = 1): List<ScrapedAnime> {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val path = if (page > 1) "/page/$page/?s=$encoded" else "/?s=$encoded"
        val doc = getDocWithFallback(path) ?: return emptyList()
        return parseAnimeListFromDoc(doc, defaultType = "TV")
    }

    suspend fun fetchAnimeDetail(animeUrl: String): AnimeDetail? = withContext(Dispatchers.IO) {
        try {
            val initialDoc = getDocWithFallback(animeUrl) ?: return@withContext null
            val seriesDoc = resolveSeriesDocumentIfEpisode(initialDoc, animeUrl, appContext)
            parseAnimeDetailFromDoc(seriesDoc, cleanSeoBoilerplate = true)
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
}
