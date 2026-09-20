package com.example.data.stream

import android.util.Log
import com.example.data.stream.detector.ContainerSniffer
import com.example.data.stream.detector.ProtocolDetector
import com.example.data.stream.model.ContainerFormat
import com.example.data.stream.model.StreamCandidate
import com.example.data.stream.model.StreamProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Service Layer multi-stage validator untuk memverifikasi kelayakan stream media (HLS, DASH, MP4, WebM, MKV).
 * Menggunakan pendekatan 8-tahap non-destruktif:
 * 1. URL Sanity
 * 2. HTTP Status & Connection
 * 3. Redirect Tracking
 * 4. Content-Type Inspection
 * 5. Magic Byte / Manifest Signature Detection
 * 6. Protocol Detection
 * 7. Container Sniffing
 * 8. Playability Confidence Scoring
 */
object StreamValidator {
    private const val TAG = "StreamValidator"

    private val probeClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .build()

    enum class ForbiddenReason {
        NONE,
        TOKEN_EXPIRED,
        REFERER_REQUIRED,
        ORIGIN_REQUIRED,
        COOKIE_REQUIRED,
        WAF_BLOCKED,
        UNKNOWN_403
    }

    // In-memory cache untuk mencegah duplicate network probe pada URL yang sama (TTL 60s)
    private data class CacheEntry(
        val result: ValidationResult,
        val timestamp: Long = System.currentTimeMillis()
    )
    private val validationCache = java.util.concurrent.ConcurrentHashMap<String, CacheEntry>()
    private const val CACHE_TTL_MS = 60_000L

    sealed class ValidationResult {
        data class Valid(
            val mediaType: StreamMediaType,
            val contentType: String,
            val protocol: StreamProtocol = StreamProtocol.UNKNOWN,
            val container: ContainerFormat = ContainerFormat.UNKNOWN,
            val confidence: Float = 0.9f
        ) : ValidationResult()

        data class Invalid(
            val statusCode: Int,
            val reason: String,
            val isTokenExpired: Boolean,
            val isDeadLink: Boolean = false,
            val forbiddenReason: ForbiddenReason = ForbiddenReason.NONE
        ) : ValidationResult()
    }

    /**
     * Memvalidasi stream URL secara asynchronous dengan header khusus.
     */
    suspend fun validateStream(
        url: String,
        headers: Map<String, String> = emptyMap()
    ): ValidationResult = withContext(Dispatchers.IO) {
        // Stage 1: URL sanity
        if (url.isBlank() || (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true))) {
            return@withContext ValidationResult.Invalid(
                statusCode = 0,
                reason = "URL stream tidak valid atau kosong",
                isTokenExpired = false
            )
        }

        // Cache Check (Anti-Duplicate Probe)
        val cached = validationCache[url]
        if (cached != null && (System.currentTimeMillis() - cached.timestamp) < CACHE_TTL_MS) {
            return@withContext cached.result
        }

