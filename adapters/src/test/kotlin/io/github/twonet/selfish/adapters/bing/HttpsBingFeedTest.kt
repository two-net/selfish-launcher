package io.github.twonet.selfish.adapters.bing

import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.Market
import io.github.twonet.selfish.domain.ports.FeedEntry
import io.github.twonet.selfish.domain.ports.ImageVariant
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The feed client over scripted connections (DESIGN §5.2): what it sends,
 * which redirects it follows and which responses it refuses. No network.
 */
class HttpsBingFeedTest {
    private class Response(
        val code: Int,
        val body: ByteArray = ByteArray(0),
        val headers: Map<String, String> = emptyMap(),
        /** The `Content-Length` to report; -1 for none, as with a chunked response. */
        val contentLength: Long = body.size.toLong(),
    )

    /** A connection that answers with [response] and records how it was used. */
    private class FakeConnection(url: URL, private val response: Response?) : HttpURLConnection(url) {
        var sentHeaders: Map<String, List<String>> = emptyMap()
            private set
        var bodyRead = false
            private set
        var disconnected = false
            private set

        override fun connect() {
            if (connected) return
            sentHeaders = requestProperties
            connected = true
        }

        override fun getResponseCode(): Int {
            connect()
            return response?.code ?: throw java.net.ConnectException("Nothing scripted for $url")
        }

        override fun getHeaderField(name: String): String? =
            response?.headers?.entries?.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

        override fun getContentLengthLong(): Long = response?.contentLength ?: -1

        override fun getInputStream(): InputStream {
            connect()
            bodyRead = true
            return ByteArrayInputStream(response?.body ?: ByteArray(0))
        }

        override fun disconnect() {
            disconnected = true
        }

        override fun usingProxy() = false
    }

    private val responses = mutableMapOf<String, Response>()
    private val connections = mutableListOf<FakeConnection>()
    private val feed = HttpsBingFeed("1.2.3") { url -> FakeConnection(url, responses[url.toString()]).also { connections += it } }

    private val requested get() = connections.map { it.url.toString() }

    private fun resource(name: String): ByteArray =
        assertNotNull(javaClass.getResource("/bing/$name"), "Missing resource $name").readBytes()

    private val entry = FeedEntry("20261113", "/th?id=OHR.AbrahamCanada_EN-US3455540217", "en-US")
    private val portrait = "https://www.bing.com/th?id=OHR.AbrahamCanada_EN-US3455540217_1080x1920.jpg"
    private val landscape = "https://www.bing.com/th?id=OHR.AbrahamCanada_EN-US3455540217_1920x1080.jpg"
    private val feed8 = "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=en-US"

    @Test
    @Verifies("IF-WP-01", "IF-WP-04")
    fun `the feed is requested for the market and read`() = runBlocking {
        responses[feed8] = Response(200, resource("feed-8.json"))

        val entries = feed.fetchFeed(Market("en-US"), 8).getOrThrow()

        assertEquals(listOf(feed8), requested)
        assertEquals(8, entries.size)
        assertEquals("Hidden depths", entries.first().title)
    }

    @Test
    @Verifies("IF-WP-04")
    fun `no more entries than asked for are returned`() = runBlocking {
        val one = "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=en-US"
        // The feed is assumed to ignore n here; the client still keeps 1.
        responses[one] = Response(200, resource("feed-8.json"))

        val entries = feed.fetchFeed(Market("en-US"), 1).getOrThrow()

        assertEquals(listOf("20261113"), entries.map { it.startDate })
    }

    @Test
    @Verifies("IF-WP-01")
    fun `each image variant is downloaded from its own address`() = runBlocking {
        responses[portrait] = Response(200, byteArrayOf(1, 2, 3))
        responses[landscape] = Response(200, byteArrayOf(4, 5))

        assertContentEquals(byteArrayOf(1, 2, 3), feed.fetchImage(entry, ImageVariant.Portrait).getOrThrow())
        assertContentEquals(byteArrayOf(4, 5), feed.fetchImage(entry, ImageVariant.Landscape).getOrThrow())
        assertEquals(listOf(portrait, landscape), requested)
    }

    @Test
    @Verifies("IF-WP-03")
    fun `requests carry only the launcher's User-Agent, with no cookies and no cache`() = runBlocking {
        responses[feed8] = Response(200, resource("feed-8.json"))
        responses[portrait] = Response(200, byteArrayOf(1))

        feed.fetchFeed(Market("en-US"), 8).getOrThrow()
        feed.fetchImage(entry, ImageVariant.Portrait).getOrThrow()

        for (connection in connections) {
            assertEquals(mapOf("User-Agent" to listOf("SelfishLauncher/1.2.3")), connection.sentHeaders)
            assertFalse(connection.useCaches)
            assertFalse(connection.instanceFollowRedirects)
            assertEquals(15_000, connection.connectTimeout)
            assertEquals(15_000, connection.readTimeout)
            assertTrue(connection.disconnected)
        }
    }

