package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object AnichinScraper {
    private const val TAG = "AnichinScraper"
    val DOMAINS = listOf(
        "https://anichin.moe",
        "https://anichin.co",
        "https://anichin.care",
        "https://anichin.cafe",
        "https://anichin.team"
    )
    var BASE_URL = DOMAINS[0]
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private fun cleanUrl(url: String): String {
        if (url.startsWith("//")) return "https:$url"
        return url
    }

    private fun getRelativePath(url: String): String {
        var clean = url.trim()
        for (domain in DOMAINS) {
            if (clean.startsWith(domain)) {
                return clean.substring(domain.length)
            }
        }
        if (clean.startsWith("http")) {
            val stripped = clean.substringAfter("://")
            val path = stripped.substringAfter("/", "")
            return "/$path"
        }
        return clean
    }

    private fun normalizeStreamUrl(url: String, baseUrl: String): String {
        var clean = url.trim()
        
        // 1. Check if it is HTML containing an iframe
        if (clean.startsWith("<") || clean.contains("<iframe")) {
            try {
                val iframeEl = Jsoup.parse(clean).select("iframe").first()
                if (iframeEl != null) {
                    clean = iframeEl.attr("src").trim()
                }
            } catch (e: Exception) {}
        }
        
        // 2. Base64 check and decoding
        if (!clean.startsWith("http") && !clean.startsWith("//") && !clean.startsWith("/")) {
            try {
                val decodedBytes = android.util.Base64.decode(clean, android.util.Base64.DEFAULT)
                val decodedStr = String(decodedBytes).trim()
                if (decodedStr.startsWith("<") || decodedStr.contains("<iframe")) {
                    val iframeEl = Jsoup.parse(decodedStr).select("iframe").first()
                    if (iframeEl != null) {
                        clean = iframeEl.attr("src").trim()
                    }
                } else if (decodedStr.startsWith("http") || decodedStr.startsWith("//") || decodedStr.startsWith("/")) {
                    clean = decodedStr
                }
            } catch (e: Exception) {}
        }
        
        // 3. Final protocol and absolute URL construction
        if (clean.startsWith("//")) {
            return "https:$clean"
        }
        if (clean.startsWith("/")) {
            return "$baseUrl$clean"
        }
        if (!clean.startsWith("http")) {
            return "https://$clean"
        }
        return clean
    }

    suspend fun getDocWithFallback(urlPath: String): Document? {
        return withContext(Dispatchers.IO) {
            val path = getRelativePath(urlPath)
            
            // Try BASE_URL first (direct attempt to avoid launching parallel requests every time)
            try {
                val fullUrl = "$BASE_URL${if (path.startsWith("/")) "" else "/"}$path"
                val doc = Jsoup.connect(fullUrl)
                    .userAgent(USER_AGENT)
                    .referrer("https://www.google.com/")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9,id;q=0.8")
                    .header("Connection", "keep-alive")
                    .timeout(3500) // 3.5s fast timeout for direct attempt
                    .get()
                
                return@withContext doc
            } catch (e: Exception) {
                Log.d(TAG, "Fast fallback from BASE_URL failed, triggering parallel search across all domains: ${e.message}")
            }

            // Fallback: Run parallel search across all domains
            coroutineScope {
                val resultChannel = kotlinx.coroutines.channels.Channel<Pair<String, Document>?>(DOMAINS.size)
                
                val jobs = DOMAINS.map { domain ->
                    launch(Dispatchers.IO) {
                        try {
                            val fullUrl = "$domain${if (path.startsWith("/")) "" else "/"}$path"
                            val doc = Jsoup.connect(fullUrl)
                                .userAgent(USER_AGENT)
                                .referrer("https://www.google.com/")
                                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8")
                                .header("Accept-Language", "en-US,en;q=0.9,id;q=0.8")
                                .header("Connection", "keep-alive")
                                .timeout(5000) // 5s timeout
                                .get()
                            
                            val hasContent = doc.select(".animepost, .post-show, .infox, .episodelist, .listeps, .listupd, .post-show article, .listupd article, .bsx, .bs").isNotEmpty()
                            if (hasContent || path == "/" || path == "" || path == "/jadwal-rilis/") {
                                resultChannel.send(Pair(domain, doc))
                            } else {
                                resultChannel.send(null)
                            }
                        } catch (e: Exception) {
                            resultChannel.send(null)
                        }
                    }
                }
                
                var successfulDoc: Document? = null
                var responsesCount = 0
                
                while (responsesCount < DOMAINS.size) {
                    val res = resultChannel.receive()
                    if (res != null) {
                        BASE_URL = res.first
                        successfulDoc = res.second
                        break
                    } else {
                        responsesCount++
                    }
                }
                
                // Cancel all running jobs as we have a winner or failed entirely
                jobs.forEach { it.cancel() }
                successfulDoc
            }
        }
    }

    private fun parseAnimeListFromDoc(doc: Document): List<ScrapedAnime> {
        val list = mutableListOf<ScrapedAnime>()
        val elements = doc.select(".listupd .animepost, .post-show .animepost, .post-show article, .listupd article, .animepost, .utao, .bsx, .bs")
        
        for (el in elements) {
            try {
                var title = el.select(".title, h2, h3, .tt h4").text().trim()
                if (title.isEmpty()) {
                    title = el.select("a").attr("title").trim()
                }

                var link = el.select("a").first()?.attr("href") ?: ""
                link = cleanUrl(link)
                if (link.startsWith("/")) {
                    link = "$BASE_URL$link"
                }
                
                val imgEl = el.select("img").first()
                var imgUrl = ""
                if (imgEl != null) {
                    imgUrl = imgEl.attr("data-src")
                    if (imgUrl.isEmpty()) imgUrl = imgEl.attr("data-lazy-src")
                    if (imgUrl.isEmpty()) imgUrl = imgEl.attr("src")
                }
                imgUrl = cleanUrl(imgUrl)

                val status = el.select(".status, .type, .limit .type").text().trim()
                val score = el.select(".score, .rating, .limit .score").text().trim()
                val type = el.select(".type, .limit .type").text().trim()
                
                var episode = el.select(".epx, .limit .ep, .adds .ep").text().trim()
                if (episode.isEmpty()) {
                    episode = el.select(".limit span").text().trim()
                }

                if (title.isNotEmpty() && link.isNotEmpty()) {
                    list.add(
                        ScrapedAnime(
                            title = title,
                            link = link,
                            imageUrl = imgUrl,
                            status = status,
                            episode = episode.ifEmpty { "Episode ?" },
                            score = score,
                            type = type.ifEmpty { "Donghua" }
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing single anime item", e)
            }
        }
        return list.distinctBy { it.link }
    }

    suspend fun fetchLatestUpdates(page: Int = 1): List<ScrapedAnime> {
        val path = if (page > 1) "/page/$page/" else "/"
        val doc = getDocWithFallback(path) ?: return emptyList()
        return parseAnimeListFromDoc(doc)
    }

    suspend fun fetchDonghuaList(page: Int = 1): List<ScrapedAnime> {
        val path = if (page > 1) "/donghua-list/page/$page/" else "/donghua-list/"
        val doc = getDocWithFallback(path) ?: return emptyList()
        val list = parseAnimeListFromDoc(doc)
        if (list.isNotEmpty()) return list
        // Fallback if donghua-list page structure is different
        val altPath = if (page > 1) "/page/$page/" else "/"
        val altDoc = getDocWithFallback(altPath) ?: return emptyList()
        return parseAnimeListFromDoc(altDoc)
    }

    suspend fun searchAnime(query: String, page: Int = 1): List<ScrapedAnime> {
        val urlEncodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val path = if (page > 1) "/page/$page/?s=$urlEncodedQuery&post_type=post" else "/?s=$urlEncodedQuery&post_type=post"
        val doc = getDocWithFallback(path) ?: return emptyList()
        return parseAnimeListFromDoc(doc)
    }

    suspend fun fetchAnimeDetail(animeUrl: String): AnimeDetail? {
        return withContext(Dispatchers.IO) {
            try {
                var doc = getDocWithFallback(animeUrl) ?: return@withContext null
                
                val isEpisodeLink = animeUrl.contains("episode", ignoreCase = true) || 
                                     animeUrl.contains("-ep-", ignoreCase = true) || 
                                     animeUrl.contains("/ep-", ignoreCase = true) || 
                                     Regex("-\\d+/?$").containsMatchIn(animeUrl.trimEnd('/'))
                
                if (isEpisodeLink) {
                    var allEpsLink = ""
                    val navLinks = doc.select(".nvs a, .nvsc a, .nvs-nav a, .nvs.nvsc a, .all-episode a, a[href*=sub-indo]")
                    for (link in navLinks) {
                        val href = link.attr("href")
                        if (href.isNotEmpty() && 
                            !href.contains("episode") && 
                            !href.contains("-ep-") && 
                            href != animeUrl && 
                            href != BASE_URL && 
                            href != "$BASE_URL/") {
                            
                            val text = link.text()
                            if (text.contains("Semua", ignoreCase = true) || 
                                text.contains("All", ignoreCase = true) || 
                                text.contains("Series", ignoreCase = true) || 
                                text.contains("Detail", ignoreCase = true) || 
                                href.contains("/seri/") || 
                                href.contains("/anime/") || 
                                href.contains("-sub-indo") || 
                                href.contains("-subtitle-indonesia")) {
                                
                                allEpsLink = href
                                break
                            }
                        }
                    }
                    
                    if (allEpsLink.isEmpty()) {
                        val breadcrumbLinks = doc.select(".ts-breadcrumb a, .breadcrumbs a, .breadcrumb a, [itemtype*=BreadcrumbList] a")
                        for (i in breadcrumbLinks.indices.reversed()) {
                            val href = breadcrumbLinks[i].attr("href")
                            if (href.isNotEmpty() && 
                                href != BASE_URL && 
                                href != "$BASE_URL/" && 
                                !href.contains("/page/") && 
                                !href.contains("episode") && 
                                !href.contains("-ep-") && 
                                !href.contains("/category/") && 
                                !href.contains("/genre/")) {
                                allEpsLink = href
                                break
                            }
                        }
                    }
                    
                    if (allEpsLink.isEmpty()) {
                        val pageLinks = doc.select("a")
                        for (link in pageLinks) {
                            val href = link.attr("href")
                            if (href.isNotEmpty() && 
                                !href.contains("episode") && 
                                !href.contains("-ep-") && 
                                href != BASE_URL && 
                                href != "$BASE_URL/" && 
                                !href.contains("/page/") && 
                                !href.contains("/category/") && 
                                !href.contains("/genre/") && 
                                (href.contains("/seri/") || href.contains("/anime/") || href.contains("-sub-indo") || href.contains("-subtitle-indonesia"))) {
                                allEpsLink = href
                                break
                            }
                        }
                    }
                    
                    if (allEpsLink.isNotEmpty()) {
                        val allDoc = getDocWithFallback(allEpsLink)
                        if (allDoc != null) {
                            doc = allDoc
                        }
                    }
                }
                
                val title = doc.select(".info-anime h1, .entry-title").text().trim()
                
                var posterUrl = ""
                val posterEl = doc.select(".thumb img, .poster img").first()
                if (posterEl != null) {
                    posterUrl = posterEl.attr("data-src")
                    if (posterUrl.isEmpty()) posterUrl = posterEl.attr("data-lazy-src")
                    if (posterUrl.isEmpty()) posterUrl = posterEl.attr("src")
                }
                posterUrl = cleanUrl(posterUrl)

                val synopsis = doc.select(".desc, .sinopsis, .entry-content p, .entry-content").text().trim()

                var rating = "N/A"
                var status = "Unknown"
                var studio = "Unknown"
                var duration = "Unknown"
                val genres = mutableListOf<String>()

                val specs = doc.select(".info-content span, .spe span, .info-anime span")
                for (spec in specs) {
                    val text = spec.text()
                    when {
                        text.contains("Rating", ignoreCase = true) -> rating = text.replace("Rating", "").replace(":", "").trim()
                        text.contains("Status", ignoreCase = true) -> status = text.replace("Status", "").replace(":", "").trim()
                        text.contains("Studio", ignoreCase = true) -> studio = text.replace("Studio", "").replace(":", "").trim()
                        text.contains("Duration", ignoreCase = true) -> duration = text.replace("Duration", "").replace(":", "").trim()
                    }
                }

                doc.select(".genre-info a, .genres a, .genbody a").forEach {
                    genres.add(it.text().trim())
                }

                val episodesList = mutableListOf<EpisodeItem>()
                val epsElements = doc.select(".listeps ul li, .lstep ul li, .eplister ul li, .lstepsiode ul li, .episodelist ul li, .clor ul li, .clor li, .dx ul li, .dx li")
                for (epsEl in epsElements) {
                    val linkEl = epsEl.select(".eps a, .episodetitle a, a").first()
                    if (linkEl != null) {
                        val epsLink = linkEl.attr("href")
                        val epsTitle = linkEl.text().trim().ifEmpty { epsEl.select(".eps, .episodetitle").text().trim() }
                        val date = epsEl.select(".date").text().trim()
                        if (epsLink.isNotEmpty()) {
                            episodesList.add(EpisodeItem(title = epsTitle, link = epsLink, date = date))
                        }
                    }
                }

                if (title.isEmpty() && synopsis.isEmpty()) return@withContext null

                AnimeDetail(
                    title = title,
                    posterUrl = posterUrl,
                    synopsis = synopsis.ifEmpty { "No synopsis available." },
                    rating = rating,
                    status = status,
                    studio = studio,
                    duration = duration,
                    genres = genres,
                    episodes = episodesList
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun fetchEpisodeDetail(episodeUrl: String): EpisodeDetail? {
        return withContext(Dispatchers.IO) {
            try {
                val doc = getDocWithFallback(episodeUrl) ?: return@withContext null
                val title = doc.select(".entry-title, .episode-title").text().trim()

                val streamList = mutableListOf<StreamEmbed>()
                
                val mirrors = doc.select(".player-select option, .mirror option, select option")
                for (mirror in mirrors) {
                    val text = mirror.text().trim()
                    val value = mirror.attr("value")
                    if (value.isNotEmpty() && !value.contains("Select", ignoreCase = true)) {
                        // Check base64 encoded iframe string
                        try {
                            val decoded = String(android.util.Base64.decode(value, android.util.Base64.DEFAULT))
                            val parsedIframe = Jsoup.parse(decoded).select("iframe").attr("src")
                            if (parsedIframe.isNotEmpty()) {
                                val norm = normalizeStreamUrl(parsedIframe, BASE_URL)
                                if (norm.isNotEmpty()) {
                                    streamList.add(StreamEmbed(serverName = text.ifEmpty { "Server Mirror" }, iframeUrl = norm))
                                    continue
                                }
                            }
                        } catch (e: Exception) {}

                        val normalized = normalizeStreamUrl(value, BASE_URL)
                        if (normalized.isNotEmpty()) {
                            streamList.add(StreamEmbed(serverName = text.ifEmpty { "Server Mirror" }, iframeUrl = normalized))
                        }
                    }
                }

                if (streamList.isEmpty()) {
                    val iframes = doc.select("iframe")
                    iframes.forEachIndexed { idx, iframe ->
                        val src = iframe.attr("src")
                        if (src.isNotEmpty() && !src.contains("disqus", ignoreCase = true)) {
                            val normalized = normalizeStreamUrl(src, BASE_URL)
                            if (normalized.isNotEmpty()) {
                                streamList.add(StreamEmbed(serverName = "Server ${idx + 1}", iframeUrl = normalized))
                            }
                        }
                    }
                }

                doc.select(".server_option li, .player_option li, .east_player_option, [data-video], [data-post], [data-embed], [data-iframe], [data-src]").forEach { 
                    val rawSrc = it.attr("data-video")
                        .ifEmpty { it.attr("data-post") }
                        .ifEmpty { it.attr("data-embed") }
                        .ifEmpty { it.attr("data-iframe") }
                        .ifEmpty { it.attr("data-src") }
                    val name = it.text().trim().ifEmpty { it.select("span").text().trim() }
                    if (rawSrc.isNotEmpty()) {
                        val normalized = normalizeStreamUrl(rawSrc, BASE_URL)
                        if (normalized.isNotEmpty() && streamList.none { s -> s.iframeUrl == normalized }) {
                            streamList.add(StreamEmbed(serverName = name.ifEmpty { "Server Mirror" }, iframeUrl = normalized))
                        }
                    }
                }
                
                if (streamList.isEmpty()) {
                    streamList.add(StreamEmbed(serverName = "Default Player", iframeUrl = episodeUrl))
                }

                val downloadList = mutableListOf<DownloadGroup>()
                val downloadBlocks = doc.select(".download-eps, .download-link, .download-area, .dl-box, .mctnx, .soraddlx")
                
                if (downloadBlocks.isNotEmpty()) {
                    for (block in downloadBlocks) {
                        val resolutions = block.select("ul, li, div.dl, .soraurlx")
                        for (resEl in resolutions) {
                            try {
                                val links = mutableListOf<DownloadLink>()
                                resEl.select("a").forEach { linkEl ->
                                    val url = linkEl.attr("href")
                                    val label = linkEl.text().trim()
                                    if (url.isNotEmpty() && label.isNotEmpty()) {
                                        links.add(DownloadLink(host = label, url = url))
                                    }
                                }

                                if (links.isNotEmpty()) {
                                    val resolutionText = resEl.select("strong, span, b").first()?.text()?.trim() ?: "Direct Download"
                                    downloadList.add(DownloadGroup(resolution = resolutionText.ifEmpty { "Various" }, links = links))
                                }
                            } catch (e: Exception) {}
                        }
                    }
                }
                
                val distinctDLs = downloadList.distinctBy { it.resolution + it.links.joinToString { l -> l.url } }

                if (title.isEmpty() && streamList.isEmpty()) return@withContext null

                EpisodeDetail(
                    title = title,
                    streamEmbeds = streamList,
                    downloads = distinctDLs.toMutableList()
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun fetchSchedule(): List<ScheduleDay> {
        return withContext(Dispatchers.IO) {
            val doc = getDocWithFallback("/jadwal-rilis/") ?: return@withContext emptyList()
            val list = mutableListOf<ScheduleDay>()
            try {
                val boxes = doc.select("div.listSchh, .schedule-box, .schedulepage, .jadwal-box, .tab-pane")
                for (box in boxes) {
                    val dayName = box.select("h2, h3, .day-title, .schedule-day").text().trim()
                    if (dayName.isNotEmpty()) {
                        val animeLinks = mutableListOf<ScheduleAnime>()
                        box.select("div.subSchh a, .schedule-item a, article a").forEach { aEl ->
                            val rawLink = aEl.attr("href")
                            val normalizedLink = if (rawLink.startsWith("/")) {
                                "$BASE_URL$rawLink"
                            } else if (rawLink.startsWith("http")) {
                                rawLink
                            } else {
                                "$BASE_URL/$rawLink"
                            }
                            val cleanLink = cleanUrl(normalizedLink)
                            var rawTitle = aEl.text().trim()
                            
                            // Extract episode info if present in span or title
                            val epSpan = aEl.select("span, .epx, .ep, .eps").text().trim()
                            var epText = epSpan
                            if (epText.isEmpty()) {
                                val epMatch = Regex("(?i)(episode|eps|ep)\\s*(\\d+)").find(rawTitle)
                                if (epMatch != null) {
                                    epText = epMatch.value
                                }
                            }
                            
                            // Clean episode suffix from title if needed
                            val cleanTitle = rawTitle.replace(Regex("(?i)episode\\s*\\d+.*"), "").trim()
                            
                            // Image URL if present
                            val imgEl = aEl.select("img").first()
                            var imgUrl = ""
                            if (imgEl != null) {
                                imgUrl = imgEl.attr("data-src").ifEmpty { imgEl.attr("data-lazy-src") }.ifEmpty { imgEl.attr("src") }
                                imgUrl = cleanUrl(imgUrl)
                            }

                            if (rawTitle.isNotEmpty() && cleanLink.isNotEmpty()) {
                                animeLinks.add(
                                    ScheduleAnime(
                                        title = cleanTitle.ifEmpty { rawTitle },
                                        link = cleanLink,
                                        episode = epText.ifEmpty { "Episode ?" },
                                        releaseDay = dayName,
                                        imageUrl = imgUrl,
                                        source = "Anichin"
                                    )
                                )
                            }
                        }
                        if (animeLinks.isNotEmpty()) {
                            list.add(ScheduleDay(day = dayName, animeList = animeLinks))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing schedule", e)
            }
            list
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
