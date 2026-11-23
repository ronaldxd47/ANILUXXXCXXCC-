package com.example.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONTokener
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Shared scraping engine for WordPress Animestream / Eastplay anime & donghua sites
 * (Anichin, Donghub/Donghive, Samehadaku).
 *
 * Centralizes:
 * - Anti-poisoning domain failover (rejects Cloudflare challenge pages & empty/parked 200 shells)
 * - Optional DoH (DNS-over-HTTPS) resolution for ISP-blocked domains
 * - Optional headless WebView fallback when Cloudflare blocks direct HTTP
 * - Standardized HTML parsers for lists, series details, episode mirrors (Base64 + AJAX), and schedules
 */
abstract class BaseAnimestreamScraper(
    protected val tag: String,
    val sourceName: String,
    val domains: List<String>,
    private val domainKeywords: List<String>,
    private val useDoh: Boolean = false
) {
    @Volatile
    var baseUrl: String = domains.first()

    companion object {
        const val CHROME_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

        // Serialize headless scraper WebViews so concurrent startup fetches never thrash Chromium's renderer process
        private val scraperWebViewMutex = Mutex()

        private const val CONTENT_SELECTORS =
            ".listupd .bs, .listupd .bsx, .listupd article, .post-show .animepost, .post-show article, .post-show ul li, .animepost, .utao, .bsx, .bs, .info-anime, .infox, .eplister, .listeps, .episodelist, .mirror, .east_player_option, .widget.schedule, .schedulepage, .listSchh, .bixbox .bs"

        private val VALID_INDONESIAN_DAYS = setOf(
            "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu", "Acak"
        )

        fun normalizeDayName(raw: String): String? {
            val cleaned = raw.trim()
                .replace("'", "")
                .replace("’", "")
                .lowercase()
            return when {
                cleaned == "monday" || cleaned == "senin" -> "Senin"
                cleaned == "tuesday" || cleaned == "selasa" -> "Selasa"
                cleaned == "wednesday" || cleaned == "rabu" -> "Rabu"
                cleaned == "thursday" || cleaned == "kamis" -> "Kamis"
                cleaned == "friday" || cleaned == "jumat" || cleaned == "jumaat" -> "Jumat"
                cleaned == "saturday" || cleaned == "sabtu" -> "Sabtu"
                cleaned == "sunday" || cleaned == "minggu" -> "Minggu"
                cleaned == "acak" || cleaned == "random" -> "Acak"
                else -> null
            }
        }

        fun isCloudflareOrBlocked(title: String, html: String): Boolean {
            val lowerTitle = title.lowercase()
            val lowerHtml = html.take(4096).lowercase()
            return lowerTitle.contains("cloudflare") ||
                lowerTitle.contains("attention required") ||
                lowerTitle.contains("checking your browser") ||
                lowerTitle.contains("just a moment") ||
                lowerTitle.contains("ddos") ||
                lowerTitle.contains("access denied") ||
                lowerTitle.contains("page not found") ||
                lowerHtml.contains("cf-browser-verification") ||
                lowerHtml.contains("challenge-platform") ||
                (lowerHtml.contains("ray id") && lowerHtml.contains("cloudflare"))
        }

        fun cleanProtocolUrl(url: String): String {
            val trimmed = url.trim()
            return if (trimmed.startsWith("//")) "https:$trimmed" else trimmed
        }

        fun redactForLog(url: String): String {
            return url.replace(
                Regex("([?&](?:token|sig|signature|expires|auth|key|tkn)=)[^&]+", RegexOption.IGNORE_CASE)
            ) { "${it.groupValues[1]}REDACTED" }
        }
    }

    object DohDns : Dns {
        private const val DNS_TAG = "ScraperDohDns"
        private val dnsCache = ConcurrentHashMap<String, Pair<List<InetAddress>, Long>>()
        private const val DNS_CACHE_TTL_MS = 5 * 60 * 1000L

        override fun lookup(hostname: String): List<InetAddress> {
            if (hostname.matches(Regex("^[0-9.]+$")) || hostname.contains(":")) {
                try {
                    return listOf(InetAddress.getByName(hostname))
                } catch (_: Exception) {
                }
            }
            if (hostname == "cloudflare-dns.com" || hostname == "1.1.1.1" || hostname == "1.0.0.1") {
                return listOf(InetAddress.getByName("1.1.1.1"))
            }
            if (hostname == "dns.google" || hostname == "8.8.8.8" || hostname == "8.8.4.4") {
                return listOf(InetAddress.getByName("8.8.8.8"))
            }

            val cached = dnsCache[hostname]
            if (cached != null && (System.currentTimeMillis() - cached.second) < DNS_CACHE_TTL_MS) {
                return cached.first
            }

            // Try system DNS first when fast, then Google/Cloudflare DoH if blocked or empty
            val resolvers = listOf(
                "https://dns.google/resolve?name=$hostname&type=A" to "application/json",
                "https://cloudflare-dns.com/dns-query?name=$hostname&type=A" to "application/dns-json"
            )
            for ((endpoint, accept) in resolvers) {
                try {
                    val conn = java.net.URL(endpoint).openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("Accept", accept)
                    conn.connectTimeout = 2500
                    conn.readTimeout = 2500
                    if (conn.responseCode == 200) {
                        val response = conn.inputStream.bufferedReader().use { it.readText() }
                        val ips = Regex("\"data\"\\s*:\\s*\"([0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3})\"")
                            .findAll(response)
                            .map { it.groupValues[1] }
                            .toList()
                        if (ips.isNotEmpty()) {
                            val addrs = ips.map { InetAddress.getByName(it) }
                            dnsCache[hostname] = addrs to System.currentTimeMillis()
                            return addrs
                        }
                    }
                } catch (e: Exception) {
                    Log.d(DNS_TAG, "DoH lookup skipped for $hostname: ${e.message}")
                }
            }

            return Dns.SYSTEM.lookup(hostname)
        }
    }

    protected val httpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(7, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .cookieJar(object : CookieJar {
                private val cookiesMap = ConcurrentHashMap<String, List<Cookie>>()
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    val existing = cookiesMap[url.host]?.toMutableList() ?: mutableListOf()
                    existing.addAll(cookies)
                    cookiesMap[url.host] = existing.distinctBy { it.name }
                }

                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    return cookiesMap[url.host] ?: emptyList()
                }
            })
        if (useDoh) {
            builder.dns(DohDns)
        }
        builder.build()
    }

    protected fun getRelativePath(url: String): String {
        val clean = url.trim()
        if (clean.isEmpty()) return "/"
        for (domain in domains) {
            val base = domain.trimEnd('/')
            if (clean.startsWith(base, ignoreCase = true)) {
                val rest = clean.substring(base.length).trimStart('/')
                return if (rest.isEmpty()) "/" else "/$rest"
            }
        }
        if (clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true)) {
            return try {
                val uri = URI(clean)
                val path = (uri.rawPath ?: "/").replace(Regex("/+"), "/")
                val query = uri.rawQuery?.let { "?$it" } ?: ""
                (if (path.startsWith("/")) path else "/$path") + query
            } catch (_: Exception) {
                val stripped = clean.substringAfter("://")
                val path = stripped.substringAfter("/", "").trimStart('/')
                if (path.isEmpty()) "/" else "/$path"
            }
        }
        val normalized = clean.replace(Regex("/+"), "/").trimStart('/')
        return if (normalized.isEmpty()) "/" else "/$normalized"
    }

    protected fun normalizeInternalLink(url: String, activeBase: String = baseUrl): String {
        val clean = cleanProtocolUrl(url)
        if (clean.isEmpty()) return ""
        if (clean.startsWith("/")) {
            val cleanPath = clean.replace(Regex("^/+"), "/")
            return "${activeBase.trimEnd('/')}$cleanPath"
        }
        val isInternal = domains.any { clean.startsWith(it.trimEnd('/'), ignoreCase = true) } ||
            domainKeywords.any { clean.contains(it, ignoreCase = true) }

        if (isInternal && (clean.startsWith("http://") || clean.startsWith("https://"))) {
            return try {
                val uri = URI(clean)
                val path = (uri.rawPath ?: "/").replace(Regex("/+"), "/")
                val query = uri.rawQuery?.let { "?$it" } ?: ""
                "${activeBase.trimEnd('/')}$path$query"
            } catch (_: Exception) {
                clean.replace(Regex("^https?://[^/]+"), activeBase.trimEnd('/'))
            }
        }
        return clean
    }

    protected fun normalizeStreamUrl(url: String, activeBase: String = baseUrl): String {
        var clean = url.trim()
        if (clean.isEmpty()) return ""

        // 1. HTML snippet containing an iframe
        if (clean.startsWith("<") || clean.contains("<iframe", ignoreCase = true)) {
            try {
                val iframeEl = Jsoup.parse(clean).select("iframe").first()
                if (iframeEl != null) {
                    val src = iframeEl.attr("src").ifEmpty { iframeEl.attr("data-src") }.trim()
                    if (src.isNotEmpty()) clean = src
                }
            } catch (_: Exception) {
            }
        }

        // 2. Base64-encoded iframe HTML or direct URL
        if (!clean.startsWith("http", ignoreCase = true) &&
            !clean.startsWith("//") &&
            !clean.startsWith("/")
        ) {
            try {
                val decodedBytes = Base64.decode(clean, Base64.DEFAULT)
                val decodedStr = String(decodedBytes, Charsets.UTF_8).trim()
                if (decodedStr.startsWith("<") || decodedStr.contains("<iframe", ignoreCase = true)) {
                    val iframeEl = Jsoup.parse(decodedStr).select("iframe").first()
                    val src = iframeEl?.attr("src")?.ifEmpty { iframeEl.attr("data-src") }?.trim() ?: ""
                    if (src.isNotEmpty()) {
                        clean = src
                    }
                } else if (decodedStr.startsWith("http", ignoreCase = true) ||
                    decodedStr.startsWith("//") ||
                    decodedStr.startsWith("/")
                ) {
                    clean = decodedStr
                }
            } catch (_: Exception) {
            }
        }

        // Reject raw numeric IDs or non-URL strings (e.g. data-post="12345")
        if (clean.matches(Regex("^\\d+$"))) return ""

        if (clean.startsWith("//")) return "https:$clean"
        if (clean.startsWith("/")) return "${activeBase.trimEnd('/')}${clean.replace(Regex("^/+"), "/")}"
        if (!clean.startsWith("http://", ignoreCase = true) && !clean.startsWith("https://", ignoreCase = true)) {
            return if (clean.contains(".")) "https://$clean" else ""
        }
        return clean
    }

    /**
     * Checks if a parsed document represents a real, non-blocked, non-empty target page.
     * Prevents parked domains or empty HTTP 200 shells from poisoning [baseUrl].
     */
    protected open fun isValidTargetDocument(doc: Document, path: String): Boolean {
        val html = doc.outerHtml()
        if (isCloudflareOrBlocked(doc.title(), html)) return false
        val hasContent = doc.select(CONTENT_SELECTORS).isNotEmpty()
        if (hasContent) return true
        // Search pages with 0 results are valid HTTP 200 pages if they contain the search layout/form
        if (path.contains("?s=") && doc.select("form[role=search], .search-no-results, .notfound, #content").isNotEmpty()) {
            return true
        }
        return false
    }

    private fun fetchDocumentDirectOkHttp(fullUrl: String): Pair<String, Document>? {
        val request = Request.Builder()
            .url(fullUrl)
            .header("User-Agent", CHROME_USER_AGENT)
            .header("Referer", "https://www.google.com/")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .header("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val finalUrl = response.request.url.toString()
            val finalOrigin = "${response.request.url.scheme}://${response.request.url.host}"
            val body = response.body?.string() ?: return null
            if (body.length < 512) return null
            val doc = Jsoup.parse(body, finalUrl)
            return finalOrigin to doc
        }
    }

    suspend fun getDocWithFallback(
        urlPath: String,
        contextForWebView: Context? = null,
        allowWebViewFallback: Boolean = true
    ): Document? = withContext(Dispatchers.IO) {
        val path = getRelativePath(urlPath)
        val currentBaseNormalized = baseUrl.trimEnd('/')

        // 1. Fast path on current baseUrl
        try {
            currentCoroutineContext().ensureActive()
            val fullUrl = "$currentBaseNormalized$path"
            val res = fetchDocumentDirectOkHttp(fullUrl)
            if (res != null && isValidTargetDocument(res.second, path)) {
                baseUrl = res.first
                return@withContext res.second
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(tag, "Fast path failed on $currentBaseNormalized$path: ${e.message}")
        }

        // 2. Parallel race across remaining configured domains
        try {
            currentCoroutineContext().ensureActive()
            val otherDomains = domains.map { it.trimEnd('/') }
                .distinct()
                .filterNot { it.equals(currentBaseNormalized, ignoreCase = true) }
            val candidates = if (otherDomains.isNotEmpty()) otherDomains else domains.map { it.trimEnd('/') }.distinct()

            val raceWinner = coroutineScope {
                val channel = Channel<Pair<String, Document>?>(candidates.size)
                val jobs = candidates.map { domain ->
                    launch(Dispatchers.IO) {
                        try {
                            val fullUrl = "$domain$path"
                            val res = fetchDocumentDirectOkHttp(fullUrl)
                            if (res != null && isValidTargetDocument(res.second, path)) {
                                channel.send(res)
                            } else {
                                channel.send(null)
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            channel.send(null)
                        }
                    }
                }

                var winner: Pair<String, Document>? = null
                var received = 0
                while (received < candidates.size) {
                    val item = channel.receive()
                    if (item != null) {
                        winner = item
                        break
                    }
                    received++
                }
                jobs.forEach { it.cancel() }
                winner
            }

            if (raceWinner != null) {
                baseUrl = raceWinner.first
                Log.d(tag, "Parallel fetch succeeded on $baseUrl for $path")
                return@withContext raceWinner.second
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(tag, "Parallel fetch failed for $path: ${e.message}")
        }

        // 3. Optional WebView fallback for Cloudflare-protected mirrors (skipped for background schedule fetches)
        if (allowWebViewFallback && contextForWebView != null && currentCoroutineContext().isActive) {
            for (domain in domains) {
                if (!currentCoroutineContext().isActive) break
                val base = domain.trimEnd('/')
                val fullUrl = "$base$path"
                try {
                    val html = scrapeWithWebView(contextForWebView, fullUrl)
                    if (!html.isNullOrEmpty()) {
                        val doc = Jsoup.parse(html, fullUrl)
                        if (isValidTargetDocument(doc, path)) {
                            baseUrl = base
                            return@withContext doc
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(tag, "WebView fallback failed for $domain: ${e.message}")
                }
            }
        }

        null
    }

    protected suspend fun scrapeWithWebView(context: Context, url: String): String? {
        return scraperWebViewMutex.withLock {
            currentCoroutineContext().ensureActive()
            withTimeoutOrNull(12_000L) {
                withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine<String?> { continuation ->
                        val hasFinished = AtomicBoolean(false)
                        val mainHandler = Handler(Looper.getMainLooper())
                        var webView: WebView? = null

                        fun cleanupWebView(destroyNow: Boolean = false) {
                            if (!hasFinished.compareAndSet(false, true)) return
                            mainHandler.removeCallbacksAndMessages(null)
                            val wv = webView
                            webView = null
                            if (wv != null) {
                                try {
                                    wv.stopLoading()
                                    wv.webChromeClient = null
                                    wv.webViewClient = object : WebViewClient() {}
                                    (wv.parent as? ViewGroup)?.removeView(wv)
                                    wv.removeAllViews()
                                    wv.onPause()
                                    if (destroyNow) {
                                        wv.destroy()
                                    } else {
                                        wv.loadUrl("about:blank")
                                        mainHandler.postDelayed({
                                            runCatching { wv.destroy() }
                                        }, 150)
                                    }
                                } catch (_: Exception) {
                                }
                            }
                        }

                        fun finishWith(result: String?, destroyNow: Boolean = false) {
                            cleanupWebView(destroyNow = destroyNow)
                            if (continuation.isActive) {
                                continuation.resume(result)
                            }
                        }

                        continuation.invokeOnCancellation {
                            if (Looper.myLooper() == Looper.getMainLooper()) {
                                cleanupWebView(destroyNow = false)
                            } else {
                                mainHandler.post { cleanupWebView(destroyNow = false) }
                            }
                        }

                        try {
                            val created = WebView(context.applicationContext)
                            webView = created
                            created.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                            created.settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                blockNetworkImage = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                userAgentString = CHROME_USER_AGENT
                            }

                            try {
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(created, true)
                            } catch (_: Exception) {
                            }

                            created.webViewClient = object : WebViewClient() {
                                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                                    Log.w(tag, "WebView render process gone during fallback (didCrash=${detail?.didCrash()})")
                                    finishWith(null, destroyNow = true)
                                    return true
                                }

                                override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                                    super.onPageFinished(view, loadedUrl)
                                    if (hasFinished.get()) return

                                    val startTime = System.currentTimeMillis()
                                    val checkRunnable = object : Runnable {
                                        override fun run() {
                                            val activeView = webView
                                            if (hasFinished.get() || activeView == null) return
                                            val jsCode = """
                                                (function() {
                                                    try {
                                                        var title = document.title ? document.title.toLowerCase() : "";
                                                        var html = document.documentElement ? document.documentElement.outerHTML : "";
                                                        var isCf = title.indexOf('cloudflare') > -1 ||
                                                                   title.indexOf('just a moment') > -1 ||
                                                                   title.indexOf('checking your browser') > -1 ||
                                                                   html.indexOf('cf-browser-verification') > -1;
                                                        if (isCf) return "CLOUDFLARE";
                                                        if (html.length > 500) return "READY_HTML";
                                                        return "WAIT";
                                                    } catch(e) { return "WAIT"; }
                                                })();
                                            """.trimIndent()

                                            activeView.evaluateJavascript(jsCode) { statusResult ->
                                                if (hasFinished.get()) return@evaluateJavascript
                                                val status = statusResult?.replace("\"", "") ?: "WAIT"
                                                val elapsed = System.currentTimeMillis() - startTime
                                                if (status == "READY_HTML") {
                                                    webView?.evaluateJavascript("document.documentElement.outerHTML") { rawHtml ->
                                                        if (hasFinished.get()) return@evaluateJavascript
                                                        val cleanHtml = if (rawHtml != null && rawHtml.length >= 2 && rawHtml.startsWith("\"") && rawHtml.endsWith("\"")) {
                                                            try {
                                                                JSONTokener(rawHtml).nextValue() as? String ?: ""
                                                            } catch (_: Exception) {
                                                                rawHtml.removePrefix("\"").removeSuffix("\"")
                                                                    .replace("\\u003C", "<").replace("\\u003E", ">")
                                                                    .replace("\\\"", "\"").replace("\\\\", "\\")
                                                            }
                                                        } else {
                                                            rawHtml ?: ""
                                                        }
                                                        finishWith(cleanHtml)
                                                    }
                                                } else if (elapsed > 10_000L) {
                                                    finishWith(null)
                                                } else if (!hasFinished.get()) {
                                                    mainHandler.postDelayed(this, 800)
                                                }
                                            }
                                        }
                                    }
                                    mainHandler.postDelayed(checkRunnable, 800)
                                }

                                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                    super.onReceivedError(view, request, error)
                                    Log.w(tag, "WebView error: ${error?.description}")
                                }
                            }

                            created.loadUrl(url)
                            mainHandler.postDelayed({
                                if (!hasFinished.get()) {
                                    finishWith(null)
                                }
                            }, 11_000L)
                        } catch (e: Exception) {
                            Log.w(tag, "Failed to initialize WebView fallback: ${e.message}")
                            finishWith(null, destroyNow = true)
                        }
                    }
                }
            }
        }
    }

    /**
     * Parses anime/donghua card grids across Animestream (.bs/.bsx/.listupd) and Eastplay (.post-show/.animepost).
     */
    fun parseAnimeListFromDoc(doc: Document, defaultType: String): List<ScrapedAnime> {
        val list = mutableListOf<ScrapedAnime>()
        val elements = doc.select(
            ".listupd .bs, .listupd .animepost, .listupd article, .post-show ul li, .post-show .animepost, .post-show article, .animepost, .utao, .bsx, .bs"
        )

        for (el in elements) {
            try {
                // Title extraction: avoid concatenating nested type/ep badges when .tt exists
                val ttEl = el.selectFirst(".tt")
                var title = if (ttEl != null) {
                    val headline = ttEl.selectFirst("h2, h3, h4")?.text()?.trim() ?: ""
                    headline.ifEmpty { ttEl.ownText().trim() }.ifEmpty { ttEl.text().trim() }
                } else {
                    el.selectFirst(".title, .entry-title, h2, h3, h4")?.text()?.trim() ?: ""
                }
                if (title.isEmpty()) {
                    title = el.selectFirst("a")?.attr("title")?.trim() ?: ""
                }

                val rawHref = el.selectFirst("a")?.attr("href") ?: ""
                val link = normalizeInternalLink(rawHref)

                val imgEl = el.selectFirst("img")
                var imgUrl = ""
                if (imgEl != null) {
                    imgUrl = imgEl.attr("data-src")
                        .ifEmpty { imgEl.attr("data-lazy-src") }
                        .ifEmpty { imgEl.attr("src") }
                }
                imgUrl = cleanProtocolUrl(imgUrl)

                val status = el.select(".status, .typez, .limit .type").firstOrNull()?.text()?.trim() ?: ""
                val score = el.select(".score, .rating, .limit .score").firstOrNull()?.text()?.trim() ?: ""
                val type = el.select(".typez, .type, .limit .type").firstOrNull()?.text()?.trim() ?: ""

                var episode = el.select(".epx, .limit .ep, .adds .ep, .dtla span:contains(Episode)").firstOrNull()?.text()?.trim() ?: ""
                if (episode.isEmpty()) {
                    episode = el.select(".limit span").firstOrNull()?.text()?.trim() ?: ""
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
                            type = type.ifEmpty { defaultType }
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(tag, "Error parsing single anime item", e)
            }
        }
        return list.distinctBy { it.link }
    }

    /**
     * Resolves an episode URL back to its parent series URL when opened from a "Latest Updates" episode card.
     */
    protected suspend fun resolveSeriesDocumentIfEpisode(
        initialDoc: Document,
        inputUrl: String,
        contextForWebView: Context? = null
    ): Document {
        val isEpisodeLink = inputUrl.contains("episode", ignoreCase = true) ||
            inputUrl.contains("-ep-", ignoreCase = true) ||
            inputUrl.contains("/ep-", ignoreCase = true) ||
            Regex("-\\d+/?$").containsMatchIn(inputUrl.trimEnd('/'))

        if (!isEpisodeLink) return initialDoc

        var allEpsLink = ""
        val navLinks = initialDoc.select(".nvs a, .nvsc a, .nvs-nav a, .nvs.nvsc a, .all-episode a")
        for (link in navLinks) {
            val href = link.attr("href")
            if (href.isNotEmpty() &&
                !href.contains("episode", ignoreCase = true) &&
                !href.contains("-ep-", ignoreCase = true) &&
                href != inputUrl &&
                href.trimEnd('/') != baseUrl.trimEnd('/')
            ) {
                val text = link.text()
                if (text.contains("Semua", ignoreCase = true) ||
                    text.contains("All", ignoreCase = true) ||
                    text.contains("Series", ignoreCase = true) ||
                    text.contains("Detail", ignoreCase = true) ||
                    href.contains("/seri/", ignoreCase = true) ||
                    href.contains("/anime/", ignoreCase = true)
                ) {
                    allEpsLink = href
                    break
                }
            }
        }

        if (allEpsLink.isEmpty()) {
            val breadcrumbLinks = initialDoc.select(".ts-breadcrumb a, .breadcrumbs a, .breadcrumb a, [itemtype*=BreadcrumbList] a")
            for (i in breadcrumbLinks.indices.reversed()) {
                val href = breadcrumbLinks[i].attr("href")
                if (href.isNotEmpty() &&
                    href.trimEnd('/') != baseUrl.trimEnd('/') &&
                    !href.contains("/page/") &&
                    !href.contains("episode", ignoreCase = true) &&
                    !href.contains("-ep-", ignoreCase = true) &&
                    !href.contains("/category/") &&
                    !href.contains("/genre/")
                ) {
                    allEpsLink = href
                    break
                }
            }
        }

        if (allEpsLink.isEmpty()) {
            val pageLinks = initialDoc.select("a")
            for (link in pageLinks) {
                val href = link.attr("href")
                if (href.isNotEmpty() &&
                    !href.contains("episode", ignoreCase = true) &&
                    !href.contains("-ep-", ignoreCase = true) &&
                    href.trimEnd('/') != baseUrl.trimEnd('/') &&
                    !href.contains("/page/") &&
                    !href.contains("/category/") &&
                    !href.contains("/genre/") &&
                    (href.contains("/seri/") || href.contains("/anime/"))
                ) {
                    allEpsLink = href
                    break
                }
            }
        }

        if (allEpsLink.isNotEmpty()) {
            val allDoc = getDocWithFallback(allEpsLink, contextForWebView)
            if (allDoc != null) return allDoc
        }
        return initialDoc
    }

    fun parseAnimeDetailFromDoc(doc: Document, cleanSeoBoilerplate: Boolean = false): AnimeDetail? {
        val title = doc.select(".info-anime h1, .entry-title").firstOrNull()?.text()?.trim() ?: ""

        var posterUrl = ""
        val posterEl = doc.select(".thumb img, .poster img").firstOrNull()
        if (posterEl != null) {
            posterUrl = posterEl.attr("data-src")
                .ifEmpty { posterEl.attr("data-lazy-src") }
                .ifEmpty { posterEl.attr("src") }
        }
        posterUrl = cleanProtocolUrl(posterUrl)

        var synopsis = doc.select(".desc, .sinopsis, .entry-content p, .entry-content").text().trim()
        if (cleanSeoBoilerplate) {
            val seoPattern = Regex(
                "Watch streaming.*?(video\\.|quota,).*?(\\.|\\n|\\r)|You can also download.*?(\\.|\\n|\\r)|Nonton anime.*?(\\.|\\n|\\r)|Jangan lupa menonton.*?(\\.|\\n|\\r)|Download gratis.*?(\\.|\\n|\\r)|Streaming anime.*?(\\.|\\n|\\r)|.*berbagai macam resolusi.*?(video\\.|\\.)",
                RegexOption.IGNORE_CASE
            )
            synopsis = synopsis.replace(seoPattern, "").trim()
        }

        var rating = "N/A"
        var status = "Unknown"
        var studio = "Unknown"
        var released = "Unknown"
        var duration = "Unknown"
        val genres = mutableListOf<String>()

        val specs = doc.select(".info-content span, .spe span, .info-anime span")
        for (spec in specs) {
            val text = spec.text()
            when {
                text.contains("Rating", ignoreCase = true) -> rating = text.replace("Rating", "", ignoreCase = true).replace(":", "").trim()
                text.contains("Status", ignoreCase = true) -> status = text.replace("Status", "", ignoreCase = true).replace(":", "").trim()
                text.contains("Studio", ignoreCase = true) -> studio = text.replace("Studio", "", ignoreCase = true).replace(":", "").trim()
                text.contains("Released", ignoreCase = true) || text.contains("Rilis", ignoreCase = true) ->
                    released = text.replace("Released", "", ignoreCase = true).replace("Rilis", "", ignoreCase = true).replace(":", "").trim()
                text.contains("Duration", ignoreCase = true) -> duration = text.replace("Duration", "", ignoreCase = true).replace(":", "").trim()
            }
        }

        doc.select(".genre-info a, .genres a, .genbody a, .genxed a").forEach {
            val g = it.text().trim()
            if (g.isNotEmpty() && !genres.contains(g)) genres.add(g)
        }

        val episodesList = mutableListOf<EpisodeItem>()
        val epsElements = doc.select(".eplister ul li, .listeps ul li, .lstep ul li, .lstepsiode ul li, .episodelist ul li")
        for (epsEl in epsElements) {
            val linkEl = epsEl.select(".eps a, .episodetitle a, a").firstOrNull()
            if (linkEl != null) {
                val epsLink = normalizeInternalLink(linkEl.attr("href"))
                val epsTitle = linkEl.text().trim()
                    .ifEmpty { epsEl.select(".epl-title, .eps, .episodetitle").text().trim() }
                    .ifEmpty { epsEl.select(".epl-num").text().trim().let { if (it.isNotEmpty()) "Episode $it" else "" } }
                val date = epsEl.select(".epl-date, .date").text().trim()
                if (epsLink.isNotEmpty()) {
                    episodesList.add(EpisodeItem(title = epsTitle.ifEmpty { "Episode" }, link = epsLink, date = date))
                }
            }
        }

        if (title.isEmpty() && synopsis.isEmpty()) return null

        return AnimeDetail(
            title = title,
            posterUrl = posterUrl,
            synopsis = synopsis.ifEmpty { "No synopsis available." },
            rating = rating.ifEmpty { "N/A" },
            status = status.ifEmpty { "Unknown" },
            studio = studio.ifEmpty { "Unknown" },
            released = released.ifEmpty { "Unknown" },
            duration = duration.ifEmpty { "Unknown" },
            genres = genres,
            episodes = episodesList
        )
    }

    /**
     * Parses episode streaming embeds (Base64 <option>, Eastplay admin-ajax.php, data-* attributes, iframes)
     * and download mirror links.
     */
    suspend fun parseEpisodeDetailFromDoc(doc: Document, episodeUrl: String): EpisodeDetail? {
        val title = doc.select(".entry-title, .episode-title").firstOrNull()?.text()?.trim() ?: ""
        val streamList = mutableListOf<StreamEmbed>()

        // 1. Animestream <select class="mirror"> / .player-select option (often Base64-encoded <iframe>)
        val mirrors = doc.select("select.mirror option, .player-select option, .mirror option")
        for (mirror in mirrors) {
            val text = mirror.text().trim()
            val value = mirror.attr("value").trim()
            if (value.isNotEmpty() &&
                !value.contains("Select", ignoreCase = true) &&
                !text.contains("Select Video Server", ignoreCase = true)
            ) {
                val normalized = normalizeStreamUrl(value)
                if (normalized.isNotEmpty() && streamList.none { it.iframeUrl == normalized }) {
                    streamList.add(StreamEmbed(serverName = text.ifEmpty { "Server Mirror" }, iframeUrl = normalized))
                }
            }
        }

        // 2. Eastplay / Samehadaku dynamic AJAX player options (.east_player_option with data-post/nume/type)
        val eastOptions = doc.select(".east_player_option[data-post], .server_option li[data-post], .player_option li[data-post]")
        if (eastOptions.isNotEmpty()) {
            val activeAjaxBase = baseUrl.trimEnd('/')
            coroutineScope {
                val jobs = eastOptions.map { el ->
                    launch(Dispatchers.IO) {
                        val postId = el.attr("data-post").trim()
                        val nume = el.attr("data-nume").trim()
                        val type = el.attr("data-type").trim()
                        val name = el.text().trim().ifEmpty { el.select("span").text().trim() }.ifEmpty { "Server $nume" }
                        if (postId.isNotEmpty() && nume.isNotEmpty() && type.isNotEmpty()) {
                            try {
                                val formBody = FormBody.Builder()
                                    .add("action", "player_ajax")
                                    .add("post", postId)
                                    .add("nume", nume)
                                    .add("type", type)
                                    .build()
                                val req = Request.Builder()
                                    .url("$activeAjaxBase/wp-admin/admin-ajax.php")
                                    .post(formBody)
                                    .header("User-Agent", CHROME_USER_AGENT)
                                    .header("Referer", episodeUrl)
                                    .header("X-Requested-With", "XMLHttpRequest")
                                    .build()
                                httpClient.newCall(req).execute().use { resp ->
                                    if (resp.isSuccessful) {
                                        val body = resp.body?.string() ?: ""
                                        val norm = normalizeStreamUrl(body)
                                        if (norm.isNotEmpty()) {
                                            synchronized(streamList) {
                                                if (streamList.none { it.iframeUrl == norm }) {
                                                    streamList.add(StreamEmbed(serverName = name, iframeUrl = norm))
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.d(tag, "AJAX player resolution failed for $name: ${e.message}")
                            }
                        }
                    }
                }
                jobs.forEach { it.join() }
            }
        }

        // 3. Direct data-* embed attributes (non-numeric)
        doc.select("[data-video], [data-embed], [data-iframe], [data-src]").forEach { el ->
            val rawSrc = el.attr("data-video")
                .ifEmpty { el.attr("data-embed") }
                .ifEmpty { el.attr("data-iframe") }
                .ifEmpty { el.attr("data-src") }
                .trim()
            if (rawSrc.isNotEmpty() && !rawSrc.matches(Regex("^\\d+$"))) {
                val normalized = normalizeStreamUrl(rawSrc)
                val name = el.text().trim().ifEmpty { el.attr("title").trim() }.ifEmpty { "Server Mirror" }
                if (normalized.isNotEmpty() && !normalized.contains("disqus", ignoreCase = true) &&
                    streamList.none { it.iframeUrl == normalized }
                ) {
                    streamList.add(StreamEmbed(serverName = name, iframeUrl = normalized))
                }
            }
        }

        // 4. Inline iframes fallback
        if (streamList.isEmpty()) {
            doc.select("iframe").forEachIndexed { idx, iframe ->
                val rawSrc = iframe.attr("src").ifEmpty { iframe.attr("data-src") }.trim()
                if (rawSrc.isNotEmpty() &&
                    !rawSrc.contains("disqus", ignoreCase = true) &&
                    !rawSrc.contains("facebook", ignoreCase = true) &&
                    !rawSrc.contains("doubleclick", ignoreCase = true)
                ) {
                    val normalized = normalizeStreamUrl(rawSrc)
                    if (normalized.isNotEmpty() && streamList.none { it.iframeUrl == normalized }) {
                        streamList.add(StreamEmbed(serverName = "Server ${idx + 1}", iframeUrl = normalized))
                    }
                }
            }
        }

        if (streamList.isEmpty()) {
            streamList.add(StreamEmbed(serverName = "Default Player", iframeUrl = episodeUrl))
        }

        // 5. Download links
        val downloadList = mutableListOf<DownloadGroup>()
        val downloadBlocks = doc.select(".download-eps, .download-link, .download-area, .dl-box, .mctnx, .soraddlx")
        for (block in downloadBlocks) {
            val resolutions = block.select("ul li, div.dl, .soraurlx")
            for (resEl in resolutions) {
                try {
                    val links = mutableListOf<DownloadLink>()
                    resEl.select("a").forEach { linkEl ->
                        val url = cleanProtocolUrl(linkEl.attr("href"))
                        val label = linkEl.text().trim()
                        if (url.isNotEmpty() && url != "#" && label.isNotEmpty()) {
                            links.add(DownloadLink(host = label, url = url))
                        }
                    }
                    if (links.isNotEmpty()) {
                        val resolutionText = resEl.select("strong, span, b").firstOrNull()?.text()?.trim() ?: "Direct Download"
                        downloadList.add(DownloadGroup(resolution = resolutionText.ifEmpty { "Various" }, links = links))
                    }
                } catch (_: Exception) {
                }
            }
        }
        val distinctDLs = downloadList.distinctBy { it.resolution + it.links.joinToString { l -> l.url } }

        if (title.isEmpty() && streamList.isEmpty()) return null

        return EpisodeDetail(
            title = title,
            streamEmbeds = streamList,
            downloads = distinctDLs.toMutableList()
        )
    }

    /**
     * Parses Animestream / Eastplay schedule boxes cleanly, rejecting unrelated sidebar/widget tabs.
     */
    fun parseScheduleFromDoc(doc: Document): List<ScheduleDay> {
        val list = mutableListOf<ScheduleDay>()

        // 1. Animestream .bixbox schedule sections (used by Donghive/Donghub & modern Animestream themes)
        val bixBoxes = doc.select(".bixbox")
        for (box in bixBoxes) {
            val headerRaw = box.select(".releases h2, .releases h3, h2, h3").firstOrNull()?.text()?.trim() ?: ""
            val dayName = normalizeDayName(headerRaw) ?: continue
            val animeLinks = extractScheduleItemsFromContainer(box, dayName)
            if (animeLinks.isNotEmpty()) {
                list.add(ScheduleDay(day = dayName, animeList = animeLinks))
            }
        }
        if (list.isNotEmpty()) return list

        // 2. Classic Anichin / Eastplay schedule boxes (strictly validated against known day names!)
        val boxes = doc.select("div.listSchh, .schedule-box, .schedulepage .tab-pane, .jadwal-box")
        for (box in boxes) {
            val headerRaw = box.select("h2, h3, .day-title, .schedule-day").firstOrNull()?.text()?.trim()
                ?: box.attr("data-day").trim()
                .ifEmpty { box.attr("id").replace("tab-", "").replace("day-", "") }
            val dayName = normalizeDayName(headerRaw) ?: continue
            if (dayName !in VALID_INDONESIAN_DAYS) continue

            val animeLinks = extractScheduleItemsFromContainer(box, dayName)
            if (animeLinks.isNotEmpty()) {
                list.add(ScheduleDay(day = dayName, animeList = animeLinks))
            }
        }
        return list
    }

    private fun extractScheduleItemsFromContainer(container: org.jsoup.nodes.Element, dayName: String): List<ScheduleAnime> {
        val items = mutableListOf<ScheduleAnime>()
        val cards = container.select("div.subSchh, .schedule-item, .bs, article")
        if (cards.isNotEmpty()) {
            for (el in cards) {
                val aEl = if (el.tagName() == "a") el else el.selectFirst("a") ?: continue
                val cleanLink = normalizeInternalLink(aEl.attr("href"))
                if (cleanLink.isEmpty()) continue

                val ttEl = el.selectFirst(".tt")
                var rawTitle = if (ttEl != null) {
                    (ttEl.selectFirst("h2, h3, h4")?.text()?.trim() ?: "").ifEmpty { ttEl.ownText().trim() }.ifEmpty { ttEl.text().trim() }
                } else {
                    el.selectFirst(".title, h2, h3, .entry-title")?.text()?.trim()
                        ?: aEl.attr("title").trim()
                        .ifEmpty { aEl.text().trim() }
                }
                val epSpan = el.select(".epx, .ep, .eps, .bt span").firstOrNull()?.text()?.trim() ?: ""
                var epText = epSpan
                if (epText.isEmpty()) {
                    val epMatch = Regex("(?i)(episode|eps|ep)\\s*(\\d+)").find(rawTitle)
                    if (epMatch != null) epText = epMatch.value
                }
                val cleanTitle = rawTitle.replace(Regex("(?i)\\s*episode\\s*\\d+.*"), "").trim()
                val imgEl = el.selectFirst("img")
                val imgUrl = imgEl?.let {
                    cleanProtocolUrl(
                        it.attr("data-src").ifEmpty { it.attr("data-lazy-src") }.ifEmpty { it.attr("src") }
                    )
                } ?: ""

                if (cleanTitle.isNotEmpty() || rawTitle.isNotEmpty()) {
                    items.add(
                        ScheduleAnime(
                            title = cleanTitle.ifEmpty { rawTitle },
                            link = cleanLink,
                            episode = epText.ifEmpty { "Episode ?" },
                            releaseDay = dayName,
                            imageUrl = imgUrl,
                            source = sourceName
                        )
                    )
                }
            }
        } else {
            container.select("a").forEach { aEl ->
                val cleanLink = normalizeInternalLink(aEl.attr("href"))
                val rawTitle = aEl.text().trim()
                if (rawTitle.isNotEmpty() && cleanLink.isNotEmpty()) {
                    items.add(
                        ScheduleAnime(
                            title = rawTitle,
                            link = cleanLink,
                            episode = "Episode ?",
                            releaseDay = dayName,
                            source = sourceName
                        )
                    )
                }
            }
        }
        return items.distinctBy { it.link }
    }
}
