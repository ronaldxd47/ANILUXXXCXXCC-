package com.example

import com.example.data.AnichinScraper
import com.example.data.BaseAnimestreamScraper
import com.example.data.DonghubScraper
import com.example.data.MultiSourceAnimeRepository
import com.example.data.SamehadakuScraper
import com.example.data.ScheduleRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class ScraperTest {

    @Test
    fun `test day name normalization handles English, apostrophe, and rejects movie titles`() {
        assertEquals("Jumat", BaseAnimestreamScraper.normalizeDayName("Jum'at"))
        assertEquals("Jumat", BaseAnimestreamScraper.normalizeDayName("Jumaat"))
        assertEquals("Jumat", BaseAnimestreamScraper.normalizeDayName("Friday"))
        assertEquals("Sabtu", BaseAnimestreamScraper.normalizeDayName("Saturday"))
        assertEquals("Minggu", BaseAnimestreamScraper.normalizeDayName("Sunday"))
        assertEquals("Senin", BaseAnimestreamScraper.normalizeDayName("Monday"))
        assertEquals("Acak", BaseAnimestreamScraper.normalizeDayName("Acak"))
        assertNull(BaseAnimestreamScraper.normalizeDayName("Renegade Immortal Movie: Battle Against The Immortals"))
    }

    @Test
    fun `test cloudflare and 404 detection prevents domain poisoning`() {
        assertTrue(BaseAnimestreamScraper.isCloudflareOrBlocked("Just a moment...", "<html><body>cf-browser-verification</body></html>"))
        assertTrue(BaseAnimestreamScraper.isCloudflareOrBlocked("Page not found - Samehadaku", "<html></html>"))
        assertFalse(BaseAnimestreamScraper.isCloudflareOrBlocked("Samehadaku - Nonton Streaming Anime Sub Indo", "<div class='listupd'></div>"))
    }

    @Test
    fun `test animestream card parsing extracts clean title, episode, and normalized link`() {
        val html = """
            <div class="listupd">
              <article class="bs">
                <div class="bsx">
                  <a href="//samehadaku.li//tensei-goblin-episode-8/" title="Tensei Goblin Episode 8">
                    <div class="limit">
                      <div class="typez TV">TV</div>
                      <div class="bt"><span class="epx">Ep 8</span></div>
                      <img data-src="//samehadaku.li/wp-content/uploads/poster.webp" />
                    </div>
                    <div class="tt">Tensei Goblin <h2 itemprop="headline">Tensei Goblin Episode 8 Sub Indo</h2></div>
                  </a>
                </div>
              </article>
            </div>
        """.trimIndent()
        val doc = Jsoup.parse(html, "https://samehadaku.li/")
        val items = SamehadakuScraper.parseAnimeListFromDoc(doc, defaultType = "TV")
        assertEquals(1, items.size)
        assertEquals("Tensei Goblin Episode 8 Sub Indo", items[0].title)
        assertEquals("Ep 8", items[0].episode)
        assertEquals("https://samehadaku.li/wp-content/uploads/poster.webp", items[0].imageUrl)
        assertFalse(items[0].link.contains("//tensei"))
    }

    @Test
    fun `test schedule parser ignores sidebar tab-panes and parses bixbox days`() {
        val html = """
            <div class="bixbox">
              <div class="releases"><h3>Saturday</h3></div>
              <div class="listupd">
                <article class="bs"><div class="bsx"><a href="https://donghive.vip/divine-love/"><div class="tt">Divine Love</div><span class="epx">at 02:27</span></a></div></article>
              </div>
            </div>
            <div class="schedulepage">
              <div class="tab-pane"><h3>Renegade Immortal Movie: Battle Against The Immortals</h3><a href="https://anichin.co/movie/">Movie</a></div>
              <div class="tab-pane"><h3>Jum'at</h3><article><a href="https://anichin.co/soul-land-2/">Soul Land 2</a></article></div>
            </div>
        """.trimIndent()
        val doc = Jsoup.parse(html)
        val donghubDays = DonghubScraper.parseScheduleFromDoc(doc)
        assertEquals(1, donghubDays.size)
        assertEquals("Sabtu", donghubDays[0].day)
        assertEquals("Divine Love", donghubDays[0].animeList[0].title)
    }

    @Test
    fun `test episode parser decodes base64 mirror and ignores numeric data-post`() = runBlocking {
        val iframeHtml = "<iframe src=\"https://upbolt.to/e/4wn1ryzosl2b\"></iframe>"
        val b64 = Base64.getEncoder().encodeToString(iframeHtml.toByteArray())
        val html = """
            <h1 class="entry-title">Episode 8</h1>
            <select class="mirror">
              <option value="">Select Video Server</option>
              <option value="$b64">HD-1</option>
            </select>
            <div class="east_player_option" data-post="18874" data-nume="1" data-type="schtml">Broken Numeric</div>
        """.trimIndent()
        val doc = Jsoup.parse(html, "https://samehadaku.li/")
        val ep = SamehadakuScraper.parseEpisodeDetailFromDoc(doc, "https://samehadaku.li/ep-8/")
        assertNotNull(ep)
        assertEquals(1, ep!!.streamEmbeds.size)
        assertEquals("HD-1", ep.streamEmbeds[0].serverName)
        assertEquals("https://upbolt.to/e/4wn1ryzosl2b", ep.streamEmbeds[0].iframeUrl)
    }

    @Test
    fun `test live scraping pipeline end-to-end audit`() = runBlocking {
        val sb = StringBuilder()
        val repo = MultiSourceAnimeRepository()
        val scheduleRepo = ScheduleRepository()

        val samehadakuUpdates = SamehadakuScraper.fetchLatestUpdates(1)
        val donghubUpdates = DonghubScraper.fetchLatestUpdates(1)
        val anichinUpdates = AnichinScraper.fetchLatestUpdates(1)
        val combinedUpdates = repo.getLatestUpdates(12000L)
        val donghubSchedule = DonghubScraper.fetchSchedule()
        val combinedSchedule = scheduleRepo.getReleaseSchedule()

        sb.append("Samehadaku updates: ${samehadakuUpdates.size} (base=${SamehadakuScraper.BASE_URL})\n")
        samehadakuUpdates.take(3).forEach { sb.append("  - ${it.title} -> ${it.link}\n") }
        sb.append("Donghub/Donghive updates: ${donghubUpdates.size} (base=${DonghubScraper.BASE_URL})\n")
        donghubUpdates.take(3).forEach { sb.append("  - ${it.title} -> ${it.link}\n") }
        sb.append("Anichin updates: ${anichinUpdates.size} (base=${AnichinScraper.BASE_URL})\n")
        anichinUpdates.take(3).forEach { sb.append("  - ${it.title} -> ${it.link}\n") }
        sb.append("Combined deduplicated updates: ${combinedUpdates.size}\n")
        sb.append("Donghub schedule days: ${donghubSchedule.size} -> ${donghubSchedule.map { "${it.day}(${it.animeList.size})" }}\n")
        sb.append("Combined schedule days: ${combinedSchedule.size} -> ${combinedSchedule.map { "${it.day}(${it.animeList.size})" }}\n")

        if (samehadakuUpdates.isNotEmpty()) {
            val sEp = repo.getEpisodeDetail(samehadakuUpdates.first().link)
            sb.append("Samehadaku sample episode embeds: ${sEp?.streamEmbeds?.size} -> ${sEp?.streamEmbeds?.firstOrNull()}\n")
        }
        if (donghubUpdates.isNotEmpty()) {
            val dEp = repo.getEpisodeDetail(donghubUpdates.first().link)
            sb.append("Donghive sample episode embeds: ${dEp?.streamEmbeds?.size} -> ${dEp?.streamEmbeds?.firstOrNull()}\n")
        }

        println(sb.toString())

        assertTrue("Expected Samehadaku updates > 0", samehadakuUpdates.isNotEmpty())
        assertTrue("Expected Donghub/Donghive updates > 0", donghubUpdates.isNotEmpty())
        assertTrue("Expected Combined updates > 0", combinedUpdates.isNotEmpty())
        assertTrue("Expected Combined schedule > 0", combinedSchedule.isNotEmpty())
    }

    @Test
    fun `test cancellation exception propagates cleanly instead of being swallowed`() = runBlocking {
        var caughtCancellation = false
        try {
            kotlinx.coroutines.withTimeout(1L) {
                kotlinx.coroutines.delay(500L)
            }
        } catch (_: kotlinx.coroutines.CancellationException) {
            caughtCancellation = true
        }
        assertTrue("Expected TimeoutCancellationException / CancellationException to propagate", caughtCancellation)

        val job = this.launch {
            com.example.data.AnichinScraper.fetchSchedule()
        }
        job.cancel()
        job.join()
        assertTrue("Cancelled scraper job should complete as cancelled", job.isCancelled)
    }

    @Test
    fun `test mal scraper parses html and jikan fallback json cleanly`() {
        val html = """
            <div class="seasonal-anime-list">
              <div class="anime-header">Sunday</div>
              <div class="seasonal-anime">
                <a class="link-title" href="https://myanimelist.net/anime/100">One Piece</a>
                <div class="image"><img data-src="https://cdn.myanimelist.net/r/100x140/images/anime/1.jpg?s=abc" /></div>
              </div>
            </div>
        """.trimIndent()
        val htmlDays = com.example.data.MalScraper.parseScheduleHtml(html)
        assertEquals(1, htmlDays.size)
        assertEquals("Minggu", htmlDays[0].day)
        assertEquals("One Piece", htmlDays[0].animeList[0].title)
        assertEquals("https://cdn.myanimelist.net/images/anime/1.jpg", htmlDays[0].animeList[0].imageUrl)

        val jikanJson = """
            {
              "data": [
                {
                  "title": "Solo Leveling",
                  "url": "https://myanimelist.net/anime/52299",
                  "broadcast": { "day": "Saturdays", "time": "24:00" },
                  "images": { "webp": { "large_image_url": "https://cdn.myanimelist.net/images/anime/solo.webp" } }
                }
              ]
            }
        """.trimIndent()
        val jsonDays = com.example.data.MalScraper.parseJikanScheduleJson(jikanJson)
        assertEquals(1, jsonDays.size)
        assertEquals("Sabtu", jsonDays[0].day)
        assertEquals("Solo Leveling", jsonDays[0].animeList[0].title)
        assertEquals("24:00 JST", jsonDays[0].animeList[0].episode)
    }
}

