package io.github.twonet.selfish.adapters.bing

import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.Market
import io.github.twonet.selfish.domain.ports.ImageVariant
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Which addresses the feed client may request, and how it builds them (DESIGN §5.2). */
class BingRequestPolicyTest {
    @Test
    @Verifies("IF-WP-01", "IF-WP-03", "IF-WP-04")
    fun `the feed address varies only in the count and the market`() {
        assertEquals(
            URI("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=en-US"),
            BingRequests.feedUrl(Market("en-US"), 8),
        )
        assertEquals(
            URI("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=zh-CN"),
            BingRequests.feedUrl(Market("zh-CN"), 1),
        )
    }

    @Test
    @Verifies("IF-WP-04")
    fun `the feed asks for 1 to 8 entries`() {
        assertTrue(BingRequests.feedUrl(Market("en-US"), 0).toString().contains("&n=1&"))
        assertTrue(BingRequests.feedUrl(Market("en-US"), 20).toString().contains("&n=8&"))
    }

    @Test
    @Verifies("IF-WP-02")
    fun `the market can't change the address`() {
        val url = BingRequests.feedUrl(Market("en-US&n=100#x"), 1)

        assertEquals("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=en-US%26n%3D100%23x", url.toString())
    }

    @Test
    @Verifies("IF-WP-01")
    fun `image addresses are built from urlbase, portrait and landscape`() {
        val base = "/th?id=OHR.AbrahamCanada_EN-US3455540217"

        assertEquals(
            URI("https://www.bing.com/th?id=OHR.AbrahamCanada_EN-US3455540217_1080x1920.jpg"),
            BingRequests.imageUrl(base, ImageVariant.Portrait),
        )
        assertEquals(
            URI("https://www.bing.com/th?id=OHR.AbrahamCanada_EN-US3455540217_1920x1080.jpg"),
            BingRequests.imageUrl(base, ImageVariant.Landscape),
        )
        assertEquals(
            URI("https://th.bing.com/th?id=OHR.X_1080x1920.jpg"),
            BingRequests.imageUrl("https://th.bing.com/th?id=OHR.X", ImageVariant.Portrait),
        )
    }

    @Test
    @Verifies("IF-WP-02", "NFR-PRIV-01")
    fun `an image address that isn't Bing's over https is refused`() {
        for (base in listOf(
            "//evil.example/th?id=OHR.X",
            "http://www.bing.com/th?id=OHR.X",
            "https://bing.com.evil.example/th?id=OHR.X",
            "javascript:alert(1)",
            "/th?id=has space",
        )) {
            assertNull(BingRequests.imageUrl(base, ImageVariant.Portrait), base)
        }
    }

    @Test
    @Verifies("IF-WP-02", "NFR-PRIV-01")
    fun `only https on Bing's hosts is allowed`() {
        for (allowed in listOf(
            "https://www.bing.com/HPImageArchive.aspx",
            "https://bing.com/",
            "https://th.bing.com/th?id=x",
            "https://WWW.BING.COM/x",
            "https://www.bing.com:443/x",
            "https://tse1.mm.bing.net/th?id=x",
            "https://bing.net/x",
        )) {
            assertTrue(BingRequests.allowed(URI(allowed)), allowed)
        }
        for (refused in listOf(
            "http://www.bing.com/x",
            "ftp://www.bing.com/x",
            "https://example.com/x",
            "https://evilbing.com/x",
            "https://bing.com.evil.example/x",
            "https://www.bing.com.evil.example/x",
            "https://bing.co/x",
            "https://user:secret@www.bing.com/x",
            "https://www.bing.com:8443/x",
            "https://127.0.0.1/x",
            "/th?id=x",
            "mailto:someone@bing.com",
        )) {
            assertFalse(BingRequests.allowed(URI(refused)), refused)
        }
    }

    @Test
    @Verifies("IF-WP-02")
    fun `a redirect is followed only to https on Bing's hosts`() {
        val from = URI("https://www.bing.com/th?id=OHR.X_1080x1920.jpg")

        assertEquals(URI("https://www.bing.com/th?id=OHR.Y_1080x1920.jpg"), BingRequests.redirect(from, "/th?id=OHR.Y_1080x1920.jpg"))
        assertEquals(URI("https://th.bing.com/th?id=OHR.X"), BingRequests.redirect(from, "https://th.bing.com/th?id=OHR.X"))
        assertNull(BingRequests.redirect(from, "http://www.bing.com/th?id=OHR.X"))
        assertNull(BingRequests.redirect(from, "https://example.com/"))
        assertNull(BingRequests.redirect(from, "//example.com/"))
        assertNull(BingRequests.redirect(from, "https://bad host/"))
        assertNull(BingRequests.redirect(from, null))
    }

    @Test
    @Verifies("IF-WP-03")
    fun `the User-Agent names only the launcher and its version`() {
        assertEquals("SelfishLauncher/1.2.3", BingRequests.userAgent("1.2.3"))
    }
}
