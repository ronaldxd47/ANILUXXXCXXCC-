package com.example.data

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Service Layer khusus untuk Ekstraksi Video Stream (HLS .m3u8 / Direct .mp4) dari Anichin dan Provider Embed.
 * Didesain modular dengan suspend functions agar siap dimigrasikan ke Backend API (Express/NestJS) di masa depan.
 */
object AnichinStreamExtractor {
    private const val TAG = "AnichinStreamExtractor"

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    /**
     * Fungsi utama suspend untuk mengekstrak URL video langsung dari link iframe / embed.
     */
    suspend fun extractStreamUrl(context: Context, iframeUrl: String): String = withContext(Dispatchers.IO) {
        if (iframeUrl.isBlank()) return@withContext ""

        var cleanUrl = iframeUrl.trim()
        if (cleanUrl.startsWith("//")) {
            cleanUrl = "https:$cleanUrl"
        }

        // 1. Cek jika URL berupa Base64 encoded string
        if (!cleanUrl.startsWith("http")) {
            try {
                val decoded = String(Base64.decode(cleanUrl, Base64.DEFAULT)).trim()
                if (decoded.startsWith("http") || decoded.contains(".m3u8") || decoded.contains(".mp4")) {
                    cleanUrl = decoded
                }
            } catch (e: Exception) {
                Log.d(TAG, "Base64 decode skipped for input: $cleanUrl")
            }
        }

        if (!cleanUrl.startsWith("http")) {
            return@withContext ""
        }

        // 2. Jika URL sudah berupa direct stream (.m3u8 atau .mp4), langsung kembalikan
        if (isDirectStreamUrl(cleanUrl)) {
            Log.d(TAG, "Direct stream URL detected: $cleanUrl")
            return@withContext cleanUrl
        }

        // 3. Ekstraksi Statis HTTP Request + Parsing HTML/JS
        try {
            val staticUrl = resolveStaticHtmlStream(cleanUrl)
            if (staticUrl.isNotEmpty()) {
                Log.d(TAG, "Static extraction succeeded: $staticUrl")
                return@withContext staticUrl
            }
        } catch (e: Exception) {
            Log.e(TAG, "Static stream resolution error: ${e.message}")
        }

        // 4. Fallback ke Headless WebView Extractor (Dynamic Network Sniffer) jika parsing statis gagal
        return@withContext try {
            val dynamicUrl = com.example.ui.HeadlessStreamExtractor.extractMediaUrl(context, cleanUrl)
            if (dynamicUrl.isNotEmpty()) {
                Log.d(TAG, "Dynamic Headless Extractor succeeded: $dynamicUrl")
                dynamicUrl
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Headless stream extraction failed: ${e.message}")
            ""
        }
    }

    /**
     * Memeriksa apakah URL merupakan direct stream media.
     */
    private fun isDirectStreamUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.endsWith(".mp4") ||
                lower.contains(".mp4?") ||
                lower.endsWith(".m3u8") ||
                lower.contains(".m3u8?") ||
                lower.contains("videoplayback")
    }

