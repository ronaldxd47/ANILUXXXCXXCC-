package com.example

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import com.example.data.stream.ResolvedStream
import com.example.data.stream.StreamMediaType
import com.example.ui.PlaybackAction
import com.example.ui.PlaybackEngine
import com.example.ui.PlaybackErrorClassifier
import com.example.ui.PlaybackSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class PlaybackPipelineTest {

    @Test
    fun `test initial playback session state`() {
        val session = PlaybackSession(
            streamUrl = "https://example.com/master.m3u8",
            serverName = "Server 1"
        )
        assertEquals(PlaybackEngine.EXO, session.activeEngine)
        assertTrue(session.canReResolve)
        assertTrue(session.canRetryExo)
        assertTrue(session.canFallbackToWeb)
    }

    @Test
    fun `test error classifier recommends ReResolve on HTTP 403`() {
        val session = PlaybackSession(
            streamUrl = "https://example.com/stream?token=expired",
            serverName = "Server 1"
        )
        val dataSpec = androidx.media3.datasource.DataSpec(android.net.Uri.parse("https://example.com/stream?token=expired"))
        val httpException = HttpDataSource.InvalidResponseCodeException(
            403,
            "Forbidden",
            null,
            emptyMap(),
            dataSpec,
            byteArrayOf()
        )
        val error = PlaybackException(
            "HTTP 403 Forbidden",
            httpException,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
        )

        val action = PlaybackErrorClassifier.classify(error, session)
        assertTrue("Expected ReResolve action on HTTP 403", action is PlaybackAction.ReResolve)
    }

    @Test
    fun `test error classifier recommends FallbackToWeb on malformed manifest`() {
        val session = PlaybackSession(
            streamUrl = "https://example.com/nonstandard.stream",
            serverName = "Server 2"
        )
        val error = PlaybackException(
            "Input does not start with the #EXTM3U header",
            IllegalArgumentException("Cannot find sync byte"),
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED
        )

        val action = PlaybackErrorClassifier.classify(error, session)
        assertTrue("Expected FallbackToWeb action on malformed manifest", action is PlaybackAction.FallbackToWeb)
    }

    @Test
    fun `test error classifier recommends FallbackToWeb on CleartextNotPermitted`() {
        val session = PlaybackSession(
            streamUrl = "http://ok.ru/video/123",
            serverName = "OK.ru"
        )
        val cleartextError = PlaybackException(
            "Cleartext HTTP traffic not permitted",
            java.io.IOException("Cleartext HTTP traffic to ok.ru not permitted"),
            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED
        )

        val action = PlaybackErrorClassifier.classify(cleartextError, session)
        assertTrue("Expected FallbackToWeb action on cleartext error", action is PlaybackAction.FallbackToWeb)
    }

    @Test
    fun `test session prevents infinite fallback loops`() {
        val initialSession = PlaybackSession(
            streamUrl = "https://example.com/stream.m3u8",
            serverName = "Server 1"
        )
        // Switch to web sandbox
        val webSession = initialSession.switchToWeb()
        assertEquals(PlaybackEngine.WEBVIEW, webSession.activeEngine)
        assertEquals(1, webSession.webAttempts)
        // Ensure further fallback is not allowed
        assertEquals(false, webSession.canFallbackToWeb)
    }

    @Test
    fun `test record fresh stream resets exo attempts`() {
        val session = PlaybackSession(
            streamUrl = "https://example.com/stream.m3u8",
            serverName = "Server 1",
            exoAttempts = 2
        )
        val freshStream = ResolvedStream(
            url = "https://example.com/fresh.m3u8",
            mediaType = StreamMediaType.HLS,
            headers = mapOf("Referer" to "https://anichin.vip")
        )
        val updated = session.recordReResolve(freshStream)
        assertEquals(1, updated.reResolveCount)
        assertEquals(0, updated.exoAttempts)
        assertEquals("https://example.com/fresh.m3u8", updated.resolvedStream?.url)
    }
}
