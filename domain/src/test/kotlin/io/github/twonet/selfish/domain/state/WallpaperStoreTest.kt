package io.github.twonet.selfish.domain.state

import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.fakes.FakeBingFeed
import io.github.twonet.selfish.domain.fakes.FakeLocalStorage
import io.github.twonet.selfish.domain.fakes.FakeOsSettings
import io.github.twonet.selfish.domain.fakes.FakeSystemWallpaper
import io.github.twonet.selfish.domain.fakes.FakeWallpaperScheduler
import io.github.twonet.selfish.domain.fakes.fakePorts
import io.github.twonet.selfish.domain.model.DownloadOn
import io.github.twonet.selfish.domain.model.Market
import io.github.twonet.selfish.domain.model.PixelSize
import io.github.twonet.selfish.domain.model.Settings
import io.github.twonet.selfish.domain.persist.SettingsDoc
import io.github.twonet.selfish.domain.persist.WallpaperIndexDoc
import io.github.twonet.selfish.domain.ports.BingFeed
import io.github.twonet.selfish.domain.ports.DocName
import io.github.twonet.selfish.domain.ports.FeedEntry
import io.github.twonet.selfish.domain.ports.ImageId
import io.github.twonet.selfish.domain.ports.ImageVariant
import io.github.twonet.selfish.domain.ports.LocalStorage
import io.github.twonet.selfish.domain.ports.Ports
import io.github.twonet.selfish.domain.ports.WallpaperScreens
import io.github.twonet.selfish.domain.wallpaper.Crop
import io.github.twonet.selfish.domain.wallpaper.WallpaperPolicy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