    /**
     * Mengeksekusi HTTP GET ke halaman embed dan mengurai isi HTML/JavaScript untuk menemukan .m3u8 atau .mp4
     */
    private fun resolveStaticHtmlStream(targetUrl: String): String {
        val request = Request.Builder()
            .url(targetUrl)
            .header("User-Agent", DEFAULT_USER_AGENT)
            .header("Referer", targetUrl)
            .build()

        val response = httpClient.newCall(request).execute()
        val html = response.body?.string() ?: return ""

        // A. Cek video tag (<video src="..." /> atau <source src="..." />)
        val doc = Jsoup.parse(html)
        val sourceEl = doc.select("video source, video").firstOrNull()
        if (sourceEl != null) {
            val src = sourceEl.attr("src")
            if (src.isNotEmpty() && !src.startsWith("blob:")) {
                val fullSrc = if (src.startsWith("//")) "https:$src" else src
                if (fullSrc.startsWith("http")) {
                    return fullSrc
                }
            }
        }

        // B. Cek Blogger / Blogspot Video JSON Config
        if (targetUrl.contains("blogger.com") || targetUrl.contains("blogspot.com") || html.contains("VIDEO_CONFIG")) {
            val bloggerStream = extractBloggerStream(html)
            if (bloggerStream.isNotEmpty()) return bloggerStream
        }

        // C. Unpack Dean Edwards packed JavaScript `eval(function(p,a,c,k,e,d)...)` jika ada
        val unpackedJs = unpackPackedJsIfAny(html)
        val htmlToScan = if (unpackedJs.isNotEmpty()) "$html\n$unpackedJs" else html

        // D. Regex matcher untuk URL .m3u8 / .mp4
        val m3u8Regex = Regex("""(https?(?::|\\/\\/)[^"'<>\s]+?\.(?:m3u8|mp4)(?:\?[^"'<>\s]*)?)""", RegexOption.IGNORE_CASE)
        val matches = m3u8Regex.findAll(htmlToScan)
        for (match in matches) {
            var found = match.value.replace("\\/", "/")
            if (found.startsWith("//")) found = "https:$found"
            if (!found.contains("googleads") && !found.contains("analytics") && !found.contains("doubleclick")) {
                return found
            }
        }

        // E. Regex matcher untuk field `file:` atau `sources:` pada JWPlayer/VideoJS
        val fileRegex = Regex("""["']?file["']?\s*:\s*["']([^"']+\.(?:m3u8|mp4)[^"']*)["']""", RegexOption.IGNORE_CASE)
        val fileMatch = fileRegex.find(htmlToScan)
        if (fileMatch != null && fileMatch.groupValues.size > 1) {
            var fileUrl = fileMatch.groupValues[1].replace("\\/", "/")
            if (fileUrl.startsWith("//")) fileUrl = "https:$fileUrl"
            return fileUrl
        }

        return ""
    }

    /**
     * Ekstraktor khusus untuk server Blogger/Blogspot Video Config
     */
    private fun extractBloggerStream(html: String): String {
        try {
            val regex = Regex("""['"]streams['"]\s*:\s*(\[[^\]]+\])""", RegexOption.IGNORE_CASE)
            val match = regex.find(html)
            if (match != null && match.groupValues.size > 1) {
                val jsonArrStr = match.groupValues[1]
                val urlRegex = Regex("""['"]play_url['"]\s*:\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE)
                val urlMatch = urlRegex.find(jsonArrStr)
                if (urlMatch != null && urlMatch.groupValues.size > 1) {
                    return urlMatch.groupValues[1].replace("\\/", "/")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Blogger stream parsing error: ${e.message}")
        }
        return ""
    }

    /**
     * Helper Unpacker untuk mendikodekan Dean Edwards JS Packer `eval(function(p,a,c,k,e,d)...)`
     */
    private fun unpackPackedJsIfAny(html: String): String {
        val packedPattern = Pattern.compile("""eval\(function\(p,a,c,k,e,d\).*?\}\('([^']*)',(\d+),(\d+),'([^']*)'\.split\('\|'\)""", Pattern.DOTALL)
        val matcher = packedPattern.matcher(html)
        if (matcher.find()) {
            try {
                val p = matcher.group(1) ?: ""
                val a = matcher.group(2)?.toIntOrNull() ?: 36
                val c = matcher.group(3)?.toIntOrNull() ?: 0
                val k = matcher.group(4)?.split("|") ?: emptyList()

                var unpacked = p
                for (i in c - 1 downTo 0) {
                    val word = if (i < k.size && k[i].isNotEmpty()) k[i] else i.toString(a)
                    val regex = Regex("\\b" + i.toString(a) + "\\b")
                    unpacked = unpacked.replace(regex, word)
                }
                return unpacked
            } catch (e: Exception) {
                Log.e(TAG, "Unpacker error: ${e.message}")
            }
        }
        return ""
    }
}
