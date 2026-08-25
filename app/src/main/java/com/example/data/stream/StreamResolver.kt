package com.example.data.stream

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Service Layer terpusat untuk Stream Resolution (HLS m3u8, DASH mpd, MP4 direct, dan Web Embed fallback).
 * Dirancang modular, asynchronous (suspend), dengan proteksi MIME type dan header retention.
 */
object StreamResolver {
    private const val TAG = "StreamResolver"
    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    /**
     * Resolves an iframe or media URL into a rich, structured [ResolvedStream] object.
     */
    suspend fun resolve(
        context: Context,
        inputUrl: String,
        serverName: String = "Server"
    ): ResolvedStream = withContext(Dispatchers.IO) {
        if (inputUrl.isBlank()) {
            return@withContext ResolvedStream(
                url = "",
                mediaType = StreamMediaType.UNKNOWN,
                serverName = serverName
            )
        }

        var cleanUrl = inputUrl.trim()
        if (cleanUrl.startsWith("//")) {
            cleanUrl = "https:$cleanUrl"
        }

        // 1. Cek jika URL adalah Base64 encoded string
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            try {
                val decoded = String(Base64.decode(cleanUrl, Base64.DEFAULT)).trim()
                if (decoded.startsWith("http://") || decoded.startsWith("https://") ||
                    decoded.contains(".m3u8") || decoded.contains(".mp4")
                ) {
                    cleanUrl = decoded
                }
            } catch (e: Exception) {
                Log.d(TAG, "Base64 decode skipped for: $cleanUrl")
            }
        }

        if (!cleanUrl.startsWith("http")) {
            return@withContext ResolvedStream(
                url = cleanUrl,
                mediaType = StreamMediaType.UNKNOWN,
                serverName = serverName
            )
        }

        // 2. Direct extension check (Fast path)
        val defaultHeaders = createStandardHeaders(cleanUrl)
        if (isObviousDirectStream(cleanUrl)) {
            val mediaType = detectMediaTypeFromUrl(cleanUrl)
            return@withContext ResolvedStream(
                url = cleanUrl,
                mediaType = mediaType,
                headers = defaultHeaders,
                isDirectVideo = true,
                serverName = serverName,
                originalIframeUrl = cleanUrl
            )
        }

        // 3. Static HTTP GET & HTML/JavaScript parsing
        try {
            val staticResult = resolveStaticStream(cleanUrl, serverName)
            if (staticResult != null) {
                Log.d(TAG, "Static stream extraction succeeded: ${staticResult.url}")
                return@withContext staticResult
            }
        } catch (e: Exception) {
            Log.w(TAG, "Static stream parsing failed for $cleanUrl: ${e.message}")
        }

        // 4. Dynamic Headless Sniffing via WebView (Dynamic JavaScript & Protected tokens)
        try {
            val dynamicStream = HeadlessStreamExtractor.extractMediaStream(context, cleanUrl)
            if (dynamicStream != null && dynamicStream.url.isNotEmpty()) {
                Log.d(TAG, "Headless dynamic extraction succeeded: ${dynamicStream.url}")
                return@withContext dynamicStream.copy(serverName = serverName)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Headless extraction exception: ${e.message}")
        }

        // 5. Fallback: Return as IFRAME sandboxed stream for WebPlayerView
        Log.d(TAG, "Fallback to Web Player Sandbox for: $cleanUrl")
        return@withContext ResolvedStream(
            url = cleanUrl,
            mediaType = StreamMediaType.IFRAME,
            headers = defaultHeaders,
            isDirectVideo = false,
            serverName = serverName,
            originalIframeUrl = cleanUrl
        )
    }

