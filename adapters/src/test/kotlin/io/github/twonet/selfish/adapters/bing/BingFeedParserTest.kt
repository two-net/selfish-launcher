package io.github.twonet.selfish.adapters.bing

import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.Market
import io.github.twonet.selfish.domain.model.PixelSize
import io.github.twonet.selfish.domain.ports.FeedEntry
import io.github.twonet.selfish.domain.wallpaper.WallpaperPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Reading the feed (DESIGN §5.2), against feeds recorded from Bing in
 * `resources/bing/` and a hand-written one with fields missing.
 */
class BingFeedParserTest {
    private fun resource(name: String): String =
        assertNotNull(javaClass.getResource("/bing/$name"), "Missing resource $name").readText()

    private val enUs = Market("en-US")

    @Test
    @Verifies("IF-WP-01", "IF-WP-04")
    fun `a recorded feed of 8 days gives 8 entries, newest first, with every field`() {
        val entries = BingFeedParser.parse(resource("feed-8.json"), enUs)

        assertEquals(
            listOf("20261113", "20261112", "20261111", "20261110", "20261109", "20261108", "20261107", "20261106"),
            entries.map { it.startDate },
        )
        assertEquals(
            FeedEntry(
                startDate = "20261113",
                urlBase = "/th?id=OHR.AbrahamCanada_EN-US3455540217",
                market = "en-US",
                hash = "80d628c13f88010af5b53fab50a8473f",
                title = "Hidden depths",
                copyright = "Abraham Lake in Alberta, Canada (© Guang Cao/Getty Images)",
                link = entries.first().link,
            ),
            entries.first(),
        )
        assertTrue(entries.first().link!!.startsWith("https://www.bing.com/search?q=Abraham+Lake+Alberta"))
        for (entry in entries) {
            assertEquals("en-US", entry.market)
            assertTrue(entry.urlBase.startsWith("/th?id=OHR."), entry.urlBase)
            assertNotNull(entry.hash)
            assertNotNull(entry.title)
            assertNotNull(entry.copyright)
            assertNotNull(entry.link)
        }
        assertEquals(8, entries.mapNotNull { WallpaperPolicy.identity(it) }.toSet().size, "Each day has its own identity")
    }

    @Test
    @Verifies("IF-WP-01")
    fun `a recorded feed of 1 day gives that day, in the market asked for`() {
        val entries = BingFeedParser.parse(resource("feed-1.json"), Market("de-DE"))

        val entry = entries.single()
        assertEquals("20261112", entry.startDate)
        assertEquals("/th?id=OHR.AbrahamCanada_DE-DE1158881043", entry.urlBase)
        assertEquals("de-DE", entry.market)
        assertEquals("9e64cc7d2daae700b94079ffb9a177ef", entry.hash)
        assertEquals("Verborgene Tiefen", entry.title)
    }

    @Test
    @Verifies("IF-WP-06")
    fun `missing and malformed optional fields are tolerated`() {
        val entries = BingFeedParser.parse(resource("feed-missing-fields.json"), enUs).associateBy { it.urlBase }

        assertEquals(FeedEntry("20261101", "/th?id=OHR.Bare_EN-US1", "en-US"), entries["/th?id=OHR.Bare_EN-US1"])
        assertEquals(
            FeedEntry("20261103", "/th?id=OHR.WrongTypes_EN-US3", "en-US"),
            entries["/th?id=OHR.WrongTypes_EN-US3"],
            "Fields of the wrong type count as missing",
        )
        val blank = assertNotNull(entries["/th?id=OHR.Blank_EN-US4"])
        assertEquals(null, blank.title, "A blank title counts as missing")
        assertEquals("Trimmed copyright", blank.copyright)
    }

    @Test
    @Verifies("IF-WP-06")
    fun `without urlbase the image address comes from url`() {
        val entry = BingFeedParser.parse(resource("feed-missing-fields.json"), enUs).single { it.startDate == "20261105" }

        assertEquals("/th?id=OHR.UrlOnly_EN-US2", entry.urlBase)
        assertEquals("aaaabbbbccccddddeeeeffff00001111", entry.hash)
    }

    @Test
    @Verifies("IF-WP-06")
    fun `entries without a start date or a Bing image address are left out, the rest sorted newest first`() {
        val entries = BingFeedParser.parse(resource("feed-missing-fields.json"), enUs)

        assertEquals(listOf("20261105", "20261103", "20261102", "20261101"), entries.map { it.startDate })
    }

    @Test
    @Verifies("IF-WP-06")
    fun `without a title the copyright text is shown, and only an https link is kept`() {
        val entry = BingFeedParser.parse(resource("feed-missing-fields.json"), enUs).single { it.startDate == "20261105" }
        val image = assertNotNull(WallpaperPolicy.identity(entry))

        val shown = assertNotNull(WallpaperPolicy.entryOf(entry, image, PixelSize(1080, 1920), 1))

        assertEquals("A place without a title (© Someone)", shown.title)
        assertEquals(null, shown.link)
    }

    @Test
    @Verifies("IF-WP-06")
    fun `an empty feed has no entries`() {
        assertEquals(emptyList(), BingFeedParser.parse("""{"images":[]}""", enUs))
    }

    @Test
    fun `a body that isn't the feed fails`() {
        for (body in listOf("", "<html>Sorry</html>", "[]", "{}", """{"images":{}}""", "null")) {
            assertFailsWith<Exception>(body) { BingFeedParser.parse(body, enUs) }
        }
    }
}
