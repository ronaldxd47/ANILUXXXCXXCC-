package com.example.data.stream

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.stream.detector.ProtocolDetector
import com.example.data.stream.model.ContainerFormat
import com.example.data.stream.model.RequestPolicy
import com.example.data.stream.model.StreamCandidate
import com.example.data.stream.model.StreamProtocol
import com.example.data.stream.ranking.CandidateRanker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Headless WebView Extractor untuk mengekstraksi URL video dinamis (HLS, DASH, MP4)
 * yang di-generate via JavaScript/obfuscated players (JWPlayer, Plyr, Clappr, dsb).
 * Dilengkapi isolasi software rendering untuk mencegah crash MESA di emulator,
 * serta pengumpulan multi-candidate stream.
 */
object HeadlessStreamExtractor {
    private const val TAG = "HeadlessStreamExtractor"
    private const val DEFAULT_TIMEOUT_MS = 8500L

    /**
     * Mengekstrak satu ResolvedStream terbaik (backward compatible).
     */
    suspend fun extractMediaStream(
        context: Context,
        pageUrl: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): ResolvedStream? = withContext(Dispatchers.Main) {
        val candidates = extractCandidateStreams(context, pageUrl, timeoutMs)
        val best = candidates.firstOrNull() ?: return@withContext null
        return@withContext best.toResolvedStream()
    }

