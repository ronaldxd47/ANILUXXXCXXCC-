package com.example.data.stream

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Service Layer untuk validasi integritas media stream (HLS/DASH/MP4).
 * Memeriksa HTTP status, Content-Type, dan byte signature (#EXTM3U / ftyp)
 * sebelum stream diserahkan ke ExoPlayer.
 */
object StreamValidator {
    private const val TAG = "StreamValidator"

    private val probeClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    sealed class ValidationResult {
        data class Valid(
            val mediaType: StreamMediaType,
            val contentType: String
        ) : ValidationResult()

        data class Invalid(
            val statusCode: Int,
            val reason: String,
            val isTokenExpired: Boolean
        ) : ValidationResult()
    }

    /**
     * Memvalidasi candidate URL secara asynchronous (suspend).
     * Melakukan quick Range probe (1KB pertama) untuk memeriksa signature tanpa boros bandwidth.
     */
    suspend fun validateStream(
        url: String,
        headers: Map<String, String>
    ): ValidationResult = withContext(Dispatchers.IO) {
        if (url.isBlank() || !url.startsWith("http")) {
            return@withContext ValidationResult.Invalid(
                statusCode = 0,
                reason = "URL stream tidak valid atau kosong",
                isTokenExpired = false
            )
        }

        try {
            val safeHeaders = headers.filter { (k, _) ->
                !k.equals("Host", ignoreCase = true) &&
                !k.equals("Content-Length", ignoreCase = true)
            }

            // Gunakan Range request agar hanya mengambil potongan awal (manifest header)
            val requestBuilder = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-2048")

            safeHeaders.forEach { (k, v) ->
                if (!k.equals("Range", ignoreCase = true)) {
                    requestBuilder.addHeader(k, v)
                }
            }

            val response = probeClient.newCall(requestBuilder.build()).execute()
            response.use { resp ->
                val code = resp.code
                val contentType = resp.header("Content-Type", "")?.lowercase() ?: ""

                // 1. Periksa HTTP status code
                if (code == 401 || code == 403) {
                    Log.w(TAG, "Probe HTTP $code on stream: $url (token expired/forbidden)")
                    return@withContext ValidationResult.Invalid(
                        statusCode = code,
                        reason = "Akses ditolak oleh server CDN (HTTP $code)",
                        isTokenExpired = true
                    )
                }

                if (code == 429) {
                    return@withContext ValidationResult.Invalid(
                        statusCode = 429,
                        reason = "Terlalu banyak permintaan (HTTP 429 Rate Limit)",
                        isTokenExpired = false
                    )
                }

                if (code !in 200..299) {
                    return@withContext ValidationResult.Invalid(
                        statusCode = code,
                        reason = "Server mengembalikan kode error HTTP $code",
                        isTokenExpired = false
                    )
                }

                // 2. Periksa Content-Type eksplisit
                if (contentType.contains("application/vnd.apple.mpegurl") ||
                    contentType.contains("application/x-mpegurl") ||
                    contentType.contains("audio/x-mpegurl")
                ) {
                    return@withContext ValidationResult.Valid(
                        mediaType = StreamMediaType.HLS,
                        contentType = contentType
                    )
                }

                if (contentType.contains("application/dash+xml")) {
                    return@withContext ValidationResult.Valid(
                        mediaType = StreamMediaType.DASH,
                        contentType = contentType
                    )
                }

                if (contentType.contains("video/mp4") || contentType.contains("video/webm") || contentType.contains("video/mkv")) {
                    return@withContext ValidationResult.Valid(
                        mediaType = StreamMediaType.MP4,
                        contentType = contentType
                    )
                }

                // 3. Fallback: Baca sample byte signature
                val bodyBytes = resp.body?.bytes() ?: byteArrayOf()
                val bodyText = if (bodyBytes.isNotEmpty()) {
                    String(bodyBytes, Charsets.UTF_8).take(1024)
                } else ""

                // Deteksi HLS signature (#EXTM3U)
                if (bodyText.contains("#EXTM3U") || bodyText.contains("#EXT-X-STREAM-INF") || bodyText.contains("#EXT-X-TARGETDURATION")) {
                    Log.d(TAG, "Stream validated via #EXTM3U manifest signature: $url")
                    return@withContext ValidationResult.Valid(
                        mediaType = StreamMediaType.HLS,
                        contentType = contentType.ifEmpty { "application/vnd.apple.mpegurl" }
                    )
                }

                // Deteksi DASH signature (<MPD)
                if (bodyText.contains("<MPD", ignoreCase = true)) {
                    return@withContext ValidationResult.Valid(
                        mediaType = StreamMediaType.DASH,
                        contentType = contentType.ifEmpty { "application/dash+xml" }
                    )
                }

                // Deteksi jika respon adalah HTML error page (Cloudflare challenge, bot block, dsb.)
                if (bodyText.contains("<!DOCTYPE html", ignoreCase = true) || bodyText.contains("<html", ignoreCase = true)) {
                    Log.w(TAG, "Candidate stream returned HTML web page instead of media: $url")
                    return@withContext ValidationResult.Invalid(
                        statusCode = code,
                        reason = "Server mengembalikan halaman web HTML, bukan stream media",
                        isTokenExpired = false
                    )
                }

                // Deteksi MP4 ftyp box signature
                if (bodyBytes.size >= 12) {
                    val boxType = String(bodyBytes.sliceArray(4..7), Charsets.US_ASCII)
                    if (boxType == "ftyp" || boxType == "moov") {
                        return@withContext ValidationResult.Valid(
                            mediaType = StreamMediaType.MP4,
                            contentType = contentType.ifEmpty { "video/mp4" }
                        )
                    }
                }

                // 4. URL Extension heuristic jika Content-Type generik (e.g. application/octet-stream)
                val lowerUrl = url.lowercase()
                return@withContext when {
                    lowerUrl.contains(".m3u8") -> ValidationResult.Valid(StreamMediaType.HLS, contentType)
                    lowerUrl.contains(".mpd") -> ValidationResult.Valid(StreamMediaType.DASH, contentType)
                    lowerUrl.contains(".mp4") || lowerUrl.contains("videoplayback") -> ValidationResult.Valid(StreamMediaType.MP4, contentType)
                    else -> ValidationResult.Valid(StreamMediaType.UNKNOWN, contentType)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Stream probe error for $url: ${e.message}")
            // Jika probe network gagal karena koneksi lambat, jangan langsung blokir jika URL jelas .m3u8
            val lower = url.lowercase()
            return@withContext if (lower.contains(".m3u8")) {
                ValidationResult.Valid(StreamMediaType.HLS, "application/vnd.apple.mpegurl")
            } else if (lower.contains(".mp4")) {
                ValidationResult.Valid(StreamMediaType.MP4, "video/mp4")
            } else {
                ValidationResult.Invalid(
                    statusCode = 0,
                    reason = "Gagal memverifikasi stream: ${e.message}",
                    isTokenExpired = false
                )
            }
        }
    }
}