    @Test
    @Verifies("IF-WP-02")
    fun `up to 3 redirects to Bing's hosts are followed`() = runBlocking {
        val hops = listOf(portrait, "https://th.bing.com/a", "https://th.bing.com/b", "https://tse1.mm.bing.net/c")
        hops.zipWithNext { from, to -> responses[from] = Response(302, headers = mapOf("Location" to to)) }
        responses[hops.last()] = Response(200, byteArrayOf(9))

        assertContentEquals(byteArrayOf(9), feed.fetchImage(entry, ImageVariant.Portrait).getOrThrow())
        assertEquals(hops, requested)
    }

    @Test
    @Verifies("IF-WP-02")
    fun `a relative redirect stays on the same host`() = runBlocking {
        responses[portrait] = Response(301, headers = mapOf("location" to "/th?id=OHR.Other_1080x1920.jpg"))
        responses["https://www.bing.com/th?id=OHR.Other_1080x1920.jpg"] = Response(200, byteArrayOf(9))

        assertTrue(feed.fetchImage(entry, ImageVariant.Portrait).isSuccess)
    }

    @Test
    @Verifies("IF-WP-02")
    fun `a fourth redirect fails`() = runBlocking {
        val hops = listOf(portrait, "https://th.bing.com/a", "https://th.bing.com/b", "https://th.bing.com/c", "https://th.bing.com/d")
        hops.zipWithNext { from, to -> responses[from] = Response(307, headers = mapOf("Location" to to)) }
        responses[hops.last()] = Response(200, byteArrayOf(9))

        assertTrue(feed.fetchImage(entry, ImageVariant.Portrait).isFailure)
        assertEquals(hops.dropLast(1), requested, "The fifth address is never requested")
    }

    @Test
    @Verifies("IF-WP-02", "NFR-PRIV-01")
    fun `a redirect to http or to another host fails without being followed`() = runBlocking {
        for (target in listOf("http://www.bing.com/x", "https://example.com/x", "//example.com/x", "https://bing.com.evil.example/x")) {
            connections.clear()
            responses[portrait] = Response(302, headers = mapOf("Location" to target))
            responses[target] = Response(200, byteArrayOf(9))

            assertTrue(feed.fetchImage(entry, ImageVariant.Portrait).isFailure, target)
            assertEquals(listOf(portrait), requested, target)
        }
    }

    @Test
    @Verifies("IF-WP-02")
    fun `a redirect without a location fails`() = runBlocking {
        responses[portrait] = Response(302)

        assertTrue(feed.fetchImage(entry, ImageVariant.Portrait).isFailure)
    }

    @Test
    @Verifies("IF-WP-02", "NFR-PRIV-01")
    fun `an image address that isn't Bing's is never requested`() = runBlocking {
        val elsewhere = entry.copy(urlBase = "//evil.example/th?id=OHR.X")

        assertTrue(feed.fetchImage(elsewhere, ImageVariant.Portrait).isFailure)
        assertEquals(emptyList(), requested)
    }

    @Test
    @Verifies("IF-WP-05")
    fun `an image announced as over 5 MB is refused before it is read`() = runBlocking {
        responses[portrait] = Response(200, contentLength = 5_000_001)

        assertTrue(feed.fetchImage(entry, ImageVariant.Portrait).isFailure)
        assertFalse(connections.single().bodyRead)
    }

    @Test
    @Verifies("IF-WP-05")
    fun `an image over 5 MB without a length is refused as it is read`() = runBlocking {
        responses[portrait] = Response(200, ByteArray(5_000_001), contentLength = -1)
        responses[landscape] = Response(200, ByteArray(5_000_000), contentLength = -1)

        assertTrue(feed.fetchImage(entry, ImageVariant.Portrait).isFailure)
        assertEquals(5_000_000, feed.fetchImage(entry, ImageVariant.Landscape).getOrThrow().size)
    }

    @Test
    @Verifies("IF-WP-05")
    fun `a feed over 256 KB is refused`() = runBlocking {
        responses[feed8] = Response(200, ByteArray(256 * 1024 + 1) { ' '.code.toByte() }, contentLength = -1)

        assertTrue(feed.fetchFeed(Market("en-US"), 8).isFailure)
    }

    @Test
    fun `an error status fails`() = runBlocking {
        for (code in listOf(204, 304, 404, 500, 503)) {
            responses[portrait] = Response(code, byteArrayOf(1))

            assertTrue(feed.fetchImage(entry, ImageVariant.Portrait).isFailure, "HTTP $code")
        }
    }

    @Test
    fun `a connection failure or an unreadable feed is a failed result, not an exception`() = runBlocking {
        assertTrue(feed.fetchImage(entry, ImageVariant.Portrait).isFailure)

        responses[feed8] = Response(200, "<html>Sorry</html>".toByteArray())
        assertTrue(feed.fetchFeed(Market("en-US"), 8).isFailure)
    }
}