        try {
            val safeHeaders = headers.filter { (k, _) ->
                !k.equals("Host", ignoreCase = true) &&
                !k.equals("Content-Length", ignoreCase = true)
            }

            // Stage 2: HTTP probe (coba Range probe lebih dulu, bila CDN merespons 416 atau 200 tetap diproses)
            val requestBuilder = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-4096")

            safeHeaders.forEach { (k, v) ->
                if (!k.equals("Range", ignoreCase = true)) {
                    requestBuilder.addHeader(k, v)
                }
            }

            val response = probeClient.newCall(requestBuilder.build()).execute()
            response.use { resp ->
                val code = resp.code
                val contentType = resp.header("Content-Type", "")?.lowercase() ?: ""
                val finalUrl = resp.request.url.toString()

                // Stage 3 & Status check
                if (code == 401 || code == 403) {
                    val serverHeader = resp.header("Server", "") ?: ""
                    val cfRay = resp.header("CF-RAY")
                    val hasReferer = safeHeaders.keys.any { it.equals("Referer", ignoreCase = true) }
                    val hasOrigin = safeHeaders.keys.any { it.equals("Origin", ignoreCase = true) }
                    val hasCookie = safeHeaders.keys.any { it.equals("Cookie", ignoreCase = true) }

                    val (forbiddenReason, reasonText) = when {
                        cfRay != null || serverHeader.contains("cloudflare", ignoreCase = true) ->
                            ForbiddenReason.WAF_BLOCKED to "Cloudflare WAF / Anti-Bot memblokir akses (HTTP $code)"
                        !hasReferer ->
                            ForbiddenReason.REFERER_REQUIRED to "Diperlukan header Referer untuk akses stream (HTTP $code)"
                        !hasOrigin ->
                            ForbiddenReason.ORIGIN_REQUIRED to "Diperlukan header Origin untuk akses stream (HTTP $code)"
                        !hasCookie ->
                            ForbiddenReason.COOKIE_REQUIRED to "Diperlukan Cookie sesi untuk akses stream (HTTP $code)"
                        else ->
                            ForbiddenReason.TOKEN_EXPIRED to "Akses ditolak atau token kedaluwarsa oleh CDN (HTTP $code)"
                    }

                    Log.w(TAG, "Probe HTTP $code on stream: $url -> $reasonText")
                    val res = ValidationResult.Invalid(
                        statusCode = code,
                        reason = reasonText,
                        isTokenExpired = (forbiddenReason == ForbiddenReason.TOKEN_EXPIRED),
                        forbiddenReason = forbiddenReason
                    )
                    validationCache[url] = CacheEntry(res)
                    return@withContext res
                }

                if (code == 404 || code == 410) {
                    Log.w(TAG, "Probe HTTP $code on stream: $url (dead stream link)")
                    val res = ValidationResult.Invalid(
                        statusCode = code,
                        reason = "Stream tidak ditemukan di server (HTTP $code)",
                        isTokenExpired = false,
                        isDeadLink = true
                    )
                    validationCache[url] = CacheEntry(res)
                    return@withContext res
                }

                if (code == 429) {
                    val res = ValidationResult.Invalid(
                        statusCode = 429,
                        reason = "Terlalu banyak permintaan (HTTP 429 Rate Limit)",
                        isTokenExpired = false
                    )
                    validationCache[url] = CacheEntry(res)
                    return@withContext res
                }

                if (code !in 200..299 && code != 206) {
                    val res = ValidationResult.Invalid(
                        statusCode = code,
                        reason = "Server mengembalikan kode error HTTP $code",
                        isTokenExpired = false,
                        isDeadLink = code in 400..499
                    )
                    validationCache[url] = CacheEntry(res)
                    return@withContext res
                }

                // Stage 4: Content-Type Inspection
                var protocol = ProtocolDetector.detect(finalUrl, contentType, "")

                // Stage 5: Bounded body sample (maksimal 4096 bytes) - ANTI-OOM Memory Safety
                val bodyBytes = resp.body?.byteStream()?.use { input ->
                    val buffer = ByteArray(4096)
                    var totalRead = 0
                    while (totalRead < buffer.size) {
                        val read = input.read(buffer, totalRead, buffer.size - totalRead)
                        if (read == -1) break
                        totalRead += read
                    }
                    if (totalRead == buffer.size) buffer else buffer.copyOf(totalRead)
                } ?: byteArrayOf()

                val bodySnippet = if (bodyBytes.isNotEmpty()) {
                    String(bodyBytes, Charsets.UTF_8).take(2048)
                } else ""

                // Deteksi jika respon adalah halaman web HTML (Cloudflare challenge, bot block)
                if (bodySnippet.contains("<!DOCTYPE html", ignoreCase = true) ||
                    bodySnippet.contains("<html", ignoreCase = true)
                ) {
                    Log.w(TAG, "Candidate stream returned HTML web page instead of media: $url")
                    val res = ValidationResult.Invalid(
                        statusCode = code,
                        reason = "Server mengembalikan halaman web HTML, bukan stream media",
                        isTokenExpired = false
                    )
                    validationCache[url] = CacheEntry(res)
                    return@withContext res
                }

                // Stage 6: Protocol detection dari manifest signature
                if (protocol == StreamProtocol.UNKNOWN || protocol == StreamProtocol.PROGRESSIVE) {
                    val detectedFromSample = ProtocolDetector.detect(finalUrl, contentType, bodySnippet)
                    if (detectedFromSample != StreamProtocol.UNKNOWN) {
                        protocol = detectedFromSample
                    }
                }

                // Stage 7: Container sniffing
                val container = ContainerSniffer.sniff(bodyBytes, finalUrl)

                // Stage 8: Confidence & mapping
                val mappedMediaType = when (protocol) {
                    StreamProtocol.HLS -> StreamMediaType.HLS
                    StreamProtocol.DASH -> StreamMediaType.DASH
                    StreamProtocol.PROGRESSIVE -> StreamMediaType.PROGRESSIVE
                    else -> {
                        if (container.isDirectVideo) StreamMediaType.PROGRESSIVE else StreamMediaType.UNKNOWN
                    }
                }

                val res = ValidationResult.Valid(
                    mediaType = mappedMediaType,
                    contentType = contentType.ifEmpty { container.defaultMime },
                    protocol = protocol,
                    container = container,
                    confidence = 0.95f
                )
                validationCache[url] = CacheEntry(res)
                return@withContext res
            }
        } catch (e: Exception) {
            Log.w(TAG, "Stream probe error for $url: ${e.message}")
            // Fallback gracefully bila probe jaringan timeout tapi ekstensi URL jelas
            val lower = url.lowercase()
            val fallbackRes = when {
                lower.contains(".m3u8") -> ValidationResult.Valid(
                    mediaType = StreamMediaType.HLS,
                    contentType = "application/vnd.apple.mpegurl",
                    protocol = StreamProtocol.HLS,
                    container = ContainerFormat.MPEG_TS,
                    confidence = 0.6f
                )
                lower.contains(".mpd") -> ValidationResult.Valid(
                    mediaType = StreamMediaType.DASH,
                    contentType = "application/dash+xml",
                    protocol = StreamProtocol.DASH,
                    container = ContainerFormat.FMP4,
                    confidence = 0.6f
                )
                lower.endsWith(".mp4") || lower.contains(".mp4?") -> ValidationResult.Valid(
                    mediaType = StreamMediaType.PROGRESSIVE,
                    contentType = "video/mp4",
                    protocol = StreamProtocol.PROGRESSIVE,
                    container = ContainerFormat.MP4,
                    confidence = 0.6f
                )
                lower.endsWith(".webm") || lower.contains(".webm?") -> ValidationResult.Valid(
                    mediaType = StreamMediaType.PROGRESSIVE,
                    contentType = "video/webm",
                    protocol = StreamProtocol.PROGRESSIVE,
                    container = ContainerFormat.WEBM,
                    confidence = 0.6f
                )
                lower.endsWith(".mkv") || lower.contains(".mkv?") -> ValidationResult.Valid(
                    mediaType = StreamMediaType.PROGRESSIVE,
                    contentType = "video/x-matroska",
                    protocol = StreamProtocol.PROGRESSIVE,
                    container = ContainerFormat.MATROSKA,
                    confidence = 0.6f
                )
                else -> ValidationResult.Invalid(
                    statusCode = 0,
                    reason = "Probe jaringan gagal: ${e.message}",
                    isTokenExpired = false
                )
            }
            return@withContext fallbackRes
        }
    }

    /**
     * Memvalidasi objek StreamCandidate secara menyeluruh.
     */
    suspend fun validateCandidate(candidate: StreamCandidate): StreamCandidate {
        val result = validateStream(candidate.url, candidate.requestPolicy.toSafeHeaderMap())
        return when (result) {
            is ValidationResult.Valid -> candidate.copy(
                protocol = if (candidate.protocol == StreamProtocol.UNKNOWN) result.protocol else candidate.protocol,
                container = if (candidate.container == ContainerFormat.UNKNOWN) result.container else candidate.container,
                mimeType = result.contentType,
                isValidated = true,
                confidence = result.confidence,
                lastStatusCode = 200
            )
            is ValidationResult.Invalid -> candidate.copy(
                isValidated = false,
                confidence = 0.1f,
                lastStatusCode = result.statusCode
            )
        }
    }
}
