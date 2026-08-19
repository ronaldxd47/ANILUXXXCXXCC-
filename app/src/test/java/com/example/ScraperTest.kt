package com.example

import com.example.data.SamehadakuScraper
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class ScraperTest {
    @Test
    fun testAnichinEpisode() = runBlocking {
        try {
            val list = com.example.data.AnichinScraper.fetchLatestUpdates(1)
            var out = "Updates Count: ${list.size}\n"
            list.take(20).forEach {
                out += " - ${it.title} -> ${it.link}\n"
            }
            File("scraper_test_result.txt").writeText(out)
        } catch (e: Exception) {
            File("scraper_test_result.txt").writeText(e.toString())
        }
    }

    @Test
    fun testDonghubUpdates() = runBlocking {
        try {
            val list = com.example.data.DonghubScraper.fetchLatestUpdates(1)
            var out = "Donghub Count: ${list.size}\n"
            list.take(20).forEach {
                out += " - ${it.title} -> ${it.link}\n"
            }
            File("donghub_test_result.txt").writeText(out)
        } catch (e: Exception) {
            File("donghub_test_result.txt").writeText(e.toString())
        }
    }

    @Test
    fun testSamehadakuSchedule() = runBlocking {
        try {
            // Fetch the schedule page using SamehadakuScraper's robust method
            val conn = SamehadakuScraper.getDocWithFallback("/jadwal-rilis/")
            if (conn == null) {
                File("schedule_structure_test.txt").writeText("Error: getDocWithFallback returned null")
                return@runBlocking
            }
            
            // Log the document title and general html size
            var out = "Title: ${conn.title()}\nHTML Size: ${conn.outerHtml().length}\n"
            
            // Let's print some of the body content if empty
            val scheduleBlocks = conn.select(".schedule, .schedule-layout, .schedule-day, .days, .tab-content, .schedule-tab, .sc-day, .schedule-card")
            out += "Found ${scheduleBlocks.size} schedule containers\n"
            scheduleBlocks.forEachIndexed { idx, el ->
                out += "[$idx] Tag: ${el.tagName()}, Class: ${el.className()}, ID: ${el.id()}\n"
                val html = el.outerHtml()
                out += "Snippet: ${if (html.length > 500) html.take(500) + "..." else html}\n\n"
            }
            
            // Search for some classes that usually contain schedule days
            val classNames = listOf(".schedule-card", ".schedule", ".tab-content", ".schedule-tab", ".sc-day", ".days", ".day", ".schedule-day")
            classNames.forEach { className ->
                val selected = conn.select(className)
                out += "Class '$className': ${selected.size} elements\n"
                selected.take(2).forEach { el ->
                    out += "  - Tag: ${el.tagName()}, Class: ${el.className()}, Text: ${el.text().take(150)}\n"
                }
            }
            
            // Also search for Indonesian day names
            val dayNames = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
            dayNames.forEach { day ->
                val containing = conn.select(":contains($day)")
                out += "Containing '$day': ${containing.size} elements\n"
                containing.take(3).forEach { el ->
                    out += "  - Tag: ${el.tagName()}, Class: ${el.className()}, Text: ${el.text().take(100)}\n"
                }
            }
            
            // Dump the first 1000 characters of body to see what's actually there
            out += "\n\n--- BODY SNAPSHOT ---\n"
            out += conn.select("body").text().take(1500)
            
            File("schedule_structure_test.txt").writeText(out)
        } catch (e: Exception) {
            File("schedule_structure_test.txt").writeText("Error: " + e.stackTraceToString())
        }
    }

    @Test
    fun testAnichinSchedule() = runBlocking {
        try {
            val list = com.example.data.AnichinScraper.fetchSchedule()
            var out = "Parsed Days count: ${list.size}\n"
            list.forEach { day ->
                out += "Day: ${day.day} (${day.animeList.size} anime)\n"
                day.animeList.forEach { anime ->
                    out += "  - ${anime.title} -> ${anime.link}\n"
                }
                out += "\n"
            }
            File("anichin_schedule_test.txt").writeText(out)
        } catch (e: Exception) {
            File("anichin_schedule_test.txt").writeText("Error: " + e.stackTraceToString())
        }
    }

    @Test
    fun testSamehadakuUpdates() = runBlocking {
        try {
            // Test with the current base URL
            val list = com.example.data.SamehadakuScraper.fetchLatestUpdates(1)
            var out = "Samehadaku Count: ${list.size}\n"
            list.take(20).forEach {
                out += " - ${it.title} -> ${it.link}\n"
            }
            File("samehadaku_test_result.txt").writeText(out)
        } catch (e: Exception) {
            File("samehadaku_test_result.txt").writeText(e.toString())
        }
    }
}