    /**
     * Inspects target URL via HTTP GET and searches for embedded streams.
     * Uses .use {} pattern on OkHttp Response to prevent socket leaks, and resolves relative URLs.
     */
    private fun resolveStaticStream(targetUrl: String, serverName: String): ResolvedStream? {
        val headers = createStandardHeaders(targetUrl)
        val request = Request.Builder()
            .url(targetUrl)
            .headers(okhttp3.Headers.headersOf(*headers.flatMap { listOf(it.key, it.value) }.toTypedArray()))
            .build()

        return httpClient.newCall(request).execute().use { response ->
            val contentType = response.header("Content-Type", "")?.lowercase() ?: ""

            // Check if response is actually a stream directly
            if (contentType.contains("application/vnd.apple.mpegurl") ||
                contentType.contains("application/x-mpegurl") ||
                contentType.contains("audio/x-mpegurl")
            ) {
                return@use ResolvedStream(
                    url = targetUrl,
                    mediaType = StreamMediaType.HLS,
                    headers = headers,
                    isDirectVideo = true,
                    serverName = serverName,
                    originalIframeUrl = targetUrl
                )
            }
            if (contentType.contains("video/mp4") || contentType.contains("video/webm") || contentType.contains("video/mkv")) {
                return@use ResolvedStream(
                    url = targetUrl,
                    mediaType = StreamMediaType.MP4,
                    headers = headers,
                    isDirectVideo = true,
                    serverName = serverName,
                    originalIframeUrl = targetUrl
                )
            }

            val html = response.body?.string() ?: return@use null

            // A. Video tag (<video src="..." /> or <source src="..." />)
            val doc = Jsoup.parse(html)
            val videoEl = doc.select("video source, video").firstOrNull()
            if (videoEl != null) {
                val src = videoEl.attr("src").trim()
                if (src.isNotEmpty() && !src.startsWith("blob:")) {
                    val fullSrc = resolveAbsoluteUrl(targetUrl, src)
                    if (fullSrc.startsWith("http")) {
                        val mediaType = detectMediaType(fullSrc, videoEl.attr("type"))
                        return@use ResolvedStream(
                            url = fullSrc,
                            mediaType = mediaType,
                            headers = headers,
                            isDirectVideo = true,
                            serverName = serverName,
                            originalIframeUrl = targetUrl
                        )
                    }
                }
            }

            // B. Blogger / Blogspot Video Config JSON
            if (targetUrl.contains("blogger.com") || targetUrl.contains("blogspot.com") || html.contains("VIDEO_CONFIG")) {
                val bloggerUrl = extractBloggerStream(html)
                if (bloggerUrl.isNotEmpty()) {
                    val fullBloggerUrl = resolveAbsoluteUrl(targetUrl, bloggerUrl)
                    return@use ResolvedStream(
                        url = fullBloggerUrl,
                        mediaType = detectMediaTypeFromUrl(fullBloggerUrl),
                        headers = headers,
                        isDirectVideo = true,
                        serverName = serverName,
                        originalIframeUrl = targetUrl
                    )
                }
            }

            // C. Unpack Dean Edwards JavaScript `eval(function(p,a,c,k,e,d)...)`
            val unpackedJs = unpackPackedJsIfAny(html)
            val htmlToScan = if (unpackedJs.isNotEmpty()) "$html\n$unpackedJs" else html

            // D. Regex matcher for absolute .m3u8 or .mp4 URLs (including escaped JSON \/ and URL encoded)
            val m3u8Regex = Regex("""(https?(?::|\\/\\/)[^"'<>\s]+?\.(?:m3u8|mp4|mpd)(?:\?[^"'<>\s]*)?)""", RegexOption.IGNORE_CASE)
            val matches = m3u8Regex.findAll(htmlToScan)
            for (match in matches) {
                var found = resolveAbsoluteUrl(targetUrl, match.value)
                if (!found.contains("googleads") && !found.contains("analytics") && !found.contains("doubleclick")) {
                    val mediaType = detectMediaTypeFromUrl(found)
                    return@use ResolvedStream(
                        url = found,
                        mediaType = mediaType,
                        headers = headers,
                        isDirectVideo = true,
                        serverName = serverName,
                        originalIframeUrl = targetUrl
                    )
                }
            }

            // E. Regex matcher for `file:` or `sources:` in player configs (JWPlayer / Plyr / Clappr)
            val fileRegex = Regex("""["']?(?:file|src|url)["']?\s*:\s*["']([^"']+\.(?:m3u8|mp4|mpd)[^"']*)["']""", RegexOption.IGNORE_CASE)
            val fileMatch = fileRegex.find(htmlToScan)
            if (fileMatch != null) {
                val found = resolveAbsoluteUrl(targetUrl, fileMatch.groupValues[1])
                val mediaType = detectMediaTypeFromUrl(found)
                return@use ResolvedStream(
                    url = found,
                    mediaType = mediaType,
                    headers = headers,
                    isDirectVideo = true,
                    serverName = serverName,
                    originalIframeUrl = targetUrl
                )
            }

            // F. Relative URL pattern e.g. "/stream/playlist.m3u8" or "../hls/master.m3u8"
            val relativeRegex = Regex("""["']((?:/[a-zA-Z0-9_\-./]+|\.\./[a-zA-Z0-9_\-./]+)\.(?:m3u8|mp4|mpd)(?:\?[^"'<>\s]*)?)["']""", RegexOption.IGNORE_CASE)
            val relativeMatch = relativeRegex.find(htmlToScan)
            if (relativeMatch != null) {
                val found = resolveAbsoluteUrl(targetUrl, relativeMatch.groupValues[1])
                if (found.startsWith("http")) {
                    val mediaType = detectMediaTypeFromUrl(found)
                    return@use ResolvedStream(
                        url = found,
                        mediaType = mediaType,
                        headers = headers,
                        isDirectVideo = true,
                        serverName = serverName,
                        originalIframeUrl = targetUrl
                    )
                }
            }

            null
        }
    }

