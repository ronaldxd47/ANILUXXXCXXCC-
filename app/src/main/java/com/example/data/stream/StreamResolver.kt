package com.example.data.stream

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.data.stream.detector.ProtocolDetector
import com.example.data.stream.model.ContainerFormat
import com.example.data.stream.model.RequestPolicy
import com.example.data.stream.model.StreamCandidate
import com.example.data.stream.model.StreamProtocol
import com.example.data.stream.ranking.CandidateRanker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Service Layer terpusat untuk Stream Resolution (Stream Intelligence Platform).
 * Menemukan dan mengevaluasi seluruh StreamCandidate dari iframe, HTML static, packed JS,
 * serta dynamic headless browser sniffing, lalu merankingnya secara cerdas.
 */
object StreamResolver {
    private const val TAG = "StreamResolver"
    private const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Resolves an iframe or media URL into a single highest-scoring [ResolvedStream] (backward compatible).
     */
    suspend fun resolve(
        context: Context,
        inputUrl: String,
        serverName: String = "Server"
    ): ResolvedStream = withContext(Dispatchers.IO) {
        val candidates = resolveCandidates(context, inputUrl, serverName)
        val best = candidates.firstOrNull()
        return@withContext best?.toResolvedStream() ?: ResolvedStream(
            url = inputUrl,
            mediaType = StreamMediaType.UNKNOWN,
            serverName = serverName
        )
    }

