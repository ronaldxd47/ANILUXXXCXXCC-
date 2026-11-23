package com.example.data.stream

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.data.stream.detector.ProtocolDetector
import com.example.data.stream.extractor.ExtractorUtils
import com.example.data.stream.extractor.HosterExtractorRegistry
import com.example.data.stream.model.ContainerFormat
import com.example.data.stream.model.RequestPolicy
import com.example.data.stream.model.StreamCandidate
import com.example.data.stream.model.StreamProtocol
import com.example.data.stream.ranking.CandidateRanker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

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
                Log.d(TAG, "Base64 decode skipped")
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
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Static stream parsing error for ${com.example.data.BaseAnimestreamScraper.redactForLog(cleanUrl)}: ${e.message}")
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
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
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
        val topRedacted = ranked.firstOrNull()?.url?.let { com.example.data.BaseAnimestreamScraper.redactForLog(it) }
        Log.d(TAG, "Resolved ${ranked.size} candidates for ${com.example.data.BaseAnimestreamScraper.redactForLog(cleanUrl)} (top=$topRedacted)")
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
            list.addAll(
                HosterExtractorRegistry.extractAll(
                    pageUrl = targetUrl,
                    html = html,
                    serverName = serverName,
                    requestPolicy = requestPolicy
                )
            )

            // 1-hop nested iframe unwrapping (when an embed page wraps another player iframe)
            if (list.isEmpty()) {
                val doc = Jsoup.parse(html, targetUrl)
                val nestedIframe = doc.select("iframe[src], iframe[data-src]").firstOrNull { el ->
                    val s = el.attr("src").ifEmpty { el.attr("data-src") }.lowercase()
                    s.isNotBlank() && !s.contains("disqus") && !s.contains("ads") && !s.contains("doubleclick") && !s.contains("about:blank")
                }
                if (nestedIframe != null) {
                    val nestedRaw = nestedIframe.attr("src").ifEmpty { nestedIframe.attr("data-src") }
                    val nestedUrl = ExtractorUtils.resolveAbsoluteUrl(targetUrl, nestedRaw)
                    if (nestedUrl.startsWith("http") && nestedUrl != targetUrl) {
                        val nestedPolicy = RequestPolicy.fromMap(createStandardHeaders(nestedUrl), targetUrl)
                        list.addAll(resolveStaticCandidatesDirect(nestedUrl, serverName, nestedPolicy))
                    }
                }
            }
        }
        return list
    }

    private fun resolveStaticCandidatesDirect(
        targetUrl: String,
        serverName: String,
        requestPolicy: RequestPolicy
    ): List<StreamCandidate> {
        return try {
            val request = Request.Builder()
                .url(targetUrl)
                .headers(okhttp3.Headers.headersOf(*requestPolicy.toSafeHeaderMap().flatMap { listOf(it.key, it.value) }.toTypedArray()))
                .build()
            httpClient.newCall(request).execute().use { response ->
                val html = response.body?.string() ?: return emptyList()
                HosterExtractorRegistry.extractAll(
                    pageUrl = targetUrl,
                    html = html,
                    serverName = serverName,
                    requestPolicy = requestPolicy
                )
            }
        } catch (_: Exception) {
            emptyList()
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
}
