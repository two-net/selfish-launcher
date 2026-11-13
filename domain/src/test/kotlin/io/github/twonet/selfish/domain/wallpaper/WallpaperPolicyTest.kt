package io.github.twonet.selfish.domain.wallpaper

import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.AppliedWallpaper
import io.github.twonet.selfish.domain.model.Market
import io.github.twonet.selfish.domain.model.PixelSize
import io.github.twonet.selfish.domain.model.WallpaperEntry
import io.github.twonet.selfish.domain.model.WallpaperStatus
import io.github.twonet.selfish.domain.ports.FeedEntry
import io.github.twonet.selfish.domain.ports.ImageId
import io.github.twonet.selfish.domain.ports.NetworkState
import io.github.twonet.selfish.domain.ports.WallpaperId
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

/** The wallpaper decisions of DESIGN §4.3, without ports or a device (NFR-MNT-01). */
class WallpaperPolicyTest {
    private val today = LocalDate.of(2026, 11, 13)
    private val now = Instant.parse("2026-11-13T12:00:00Z")

    private fun entry(day: Int, bytes: Int = 500_000, market: String = "en-US") = WallpaperEntry(
        image = ImageId("202611${"%02d".format(day)}_$market"),
        title = "Day $day",
        copyright = "© Day $day",
        link = null,
        date = LocalDate.of(2026, 11, day),
        market = Market(market),
        size = PixelSize(1080, 1920),
        bytes = bytes,
    )

    private fun feed(day: Int, hash: String? = null) =
        FeedEntry(startDate = "202611${"%02d".format(day)}", urlBase = "/th?id=OHR.Day$day", market = "en-US", hash = hash)

    private val online = NetworkState(connected = true, metered = false, dataSaver = false)

    private fun Instant.minus(duration: kotlin.time.Duration): Instant = minus(duration.toJavaDuration())

    // When to check (FR-WP-02, NFR-PWR-01)

    @Test
    @Verifies("FR-WP-02")
    fun `showing Home checks only once the last check is more than 3 hours old`() {
        assertTrue(WallpaperPolicy.due(WallpaperStatus(), CheckTrigger.HomeShown, now, today), "never checked")
        val recent = WallpaperStatus(lastCheck = now.minus(3.hours))
        assertFalse(WallpaperPolicy.due(recent, CheckTrigger.HomeShown, now, today), "exactly 3 hours")
        val stale = WallpaperStatus(lastCheck = now.minus(3.hours + 1.milliseconds))
        assertTrue(WallpaperPolicy.due(stale, CheckTrigger.HomeShown, now, today))
        // A last check in the future means the clock went back.
        assertTrue(WallpaperPolicy.due(WallpaperStatus(lastCheck = now.plusSeconds(60)), CheckTrigger.HomeShown, now, today))
    }

    @Test
    @Verifies("FR-WP-02", "NFR-PWR-01")
    fun `the background job checks on every run, up to 8 checks a day`() {
        val recent = WallpaperStatus(lastCheck = now.minus(10.minutes), day = today, checksToday = 7)
        assertTrue(WallpaperPolicy.due(recent, CheckTrigger.Scheduled, now, today))
        val capped = recent.copy(checksToday = 8)
        assertFalse(WallpaperPolicy.due(capped, CheckTrigger.Scheduled, now, today))
        assertFalse(WallpaperPolicy.due(capped.copy(lastCheck = now.minus(4.hours)), CheckTrigger.HomeShown, now, today))
        // The count is per day.
        assertTrue(WallpaperPolicy.due(capped, CheckTrigger.Scheduled, now, today.plusDays(1)))
    }

    @Test
    @Verifies("NFR-PWR-01")
    fun `the user's own requests always check`() {
        val capped = WallpaperStatus(lastCheck = now, day = today, checksToday = 8)
        assertTrue(WallpaperPolicy.due(capped, CheckTrigger.User, now, today))
    }