    /**
     * Mengekstrak seluruh candidate stream yang tersedia dari URL target,
     * melakukan validasi integritas multi-tahap, dan mengurutkan berdasarkan playability score.
     */
    suspend fun resolveCandidates(
        context: Context,
        inputUrl: String,
        serverName: String = "Server"
    ): List<StreamCandidate> = withContext(Dispatchers.IO) {
        if (inputUrl.isBlank()) return@withContext emptyList()

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

        if (!cleanUrl.startsWith("http")) return@withContext emptyList()

        val candidates = mutableListOf<StreamCandidate>()
        val defaultHeaders = createStandardHeaders(cleanUrl)
        val reqPolicy = RequestPolicy.fromMap(defaultHeaders, cleanUrl)

        // 2. Direct extension check (Fast path)
        if (isObviousDirectStream(cleanUrl)) {
            val protocol = ProtocolDetector.detect(cleanUrl, "", "")
            val directCandidate = StreamCandidate(
                url = cleanUrl,
                protocol = protocol,
                container = if (protocol == StreamProtocol.HLS) ContainerFormat.MPEG_TS else ContainerFormat.MP4,
                requestPolicy = reqPolicy,
                isDirectVideo = true,
                providerName = serverName,
                originalPageUrl = cleanUrl
            )
            val validated = StreamValidator.validateCandidate(directCandidate)
            if (validated.isValidated) {
                candidates.add(validated)
            }
        }

        // 3. Static HTTP GET & HTML/JavaScript parsing
        try {
            val staticList = resolveStaticCandidates(cleanUrl, serverName, reqPolicy)
            for (candidate in staticList) {
                val validated = StreamValidator.validateCandidate(candidate)
                if (validated.isValidated) {
                    candidates.add(validated)
                } else if (candidate.url.contains(".m3u8") && !validated.isDeadLink) {
                    // Berikan toleransi hanya jika CDN menolak probe (misal token) tapi BUKAN 404 dead link!
                    candidates.add(candidate.copy(protocol = StreamProtocol.HLS, lastStatusCode = validated.lastStatusCode))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Static stream parsing error for $cleanUrl: ${e.message}")
        }

        // 4. Dynamic Headless Sniffing via WebView jika belum ada candidate HLS direct yang solid
        val hasSolidHls = candidates.any { it.protocol == StreamProtocol.HLS && it.isValidated }
        if (!hasSolidHls) {
            try {
                val dynamicCandidates = HeadlessStreamExtractor.extractCandidateStreams(context, cleanUrl)
                for (dyn in dynamicCandidates) {
                    val candidateWithProvider = dyn.copy(providerName = serverName)
                    val validated = StreamValidator.validateCandidate(candidateWithProvider)
                    if (validated.isValidated) {
                        candidates.add(validated)
                    } else if (candidateWithProvider.url.contains(".m3u8") && !validated.isDeadLink) {
                        candidates.add(candidateWithProvider.copy(protocol = StreamProtocol.HLS, lastStatusCode = validated.lastStatusCode))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Headless dynamic extraction error: ${e.message}")
            }
        }

        // 5. Fallback Web Sandbox Candidate (Iframe embed)
        candidates.add(
            StreamCandidate(
                url = cleanUrl,
                protocol = StreamProtocol.WEB_EMBED,
                container = ContainerFormat.UNKNOWN,
                requestPolicy = reqPolicy,
                isDirectVideo = false,
                providerName = serverName,
                originalPageUrl = cleanUrl,
                score = 15,
                confidence = 0.5f
            )
        )

        // 6. Ranking Candidates
        val ranked = CandidateRanker.rankCandidates(candidates)
        Log.d(TAG, "Resolved ${ranked.size} candidates for $cleanUrl (top=${ranked.firstOrNull()?.url})")
        return@withContext ranked
    }

    /**
     * Membedah halaman HTML static untuk mencari referensi stream (video tag, JWPlayer, packed JS, blogger).
     */
    private fun resolveStaticCandidates(
        targetUrl: String,
        serverName: String,
        requestPolicy: RequestPolicy
    ): List<StreamCandidate> {
        val list = mutableListOf<StreamCandidate>()
        val request = Request.Builder()
            .url(targetUrl)
            .headers(okhttp3.Headers.headersOf(*requestPolicy.toSafeHeaderMap().flatMap { listOf(it.key, it.value) }.toTypedArray()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val contentType = response.header("Content-Type", "")?.lowercase() ?: ""
            val finalUrl = response.request.url.toString()

            // Jika response langsung file media
            if (contentType.contains("mpegurl") || contentType.contains("video/")) {
                val protocol = ProtocolDetector.detect(finalUrl, contentType, "")
                list.add(
                    StreamCandidate(
                        url = finalUrl,
                        protocol = protocol,
                        mimeType = contentType,
                        requestPolicy = requestPolicy,
                        isDirectVideo = true,
                        providerName = serverName,
                        originalPageUrl = targetUrl
                    )
                )
                return list
            }

            val html = response.body?.string() ?: return list
            val doc = Jsoup.parse(html)

            // A. HTML5 Video / Source tag
            val videoEls = doc.select("video source, video")
            for (videoEl in videoEls) {
                val src = videoEl.attr("src").trim()
                if (src.isNotEmpty() && !src.startsWith("blob:")) {
                    val fullSrc = resolveAbsoluteUrl(targetUrl, src)
                    if (fullSrc.startsWith("http")) {
                        val protocol = ProtocolDetector.detect(fullSrc, videoEl.attr("type"), "")
                        list.add(
                            StreamCandidate(
                                url = fullSrc,
                                protocol = protocol,
                                mimeType = videoEl.attr("type").ifEmpty { null },
                                requestPolicy = requestPolicy,
                                isDirectVideo = true,
                                providerName = serverName,
                                originalPageUrl = targetUrl
                            )
                        )
                    }
                }
            }

            // B. Blogger / Blogspot config
            if (targetUrl.contains("blogger.com") || targetUrl.contains("blogspot.com") || html.contains("VIDEO_CONFIG")) {
                val bloggerUrl = extractBloggerStream(html)
                if (bloggerUrl.isNotEmpty()) {
                    val fullBloggerUrl = resolveAbsoluteUrl(targetUrl, bloggerUrl)
                    val protocol = ProtocolDetector.detect(fullBloggerUrl, "", "")
                    list.add(
                        StreamCandidate(
                            url = fullBloggerUrl,
                            protocol = protocol,
                            requestPolicy = requestPolicy,
                            isDirectVideo = true,
                            providerName = serverName,
                            originalPageUrl = targetUrl
                        )
                    )
                }
            }

            // C. Unpack Dean Edwards JavaScript
            val unpackedJs = unpackPackedJsIfAny(html)
            val htmlToScan = if (unpackedJs.isNotEmpty()) "$html\n$unpackedJs" else html

            // D. Regex matcher for absolute .m3u8, .mpd, or .mp4
            val m3u8Regex = Regex("""(https?(?::|\\/\\/)[^"'<>\s]+?\.(?:m3u8|mp4|mpd|webm|mkv)(?:\?[^"'<>\s]*)?)""", RegexOption.IGNORE_CASE)
            for (match in m3u8Regex.findAll(htmlToScan)) {
                val found = resolveAbsoluteUrl(targetUrl, match.value)
                if (!found.contains("googleads") && !found.contains("analytics") && !found.contains("doubleclick")) {
                    val protocol = ProtocolDetector.detect(found, "", "")
                    list.add(
                        StreamCandidate(
                            url = found,
                            protocol = protocol,
                            requestPolicy = requestPolicy,
                            isDirectVideo = true,
                            providerName = serverName,
                            originalPageUrl = targetUrl
                        )
                    )
                }
            }

            // E. Regex matcher for player sources: file/src/url
            val fileRegex = Regex("""["']?(?:file|src|url)["']?\s*:\s*["']([^"']+\.(?:m3u8|mp4|mpd|webm)[^"']*)["']""", RegexOption.IGNORE_CASE)
            for (match in fileRegex.findAll(htmlToScan)) {
                val found = resolveAbsoluteUrl(targetUrl, match.groupValues[1])
                val protocol = ProtocolDetector.detect(found, "", "")
                list.add(
                    StreamCandidate(
                        url = found,
                        protocol = protocol,
                        requestPolicy = requestPolicy,
                        isDirectVideo = true,
                        providerName = serverName,
                        originalPageUrl = targetUrl
                    )
                )
            }
        }
        return list
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
                lower.endsWith(".webm") || lower.contains(".webm?") ||
                lower.endsWith(".mkv") || lower.contains(".mkv?") ||
                lower.contains("videoplayback")
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
                // Ignore
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
