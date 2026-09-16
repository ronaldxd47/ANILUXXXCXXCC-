package com.example.data.stream

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Headless WebView Extractor untuk menangkap network request video secara dinamis
 * pada embed yang menggunakan enkripsi JavaScript runtime, token dinamis, atau anti-bot protection.
 */
object HeadlessStreamExtractor {
    private const val TAG = "HeadlessStreamExtractor"

    suspend fun extractMediaStream(
        context: Context,
        pageUrl: String,
        timeoutMs: Long = 7500L
    ): ResolvedStream? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            var isDone = false
            val handler = Handler(Looper.getMainLooper())
            var webView: WebView? = null

            fun cleanup() {
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

            val timeoutRunnable = Runnable {
                if (continuation.isActive && !isDone) {
                    cleanup()
                    continuation.resumeWith(Result.success(null))
                }
            }
            handler.postDelayed(timeoutRunnable, timeoutMs)

            try {
                webView = WebView(context).apply {
                    setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
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
                            Log.w(TAG, "Render process gone in HeadlessStreamExtractor (didCrash=${detail?.didCrash()})")
                            if (continuation.isActive && !isDone) {
                                handler.post {
                                    if (continuation.isActive && !isDone) {
                                        cleanup()
                                        continuation.resumeWith(Result.success(null))
                                    }
                                }
                            }
                            return true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (isDone) return
                            // Trigger play buttons inside dynamic iframe player safely without hardware media decoding
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
                                if (directSrc.isNotEmpty() && isMediaStreamUrl(directSrc) && continuation.isActive && !isDone) {
                                    val streamMediaType = when {
                                        directSrc.contains(".m3u8", ignoreCase = true) -> StreamMediaType.HLS
                                        directSrc.contains(".mpd", ignoreCase = true) -> StreamMediaType.DASH
                                        else -> StreamMediaType.MP4
                                    }
                                    val resolved = ResolvedStream(
                                        url = directSrc,
                                        mediaType = streamMediaType,
                                        headers = mapOf("Referer" to pageUrl, "User-Agent" to (settings.userAgentString ?: "")),
                                        isDirectVideo = true,
                                        originalIframeUrl = pageUrl
                                    )
                                    handler.post {
                                        if (continuation.isActive && !isDone) {
                                            cleanup()
                                            continuation.resumeWith(Result.success(resolved))
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
                                if (continuation.isActive && !isDone) {
                                    val streamMediaType = when {
                                        reqUrl.contains(".m3u8", ignoreCase = true) -> StreamMediaType.HLS
                                        reqUrl.contains(".mpd", ignoreCase = true) -> StreamMediaType.DASH
                                        else -> StreamMediaType.MP4
                                    }

                                    // Extract session cookies from both request headers and system CookieManager
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

                                    val resolved = ResolvedStream(
                                        url = reqUrl,
                                        mediaType = streamMediaType,
                                        headers = resolvedHeaders,
                                        isDirectVideo = true,
                                        originalIframeUrl = pageUrl
                                    )

                                    handler.post {
                                        if (continuation.isActive && !isDone) {
                                            cleanup()
                                            continuation.resumeWith(Result.success(resolved))
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
                    cleanup()
                    continuation.resumeWith(Result.success(null))
                }
            }

            continuation.invokeOnCancellation {
                cleanup()
            }
        }
    }

    private fun isMediaStreamUrl(url: String): Boolean {
        val lower = url.lowercase()
        // Ignore ads and tracking beacons
        if (lower.contains("googleads") || lower.contains("analytics") || lower.contains("doubleclick") ||
            lower.contains("favicon") || lower.contains(".css") || lower.contains(".jpg") || lower.contains(".png")
        ) {
            return false
        }
        return lower.contains(".m3u8") ||
                lower.contains(".mpd") ||
                lower.contains(".mp4") ||
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
