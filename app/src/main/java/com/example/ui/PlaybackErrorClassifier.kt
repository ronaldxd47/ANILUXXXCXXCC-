package com.example.ui

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource

/**
 * Classifier cerdas untuk menganalisis error pemutar Media3 / ExoPlayer
 * dan menentukan tindakan pemulihan yang tepat (NextCandidate, Re-resolve, Retry, Fallback, atau Fatal Error).
 */
@OptIn(UnstableApi::class)
object PlaybackErrorClassifier {
    private const val TAG = "PlaybackErrorClassifier"

    fun classify(
        error: PlaybackException,
        session: PlaybackSession
    ): PlaybackAction {
        Log.w(TAG, "Classifying PlaybackException: code=${error.errorCodeName} (${error.errorCode}), msg=${error.message}")

        val cause = error.cause
        val causeMsg = cause?.message ?: ""

        // 1. HTTP Status Inspection (401 / 403 / 404 / 429) & Cleartext HTTP
        var httpCode: Int? = null
        if (cause is HttpDataSource.InvalidResponseCodeException) {
            httpCode = cause.responseCode
        }

        if (error.errorCode == PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED ||
            cause is HttpDataSource.CleartextNotPermittedException ||
            causeMsg.contains("Cleartext HTTP traffic", ignoreCase = true)
        ) {
            val nextCand = session.nextCandidate
            return if (nextCand != null && nextCand.url.startsWith("https://")) {
                Log.i(TAG, "Cleartext HTTP error, switching to HTTPS candidate: ${nextCand.url}")
                PlaybackAction.NextCandidate(nextCand, "Mencoba sumber aman (HTTPS) alternatif...")
            } else if (session.canFallbackToWeb) {
                Log.i(TAG, "Cleartext HTTP error detected: recommending Web Sandbox fallback")
                PlaybackAction.FallbackToWeb("Server menggunakan stream HTTP, beralih ke pemutar web...")
            } else {
                PlaybackAction.FatalError("Koneksi HTTP ke server video tidak diizinkan")
            }
        }

        if (httpCode == 404 || httpCode == 410) {
            val nextCand = session.nextCandidate
            return if (nextCand != null) {
                Log.i(TAG, "HTTP $httpCode on active stream, trying next candidate: ${nextCand.url}")
                PlaybackAction.NextCandidate(nextCand, "Sumber video tidak ditemukan ($httpCode). Mencoba sumber alternatif...")
            } else if (session.canFallbackToWeb) {
                Log.i(TAG, "HTTP $httpCode detected, falling back to Web Sandbox embed")
                PlaybackAction.FallbackToWeb("Video direct stream tidak ditemukan (HTTP $httpCode). Mengalihkan ke pemutar web...")
            } else {
                PlaybackAction.FatalError("Video tidak ditemukan di server ini (HTTP $httpCode). Silakan ganti server.")
            }
        }

        if (httpCode == 401 || httpCode == 403 || error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) {
            // Token expired atau CDN security block
            val nextCand = session.nextCandidate
            return if (nextCand != null) {
                Log.i(TAG, "HTTP ${httpCode ?: 403} on active stream, trying next candidate: ${nextCand.url}")
                PlaybackAction.NextCandidate(nextCand, "Server menolak sumber utama, mencoba sumber alternatif...")
            } else if (session.canReResolve) {
                Log.i(TAG, "HTTP ${httpCode ?: 403} detected: recommending Re-resolve for fresh token")
                PlaybackAction.ReResolve("Sesi token video kedaluwarsa (HTTP ${httpCode ?: 403}). Memperbarui URL...")
            } else if (session.canFallbackToWeb) {
                Log.i(TAG, "Re-resolve exhausted, falling back to Web Sandbox")
                PlaybackAction.FallbackToWeb("Server CDN menolak akses langsung (HTTP ${httpCode ?: 403}). Mengalihkan ke pemutar web...")
            } else {
                PlaybackAction.FatalError("Akses stream ditolak oleh penyedia video (HTTP ${httpCode ?: 403})")
            }
        }

        if (httpCode == 429) {
            return if (session.canRetryExo) {
                PlaybackAction.RetryExo(delayMs = 2500L)
            } else {
                PlaybackAction.FatalError("Terlalu banyak permintaan ke server video. Mohon tunggu sesaat.")
            }
        }

        // 2. Manifest & Parser Corrupted (Bukan HLS standar atau format asing)
        if (error.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ||
            error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
            causeMsg.contains("Cannot find sync byte", ignoreCase = true) ||
            causeMsg.contains("Input does not start with the #EXTM3U", ignoreCase = true)
        ) {
            val nextCand = session.nextCandidate
            return if (nextCand != null) {
                Log.i(TAG, "Malformed manifest on active stream, trying next candidate: ${nextCand.url}")
                PlaybackAction.NextCandidate(nextCand, "Format tidak sesuai, mencoba sumber alternatif...")
            } else if (session.canFallbackToWeb) {
                Log.i(TAG, "Malformed manifest or non-standard container: falling back to Web Sandbox")
                PlaybackAction.FallbackToWeb("Format stream membutuhkan pemutar web khusus")
            } else {
                PlaybackAction.FatalError("Format video tidak didukung oleh pemutar sistem")
            }
        }

        // 3. Decoder Failure (Emulator codec / format unhandled)
        if (error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED
        ) {
            val nextCand = session.nextCandidate
            return if (nextCand != null) {
                PlaybackAction.NextCandidate(nextCand, "Decoder perangkat tidak kompatibel, mencoba format lain...")
            } else if (session.canFallbackToWeb) {
                PlaybackAction.FallbackToWeb("Codec perangkat tidak kompatibel, beralih ke web player...")
            } else {
                PlaybackAction.FatalError("Codec video perangkat tidak kompatibel")
            }
        }

        // 4. Behind Live Window (Khusus live HLS stream yang tertinggal buffer)
        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            return PlaybackAction.RetryExo(delayMs = 500L)
        }

        // 5. Network Timeout / Connection Failed
        if (error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
        ) {
            val nextCand = session.nextCandidate
            return if (session.canRetryExo) {
                PlaybackAction.RetryExo(delayMs = 1500L)
            } else if (nextCand != null) {
                PlaybackAction.NextCandidate(nextCand, "Koneksi lambat, mencoba sumber cadangan...")
            } else if (session.canFallbackToWeb) {
                PlaybackAction.FallbackToWeb("Koneksi ExoPlayer lambat, mencoba pemutar web alternatif...")
            } else {
                PlaybackAction.FatalError("Koneksi jaringan internet terputus atau timeout")
            }
        }

        // 6. Generic Fallback
        val nextCand = session.nextCandidate
        return if (nextCand != null) {
            PlaybackAction.NextCandidate(nextCand, "Mencoba sumber video alternatif...")
        } else if (session.canFallbackToWeb) {
            PlaybackAction.FallbackToWeb("Terjadi kendala pemutaran. Beralih ke pemutar web alternatif...")
        } else {
            PlaybackAction.FatalError("Gagal memutar video: ${error.errorCodeName}")
        }
    }
}
