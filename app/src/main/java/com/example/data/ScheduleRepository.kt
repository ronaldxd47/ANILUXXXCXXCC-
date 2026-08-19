package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Repository yang mengabstraksi pengambilan data jadwal rilis (release schedule).
 * Mengagregasi data dari Anichin, Samehadaku, dan Donghub secara modular dan aman untuk migrasi masa depan.
 */
class ScheduleRepository {

    /**
     * Mengambil data jadwal rilis dari seluruh sumber anime/donghua scrapers secara paralel.
     * Dibangun asinkron menggunakan Coroutines agar siap diubah menjadi Retrofit API fetch ke backend di masa depan.
     */
    suspend fun getReleaseSchedule(): List<ScheduleDay> = withContext(Dispatchers.IO) {
        val daysList = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
        val combinedMap = daysList.associateWith { mutableListOf<ScheduleAnime>() }.toMutableMap()

        try {
            kotlinx.coroutines.withTimeoutOrNull(6000) {
                coroutineScope {
                    val anichinDef = async { try { AnichinScraper.fetchSchedule() } catch (e: Exception) { emptyList() } }
                    val samehadakuDef = async { try { SamehadakuScraper.fetchSchedule() } catch (e: Exception) { emptyList() } }
                    val donghubDef = async { try { DonghubScraper.fetchSchedule() } catch (e: Exception) { emptyList() } }
                    val malDef = async { try { MalScraper.fetchSchedule() } catch (e: Exception) { emptyList() } }

                    val results = kotlinx.coroutines.awaitAll(anichinDef, samehadakuDef, donghubDef, malDef)
                    val anichinRes = results[0]
                    val samehadakuRes = results[1]
                    val donghubRes = results[2]
                    val malRes = results[3]

                    fun mergeSchedules(schedules: List<ScheduleDay>) {
                        for (scheduleDay in schedules) {
                            val rawDay = scheduleDay.day.trim()
                            val matchedDay = daysList.find { it.equals(rawDay, ignoreCase = true) || rawDay.contains(it, ignoreCase = true) }
                                ?: rawDay

                            val targetList = combinedMap.getOrPut(matchedDay) { mutableListOf() }
                            for (anime in scheduleDay.animeList) {
                                val cleanTitle = anime.title.replace("[SVIP]", "").trim()
                                if (targetList.none { 
                                    it.link == anime.link || 
                                    it.title.equals(cleanTitle, ignoreCase = true) ||
                                    (it.title.length > 5 && cleanTitle.length > 5 && (it.title.contains(cleanTitle, ignoreCase = true) || cleanTitle.contains(it.title, ignoreCase = true)))
                                }) {
                                    targetList.add(anime)
                                }
                            }
                        }
                    }

                    mergeSchedules(samehadakuRes)
                    mergeSchedules(anichinRes)
                    mergeSchedules(donghubRes)
                    mergeSchedules(malRes)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ScheduleRepository", "Error fetching combined schedule", e)
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

        result
    }
}
