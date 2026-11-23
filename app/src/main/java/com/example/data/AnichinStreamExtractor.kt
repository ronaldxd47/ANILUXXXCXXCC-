package com.example.data

import android.content.Context
import com.example.data.stream.StreamResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service Layer khusus untuk Ekstraksi Video Stream (HLS .m3u8 / Direct .mp4).
 * Mendelegasikan seluruh logika resolusi ke [StreamResolver] terpusat agar tidak ada duplikasi kode (DRY).
 */
object AnichinStreamExtractor {
    suspend fun extractStreamUrl(context: Context, iframeUrl: String): String = withContext(Dispatchers.IO) {
        val resolved = StreamResolver.resolve(context, iframeUrl)
        if (resolved.isDirect) resolved.url else ""
    }
}
