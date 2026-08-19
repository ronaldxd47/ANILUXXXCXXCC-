package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

object MalScraper {
    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    )

    suspend fun fetchSchedule(): List<ScheduleDay> = withContext(Dispatchers.IO) {
        val daysList = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday", "Other", "Unknown")
        val dayMap = mapOf(
            "Monday" to "Senin",
            "Tuesday" to "Selasa",
            "Wednesday" to "Rabu",
            "Thursday" to "Kamis",
            "Friday" to "Jumat",
            "Saturday" to "Sabtu",
            "Sunday" to "Minggu",
            "Other" to "Lainnya",
            "Unknown" to "Tidak Diketahui"
        )
        val result = mutableListOf<ScheduleDay>()

        try {
            val doc = Jsoup.connect("https://myanimelist.net/anime/season/schedule")
                .headers(headers)
                .timeout(8000)
                .get()

            val dayContainers = doc.select(".seasonal-anime-list")
            for (dayContainer in dayContainers) {
                val headerText = dayContainer.selectFirst(".anime-header")?.text()?.trim() ?: continue
                val mappedDay = dayMap[headerText] ?: headerText

                val animeNodes = dayContainer.select(".seasonal-anime")
                val scheduleAnimes = mutableListOf<ScheduleAnime>()

                for (node in animeNodes) {
                    val titleNode = node.selectFirst(".link-title") ?: continue
                    val title = titleNode.text()
                    val link = titleNode.attr("href")

                    val imageNode = node.selectFirst(".image img")
                    val imageUrl = imageNode?.attr("data-src")?.takeIf { it.isNotBlank() }
                        ?: imageNode?.attr("src") ?: ""
                        
                    // Fix high-res image URL from MAL (remove formatting params)
                    var cleanImgUrl = imageUrl
                    if (cleanImgUrl.contains("/r/")) {
                        cleanImgUrl = cleanImgUrl.substringBefore("?").replace(Regex("/r/\\d+x\\d+"), "")
                    }

                    scheduleAnimes.add(
                        ScheduleAnime(
                            title = title,
                            link = link,
                            imageUrl = cleanImgUrl,
                            source = "MyAnimeList"
                        )
                    )
                }

                if (scheduleAnimes.isNotEmpty()) {
                    result.add(ScheduleDay(day = mappedDay, animeList = scheduleAnimes))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MalScraper", "Error fetching schedule: \${e.message}")
        }
        result
    }
}