    @Test
    @Verifies("FR-WP-06")
    fun `downloads wait offline, under Data Saver or Battery Saver, and on metered networks with Wi-Fi only`() {
        assertFalse(WallpaperPolicy.gated(online, batterySaver = false, wifiOnly = false))
        assertFalse(WallpaperPolicy.gated(online, batterySaver = false, wifiOnly = true))
        assertFalse(WallpaperPolicy.gated(online.copy(metered = true), batterySaver = false, wifiOnly = false))
        assertTrue(WallpaperPolicy.gated(online.copy(metered = true), batterySaver = false, wifiOnly = true))
        assertTrue(WallpaperPolicy.gated(online.copy(connected = false), batterySaver = false, wifiOnly = false))
        assertTrue(WallpaperPolicy.gated(online.copy(dataSaver = true), batterySaver = false, wifiOnly = false))
        assertTrue(WallpaperPolicy.gated(online, batterySaver = true, wifiOnly = false))
    }

    // What to download (FR-WP-02, IF-WP-01, IF-WP-04)

    @Test
    @Verifies("FR-WP-07")
    fun `a first run and a gap ask for 8 entries, otherwise just today's`() {
        assertEquals(8, WallpaperPolicy.feedCount(emptyList(), today))
        assertEquals(1, WallpaperPolicy.feedCount(listOf(entry(13)), today))
        assertEquals(1, WallpaperPolicy.feedCount(listOf(entry(12)), today))
        assertEquals(8, WallpaperPolicy.feedCount(listOf(entry(11), entry(10)), today))
    }

    @Test
    @Verifies("FR-WP-02")
    fun `an image is downloaded only when it differs from the cached ones`() {
        val cached = setOf(ImageId("aaaa"), ImageId("20261112_en-US"))
        val wanted = WallpaperPolicy.toDownload(
            listOf(feed(13, hash = "aaaa"), feed(14, hash = "bbbb"), feed(12), feed(11), feed(14, hash = "bbbb")),
            cached,
        )

        // Newest first, each once.
        assertEquals(listOf("bbbb", "20261111_en-US"), wanted.map { WallpaperPolicy.identity(it)?.value })
    }

    @Test
    fun `an image is identified by its hash, or else its date and market, and only by a safe name`() {
        assertEquals(ImageId("0a1b2c"), WallpaperPolicy.identity(feed(13, hash = "0a1b2c")))
        assertEquals(ImageId("20261113_en-US"), WallpaperPolicy.identity(feed(13)))
        assertEquals(ImageId("20261113_en-US"), WallpaperPolicy.identity(feed(13, hash = "../../etc")))
        assertNull(WallpaperPolicy.identity(FeedEntry("2026/11/13", "/x", "en-US")))
        // Entries without a valid date aren't downloaded.
        assertEquals(emptyList(), WallpaperPolicy.toDownload(listOf(FeedEntry("tomorrow", "/x", "en-US", hash = "cafe")), emptySet()))
    }

    @Test
    fun `a missing title falls back to the copyright text, and only https links are kept`() {
        val item = feed(13).copy(title = " ", copyright = "Lighthouse (© Someone)", link = "http://example.com")
        val made = WallpaperPolicy.entryOf(item, ImageId("x"), PixelSize(1080, 1920), 1000)!!

        assertEquals("Lighthouse (© Someone)", made.title)
        assertNull(made.link)
        assertEquals(LocalDate.of(2026, 11, 13), made.date)
        assertEquals(Market("en-US"), made.market)
        assertEquals("https://www.bing.com/x", WallpaperPolicy.entryOf(item.copy(link = "https://www.bing.com/x"), ImageId("x"), PixelSize(1, 1), 1)!!.link)
    }

    // Retries (FR-WP-11)

    @Test
    @Verifies("FR-WP-11")
    fun `failures retry after 15 minutes, 1 hour and 4 hours, at most 3 times a day`() {
        var status = WallpaperStatus()
        val delays = (1..5).map {
            val after = WallpaperPolicy.afterCheck(status, succeeded = false, now = now, today = today)
            status = after.status
            after.retry
        }

        assertEquals(listOf(15.minutes, 1.hours, 4.hours, null, null), delays)
        assertEquals(5, status.checksToday)
        assertEquals(3, status.retriesToday)
        assertEquals(now, status.lastCheck)
        assertNull(status.lastSuccess)
        // A new day allows retries again, still at the longest delay.
        assertEquals(4.hours, WallpaperPolicy.afterCheck(status, false, now.plusSeconds(86_400), today.plusDays(1)).retry)
    }

