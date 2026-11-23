package com.example.data

import com.example.data.cache.TtlMemoryCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Interface Repository yang mengabstraksi pengambilan jadwal rilis (release schedule).
 * Siap dimigrasi ke endpoint REST API backend di masa depan tanpa mengubah ViewModel/UI.
 */
interface ScheduleRepository {
    suspend fun getReleaseSchedule(forceRefresh: Boolean = false): List<ScheduleDay>
    fun invalidateCache()

    companion object {
        operator fun invoke(): ScheduleRepository = MultiSourceScheduleRepository()
    }
}

/**
 * Implementasi multi-source ScheduleRepository yang menggabungkan jadwal dari
 * Samehadaku, Anichin, Donghive/Donghub, dan MyAnimeList dengan TTL caching 10 menit.
 */
class MultiSourceScheduleRepository : ScheduleRepository {

    companion object {
        private const val SCHEDULE_CACHE_KEY = "combined_schedule"
        private const val SCHEDULE_TTL_MS = 10 * 60 * 1000L
    }

    private val cache = TtlMemoryCache<String, List<ScheduleDay>>(defaultTtlMs = SCHEDULE_TTL_MS, maxEntries = 2)

    override suspend fun getReleaseSchedule(forceRefresh: Boolean): List<ScheduleDay> = withContext(Dispatchers.IO) {
        cache.getOrFetch(
            key = SCHEDULE_CACHE_KEY,
            forceRefresh = forceRefresh,
            shouldCache = { !it.isNullOrEmpty() }
        ) {
            fetchCombinedSchedule()
        } ?: emptyList()
    }

    override fun invalidateCache() {
        cache.clear()
    }

    private suspend fun fetchCombinedSchedule(): List<ScheduleDay> {
        val daysList = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
        val combinedMap = daysList.associateWith { mutableListOf<ScheduleAnime>() }.toMutableMap()

        try {
            withTimeoutOrNull(12000L) {
                supervisorScope {
                    val anichinDef = async { safeScheduleSource("Anichin") { AnichinScraper.fetchSchedule() } }
                    val samehadakuDef = async { safeScheduleSource("Samehadaku") { SamehadakuScraper.fetchSchedule() } }
                    val donghubDef = async { safeScheduleSource("Donghub") { DonghubScraper.fetchSchedule() } }
                    val malDef = async { safeScheduleSource("MAL") { MalScraper.fetchSchedule() } }

                    val results = awaitAll(samehadakuDef, anichinDef, donghubDef, malDef)
                    results.forEach { schedules ->
                        mergeSchedules(schedules, daysList, combinedMap)
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("ScheduleRepository", "Error fetching combined schedule: ${e.message}")
        }

        val result = mutableListOf<ScheduleDay>()
        for (day in daysList) {
            val list = combinedMap[day] ?: emptyList()
            if (list.isNotEmpty()) {
                result.add(ScheduleDay(day = day, animeList = list))
            }
        }

        combinedMap.forEach { (day, list) ->
            if (!daysList.contains(day) && list.isNotEmpty()) {
                result.add(ScheduleDay(day = day, animeList = list))
            }
        }

        return result
    }

    private suspend inline fun safeScheduleSource(
        source: String,
        crossinline block: suspend () -> List<ScheduleDay>
    ): List<ScheduleDay> {
        return try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("ScheduleRepository", "$source schedule failed: ${e.message}")
            emptyList()
        }
    }

    private fun mergeSchedules(
        schedules: List<ScheduleDay>,
        daysList: List<String>,
        combinedMap: MutableMap<String, MutableList<ScheduleAnime>>
    ) {
        for (scheduleDay in schedules) {
            val rawDay = scheduleDay.day.trim()
            val matchedDay = BaseAnimestreamScraper.normalizeDayName(rawDay)
                ?: daysList.find { it.equals(rawDay, ignoreCase = true) || rawDay.contains(it, ignoreCase = true) }
                ?: rawDay

            val targetList = combinedMap.getOrPut(matchedDay) { mutableListOf() }
            for (anime in scheduleDay.animeList) {
                val cleanTitle = anime.title.replace("[SVIP]", "").trim()
                if (targetList.none {
                        it.link == anime.link ||
                            it.title.equals(cleanTitle, ignoreCase = true) ||
                            (it.title.length > 5 && cleanTitle.length > 5 &&
                                (it.title.contains(cleanTitle, ignoreCase = true) || cleanTitle.contains(it.title, ignoreCase = true)))
                    }
                ) {
                    targetList.add(anime)
                }
            }
        }
    }
}