/**
 * Daily Bing wallpaper end to end through the store, with the port fakes
 * and a clock that follows virtual time (DESIGN §4.3).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Verifies("AR-03")
class WallpaperStoreTest {
    private val storage = FakeLocalStorage()
    private val scheduler = FakeWallpaperScheduler()
    private val os = FakeOsSettings()
    private val feed = FakeBingFeed()
    private val system = FakeSystemWallpaper()
    private val ports = fakePorts(localStorage = storage, systemWallpaper = system, bingFeed = feed, wallpaperScheduler = scheduler, osSettings = os)

    /** The first day of every test, 08:00 UTC. */
    private val start = Instant.parse("2026-11-13T08:00:00Z")
    private val firstDay = LocalDate.of(2026, 11, 13)

    private val published = mutableListOf<FeedEntry>()
    private var finishedRuns = 0

    /** While set, image downloads wait for it, so a check can be caught mid-download. */
    private var hold: CompletableDeferred<Unit>? = null
    private val holdingFeed = object : BingFeed by feed {
        override suspend fun fetchImage(entry: FeedEntry, variant: ImageVariant): Result<ByteArray> {
            hold?.await()
            return feed.fetchImage(entry, variant)
        }
    }

    /** The running store's scope; starting another store ends it, as a new process would. */
    private var process: CoroutineScope? = null

    private fun TestScope.clock(): Clock = object : Clock() {
        override fun instant(): Instant = start.plusMillis(testScheduler.currentTime)

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this
    }

    private fun TestScope.startedStore(ports: Ports = this@WallpaperStoreTest.ports, locale: Locale = Locale.US): LauncherStore {
        process?.cancel()
        val scope = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext[Job]))
        process = scope
        return LauncherStore(scope, ports, clock = clock(), locale = { locale }).also { it.start(); runCurrent() }
    }

    private fun label(day: LocalDate) = day.format(DateTimeFormatter.BASIC_ISO_DATE)

    /** Bing publishes [day]'s image, in portrait and landscape. */
    private fun publish(day: LocalDate, portrait: ByteArray? = FakeSystemWallpaper.image(label(day), 1080, 1920)) {
        val item = FeedEntry(
            startDate = label(day),
            urlBase = "/th?id=OHR.${label(day)}",
            market = "en-US",
            hash = "h${label(day)}",
            title = "Image of $day",
            copyright = "© Photographer $day",
            link = "https://www.bing.com/search?q=$day",
        )
        published.add(0, item)
        feed.feed = Result.success(published.toList())
        portrait?.let { feed.images[item.urlBase to ImageVariant.Portrait] = it }
        feed.images[item.urlBase to ImageVariant.Landscape] = FakeSystemWallpaper.image("${label(day)}-wide", 1920, 1080)
    }

    private fun id(day: LocalDate) = ImageId("h${label(day)}")

    /** The system runs the background job. */
    private fun TestScope.runJob() {
        scheduler.trigger { finishedRuns++ }
        runCurrent()
    }

    private fun TestScope.after(duration: Duration) {
        advanceTimeBy(duration)
        runCurrent()
    }

    private fun savedIndex() = WallpaperIndexDoc.decodeOrNull(storage.docs[DocName.WallpaperIndex])

    private val portraitCrop = Crop.center(PixelSize(1080, 1920), PixelSize(1080, 2400))

    // First run and daily changes (FR-WP-01, FR-WP-02, FR-WP-07, AC-03)

    @Test
    @Verifies("FR-WP-01", "FR-WP-07", "FR-WP-04")
    fun `the first run fills the cache and applies the newest image, cropped to the screen`() = runTest {
        for (offset in 2L downTo 0L) publish(firstDay.minusDays(offset))
        val store = startedStore()
        assertTrue(scheduler.periodic)

        runJob()

        assertEquals(listOf(Market("en-US") to 8), feed.feedRequests)
        val state = store.state.value
        assertEquals(listOf(firstDay, firstDay.minusDays(1), firstDay.minusDays(2)), state.wallpapers.map { it.date })
        assertEquals(id(firstDay), state.wallpaper?.image)
        assertEquals("Image of $firstDay", state.wallpaper?.title)
        assertEquals(listOf(FakeSystemWallpaper.Applied(label(firstDay), portraitCrop, WallpaperScreens.Home)), system.applied)
        assertEquals(id(firstDay), state.wallpaperStatus.applied?.image)
        assertEquals(start, state.wallpaperStatus.lastSuccess)
        assertEquals(1, finishedRuns)
        assertEquals(state.wallpaperIndex, savedIndex())
        assertEquals(setOf(id(firstDay), id(firstDay.minusDays(1)), id(firstDay.minusDays(2))), storage.images.keys)
    }

    @Test
    @Verifies("FR-WP-02", "AC-03", "FR-ANIM-10")
    fun `a new image is applied by the job within 3 hours, or when Home shows after 3 hours`() = runTest {
        publish(firstDay)
        val store = startedStore()
        store.dispatch(Action.HomeShown)
        runCurrent()
        // On screen, it waits for Presentation, which applies it once no gesture is in progress.
        assertTrue(system.applied.isEmpty())
        assertEquals(id(firstDay), store.state.value.wallpaperDue?.image)
        store.dispatch(Action.ApplyWallpaperNow)
        runCurrent()
        assertEquals(listOf(label(firstDay)), system.applied.map { it.label })

        // A new image is published. Home shown within 3 hours doesn't check.
        val nextDay = firstDay.plusDays(1)
        publish(nextDay)
        store.dispatch(Action.HomeHidden)
        after(3.hours)
        store.dispatch(Action.HomeShown)
        runCurrent()
        assertEquals(1, feed.feedRequests.size)

        // After 3 hours it does, and the new image is applied after the gesture.
        store.dispatch(Action.HomeHidden)
        after(1.milliseconds)
        store.dispatch(Action.HomeShown)
        runCurrent()
        assertEquals(listOf(Market("en-US") to 1), feed.feedRequests.drop(1))
        assertEquals(id(nextDay), store.state.value.wallpaper?.image)
        store.dispatch(Action.ApplyWallpaperNow)
        runCurrent()
        assertEquals(label(nextDay), system.applied.last().label)

        // In the background the job applies the next one at once.
        val dayAfter = nextDay.plusDays(1)
        publish(dayAfter)
        store.dispatch(Action.HomeHidden)
        after(3.hours)
        runJob()
        assertEquals(label(dayAfter), system.applied.last().label)
        assertEquals(id(dayAfter), store.state.value.wallpaperStatus.applied?.image)
    }

    @Test
    @Verifies("FR-WP-02")
    fun `an image already cached is never downloaded again`() = runTest {
        publish(firstDay)
        val store = startedStore()
        runJob()
        after(3.hours)
        runJob()

        assertEquals(1, feed.imageRequests.size)
        assertEquals(1, system.applied.size)
        assertEquals(2, store.state.value.wallpaperStatus.checksToday)
    }

    // Failures and offline (FR-WP-11, AC-04, NFR-REL-02, NFR-PWR-01)

    @Test
    @Verifies("FR-WP-11", "AC-04", "NFR-REL-02", "NFR-PWR-01")
    fun `three days offline and three days of failures keep the last image, quietly`() = runTest {
        publish(firstDay)
        val store = startedStore()
        runJob()
        val applied = system.applied.toList()
        val lastSuccess = store.state.value.wallpaperStatus.lastSuccess
        assertNotNull(lastSuccess)

        // Every 3 hours the job runs, and the launcher comes on screen and leaves.
        val checksByDay = mutableMapOf<LocalDate, Int>()
        val retriesByDay = mutableMapOf<LocalDate, Int>()
        fun TestScope.threeHoursLater() {
            after(3.hours)
            val day = LocalDate.ofInstant(clock().instant(), ZoneOffset.UTC)
            val checks = feed.feedRequests.size
            val retries = scheduler.retries.size
            runJob()
            store.dispatch(Action.HomeShown)
            runCurrent()
            store.dispatch(Action.HomeHidden)
            runCurrent()
            checksByDay.merge(day, feed.feedRequests.size - checks, Int::plus)
            retriesByDay.merge(day, scheduler.retries.size - retries, Int::plus)
        }

        // Offline: nothing is asked of the network, and nothing counts as a failure.
        os.network = os.network.copy(connected = false)
        repeat(3 * 8) { threeHoursLater() }
        assertEquals(1, feed.feedRequests.size)
        assertTrue(scheduler.retries.isEmpty())

        // Online, but the feed fails: at most 8 checks and 3 retries a day.
        os.network = os.network.copy(connected = true)
        feed.feed = Result.failure(IOException("Bing is down"))
        repeat(3 * 8) { threeHoursLater() }
        assertTrue(checksByDay.values.all { it <= WallpaperPolicy.MAX_CHECKS_PER_DAY }, "checks by day: $checksByDay")
        assertTrue(retriesByDay.values.all { it <= WallpaperPolicy.MAX_RETRIES_PER_DAY }, "retries by day: $retriesByDay")
        assertTrue(scheduler.retries.isNotEmpty())

        val state = store.state.value
        assertEquals(applied, system.applied)
        assertEquals(id(firstDay), state.wallpaper?.image)
        assertEquals(lastSuccess, state.wallpaperStatus.lastSuccess)
        assertEquals(lastSuccess, savedIndex()?.lastSuccess)
        assertEquals(1 + 24 + 24, finishedRuns)
    }

    @Test
    @Verifies("FR-WP-06")
    fun `downloads wait on a metered network with Wi-Fi only, and under Data Saver or Battery Saver`() = runTest {
        publish(firstDay)
        storage.docs[DocName.Settings] = SettingsDoc.encode(Settings(downloadOn = DownloadOn.WifiOnly))
        val store = startedStore()
        assertTrue(scheduler.wifiOnly)

        os.network = os.network.copy(metered = true)
        runJob()
        os.network = os.network.copy(metered = false, dataSaver = true)
        runJob()
        os.network = os.network.copy(dataSaver = false)
        os.batterySaverOn = true
        runJob()
        assertEquals(emptyList(), feed.feedRequests)
        assertNull(store.state.value.wallpaper)

        os.batterySaverOn = false
        runJob()
        assertEquals(id(firstDay), store.state.value.wallpaper?.image)
    }

    // Images (FR-WP-04, FR-WP-14)

    @Test
    @Verifies("FR-WP-14", "FR-WP-04")
    fun `an invalid or oversized portrait falls back to the landscape image`() = runTest {
        publish(firstDay, portrait = "<html>Not found</html>".encodeToByteArray())
        publish(firstDay.plusDays(1), portrait = FakeSystemWallpaper.image("huge", bytes = WallpaperPolicy.MAX_IMAGE_BYTES + 1))
        val store = startedStore()

        runJob()

        val newest = store.state.value.wallpapers.first()
        assertEquals(PixelSize(1920, 1080), newest.size)
        assertEquals(listOf("${label(firstDay.plusDays(1))}-wide"), system.applied.map { it.label })
        assertEquals(Crop.center(PixelSize(1920, 1080), system.screen), system.applied.single().crop)
    }

    @Test
    @Verifies("FR-WP-14", "FR-WP-11")
    fun `an image that isn't valid in any version is never stored or applied`() = runTest {
        publish(firstDay, portrait = ByteArray(0))
        feed.images[published.first().urlBase to ImageVariant.Landscape] = FakeSystemWallpaper.image("truncated").copyOf(5)
        val store = startedStore()

        runJob()

        assertTrue(storage.images.isEmpty())
        assertTrue(system.applied.isEmpty())
        assertNull(store.state.value.wallpaper)
        assertEquals(1, scheduler.retries.size)
    }

    @Test
    @Verifies("FR-WP-11")
    fun `a wallpaper the system refuses leaves the current one, without trying again and again`() = runTest {
        publish(firstDay)
        system.failApply = true
        val store = startedStore()

        runJob()
        runCurrent()

        assertTrue(system.applied.isEmpty())
        assertNull(store.state.value.wallpaperWork.applying)
        assertNull(store.state.value.wallpaperDue)
    }

    @Test
    @Verifies("FR-WP-05")
    fun `with Also use on lock screen the image goes on the lock screen too`() = runTest {
        publish(firstDay)
        storage.docs[DocName.Settings] = SettingsDoc.encode(Settings(lockScreen = true))
        startedStore()

        runJob()

        assertEquals(WallpaperScreens.HomeAndLock, system.applied.single().screens)
        assertNotNull(system.lockCurrent)
    }

    @Test
    @Verifies("FR-WP-03")
    fun `Automatic asks for the device's market, and a chosen market wins`() = runTest {
        publish(firstDay)
        val store = startedStore(locale = Locale.GERMANY)
        runJob()
        assertEquals(Market("de-DE"), feed.feedRequests.last().first)

        store.dispatch(Action.SetMarket(Market("ja-JP")))
        runCurrent()
        assertEquals(Market("ja-JP"), feed.feedRequests.last().first)
    }

    @Test
    @Verifies("FR-WP-12")
    fun `until the first image is downloaded the system wallpaper is left alone`() = runTest {
        val store = startedStore()
        store.dispatch(Action.HomeShown)
        runCurrent()
        store.dispatch(Action.ApplyWallpaperNow)
        runJob()

        assertTrue(system.applied.isEmpty())
        assertNull(store.state.value.wallpaper)
    }

    // Pinning and restarts (FR-WP-08, FR-GEN-07)

    @Test
    @Verifies("FR-WP-08")
    fun `a pinned image stays through new images and a restart, until Daily is chosen`() = runTest {
        publish(firstDay.minusDays(1))
        publish(firstDay)
        val store = startedStore()
        runJob()
        store.dispatch(Action.PinWallpaper(id(firstDay.minusDays(1))))
        runCurrent()
        assertEquals(label(firstDay.minusDays(1)), system.applied.last().label)
        assertEquals(id(firstDay.minusDays(1)), savedIndex()?.pinned)

        // A new process: the pin is restored, and new images don't replace it.
        val restarted = startedStore()
        assertEquals(id(firstDay.minusDays(1)), restarted.state.value.wallpaper?.image)
        publish(firstDay.plusDays(1))
        after(3.hours)
        runJob()
        assertEquals(id(firstDay.plusDays(1)), restarted.state.value.wallpapers.first().image)
        assertEquals(label(firstDay.minusDays(1)), system.applied.last().label)

        restarted.dispatch(Action.ResumeDaily)
        runCurrent()
        assertEquals(label(firstDay.plusDays(1)), system.applied.last().label)
        assertNull(savedIndex()?.pinned)
    }

    @Test
    fun `at start, entries whose image is missing are dropped and images no entry names are deleted`() = runTest {
        publish(firstDay.minusDays(1))
        publish(firstDay)
        startedStore()
        runJob()
        storage.images.remove(id(firstDay.minusDays(1)))
        storage.writeImage(ImageId("stray"), byteArrayOf(1))

        val restarted = startedStore()

        assertEquals(listOf(id(firstDay)), restarted.state.value.wallpapers.map { it.image })
        assertEquals(setOf(id(firstDay)), storage.images.keys)
        assertEquals(listOf(id(firstDay)), savedIndex()?.entries?.map { it.image })
    }

    @Test
    fun `an index that couldn't be read is never written, and no image is deleted for it`() = runTest {
        publish(firstDay)
        startedStore()
        runJob()
        val images = storage.images.keys.toSet()
        val failing = object : LocalStorage by storage {
            override suspend fun read(doc: DocName): String? =
                if (doc == DocName.WallpaperIndex) throw IOException("Busy") else storage.read(doc)
        }
        val writes = storage.writes.size

        val restarted = startedStore(fakePorts(localStorage = failing, systemWallpaper = system, bingFeed = feed, wallpaperScheduler = scheduler, osSettings = os))
        after(1.days)
        publish(firstDay.plusDays(1))
        runJob()

        assertEquals(images + id(firstDay.plusDays(1)), storage.images.keys)
        assertTrue(storage.writes.drop(writes).none { it.first == DocName.WallpaperIndex })
        assertEquals(id(firstDay.plusDays(1)), restarted.state.value.wallpaper?.image)
    }

    // Turning Daily off (FR-WP-13)

    @Test
    @Verifies("FR-WP-13")
    fun `turning Daily off stops a running check, cancels the job and deletes the cache`() = runTest {
        publish(firstDay.minusDays(1))
        val store = startedStore(fakePorts(localStorage = storage, systemWallpaper = system, bingFeed = holdingFeed, wallpaperScheduler = scheduler, osSettings = os))
        runJob()
        val systemBefore = system.applied.toList()
        assertEquals(1, systemBefore.size)

        // The next check is caught mid-download.
        publish(firstDay)
        val gate = CompletableDeferred<Unit>().also { hold = it }
        after(3.hours)
        runJob()
        assertNotNull(store.state.value.wallpaperWork.checking)

        store.dispatch(Action.SetDailyWallpaper(false))
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        assertFalse(scheduler.periodic)
        assertTrue(storage.images.isEmpty())
        assertNull(storage.docs[DocName.WallpaperIndex])
        // The stopped check's run ended too.
        assertEquals(2, finishedRuns)
        // The system wallpaper is left as it is.
        assertEquals(systemBefore, system.applied)
        val state = store.state.value
        assertTrue(state.wallpapers.isEmpty())
        assertNull(state.wallpaper)
        assertFalse(state.showsAmbiences)

        // Nothing runs while it is off, not even the job.
        store.dispatch(Action.HomeShown)
        runJob()
        assertEquals(3, finishedRuns)
        assertNull(storage.docs[DocName.WallpaperIndex])
        assertTrue(storage.images.isEmpty())
        assertEquals(2, feed.feedRequests.size)
    }
}