    @Test
    @Verifies("FR-WP-11", "FR-WP-09")
    fun `a success records the time and restarts the delays, but not the day's retry count`() {
        val failing = WallpaperStatus(day = today, checksToday = 2, retriesToday = 2, failures = 2)
        val ok = WallpaperPolicy.afterCheck(failing, succeeded = true, now = now, today = today)

        assertNull(ok.retry)
        assertEquals(now, ok.status.lastSuccess)
        assertEquals(0, ok.status.failures)
        assertEquals(3, ok.status.checksToday)
        val next = WallpaperPolicy.afterCheck(ok.status, succeeded = false, now = now, today = today)
        assertEquals(15.minutes, next.retry)
        assertEquals(3, next.status.retriesToday)
        assertNull(WallpaperPolicy.afterCheck(next.status, false, now, today).retry)
    }

    // The cache (FR-WP-07, NFR-PWR-04, NFR-REL-02)

    @Test
    @Verifies("FR-WP-07")
    fun `the cache keeps the 8 newest images`() {
        val entries = (13 downTo 3).map { entry(it) }
        val (kept, evicted) = WallpaperPolicy.evict(entries, protected = setOf(entries.first().image))

        assertEquals((13 downTo 6).map { entry(it) }, kept)
        assertEquals((5 downTo 3).map { entry(it) }, evicted)
    }

    @Test
    @Verifies("FR-WP-07", "FR-WP-08", "NFR-REL-02")
    fun `pinned and applied images are never evicted`() {
        val entries = (13 downTo 1).map { entry(it) }
        val pinned = entry(2).image
        val applied = entry(4).image
        val (kept, _) = WallpaperPolicy.evict(entries, protected = setOf(pinned, applied, entry(13).image))

        assertEquals((13 downTo 6).map { entry(it) } + entry(4) + entry(2), kept)
    }

    @Test
    @Verifies("NFR-PWR-04", "NFR-REL-02")
    fun `the cache stays under 25 MB, dropping the oldest unprotected images first`() {
        val entries = (13 downTo 6).map { entry(it, bytes = 4_900_000) }
        val (kept, evicted) = WallpaperPolicy.evict(entries, protected = setOf(entry(6).image, entry(13).image))

        assertTrue(kept.sumOf { it.bytes.toLong() } < WallpaperPolicy.CACHE_BUDGET_BYTES)
        assertEquals(listOf(entry(13), entry(12), entry(11), entry(10), entry(6)).map { it.image }, kept.map { it.image })
        assertEquals(listOf(9, 8, 7).map { entry(it).image }, evicted.map { it.image })
    }

    @Test
    @Verifies("FR-WP-01", "FR-WP-08")
    fun `the image in use is the pinned one, or else the newest`() {
        val entries = listOf(entry(13), entry(12), entry(11))

        assertEquals(entry(13), WallpaperPolicy.inUse(entries, pinned = null))
        assertEquals(entry(11), WallpaperPolicy.inUse(entries, pinned = entry(11).image))
        assertEquals(entry(13), WallpaperPolicy.inUse(entries, pinned = ImageId("gone")))
    }

    @Test
    @Verifies("FR-WP-12")
    fun `nothing is in use until the first image is cached`() {
        assertNull(WallpaperPolicy.inUse(emptyList(), pinned = null))
    }

    @Test
    fun `new images are merged newest first, and on the same day the new one comes first`() {
        val other = entry(13, market = "de-DE")
        val merged = WallpaperPolicy.merge(listOf(entry(13), entry(12)), listOf(other, entry(12)))

        assertEquals(listOf(other, entry(13), entry(12)), merged)
    }

    @Test
    fun `a status counts per day`() {
        val status = WallpaperStatus(day = today, checksToday = 4, retriesToday = 2, applied = AppliedWallpaper(ImageId("x"), WallpaperId(1)))

        assertEquals(4, status.checksOn(today))
        assertEquals(0, status.checksOn(today.plusDays(1)))
        assertEquals(2, status.retriesOn(today))
        assertEquals(0, status.retriesOn(today.minusDays(1)))
    }
}