    private fun resolveAbsoluteUrl(baseUrl: String, candidate: String): String {
        var clean = candidate.trim().replace("\\/", "/")
        if (clean.startsWith("//")) return "https:$clean"
        if (clean.startsWith("http://") || clean.startsWith("https://")) return clean

        return try {
            val baseUri = java.net.URI(baseUrl)
            baseUri.resolve(clean).toString()
        } catch (e: Exception) {
            try {
                val uri = Uri.parse(baseUrl)
                val origin = if (uri.scheme != null && uri.host != null) "${uri.scheme}://${uri.host}" else ""
                if (clean.startsWith("/") && origin.isNotEmpty()) {
                    "$origin$clean"
                } else {
                    clean
                }
            } catch (ex: Exception) {
                clean
            }
        }
    }

    private fun isObviousDirectStream(url: String): Boolean {
        val lower = url.lowercase()
        return lower.endsWith(".mp4") || lower.contains(".mp4?") ||
                lower.endsWith(".m3u8") || lower.contains(".m3u8?") ||
                lower.endsWith(".mpd") || lower.contains(".mpd?") ||
                lower.contains("videoplayback")
    }

    private fun detectMediaTypeFromUrl(url: String): StreamMediaType {
        val lower = url.lowercase()
        return when {
            lower.contains(".m3u8") -> StreamMediaType.HLS
            lower.contains(".mpd") -> StreamMediaType.DASH
            lower.contains(".mp4") || lower.contains("videoplayback") -> StreamMediaType.MP4
            else -> StreamMediaType.UNKNOWN
        }
    }

    private fun detectMediaType(url: String, typeAttr: String): StreamMediaType {
        val lowerType = typeAttr.lowercase()
        if (lowerType.contains("mpegurl") || lowerType.contains("hls")) return StreamMediaType.HLS
        if (lowerType.contains("dash") || lowerType.contains("mpd")) return StreamMediaType.DASH
        if (lowerType.contains("mp4")) return StreamMediaType.MP4
        return detectMediaTypeFromUrl(url)
    }

    fun createStandardHeaders(targetUrl: String): Map<String, String> {
        val domainOrigin = try {
            val uri = Uri.parse(targetUrl)
            if (uri.scheme != null && uri.host != null) "${uri.scheme}://${uri.host}" else targetUrl
        } catch (e: Exception) {
            targetUrl
        }

        return mapOf(
            "User-Agent" to DEFAULT_USER_AGENT,
            "Referer" to targetUrl,
            "Origin" to domainOrigin,
            "Accept" to "*/*"
        )
    }

    private fun extractBloggerStream(html: String): String {
        return try {
            val jsonPattern = Pattern.compile("var\\s+VIDEO_CONFIG\\s*=\\s*(\\{.+?\\});", Pattern.DOTALL)
            val matcher = jsonPattern.matcher(html)
            if (matcher.find()) {
                val jsonString = matcher.group(1) ?: return ""
                val json = JSONObject(jsonString)
                val streams = json.optJSONArray("streams")
                if (streams != null && streams.length() > 0) {
                    var bestUrl = ""
                    var maxFormat = 0
                    for (i in 0 until streams.length()) {
                        val streamObj = streams.getJSONObject(i)
                        val playUrl = streamObj.optString("play_url")
                        val formatId = streamObj.optInt("format_id", 0)
                        if (playUrl.isNotEmpty() && formatId >= maxFormat) {
                            maxFormat = formatId
                            bestUrl = playUrl
                        }
                    }
                    if (bestUrl.isNotEmpty()) return bestUrl
                }
            }
            ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun unpackPackedJsIfAny(html: String): String {
        val packedPattern = Pattern.compile("eval\\(function\\(p,a,c,k,e,d\\).*?\\}\\('(.*?)'\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*'([^']+)'\\.split\\('\\|'\\)", Pattern.DOTALL)
        val matcher = packedPattern.matcher(html)
        val results = StringBuilder()

        while (matcher.find()) {
            try {
                val p = matcher.group(1) ?: continue
                val a = matcher.group(2)?.toIntOrNull() ?: 62
                val c = matcher.group(3)?.toIntOrNull() ?: 0
                val k = matcher.group(4)?.split("|") ?: continue

                var unpacked = p
                for (i in (k.size - 1) downTo 0) {
                    val word = k[i]
                    if (word.isNotEmpty()) {
                        val token = toRadix(i, a)
                        unpacked = unpacked.replace(Regex("\\b$token\\b"), word)
                    }
                }
                results.append(unpacked).append("\n")
            } catch (e: Exception) {
                // Ignore unpacking errors
            }
        }
        return results.toString()
    }

    private fun toRadix(num: Int, radix: Int): String {
        val alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        if (num == 0) return "0"
        var n = num
        val sb = StringBuilder()
        while (n > 0) {
            sb.append(alphabet[n % radix])
            n /= radix
        }
        return sb.reverse().toString()
    }
}