    /**
     * Mengekstrak seluruh StreamCandidate yang ditemukan selama eksekusi JavaScript halaman web,
     * mengumpulkan cookie sesi dan header request.
     */
    suspend fun extractCandidateStreams(
        context: Context,
        pageUrl: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): List<StreamCandidate> = withContext(Dispatchers.Main) {
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { continuation ->
                var webView: WebView? = null
                var isDone = false
                val handler = Handler(Looper.getMainLooper())
                val collectedCandidates = mutableListOf<StreamCandidate>()

                val cleanup = {
                    if (!isDone) {
                        isDone = true
                        handler.removeCallbacksAndMessages(null)
                        val wv = webView
                        webView = null
                        if (wv != null) {
                            try {
                                wv.stopLoading()
                                wv.webChromeClient = null
                                wv.webViewClient = object : WebViewClient() {}
                                wv.onPause()
                            } catch (e: Exception) {
                                Log.w(TAG, "WebView pause error: ${e.message}")
                            }
                            handler.postDelayed({
                                try {
                                    wv.destroy()
                                } catch (e: Exception) {}
                            }, 300)
                        }
                    }
                }

                handler.postDelayed({
                    if (continuation.isActive && !isDone) {
                        val ranked = CandidateRanker.rankCandidates(collectedCandidates)
                        cleanup()
                        continuation.resumeWith(Result.success(ranked))
                    }
                }, timeoutMs - 500)

                try {
                    webView = WebView(context).apply {
                        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = false
                            allowFileAccess = false
                            allowContentAccess = false
                            blockNetworkImage = true
                            mediaPlaybackRequiresUserGesture = true
                            userAgentString =
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onRenderProcessGone(
                                view: WebView?,
                                detail: RenderProcessGoneDetail?
                            ): Boolean {
                                Log.w(TAG, "Headless WebView render process gone: didCrash=${detail?.didCrash()}")
                                if (continuation.isActive && !isDone) {
                                    val ranked = CandidateRanker.rankCandidates(collectedCandidates)
                                    cleanup()
                                    continuation.resumeWith(Result.success(ranked))
                                }
                                return true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                if (isDone) return
                                
                                val autoPlayJs = """
                                    (function() {
                                        try {
                                            var playBtns = document.querySelectorAll('.jw-video, .jw-button-color, .plyr__control--overlaid, .vjs-big-play-button, .play-button, [aria-label="Play"], #player, iframe, .video-js');
                                            playBtns.forEach(function(btn) { btn.click(); });
                                            var v = document.querySelector('video');
                                            if (v) {
                                                v.muted = true;
                                                return v.src || (v.querySelector('source') ? v.querySelector('source').src : '');
                                            }
                                        } catch(e){}
                                        return '';
                                    })();
                                """.trimIndent()
                                view?.evaluateJavascript(autoPlayJs) { rawSrc ->
                                    val directSrc = rawSrc?.replace("\"", "")?.trim() ?: ""
                                    if (directSrc.isNotEmpty() && isMediaStreamUrl(directSrc)) {
                                        val protocol = ProtocolDetector.detect(directSrc, "", "")
                                        val reqPolicy = RequestPolicy(
                                            referer = pageUrl,
                                            origin = getOrigin(pageUrl),
                                            userAgent = settings.userAgentString
                                        )
                                        val candidate = StreamCandidate(
                                            url = directSrc,
                                            protocol = protocol,
                                            container = if (protocol == StreamProtocol.HLS) ContainerFormat.MPEG_TS else ContainerFormat.MP4,
                                            requestPolicy = reqPolicy,
                                            isDirectVideo = true,
                                            originalPageUrl = pageUrl
                                        )
                                        collectedCandidates.add(candidate)

                                        // Jika master HLS ditemukan dari video DOM, langsung selesaikan
                                        if (protocol == StreamProtocol.HLS && continuation.isActive && !isDone) {
                                            val ranked = CandidateRanker.rankCandidates(collectedCandidates)
                                            handler.post {
                                                if (continuation.isActive && !isDone) {
                                                    cleanup()
                                                    continuation.resumeWith(Result.success(ranked))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val reqUrl = request?.url?.toString() ?: ""
                                val headers = request?.requestHeaders ?: emptyMap()

                                if (isMediaStreamUrl(reqUrl)) {
                                    val protocol = ProtocolDetector.detect(reqUrl, "", "")

                                    val cookieManager = android.webkit.CookieManager.getInstance()
                                    val sysCookies = cookieManager.getCookie(reqUrl) ?: cookieManager.getCookie(pageUrl) ?: ""
                                    val effectiveCookies = headers["Cookie"] ?: sysCookies

                                    val resolvedHeaders = HashMap<String, String>().apply {
                                        put("Referer", headers["Referer"] ?: pageUrl)
                                        put("Origin", headers["Origin"] ?: getOrigin(pageUrl))
                                        put("User-Agent", headers["User-Agent"] ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                                        if (effectiveCookies.isNotBlank()) {
                                            put("Cookie", effectiveCookies)
                                        }
                                        put("Accept", "*/*")
                                    }

                                    val candidate = StreamCandidate(
                                        url = reqUrl,
                                        protocol = protocol,
                                        container = if (protocol == StreamProtocol.HLS) ContainerFormat.MPEG_TS else ContainerFormat.MP4,
                                        requestPolicy = RequestPolicy(
                                            manifestHeaders = resolvedHeaders,
                                            segmentHeaders = resolvedHeaders,
                                            referer = resolvedHeaders["Referer"],
                                            origin = resolvedHeaders["Origin"],
                                            cookie = effectiveCookies.takeIf { it.isNotBlank() },
                                            userAgent = resolvedHeaders["User-Agent"]
                                        ),
                                        isDirectVideo = true,
                                        originalPageUrl = pageUrl
                                    )
                                    collectedCandidates.add(candidate)

                                    // Jika kita mendeteksi manifest HLS master (.m3u8), langsung selesaikan tanpa menunggu timeout
                                    if (reqUrl.contains(".m3u8", ignoreCase = true) && continuation.isActive && !isDone) {
                                        handler.post {
                                            if (continuation.isActive && !isDone) {
                                                val ranked = CandidateRanker.rankCandidates(collectedCandidates)
                                                cleanup()
                                                continuation.resumeWith(Result.success(ranked))
                                            }
                                        }
                                    }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                        }

                        var cleanUrl = pageUrl.trim()
                        if (cleanUrl.startsWith("//")) cleanUrl = "https:$cleanUrl"
                        loadUrl(cleanUrl)
                    }
                } catch (e: Exception) {
                    if (continuation.isActive && !isDone) {
                        val ranked = CandidateRanker.rankCandidates(collectedCandidates)
                        cleanup()
                        continuation.resumeWith(Result.success(ranked))
                    }
                }

                continuation.invokeOnCancellation {
                    cleanup()
                }
            }
        } ?: emptyList()
    }

    private fun isMediaStreamUrl(url: String): Boolean {
        val lower = url.lowercase()
        // Abaikan iklan dan tracking
        if (lower.contains("googleads") || lower.contains("analytics") || lower.contains("doubleclick") ||
            lower.contains("favicon") || lower.contains(".css") || lower.contains(".jpg") || lower.contains(".png")
        ) {
            return false
        }
        return lower.contains(".m3u8") ||
                lower.contains(".mpd") ||
                lower.contains(".mp4") ||
                lower.contains(".webm") ||
                lower.contains(".mkv") ||
                lower.contains("videoplayback") ||
                (lower.contains(".m4s") && !lower.contains("audio"))
    }

    private fun getOrigin(url: String): String {
        return try {
            val uri = android.net.Uri.parse(url)
            if (uri.scheme != null && uri.host != null) "${uri.scheme}://${uri.host}" else url
        } catch (e: Exception) {
            url
        }
    }
}
