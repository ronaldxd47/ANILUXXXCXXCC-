package com.example.ui.player

import android.graphics.Color as AndroidColor
import android.net.Uri
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

/**
 * Sandboxed Web Player dengan custom User-Agent,
 * penanganan navigasi anti-iklan/intent, injeksi CSS video fullscreen,
 * dan embedded HLS.js HTML5 runtime untuk URL stream langsung.
 */
@Composable
fun WebPlayerView(
    url: String,
    title: String,
    onBack: () -> Unit,
    onSwitchToExo: () -> Unit,
    onOpenServerSelector: () -> Unit,
    onToggleFullscreen: () -> Unit,
    isFullscreen: Boolean,
    headers: Map<String, String> = emptyMap(),
    baseUrl: String? = null,
    modifier: Modifier = Modifier
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember(url) { mutableStateOf(true) }
    var loadProgress by remember(url) { mutableIntStateOf(0) }
    var renderEpoch by remember(url) { mutableIntStateOf(0) }
    var rendererCrashed by remember(url) { mutableStateOf(false) }

    DisposableEffect(url, renderEpoch) {
        onDispose {
            try {
                webViewInstance?.let { wv ->
                    webViewInstance = null
                    wv.stopLoading()
                    wv.webChromeClient = null
                    wv.webViewClient = object : WebViewClient() {}
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    wv.removeAllViews()
                    wv.onPause()
                    wv.postDelayed({
                        try {
                            wv.destroy()
                        } catch (e: Exception) {
                            Log.w("WebPlayerView", "Error destroying WebView: ${e.message}")
                        }
                    }, 150)
                }
            } catch (e: Exception) {
                Log.w("WebPlayerView", "Error disposing WebView: ${e.message}")
            }
            webViewInstance = null
        }
    }

    fun loadTargetIntoWebView(webView: WebView, rawUrl: String) {
        var cleanUrl = rawUrl.trim()
        if (cleanUrl.startsWith("//")) cleanUrl = "https:$cleanUrl"
        if (cleanUrl.isEmpty()) return

        webView.tag = cleanUrl
        isLoading = true

        if (cleanUrl.contains(".m3u8", ignoreCase = true)) {
            val headersJson = JSONObject(headers as Map<*, *>).toString()
            val quotedUrl = JSONObject.quote(cleanUrl)
            val hlsHtml = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                    <style>
                        html, body { margin:0; padding:0; width:100%; height:100%; background:#000; overflow:hidden; display:flex; justify-content:center; align-items:center; }
                        video { width:100%; height:100%; object-fit:contain; }
                    </style>
                    <script src="file:///android_asset/hls.min.js"></script>
                    <script>
                        if (typeof Hls === 'undefined') {
                            var cdnScript = document.createElement('script');
                            cdnScript.src = 'https://cdn.jsdelivr.net/npm/hls.js@1.5.17/dist/hls.min.js';
                            document.head.appendChild(cdnScript);
                        }
                    </script>
                </head>
                <body>
                    <video id="video" controls autoplay playsinline></video>
                    <script>
                        function initHls() {
                            var video = document.getElementById('video');
                            var videoSrc = $quotedUrl;
                            var customHeaders = $headersJson;
                            if (typeof Hls !== 'undefined' && Hls.isSupported()) {
                                if (window.__vibestreamHls) { try { window.__vibestreamHls.destroy(); } catch(e){} }
                                var hls = new Hls({
                                    xhrSetup: function(xhr, url) {
                                        xhr.withCredentials = false;
                                        for (var k in customHeaders) {
                                            if (customHeaders.hasOwnProperty(k)) {
                                                var lk = k.toLowerCase();
                                                if (lk !== 'referer' && lk !== 'user-agent' && lk !== 'origin' && lk !== 'host') {
                                                    try { xhr.setRequestHeader(k, customHeaders[k]); } catch(e){}
                                                }
                                            }
                                        }
                                    }
                                });
                                window.__vibestreamHls = hls;
                                hls.loadSource(videoSrc);
                                hls.attachMedia(video);
                                hls.on(Hls.Events.MANIFEST_PARSED, function() {
                                    video.play().catch(function(){});
                                });
                            } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
                                video.src = videoSrc;
                                video.addEventListener('loadedmetadata', function() {
                                    video.play().catch(function(){});
                                });
                            }
                        }
                        if (typeof Hls !== 'undefined') {
                            initHls();
                        } else {
                            window.addEventListener('load', function() { setTimeout(initHls, 300); });
                        }
                    </script>
                </body>
                </html>
            """.trimIndent()

            val effectiveBaseUrl = when {
                !baseUrl.isNullOrBlank() && (baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) -> baseUrl
                headers["Referer"]?.startsWith("http") == true -> headers["Referer"]!!
                headers["Origin"]?.startsWith("http") == true -> headers["Origin"]!!
                cleanUrl.startsWith("http") -> {
                    try {
                        val uri = Uri.parse(cleanUrl)
                        if (uri.scheme != null && uri.host != null) "${uri.scheme}://${uri.host}" else cleanUrl
                    } catch (_: Exception) {
                        cleanUrl
                    }
                }
                else -> "https://anichin.co"
            }
            webView.loadDataWithBaseURL(effectiveBaseUrl, hlsHtml, "text/html", "UTF-8", null)
        } else {
            if (headers.isNotEmpty()) {
                webView.loadUrl(cleanUrl, headers)
            } else {
                webView.loadUrl(cleanUrl)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        key(url, renderEpoch) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(AndroidColor.BLACK)
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            allowFileAccess = false
                            allowContentAccess = false
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            setSupportZoom(false)
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            userAgentString = headers["User-Agent"]
                                ?: "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadProgress = newProgress
                                if (newProgress >= 85) {
                                    isLoading = false
                                }
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val targetUrl = request?.url?.toString() ?: ""
                                val lower = targetUrl.lowercase()
                                if (lower.startsWith("intent://") || lower.startsWith("market://") ||
                                    lower.startsWith("whatsapp://") || lower.startsWith("tg://") ||
                                    lower.startsWith("javascript:") || lower.startsWith("about:blank")
                                ) {
                                    return true
                                }
                                return false
                            }

                            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                                Log.w("WebPlayerView", "Render process gone (didCrash=${detail?.didCrash()})")
                                isLoading = false
                                webViewInstance = null
                                runCatching {
                                    view?.stopLoading()
                                    (view?.parent as? ViewGroup)?.removeView(view)
                                    view?.destroy()
                                }
                                if (renderEpoch == 0) {
                                    renderEpoch = 1
                                } else {
                                    rendererCrashed = true
                                }
                                return true
                            }

                            override fun onPageFinished(view: WebView?, pageUrl: String?) {
                                super.onPageFinished(view, pageUrl)
                                isLoading = false
                                rendererCrashed = false

                                val injectScript = """
                                    (function() {
                                        try {
                                            var style = document.createElement('style');
                                            style.innerHTML = 'body, html { margin:0; padding:0; background:#000!important; overflow:hidden!important; width:100%!important; height:100%!important; } video, iframe, #player, .player, .jwplayer, .video-js { position:absolute!important; top:0!important; left:0!important; width:100%!important; height:100%!important; object-fit:contain!important; } .header, .footer, .sidebar, .comments, #disqus_thread, [class*="ad"], [id*="ad"], [class*="banner"] { display:none!important; }';
                                            document.head.appendChild(style);

                                            setTimeout(function() {
                                                var v = document.querySelector('video');
                                                if (v) { v.play().catch(function(){}); }
                                                var btns = document.querySelectorAll('.jw-display-icon-container, .vjs-big-play-button, .play-button, [aria-label="Play"], #play');
                                                btns.forEach(function(b) { b.click(); });
                                            }, 600);
                                        } catch(e){}
                                    })();
                                """.trimIndent()
                                view?.evaluateJavascript(injectScript, null)
                            }
                        }

                        loadTargetIntoWebView(this, url)
                        webViewInstance = this
                    }
                },
                update = { webView ->
                    var cleanUrl = url.trim()
                    if (cleanUrl.startsWith("//")) cleanUrl = "https:$cleanUrl"
                    val lastLoaded = webView.tag as? String
                    if (cleanUrl.isNotEmpty() && lastLoaded != cleanUrl) {
                        loadTargetIntoWebView(webView, cleanUrl)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Web Player Top Control Overlay
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                    )
                )
                .padding(horizontal = if (isFullscreen) 14.dp else 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isFullscreen) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                        .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (isFullscreen) "WEB SANDBOX" else "WEB",
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { onSwitchToExo() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.FlashOn,
                            contentDescription = "Beralih ke ExoPlayer",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("ExoPlayer", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                IconButton(
                    onClick = {
                        if (rendererCrashed) {
                            rendererCrashed = false
                            renderEpoch++
                        } else {
                            webViewInstance?.reload()
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Muat Ulang",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (isFullscreen) {
                    IconButton(
                        onClick = onOpenServerSelector,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Filled.Dns,
                            contentDescription = "Pilih Server",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onToggleFullscreen,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        contentDescription = "Layar Penuh",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (rendererCrashed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.86f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "Pemutar web terhenti sementara",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable {
                                    rendererCrashed = false
                                    renderEpoch++
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text("Muat Ulang Web", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                .clickable { onSwitchToExo() }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text("Ganti ke ExoPlayer", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        } else if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(38.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Memuat Pemutar Web ($loadProgress%)...",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
