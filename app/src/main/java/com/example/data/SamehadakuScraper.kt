package com.example.data

import android.util.Log
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.Base64
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate
import java.security.SecureRandom
import javax.net.ssl.HttpsURLConnection
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

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

object SamehadakuScraper {
    private const val TAG = "SamehadakuScraper"
    
    var appContext: Context? = null
    
    // Domain list
    val DOMAINS = listOf(
        "https://samehadaku.li",
        "https://samehadaku.email",
        "https://v2.samehadaku.how",
        "https://samehadaku.ac"
    )
    var BASE_URL = DOMAINS[0]

    val unsafeX509TrustManager = object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
    }

    val unsafeSslSocketFactory: javax.net.ssl.SSLSocketFactory = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf<TrustManager>(unsafeX509TrustManager), SecureRandom())
    }.socketFactory

    object DohDns : Dns {
        private const val DNS_TAG = "DohDns"

        override fun lookup(hostname: String): List<java.net.InetAddress> {
            if (hostname.matches(Regex("^[0-9.]+$")) || hostname.contains(":")) {
                try {
                    return listOf(java.net.InetAddress.getByName(hostname))
                } catch (e: Exception) {
                    // ignore
                }
            }
            
            if (hostname == "cloudflare-dns.com" || hostname == "1.1.1.1" || hostname == "1.0.0.1") {
                return listOf(java.net.InetAddress.getByName("1.1.1.1"))
            }
            if (hostname == "dns.google" || hostname == "8.8.8.8" || hostname == "8.8.4.4") {
                return listOf(java.net.InetAddress.getByName("8.8.8.8"))
            }

            // Google DoH via raw IP
            try {
                val url = java.net.URL("https://8.8.8.8/resolve?name=$hostname&type=A")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/json")
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                
                if (conn is javax.net.ssl.HttpsURLConnection) {
                    conn.sslSocketFactory = unsafeSslSocketFactory
                    conn.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
                }

                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val ips = parseJsonIps(response)
                    if (ips.isNotEmpty()) {
                        Log.d(DNS_TAG, "Resolved $hostname via Google DoH IP to: $ips")
                        return ips.map { java.net.InetAddress.getByName(it) }
                    }
                }
            } catch (e: Exception) {
                Log.e(DNS_TAG, "Google DoH IP lookup failed for $hostname: ${e.message}")
            }

            // Cloudflare DoH via raw IP
            try {
                val url = java.net.URL("https://1.1.1.1/dns-query?name=$hostname&type=A")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/dns-json")
                conn.connectTimeout = 3000
                conn.readTimeout = 3000

                if (conn is javax.net.ssl.HttpsURLConnection) {
                    conn.sslSocketFactory = unsafeSslSocketFactory
                    conn.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
                }

                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val ips = parseJsonIps(response)
                    if (ips.isNotEmpty()) {
                        Log.d(DNS_TAG, "Resolved $hostname via Cloudflare DoH IP to: $ips")
                        return ips.map { java.net.InetAddress.getByName(it) }
                    }
                }
            } catch (e: Exception) {
                Log.e(DNS_TAG, "Cloudflare DoH IP lookup failed for $hostname: ${e.message}")
            }

            Log.d(DNS_TAG, "Falling back to system DNS for $hostname")
            return Dns.SYSTEM.lookup(hostname)
        }

        private fun parseJsonIps(json: String): List<String> {
            val ips = mutableListOf<String>()
            val matcher = Regex("\"data\"\\s*:\\s*\"([0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3})\"").findAll(json)
            for (match in matcher) {
                ips.add(match.groupValues[1])
            }
            return ips
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
        .connectionSpecs(listOf(
            okhttp3.ConnectionSpec.Builder(okhttp3.ConnectionSpec.MODERN_TLS)
                .tlsVersions(okhttp3.TlsVersion.TLS_1_2, okhttp3.TlsVersion.TLS_1_3)
                .cipherSuites(
                    okhttp3.CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
                    okhttp3.CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                    okhttp3.CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
                    okhttp3.CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
                    okhttp3.CipherSuite.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256,
                    okhttp3.CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256
                )
                .build(),
            okhttp3.ConnectionSpec.COMPATIBLE_TLS,
            okhttp3.ConnectionSpec.CLEARTEXT
        ))
        .sslSocketFactory(unsafeSslSocketFactory, unsafeX509TrustManager)
        .hostnameVerifier { _, _ -> true }
        .dns(DohDns)
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .cookieJar(object : CookieJar {
            private val cookiesMap = java.util.concurrent.ConcurrentHashMap<String, List<Cookie>>()
            
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val existing = cookiesMap[url.host]?.toMutableList() ?: mutableListOf()
                existing.addAll(cookies)
                cookiesMap[url.host] = existing.distinctBy { it.name }
            }
            
            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookiesMap[url.host] ?: emptyList()
            }
        })
        .build()

    init {
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
            })
            val sc = SSLContext.getInstance("SSL")
            sc.init(null, trustAllCerts, SecureRandom())
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)
            HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
            Log.d(TAG, "Unsafe SSL initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Unsafe SSL init failed", e)
        }
    }

    private val cookieStore = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>()

    private const val USER_AGENT_DESKTOP = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    private fun cleanUrl(url: String): String {
        if (url.startsWith("//")) return "https:$url"
        return url
    }

    private fun normalizeInternalLink(url: String): String {
        var clean = cleanUrl(url).trim()
        if (clean.isEmpty()) return ""
        if (clean.startsWith("/")) {
            return "$BASE_URL$clean"
        }
        
        var isIntern = false
        for (domain in DOMAINS) {
            if (clean.startsWith(domain)) {
                isIntern = true
                break
            }
        }
        
        if (!isIntern && clean.contains("samehadaku", ignoreCase = true)) {
            isIntern = true
        }
        
        if (isIntern) {
            return clean.replace(Regex("^https?://[^/]+"), BASE_URL)
        }
        return clean
    }

    private fun getRelativePath(url: String): String {
        var clean = url.trim()
        for (domain in DOMAINS) {
            val withSlash = if (domain.endsWith("/")) domain else "$domain/"
            if (clean.startsWith(withSlash)) {
                return "/" + clean.substring(withSlash.length).trimStart('/')
            }
            if (clean.startsWith(domain)) {
                return "/" + clean.substring(domain.length).trimStart('/')
            }
        }
        if (clean.startsWith("http")) {
            val stripped = clean.substringAfter("://")
            val path = stripped.substringAfter("/", "")
            return "/$path"
        }
        return if (clean.startsWith("/")) clean else "/$clean"
    }

    private suspend fun scrapeWithWebView(context: Context, url: String): String? = withContext(Dispatchers.Main) {
        val deferred = kotlinx.coroutines.CompletableDeferred<String?>()
        val webView = WebView(context)
        
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        }
        
        try {
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure CookieManager", e)
        }
        
        var hasFinished = false
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (hasFinished) return
                
                // Polling content checking
                val startTime = System.currentTimeMillis()
                val checkRunnable = object : Runnable {
                    override fun run() {
                        if (hasFinished) return
                        
                        val jsCode = """
                            (function() {
                                try {
                                    var title = document.title ? document.title.toLowerCase() : "";
                                    var html = document.documentElement ? document.documentElement.outerHTML : "";
                                    var isCloudflare = title.indexOf('cloudflare') > -1 || 
                                                       title.indexOf('just a moment') > -1 || 
                                                       title.indexOf('checking your browser') > -1 || 
                                                       title.indexOf('ddos') > -1 || 
                                                       html.indexOf('cf-browser-verification') > -1 || 
                                                       html.indexOf('ray id') > -1;
                                    
                                    if (isCloudflare) {
                                        return "CLOUDFLARE";
                                    } else if (html.length > 0) {
                                        return "READY_HTML";
                                    } else {
                                        return "WAIT";
                                    }
                                } catch(e) {
                                    return "WAIT";
                                }
                            })();
                        """.trimIndent()
                        
                        webView.evaluateJavascript(jsCode) { statusResult ->
                            if (hasFinished) return@evaluateJavascript
                            
                            val status = statusResult?.replace("\"", "") ?: "WAIT"
                            val elapsed = System.currentTimeMillis() - startTime
                            
                            if (status == "READY_HTML") {
                                webView.evaluateJavascript("document.documentElement.outerHTML") { rawHtml ->
                                    if (hasFinished) return@evaluateJavascript
                                    
                                    var cleanHtml = ""
                                    if (rawHtml != null && rawHtml.length >= 2 && rawHtml.startsWith("\"") && rawHtml.endsWith("\"")) {
                                        try {
                                            val parser = org.json.JSONTokener(rawHtml)
                                            cleanHtml = parser.nextValue() as? String ?: ""
                                        } catch (e: Exception) {
                                            cleanHtml = rawHtml.removePrefix("\"").removeSuffix("\"")
                                                .replace("\\u003C", "<").replace("\\u003E", ">")
                                                .replace("\\\"", "\"").replace("\\\\", "\\")
                                        }
                                    } else {
                                        cleanHtml = rawHtml ?: ""
                                    }
                                    
                                    hasFinished = true
                                    deferred.complete(cleanHtml)
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        try { webView.destroy() } catch (e: Exception) {}
                                    }
                                }
                            } else if (elapsed > 15000) {
                                hasFinished = true
                                deferred.complete(null)
                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                    try { webView.destroy() } catch (e: Exception) {}
                                }
                            } else {
                                webView.postDelayed(this, 1000)
                            }
                        }
                    }
                }
                webView.postDelayed(checkRunnable, 1000)
            }
            
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                Log.e(TAG, "WebView error: ${error?.description} for url: ${request?.url}")
            }
        }
        
        Log.d(TAG, "WebView loading URL: $url")
        webView.loadUrl(url)
        
        // Safety timeout of 35 seconds
        val timeoutJob = kotlinx.coroutines.GlobalScope.launch(Dispatchers.Main) {
            kotlinx.coroutines.delay(35000)
            if (!hasFinished) {
                hasFinished = true
                webView.destroy()
                deferred.complete(null)
            }
        }
        
        deferred.await().also {
            timeoutJob.cancel()
        }
    }

    suspend fun getDocWithFallback(urlPath: String): Document? {
        val path = getRelativePath(urlPath)
        
        // 1. First attempt: Parallel direct HTTP connections (extremely fast, handles 95% of requests instantly)
        try {
            val resultDoc = withContext(Dispatchers.IO) {
                coroutineScope {
                    val resultChannel = kotlinx.coroutines.channels.Channel<Pair<String, Document>?>(DOMAINS.size)
                    
                    val jobs = DOMAINS.map { domain ->
                        launch {
                            try {
                                val base = domain.trimEnd('/')
                                val fullUrl = "$base$path"
                                val doc = Jsoup.connect(fullUrl)
                                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                                    .referrer("https://www.google.com/")
                                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8")
                                    .header("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
                                    .timeout(4000) // 4 seconds fast parallel timeout
                                    .get()
                                
                                val title = doc.title().lowercase()
                                val isBlocked = title.contains("cloudflare") || 
                                                title.contains("attention required") ||
                                                title.contains("checking your browser") ||
                                                title.contains("just a moment") ||
                                                title.contains("ddos") ||
                                                doc.outerHtml().contains("cf-browser-verification") ||
                                                doc.outerHtml().contains("ray id")

                                if (!isBlocked) {
                                    val hasContent = doc.select(".animepost, .post-show, .infox, .episodelist, .listeps, .listupd, .bsx, .bs").isNotEmpty()
                                    if (hasContent || path == "/" || path == "" || path == "/jadwal-rilis/") {
                                        resultChannel.send(Pair(base, doc))
                                    } else {
                                        resultChannel.send(null)
                                    }
                                } else {
                                    resultChannel.send(null)
                                }
                            } catch (e: Exception) {
                                resultChannel.send(null)
                            }
                        }
                    }
                    
                    var successfulPair: Pair<String, Document>? = null
                    var responsesCount = 0
                    
                    while (responsesCount < DOMAINS.size) {
                        val res = resultChannel.receive()
                        if (res != null) {
                            successfulPair = res
                            break
                        } else {
                            responsesCount++
                        }
                    }
                    
                    jobs.forEach { it.cancel() }
                    successfulPair
                }
            }
            
            if (resultDoc != null) {
                BASE_URL = resultDoc.first
                Log.d(TAG, "Parallel Jsoup Direct search succeeded for $path on domain $BASE_URL")
                return resultDoc.second
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parallel Jsoup Direct search failed: ${e.message}")
        }

        // 2. Second attempt: Headless WebView scraper (extremely robust fallback for Cloudflare)
        appContext?.let { context ->
            for (domain in DOMAINS) {
                val base = domain.trimEnd('/')
                val fullUrl = "$base$path"
                try {
                    Log.d(TAG, "Attempting fallback WebView scraping for: $fullUrl")
                    val html = scrapeWithWebView(context, fullUrl)
                    if (!html.isNullOrEmpty()) {
                        val doc = Jsoup.parse(html, fullUrl)
                        val title = doc.title().lowercase()
                        val isBlocked = title.contains("cloudflare") || 
                                        title.contains("attention required") ||
                                        title.contains("checking your browser") ||
                                        title.contains("just a moment") ||
                                        title.contains("ddos") ||
                                        html.contains("cf-browser-verification") ||
                                        html.contains("ray id")

                        if (!isBlocked) {
                            val hasContent = doc.select(".animepost, .post-show, .infox, .episodelist, .listeps, .listupd, .post-show article, .listupd article, .bsx, .bs").isNotEmpty()
                            if (hasContent || path == "/" || path == "/anime-terbaru/" || path == "/daftar-anime-2/" || path == "/jadwal-rilis/") {
                                Log.d(TAG, "WebView fallback successfully loaded Samehadaku with domain $domain")
                                BASE_URL = base
                                return doc
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "WebView fallback scraping failed for domain $domain", e)
                }
            }
        }

        // 3. Third attempt: Fallback to existing OkHttp proxy-scanning
        return withContext(Dispatchers.IO) {
            val userAgents = listOf(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4.1 Safari/605.1.15"
            )

            for (domain in DOMAINS) {
                val base = domain.trimEnd('/')
                val fullUrl = "$base$path"
                val proxyUrls = listOf(
                    fullUrl,
                    "https://corsproxy.io/?${java.net.URLEncoder.encode(fullUrl, "UTF-8")}"
                )

                for (targetUrl in proxyUrls) {
                    for (ua in userAgents) {
                        try {
                            val requestBuilder = Request.Builder()
                                .url(targetUrl)
                                .header("User-Agent", ua)
                                .header("Referer", "https://www.google.com/")
                                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                                .header("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8")
                                .header("Connection", "keep-alive")
                                
                            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                                if (response.isSuccessful) {
                                    val body = response.body?.string() ?: ""
                                    val doc = Jsoup.parse(body, fullUrl)
                                    val title = doc.title().lowercase()
                                    
                                    val isBlocked = title.contains("cloudflare") || 
                                                    title.contains("attention required") ||
                                                    title.contains("checking your browser") ||
                                                    title.contains("just a moment") ||
                                                    title.contains("ddos") ||
                                                    body.contains("cf-browser-verification") ||
                                                    body.contains("ray id")

                                    if (!isBlocked) {
                                        val hasContent = doc.select(".animepost, .post-show, .infox, .episodelist").isNotEmpty()
                                        if (hasContent || path == "/" || path == "/jadwal-rilis/") {
                                            Log.d(TAG, "Successfully bypassed with domain $domain using URL: $targetUrl")
                                            BASE_URL = base
                                            return@withContext doc
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, "Proxy fallback failed for URL $targetUrl: ${e.message}")
                        }
                    }
                }
            }
            Log.e(TAG, "All domains, fallbacks, and proxies failed for path: $path")
            null
        }
    }

    private fun parseAnimeListFromDoc(doc: Document): List<ScrapedAnime> {
        val list = mutableListOf<ScrapedAnime>()
        // Animestream/MangaTheme uses .animepost, .bsx, .bs
        val elements = doc.select(".listupd .animepost, .post-show .animepost, .post-show article, .listupd article, .animepost, .utao, .bsx, .bs")
        
        for (el in elements) {
            try {
                // Get general title
                var title = el.select(".title, h2, h3, .tt h4").text().trim()
                if (title.isEmpty()) {
                    title = el.select("a").attr("title").trim()
                }

                // Get URL
                val link = normalizeInternalLink(el.select("a").first()?.attr("href") ?: "")
                
                // Get Image
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
                            type = type
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
        val path = if (page > 1) "/anime-terbaru/page/$page/" else "/anime-terbaru/"
        val doc = getDocWithFallback(path) ?: return emptyList()
        return parseAnimeListFromDoc(doc)
    }

    suspend fun fetchAllAnime(page: Int = 1): List<ScrapedAnime> {
        val path = if (page > 1) "/daftar-anime-2/page/$page/" else "/daftar-anime-2/"
        val doc = getDocWithFallback(path) ?: return emptyList()
        return parseAnimeListFromDoc(doc)
    }

    suspend fun fetchSchedule(): List<ScheduleDay> {
        return withContext(Dispatchers.IO) {
            val doc = getDocWithFallback("/jadwal-rilis/") ?: return@withContext emptyList()
            val list = mutableListOf<ScheduleDay>()
            try {
                // Try standard schedule containers used in Samehadaku / WordPress themes
                val boxes = doc.select(".schedulepage .tab-pane, .schedule-box, .listSchh, .bip, .jadwal-box, div.schedule-item")
                if (boxes.isNotEmpty()) {
                    for (box in boxes) {
                        val dayName = box.select("h2, h3, .day-title, .schedule-day, .tab-title").text().trim()
                            .ifEmpty { box.attr("id").replace("tab-", "").replace("day-", "").replaceFirstChar { it.uppercase() } }
                        if (dayName.isNotEmpty()) {
                            val animeLinks = mutableListOf<ScheduleAnime>()
                            box.select("article, .subSchh a, .bs, .schedule-item, .animepost").forEach { el ->
                                val aEl = if (el.tagName() == "a") el else el.select("a").first()
                                if (aEl != null) {
                                    val rawLink = aEl.attr("href")
                                    val cleanLink = cleanUrl(rawLink)
                                    val rawTitle = el.select(".title, .tt, h2, h3, .entry-title").text().ifEmpty { aEl.text() }.trim()
                                    val epText = el.select(".epx, .ep, .eps, span").text().trim()
                                    val imgEl = el.select("img").first()
                                    var imgUrl = ""
                                    if (imgEl != null) {
                                        imgUrl = imgEl.attr("data-src").ifEmpty { imgEl.attr("data-lazy-src") }.ifEmpty { imgEl.attr("src") }
                                        imgUrl = cleanUrl(imgUrl)
                                    }

                                    if (rawTitle.isNotEmpty() && cleanLink.isNotEmpty()) {
                                        animeLinks.add(
                                            ScheduleAnime(
                                                title = rawTitle,
                                                link = cleanLink,
                                                episode = epText.ifEmpty { "Episode ?" },
                                                releaseDay = dayName,
                                                imageUrl = imgUrl,
                                                source = "Samehadaku"
                                            )
                                        )
                                    }
                                }
                            }
                            if (animeLinks.isNotEmpty()) {
                                list.add(ScheduleDay(day = dayName, animeList = animeLinks))
                            }
                        }
                    }
                } else {
                    // Fallback parse if no containers match
                    val parsedAnimes = parseAnimeListFromDoc(doc)
                    if (parsedAnimes.isNotEmpty()) {
                        val items = parsedAnimes.map { 
                            ScheduleAnime(
                                title = it.title,
                                link = it.link,
                                episode = it.episode,
                                releaseDay = "Hari Ini",
                                imageUrl = it.imageUrl,
                                source = "Samehadaku"
                            )
                        }
                        list.add(ScheduleDay(day = "Hari Ini", animeList = items))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing Samehadaku schedule", e)
            }
            list
        }
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
                    // Method 1: Check .nvs a or similar navigation elements (usually "Semua Episode")
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
                    
                    // Method 2: Check breadcrumbs format (extremely reliable for hierarchy!)
                    if (allEpsLink.isEmpty()) {
                        val breadcrumbLinks = doc.select(".ts-breadcrumb a, .breadcrumbs a, .breadcrumb a, [itemtype*=BreadcrumbList] a")
                        // Iterate backwards from the last link (the last link is the level above the episode page)
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
                    
                    // Method 3: Fallback search of any links in the page body matching series styles
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

                var synopsis = doc.select(".desc, .sinopsis, .entry-content p, .entry-content").text().trim()
                
                // Clean up Samehadaku SEO boilerplate
                val seoPattern = Regex("Watch streaming.*?(video\\.|quota,).*?(\\.|\\n|\\r)|You can also download.*?(\\.|\\n|\\r)|Nonton anime.*?(\\.|\\n|\\r)|Jangan lupa menonton.*?(\\.|\\n|\\r)|Download gratis.*?(\\.|\\n|\\r)|Streaming anime.*?(\\.|\\n|\\r)|.*berbagai macam resolusi.*?(video\\.|\\.)", RegexOption.IGNORE_CASE)
                synopsis = synopsis.replace(seoPattern, "").trim()
                if (synopsis.startsWith("In a Victorian world", ignoreCase = true)) {
                    // It's clean
                }

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
                val epsElements = doc.select(".listeps ul li, .lstep ul li, .eplister ul li, .lstepsiode ul li")
                for (epsEl in epsElements) {
                    val linkEl = epsEl.select(".eps a, a").first()
                    if (linkEl != null) {
                        val epsLink = normalizeInternalLink(linkEl.attr("href"))
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
                Log.e(TAG, "Failed fetchAnimeDetail: ${e.message}")
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
                
                // Get player mirrors inside <option>
                val mirrors = doc.select(".player-select option, .mirror option")
                for (mirror in mirrors) {
                    val text = mirror.text().trim()
                    val value = mirror.attr("value")
                    if (value.isNotEmpty() && !value.contains("Select")) {
                        try {
                            val decoded = String(android.util.Base64.decode(value, android.util.Base64.DEFAULT))
                            val iframeSrc = Jsoup.parse(decoded).select("iframe").attr("src")
                            if (iframeSrc.isNotEmpty()) {
                                streamList.add(StreamEmbed(serverName = text, iframeUrl = iframeSrc))
                                continue
                            }
                        } catch (e: Exception) {}
                        
                        if (value.startsWith("http")) {
                            streamList.add(StreamEmbed(serverName = text, iframeUrl = value))
                        }
                    }
                }

                if (streamList.isEmpty()) {
                    val iframes = doc.select("iframe")
                    iframes.forEachIndexed { idx, iframe ->
                        val src = iframe.attr("src")
                        if (src.isNotEmpty() && !src.contains("disqus")) {
                            streamList.add(StreamEmbed(serverName = "Server ${idx + 1}", iframeUrl = src))
                        }
                    }
                }

                doc.select(".server_option li, .player_option li, .east_player_option, [data-video], [data-embed], [data-iframe]").forEach { el ->
                    var rawSrc = el.attr("data-video")
                        .ifEmpty { el.attr("data-post") }
                        .ifEmpty { el.attr("data-embed") }
                        .ifEmpty { el.attr("data-iframe") }
                        .ifEmpty { el.attr("data-src") }
                        .trim()
                    
                    if (rawSrc.startsWith("//")) {
                        rawSrc = "https:$rawSrc"
                    }
                    
                    val name = el.text().trim()
                        .ifEmpty { el.attr("title").trim() }
                        .ifEmpty { el.select("span").text().trim() }
                        .ifEmpty { "Server Mirror" }
                        
                    if (rawSrc.isNotEmpty() && !rawSrc.contains("disqus") && (rawSrc.startsWith("http") || rawSrc.contains("/embed/") || rawSrc.contains("player") || rawSrc.contains("post"))) {
                        if (streamList.none { it.iframeUrl == rawSrc }) {
                            streamList.add(StreamEmbed(serverName = name, iframeUrl = rawSrc))
                        }
                    }
                }
                
                // Extra fallback, default player is the page url
                if (streamList.isEmpty()) {
                    streamList.add(StreamEmbed(serverName = "Default Player", iframeUrl = episodeUrl))
                }

                val downloadList = mutableListOf<DownloadGroup>()
                val downloadBlocks = doc.select(".download-eps, .download-link, .download-area, .dl-box")
                
                if (downloadBlocks.isNotEmpty()) {
                    for (block in downloadBlocks) {
                        val resolutions = block.select("ul, li, div.dl")
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

                if (title.isEmpty() && streamList.isEmpty()) return@withContext null

                EpisodeDetail(
                    title = title,
                    streamEmbeds = streamList,
                    downloads = downloadList
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed fetchEpisodeDetail: ${e.message}")
                null
            }
        }
    }
}
