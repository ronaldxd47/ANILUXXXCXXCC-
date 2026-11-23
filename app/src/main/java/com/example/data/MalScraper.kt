package com.example.data

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.IOException
import java.util.concurrent.TimeUnit

object MalScraper {
    private const val TAG = "MalScraper"
    private const val MAL_SCHEDULE_URL = "https://myanimelist.net/anime/season/schedule"
    private const val JIKAN_SCHEDULE_URL = "https://api.jikan.moe/v4/schedules?sfw=true"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val dayMap = mapOf(
        "monday" to "Senin",
        "mondays" to "Senin",
        "tuesday" to "Selasa",
        "tuesdays" to "Selasa",
        "wednesday" to "Rabu",
        "wednesdays" to "Rabu",
        "thursday" to "Kamis",
        "thursdays" to "Kamis",
        "friday" to "Jumat",
        "fridays" to "Jumat",
        "saturday" to "Sabtu",
        "saturdays" to "Sabtu",
        "sunday" to "Minggu",
        "sundays" to "Minggu",
        "other" to "Lainnya",
        "unknown" to "Tidak Diketahui"
    )

    suspend fun fetchSchedule(): List<ScheduleDay> = withContext(Dispatchers.IO) {
        try {
            val html = fetchUrl(
                url = MAL_SCHEDULE_URL,
                accept = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
            )
            if (!html.isNullOrBlank()) {
                val parsed = parseScheduleHtml(html)
                if (parsed.isNotEmpty()) {
                    return@withContext parsed
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "Primary MAL schedule request timed out or failed (${e.message}), trying Jikan fallback")
        } catch (e: Exception) {
            Log.w(TAG, "Primary MAL schedule parse failed (${e.message}), trying Jikan fallback")
        }

        // Fallback ringan ke Jikan REST API v4 bila myanimelist.net lambat/timeout
        try {
            val json = fetchUrl(
                url = JIKAN_SCHEDULE_URL,
                accept = "application/json"
            )
            if (!json.isNullOrBlank()) {
                return@withContext parseJikanScheduleJson(json)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "MAL & Jikan schedule unavailable: ${e.message}")
        }

        emptyList()
    }

    private fun fetchUrl(url: String, accept: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", BaseAnimestreamScraper.CHROME_USER_AGENT)
            .header("Accept", accept)
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "HTTP ${response.code} for $url")
                return null
            }
            return response.body?.string()
        }
    }

    internal fun parseScheduleHtml(html: String): List<ScheduleDay> {
        val result = mutableListOf<ScheduleDay>()
        val doc = Jsoup.parse(html, MAL_SCHEDULE_URL)
        val dayContainers = doc.select(".seasonal-anime-list")

        for (dayContainer in dayContainers) {
            val headerText = dayContainer.selectFirst(".anime-header")?.text()?.trim() ?: continue
            val mappedDay = normalizeMalDay(headerText)

            val animeNodes = dayContainer.select(".seasonal-anime")
            val scheduleAnimes = mutableListOf<ScheduleAnime>()

            for (node in animeNodes) {
                val titleNode = node.selectFirst(".link-title") ?: continue
                val title = titleNode.text().trim()
                val link = titleNode.attr("href").trim()
                if (title.isEmpty() || link.isEmpty()) continue

                val imageNode = node.selectFirst(".image img")
                val imageUrl = imageNode?.attr("data-src")?.takeIf { it.isNotBlank() }
                    ?: imageNode?.attr("src") ?: ""

                var cleanImgUrl = imageUrl.trim()
                if (cleanImgUrl.contains("/r/")) {
                    cleanImgUrl = cleanImgUrl.substringBefore("?").replace(Regex("/r/\\d+x\\d+"), "")
                }

                scheduleAnimes.add(
                    ScheduleAnime(
                        title = title,
                        link = link,
                        releaseDay = mappedDay,
                        imageUrl = cleanImgUrl,
                        source = "MyAnimeList"
                    )
                )
            }

            if (scheduleAnimes.isNotEmpty()) {
                result.add(ScheduleDay(day = mappedDay, animeList = scheduleAnimes))
            }
        }
        return result
    }

    internal fun parseJikanScheduleJson(rawJson: String): List<ScheduleDay> {
        val root = JSONObject(rawJson)
        val dataArray = root.optJSONArray("data") ?: return emptyList()
        val grouped = linkedMapOf<String, MutableList<ScheduleAnime>>()

        for (i in 0 until dataArray.length()) {
            val item = dataArray.optJSONObject(i) ?: continue
            val title = item.optString("title", "").trim()
            val link = item.optString("url", "").trim()
            if (title.isEmpty() || link.isEmpty()) continue

            val broadcast = item.optJSONObject("broadcast")
            val rawDay = broadcast?.optString("day", "")?.trim().orEmpty()
            val mappedDay = normalizeMalDay(rawDay)
            val time = broadcast?.optString("time", "")?.trim().orEmpty()

            val images = item.optJSONObject("images")
            val webp = images?.optJSONObject("webp")
            val jpg = images?.optJSONObject("jpg")
            val imageUrl = webp?.optString("large_image_url").orEmpty()
                .ifEmpty { webp?.optString("image_url").orEmpty() }
                .ifEmpty { jpg?.optString("large_image_url").orEmpty() }
                .ifEmpty { jpg?.optString("image_url").orEmpty() }

            grouped.getOrPut(mappedDay) { mutableListOf() }.add(
                ScheduleAnime(
                    title = title,
                    link = link,
                    episode = if (time.isNotEmpty()) "$time JST" else "",
                    releaseDay = mappedDay,
                    imageUrl = imageUrl,
                    source = "MyAnimeList"
                )
            )
        }

        return grouped.map { (day, list) -> ScheduleDay(day = day, animeList = list) }
    }

    private fun normalizeMalDay(raw: String): String {
        val clean = raw.trim().lowercase()
        return dayMap[clean]
            ?: BaseAnimestreamScraper.normalizeDayName(raw)
            ?: raw.ifBlank { "Lainnya" }
    }
}
