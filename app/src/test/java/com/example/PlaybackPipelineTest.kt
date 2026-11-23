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

    @Test
    fun `test protocol detector identifies HLS manifest signature and URL`() {
        val sampleHls = "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-STREAM-INF:BANDWIDTH=800000\nchunklist.m3u8"
        val detected = com.example.data.stream.detector.ProtocolDetector.detect(
            url = "https://cdn.example.com/live/playlist.m3u8",
            sampleContent = sampleHls
        )
        assertEquals(com.example.data.stream.model.StreamProtocol.HLS, detected)
    }

    @Test
    fun `test container sniffer recognizes MP4 ftyp box`() {
        val fakeMp4Header = byteArrayOf(0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70, 0x69, 0x73, 0x6F, 0x6D) // 'ftyp'
        val format = com.example.data.stream.detector.ContainerSniffer.sniff(fakeMp4Header, "https://cdn.example.com/video")
        assertEquals(com.example.data.stream.model.ContainerFormat.MP4, format)
    }

    @Test
    fun `test container sniffer recognizes Matroska EBML header`() {
        val ebmlHeader = byteArrayOf(0x1A.toByte(), 0x45.toByte(), 0xDF.toByte(), 0xA3.toByte(), 0x01, 0x00)
        val format = com.example.data.stream.detector.ContainerSniffer.sniff(ebmlHeader, "https://cdn.example.com/video.mkv")
        assertEquals(com.example.data.stream.model.ContainerFormat.MATROSKA, format)
    }

    @Test
    fun `test candidate ranker penalizes ads and prioritizes direct HLS`() {
        val adCandidate = com.example.data.stream.model.StreamCandidate(
            url = "https://googleads.g.doubleclick.net/pagead/ads?video=1",
            protocol = com.example.data.stream.model.StreamProtocol.PROGRESSIVE
        )
        val hlsCandidate = com.example.data.stream.model.StreamCandidate(
            url = "https://stream.anichin.vip/hls/master.m3u8",
            protocol = com.example.data.stream.model.StreamProtocol.HLS,
            isDirectVideo = true,
            isValidated = true
        )
        val ranked = com.example.data.stream.ranking.CandidateRanker.rankCandidates(listOf(adCandidate, hlsCandidate))
        assertEquals(1, ranked.size)
        assertEquals(hlsCandidate.url, ranked.first().url)
    }

    @Test
    fun `test error classifier suggests NextCandidate before re-resolving or web fallback`() {
        val cand1 = com.example.data.stream.model.StreamCandidate(
            url = "https://server1.com/stream.m3u8",
            protocol = com.example.data.stream.model.StreamProtocol.HLS
        )
        val cand2 = com.example.data.stream.model.StreamCandidate(
            url = "https://server2.com/backup.m3u8",
            protocol = com.example.data.stream.model.StreamProtocol.HLS
        )
        val session = PlaybackSession(
            streamUrl = cand1.url,
            serverName = "Server 1",
            candidates = listOf(cand1, cand2),
            currentCandidateIndex = 0
        )
        val dataSpec = androidx.media3.datasource.DataSpec(android.net.Uri.parse(cand1.url))
        val http403 = HttpDataSource.InvalidResponseCodeException(
            403, "Forbidden", null, emptyMap(), dataSpec, byteArrayOf()
        )
        val error = PlaybackException("HTTP 403", http403, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)

        val action = PlaybackErrorClassifier.classify(error, session)
        assertTrue("Expected NextCandidate action", action is PlaybackAction.NextCandidate)
        assertEquals(cand2.url, (action as PlaybackAction.NextCandidate).candidate.url)
    }

    @Test
    fun `test candidate ranker disqualifies dead links with 404 or 410`() {
        val deadCandidate = com.example.data.stream.model.StreamCandidate(
            url = "https://cdn.example.com/dead.m3u8",
            protocol = com.example.data.stream.model.StreamProtocol.HLS,
            lastStatusCode = 404
        )
        val validCandidate = com.example.data.stream.model.StreamCandidate(
            url = "https://cdn.example.com/alive.m3u8",
            protocol = com.example.data.stream.model.StreamProtocol.HLS,
            isValidated = true,
            lastStatusCode = 200
        )
        val ranked = com.example.data.stream.ranking.CandidateRanker.rankCandidates(listOf(deadCandidate, validCandidate))
        assertEquals(1, ranked.size)
        assertEquals("https://cdn.example.com/alive.m3u8", ranked.first().url)
    }

    @Test
    fun `test error classifier handles 404 specifically by advancing candidate`() {
        val cand1 = com.example.data.stream.model.StreamCandidate(
            url = "https://server1.com/dead.m3u8",
            protocol = com.example.data.stream.model.StreamProtocol.HLS
        )
        val cand2 = com.example.data.stream.model.StreamCandidate(
            url = "https://server2.com/backup.m3u8",
            protocol = com.example.data.stream.model.StreamProtocol.HLS
        )
        val session = PlaybackSession(
            streamUrl = cand1.url,
            serverName = "Server 1",
            candidates = listOf(cand1, cand2),
            currentCandidateIndex = 0
        )
        val dataSpec = androidx.media3.datasource.DataSpec(android.net.Uri.parse(cand1.url))
        val http404 = HttpDataSource.InvalidResponseCodeException(
            404, "Not Found", null, emptyMap(), dataSpec, byteArrayOf()
        )
        val error = PlaybackException("HTTP 404", http404, PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)

        val action = PlaybackErrorClassifier.classify(error, session)
        assertTrue("Expected NextCandidate action on 404", action is PlaybackAction.NextCandidate)
        assertEquals(cand2.url, (action as PlaybackAction.NextCandidate).candidate.url)
    }

    @Test
    fun `test stream candidate preserves container and protocol across conversions`() {
        val candidate = com.example.data.stream.model.StreamCandidate(
            url = "https://cdn.example.com/video.webm",
            protocol = com.example.data.stream.model.StreamProtocol.PROGRESSIVE,
            container = com.example.data.stream.model.ContainerFormat.WEBM,
            mimeType = "video/webm",
            isDirectVideo = true
        )
        val resolved = candidate.toResolvedStream()
        assertEquals(StreamMediaType.PROGRESSIVE, resolved.mediaType)
        assertEquals(com.example.data.stream.model.StreamProtocol.PROGRESSIVE, resolved.protocol)
        assertEquals(com.example.data.stream.model.ContainerFormat.WEBM, resolved.container)
        assertTrue(resolved.isProgressive)
        assertTrue(resolved.isDirect)

        val reconstructed = com.example.data.stream.model.StreamCandidate.fromResolvedStream(resolved)
        assertEquals(com.example.data.stream.model.StreamProtocol.PROGRESSIVE, reconstructed.protocol)
        assertEquals(com.example.data.stream.model.ContainerFormat.WEBM, reconstructed.container)
    }

    @Test
    fun `test url token redaction masks sensitive credentials`() {
        val rawUrl = "https://cdn.example.com/stream.m3u8?token=SECRET_ABCD1234&expires=1726000000&id=42"
        val redacted = com.example.ui.PlayerManager.redactUrl(rawUrl)
        assertTrue("Token should be masked", redacted.contains("token=REDACTED"))
        assertTrue("Expires should be masked", redacted.contains("expires=REDACTED"))
        assertTrue("Normal query params should remain", redacted.contains("id=42"))
    }

    @Test
    fun `test playback router selects EXO for direct protocols and WEBVIEW for embeds`() {
        val hlsCandidate = com.example.data.stream.model.StreamCandidate(
            url = "https://cdn.example.com/master.m3u8",
            protocol = com.example.data.stream.model.StreamProtocol.HLS,
            isDirectVideo = true
        )
        val embedCandidate = com.example.data.stream.model.StreamCandidate(
            url = "https://stream.example.com/embed/abc",
            protocol = com.example.data.stream.model.StreamProtocol.WEB_EMBED,
            isDirectVideo = false
        )
        assertEquals(
            PlaybackEngine.EXO,
            com.example.playback.router.PlaybackRouter.route(hlsCandidate)
        )
        assertEquals(
            PlaybackEngine.WEBVIEW,
            com.example.playback.router.PlaybackRouter.route(embedCandidate)
        )

        val resolvedIframe = ResolvedStream(
            url = "https://stream.example.com/embed/abc",
            mediaType = StreamMediaType.IFRAME
        )
        val resolvedProgressive = ResolvedStream(
            url = "https://cdn.example.com/video.mp4",
            mediaType = StreamMediaType.PROGRESSIVE,
            protocol = com.example.data.stream.model.StreamProtocol.PROGRESSIVE
        )
        assertEquals(
            PlaybackEngine.WEBVIEW,
            com.example.playback.router.PlaybackRouter.route(resolvedIframe)
        )
        assertEquals(
            PlaybackEngine.EXO,
            com.example.playback.router.PlaybackRouter.route(resolvedProgressive)
        )
    }

    @Test
    fun `test anime source detects donghive samehadaku anichin and unknown urls`() {
        assertEquals(
            com.example.data.AnimeSource.ANICHIN,
            com.example.data.AnimeSource.fromUrl("https://anichin.cafe/renegade-immortal")
        )
        assertEquals(
            com.example.data.AnimeSource.DONGHUB,
            com.example.data.AnimeSource.fromUrl("https://donghive.com/soul-land")
        )
        assertEquals(
            com.example.data.AnimeSource.DONGHUB,
            com.example.data.AnimeSource.fromUrl("https://donghub.org/btth")
        )
        assertEquals(
            com.example.data.AnimeSource.SAMEHADAKU,
            com.example.data.AnimeSource.fromUrl("https://samehadaku.email/one-piece")
        )
        assertEquals(
            com.example.data.AnimeSource.UNKNOWN,
            com.example.data.AnimeSource.fromUrl("https://example.org/other")
        )
    }

    @Test
    fun `test ttl memory cache reuses cached value and respects forceRefresh`() = kotlinx.coroutines.runBlocking {
        val cache = com.example.data.cache.TtlMemoryCache<String, String>(defaultTtlMs = 60_000L)
        var fetchCount = 0

        val first = cache.getOrFetch("home") {
            fetchCount++
            "payload-1"
        }
        val second = cache.getOrFetch("home") {
            fetchCount++
            "payload-2"
        }
        assertEquals("payload-1", first)
        assertEquals("payload-1", second)
        assertEquals(1, fetchCount)

        val refreshed = cache.getOrFetch("home", forceRefresh = true) {
            fetchCount++
            "payload-3"
        }
        assertEquals("payload-3", refreshed)
        assertEquals(2, fetchCount)
    }

    @Test
    fun `test blogger extractor sorts highest quality first and attaches subtitles`() {
        val html = """
            <html>
              <script>
                var VIDEO_CONFIG = {
                  "streams": [
                    {"format_id": 18, "play_url": "https://cdn.example.com/video_360.mp4"},
                    {"format_id": 22, "play_url": "https://cdn.example.com/video_720.mp4"}
                  ]
                };
              </script>
              <video>
                <track kind="subtitles" src="/subs/ep1_id.vtt" srclang="id" label="Indonesia" />
              </video>
            </html>
        """.trimIndent()
        val policy = com.example.data.stream.model.RequestPolicy(
            referer = "https://www.blogger.com/video.g"
        )
        val extractor = com.example.data.stream.extractor.BloggerExtractor()
        assertTrue(extractor.canHandle("https://www.blogger.com/video.g", html))

        val candidates = extractor.extract(
            pageUrl = "https://www.blogger.com/video.g",
            html = html,
            serverName = "Blogger 720p",
            requestPolicy = policy
        )
        assertEquals(2, candidates.size)
        assertEquals("720p", candidates[0].quality.label)
        assertEquals("360p", candidates[1].quality.label)
        assertEquals(1, candidates[0].subtitles.size)
        assertEquals("https://www.blogger.com/subs/ep1_id.vtt", candidates[0].subtitles.first().url)
        assertEquals("text/vtt", candidates[0].subtitles.first().mimeType)
    }

    @Test
    fun `test packed hoster extractor unpacks obfuscated script and extracts hls stream`() {
        // eval(function(p,a,c,k,e,d){...}('0://1.2/3.4',10,5,'https|cdn|streamwish|master|m3u8'.split('|'),0,{}))
        val html = """
            <script>
            eval(function(p,a,c,k,e,d){return p}('0://1.2/3.4',10,5,'https|cdn|streamwish|master|m3u8'.split('|'),0,{}))
            </script>
        """.trimIndent()
        val policy = com.example.data.stream.model.RequestPolicy(referer = "https://streamwish.to/e/abc")
        val extractor = com.example.data.stream.extractor.PackedHosterExtractor()
        assertTrue(extractor.canHandle("https://streamwish.to/e/abc", html))

        val candidates = extractor.extract(
            pageUrl = "https://streamwish.to/e/abc",
            html = html,
            serverName = "Streamwish",
            requestPolicy = policy
        )
        assertTrue(candidates.any { it.url == "https://cdn.streamwish/master.m3u8" })
        assertEquals(
            com.example.data.stream.model.StreamProtocol.HLS,
            candidates.first { it.url == "https://cdn.streamwish/master.m3u8" }.protocol
        )
    }

    @Test
    fun `test hoster registry extracts html5 media and preserves subtitle tracks across conversions`() {
        val html = """
            <html>
              <body>
                <video controls>
                  <source src="/streams/episode10.mp4" type="video/mp4" />
                  <track kind="subtitles" src="/subs/ep10_indo.srt" srclang="id" label="Indonesia" />
                  <track kind="captions" src="https://cdn.example.com/subs/ep10_en.vtt" srclang="en" label="English" />
                </video>
              </body>
            </html>
        """.trimIndent()
        val policy = com.example.data.stream.model.RequestPolicy(referer = "https://player.example.com/watch/10")

        val candidates = com.example.data.stream.extractor.HosterExtractorRegistry.extractAll(
            pageUrl = "https://player.example.com/watch/10",
            html = html,
            serverName = "Direct MP4",
            requestPolicy = policy
        )
        assertEquals(1, candidates.size)
        val candidate = candidates.first()
        assertEquals("https://player.example.com/streams/episode10.mp4", candidate.url)
        assertEquals(2, candidate.subtitles.size)
        assertEquals("application/x-subrip", candidate.subtitles[0].mimeType)
        assertEquals("text/vtt", candidate.subtitles[1].mimeType)

        val resolved = candidate.toResolvedStream()
        assertEquals(2, resolved.subtitles.size)
        assertEquals("Indonesia", resolved.subtitles[0].label)

        val restored = com.example.data.stream.model.StreamCandidate.fromResolvedStream(resolved)
        assertEquals(2, restored.subtitles.size)
        assertEquals("https://cdn.example.com/subs/ep10_en.vtt", restored.subtitles[1].url)
    }
}
