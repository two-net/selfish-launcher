package io.github.twonet.selfish.domain.state

import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.fakes.FakeAppCatalog
import io.github.twonet.selfish.domain.fakes.FakeBingFeed
import io.github.twonet.selfish.domain.fakes.FakeLocalStorage
import io.github.twonet.selfish.domain.fakes.FakeNotificationSource
import io.github.twonet.selfish.domain.fakes.FakeOsSettings
import io.github.twonet.selfish.domain.fakes.FakeSystemWallpaper
import io.github.twonet.selfish.domain.fakes.FakeWallpaperScheduler
import io.github.twonet.selfish.domain.fakes.fakePorts
import io.github.twonet.selfish.domain.model.Animations
import io.github.twonet.selfish.domain.model.Haptics
import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.AppId
import io.github.twonet.selfish.domain.model.Appearance
import io.github.twonet.selfish.domain.model.AppearanceMode
import io.github.twonet.selfish.domain.model.Bounds
import io.github.twonet.selfish.domain.model.FolderId
import io.github.twonet.selfish.domain.model.GridItem
import io.github.twonet.selfish.domain.model.Layout
import io.github.twonet.selfish.domain.model.Settings
import io.github.twonet.selfish.domain.persist.LayoutDoc
import io.github.twonet.selfish.domain.persist.SettingsDoc
import io.github.twonet.selfish.domain.ports.AppCatalog
import io.github.twonet.selfish.domain.ports.AppIconImage
import io.github.twonet.selfish.domain.ports.ClockInfo
import io.github.twonet.selfish.domain.ports.DefaultApps
import io.github.twonet.selfish.domain.ports.DocName
import io.github.twonet.selfish.domain.ports.FeedEntry
import io.github.twonet.selfish.domain.ports.ImageId
import io.github.twonet.selfish.domain.ports.ImageVariant
import io.github.twonet.selfish.domain.ports.LocalStorage
import io.github.twonet.selfish.domain.ports.NotificationItem
import io.github.twonet.selfish.domain.ports.NotificationSource
import io.github.twonet.selfish.domain.ports.Ports
import io.github.twonet.selfish.domain.ports.ScheduledRun
import io.github.twonet.selfish.domain.ports.SystemScreen
import io.github.twonet.selfish.domain.ports.UsageSource
import io.github.twonet.selfish.domain.remorse.REMORSE_DELAY
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/** The store runs on fakes, with the optional sources disabled. */
@Verifies("AR-02", "AR-03", "AR-09")
@OptIn(ExperimentalCoroutinesApi::class)
class LauncherStoreTest {
    private val maps = AppEntry(AppId("com.maps", "Main", 0), "Maps")
    private val clock = AppEntry(AppId("com.clock", "Main", 0), "Clock")

    private val catalog = FakeAppCatalog(listOf(maps))
    private val scheduler = FakeWallpaperScheduler()
    private val storage = FakeLocalStorage()
    private val os = FakeOsSettings()
    private val ports = fakePorts(appCatalog = catalog, localStorage = storage, wallpaperScheduler = scheduler, osSettings = os)

    // Matches the catalog, so reconciling it at start changes nothing.
    private val savedLayout = Layout(grid = listOf(GridItem.App(maps.id)), home = listOf(maps.id))
    private val savedSettings = Settings(appearance = AppearanceMode.Dark, gridColumns = 5)
    private val changedLayout = Layout(grid = listOf(GridItem.App(maps.id)))
    private val changedSettings = Settings(showClock = false)

    private fun TestScope.startedStore(ports: Ports = this@LauncherStoreTest.ports) =
        LauncherStore(backgroundScope, ports).also {
            it.start()
            runCurrent()
        }

    private fun save(layout: Layout, settings: Settings) {
        storage.docs[DocName.Layout] = LayoutDoc.encode(layout)
        storage.docs[DocName.Settings] = SettingsDoc.encode(settings)
    }

    /**
     * Replaces the layout and settings at once, by dispatching another
     * [Action.Restored]; savers can't tell the difference.
     */
    private fun LauncherStore.change(layout: Layout, settings: Settings) = dispatch(Action.Restored(layout, settings))

    /** The first-run layout for the catalog of [maps] alone, with no default apps. */
    private val firstRunLayout = Layout(grid = listOf(GridItem.App(maps.id)))

    @Test
    fun `optional sources default to disabled`() {
        assertSame(NotificationSource.Disabled, ports.notificationSource)
        assertSame(UsageSource.Disabled, ports.usageSource)
    }

    @Test
    fun `catalog emissions reach the state`() = runTest {
        val store = startedStore()
        assertEquals(listOf(maps), store.state.value.apps)

        catalog.installed.value = listOf(maps, clock)
        runCurrent()

        assertEquals(listOf(clock, maps), store.state.value.apps)
    }

    @Test
    fun `launch actions reach the app catalog`() = runTest {
        val store = startedStore()
        val bounds = Bounds(0, 0, 10, 10)

        store.dispatch(Action.Launch(maps.id, bounds))
        runCurrent()

        assertEquals(listOf(FakeAppCatalog.Launched(maps.id, bounds)), catalog.launched)
    }

    @Test
    @Verifies("FR-ANIM-15", "NFR-A11Y-05")
    fun `the system's removed animations are followed, and launches then don't animate`() = runTest {
        val store = startedStore()
        assertFalse(store.state.value.reducedMotion)

        os.animationsOff.value = true
        runCurrent()
        assertTrue(store.state.value.reducedMotion)

        store.dispatch(Action.Launch(maps.id, null))
        runCurrent()
        assertEquals(listOf(FakeAppCatalog.Launched(maps.id, null, animate = false)), catalog.launched)

        os.animationsOff.value = false
        runCurrent()
        assertFalse(store.state.value.reducedMotion)
    }

    @Test
    @Verifies("FR-THM-02", "FR-THM-03", "AC-01")
    fun `on a fresh install in dark theme the launcher is Dark, and follows the device as it changes`() = runTest {
        os.systemDark.value = true
        val store = startedStore()

        assertTrue(store.state.value.restored)
        assertEquals(AppearanceMode.System, store.state.value.settings.appearance)
        assertEquals(Appearance.Dark, store.state.value.appearance)

        os.systemDark.value = false
        runCurrent()
        assertEquals(Appearance.Light, store.state.value.appearance)
    }

    @Test
    fun `the device's theme is known before the saved state is restored`() = runTest {
        os.systemDark.value = true
        val reading = CompletableDeferred<Unit>()
        val slowStorage = object : LocalStorage by storage {
            override suspend fun read(doc: DocName): String? {
                reading.await()
                return storage.read(doc)
            }
        }
        val store = startedStore(fakePorts(appCatalog = catalog, localStorage = slowStorage, osSettings = os))

        assertFalse(store.state.value.restored)
        assertEquals(Appearance.Dark, store.state.value.appearance)
        reading.complete(Unit)
    }

    @Test
    @Verifies("FR-THM-06")
    fun `an explicit appearance overrides the device and is saved`() = runTest {
        os.systemDark.value = true
        val store = startedStore()

        store.dispatch(Action.SetAppearance(AppearanceMode.Light))
        runCurrent()
        assertEquals(Appearance.Light, store.state.value.appearance)
        assertEquals(AppearanceMode.Light, SettingsDoc.decode(storage.docs[DocName.Settings]).appearance)

        os.systemDark.value = false
        store.dispatch(Action.SetAppearance(AppearanceMode.Dark))
        runCurrent()
        assertEquals(Appearance.Dark, store.state.value.appearance, "Dark while the device is light")
        assertEquals(AppearanceMode.Dark, SettingsDoc.decode(storage.docs[DocName.Settings]).appearance)
    }

    @Test
    @Verifies("FR-GRID-06")
    fun `app info and uninstall reach the app catalog`() = runTest {
        val store = startedStore()

        store.dispatch(Action.OpenAppInfo(maps.id))
        store.dispatch(Action.Uninstall(maps.id))
        runCurrent()

        assertEquals(listOf(maps.id), catalog.openedAppInfo)
        assertEquals(listOf(maps.id), catalog.uninstallRequests)
    }

    @Test
    @Verifies("FR-GEN-05")
    fun `a launch blocks further launches until it times out`() = runTest {
        val store = startedStore()

        store.dispatch(Action.Launch(maps.id, null))
        store.dispatch(Action.Launch(maps.id, null))
        runCurrent()
        assertEquals(1, catalog.launched.size)

        advanceTimeBy(LAUNCH_TIMEOUT - 1.milliseconds)
        runCurrent()
        assertTrue(store.state.value.launching)

        advanceTimeBy(1.milliseconds)
        runCurrent()
        assertFalse(store.state.value.launching)

        store.dispatch(Action.Launch(maps.id, null))
        runCurrent()
        assertEquals(2, catalog.launched.size)
    }

    @Test
    @Verifies("FR-GEST-06", "FR-FOLD-07")
    fun `a folder's removal runs when its timer runs out, even after the launcher is hidden, and is saved`() = runTest {
        catalog.installed.value = listOf(maps, clock)
        val folder = GridItem.Folder(FolderId(1), "Tools", listOf(maps.id, clock.id))
        save(Layout(grid = listOf(folder)), savedSettings)
        val store = startedStore()

        store.dispatch(Action.RemoveFolder(folder.id))
        store.dispatch(Action.HomeHidden)
        advanceTimeBy(REMORSE_DELAY - 1.milliseconds)
        runCurrent()
        assertEquals(listOf<GridItem>(folder), store.state.value.layout!!.grid, "Still waiting")

        advanceTimeBy(1.milliseconds)
        runCurrent()

        val returned = Layout(grid = listOf(GridItem.App(maps.id), GridItem.App(clock.id)))
        assertEquals(returned, store.state.value.layout)
        assertNull(store.state.value.remorse)
        assertEquals(returned, LayoutDoc.decodeOrNull(storage.docs[DocName.Layout]))
    }

    @Test
    @Verifies("FR-GEN-02")
    fun `a first run asks to become the default Home app`() = runTest {
        os.defaultHome = false
        val store = startedStore()

        store.dispatch(Action.HomeShown)
        runCurrent()

        assertEquals(1, os.defaultHomeRequests)
    }

    @Test
    @Verifies("FR-GEN-02")
    fun `no prompt when the launcher already is the default Home app`() = runTest {
        os.defaultHome = true
        val store = startedStore()

        store.dispatch(Action.HomeShown)
        runCurrent()

        assertEquals(0, os.defaultHomeRequests)
    }

    @Test
    @Verifies("FR-GEN-02")
    fun `no prompt after the first run`() = runTest {
        os.defaultHome = false
        save(savedLayout, savedSettings)
        val store = startedStore()

        store.dispatch(Action.HomeShown)
        runCurrent()

        assertEquals(0, os.defaultHomeRequests)
    }

    @Test
    fun `icons come from the app catalog`() = runTest {
        val icon = AppIconImage(1, 1, intArrayOf(0), shaped = true)
        catalog.icons[maps.id] = icon
        val store = startedStore()

        assertSame(icon, store.icon(maps.id, 1))
        assertNull(store.icon(clock.id, 1))
    }

    @Test
    fun `an icon that fails to load is missing, not an error`() = runTest {
        val failing = object : AppCatalog by catalog {
            override suspend fun icon(app: AppId, sizePx: Int): AppIconImage = error("App removed")
        }
        val store = LauncherStore(backgroundScope, fakePorts(appCatalog = failing))

        assertNull(store.icon(maps.id, 1))
    }

    @Test
    fun `scheduled runs are finished`() = runTest {
        startedStore()
        var finished = 0

        scheduler.trigger(ScheduledRun { finished++ })
        runCurrent()

        assertEquals(1, finished)
    }

    @Test
    fun `a failing effect does not stop the store`() = runTest {
        val failing = object : AppCatalog by catalog {
            override suspend fun launch(app: AppId, from: Bounds?, animate: Boolean) = error("App removed")
        }
        val store = LauncherStore(backgroundScope, fakePorts(appCatalog = failing))
        store.start()
        runCurrent()

        store.dispatch(Action.Launch(maps.id, null))
        runCurrent()
        catalog.installed.value = listOf(maps, clock)
        runCurrent()

        assertEquals(listOf(clock, maps), store.state.value.apps)
    }

    @Test
    @Verifies("FR-GEN-07", "FR-GEN-08")
    fun `the saved layout and settings are restored at start`() = runTest {
        save(savedLayout, savedSettings)

        val state = startedStore().state.value

        assertEquals(savedLayout, state.layout)
        assertEquals(savedSettings, state.settings)
        assertTrue(state.restored)
    }

    @Test
    @Verifies("FR-GEN-06", "FR-GEN-07")
    fun `apps that are gone are removed from the saved layout`() = runTest {
        save(Layout(grid = listOf(GridItem.App(clock.id), GridItem.App(maps.id)), home = listOf(clock.id)), savedSettings)

        val store = startedStore()

        val expected = Layout(grid = listOf(GridItem.App(maps.id)))
        assertEquals(expected, store.state.value.layout)
        assertEquals(expected, LayoutDoc.decodeOrNull(storage.docs[DocName.Layout]))
    }

    @Test
    @Verifies("FR-GEN-08")
    fun `a new process opens on Home with the saved layout`() = runTest {
        save(savedLayout, savedSettings)
        val before = startedStore()
        before.dispatch(Action.Restored(changedLayout, savedSettings))
        runCurrent()

        // The system ends the process; a new one builds a new store over the same storage.
        val after = startedStore().state.value

        assertEquals(changedLayout, after.layout)
        assertEquals(Layer.Home, after.layer)
        assertEquals(emptyList(), after.overlays)
    }

    @Test
    @Verifies("FR-GEN-07")
    fun `with nothing saved and no apps yet the layout waits for the first run and nothing is written`() = runTest {
        catalog.installed.value = emptyList()

        val state = startedStore().state.value

        assertNull(state.layout)
        assertEquals(Settings(), state.settings)
        assertTrue(state.restored)
        assertEquals(emptyList(), storage.writes)
    }

    @Test
    @Verifies("FR-HOME-07", "FR-ONB-01", "FR-GEN-07")
    fun `a first run fills Home with the default apps and saves the layout`() = runTest {
        catalog.installed.value = listOf(maps, clock)
        catalog.defaults = DefaultApps(phone = clock.id, camera = AppId("com.gone", "Main", 0))

        val store = startedStore()

        val expected = Layout(grid = listOf(GridItem.App(clock.id), GridItem.App(maps.id)), home = listOf(clock.id))
        assertEquals(expected, store.state.value.layout)
        assertFalse(store.state.value.resolvingDefaults)
        assertEquals(expected, LayoutDoc.decodeOrNull(storage.docs[DocName.Layout]))
        assertEquals(listOf(DocName.Layout), storage.writes.map { it.first })
    }

    @Test
    @Verifies("FR-ONB-01", "FR-ONB-02", "FR-GEN-02", "FR-GRID-05", "FR-HOME-07", "FR-WP-01")
    fun `a first run offers the Home role, lays out the apps, starts the wallpaper download and the tutorial`() = runTest {
        val feed = FakeBingFeed()
        val today = FeedEntry(startDate = "20261113", urlBase = "/th?id=OHR.Today", market = "en-US", hash = "htoday", title = "Today")
        feed.feed = Result.success(listOf(today))
        feed.images[today.urlBase to ImageVariant.Portrait] = FakeSystemWallpaper.image("today", 1080, 1920)
        os.defaultHome = false
        catalog.installed.value = listOf(maps, clock)
        catalog.defaults = DefaultApps(phone = clock.id)
        val store = startedStore(fakePorts(appCatalog = catalog, localStorage = storage, bingFeed = feed, wallpaperScheduler = scheduler, osSettings = os))

        store.dispatch(Action.HomeShown)
        runCurrent()

        val state = store.state.value
        assertEquals(1, os.defaultHomeRequests, "The system's prompt to become the default Home app")
        assertEquals(Layout(grid = listOf(GridItem.App(clock.id), GridItem.App(maps.id)), home = listOf(clock.id)), state.layout, "Alphabetical, with the default apps on Home")
        assertTrue(scheduler.periodic, "The background check is scheduled")
        assertEquals(listOf(8), feed.feedRequests.map { it.second }, "The cache is filled")
        assertEquals(setOf(ImageId("htoday")), storage.images.keys)
        assertEquals(Tutorial(TutorialStep.Apps), state.tutorial)
    }

    @Test
    @Verifies("FR-ONB-02")
    fun `the tutorial isn't saved, so a new process doesn't resume it`() = runTest {
        val first = startedStore()
        first.dispatch(Action.HomeShown)
        runCurrent()
        assertEquals(Tutorial(TutorialStep.Apps), first.state.value.tutorial)

        val next = startedStore()
        next.dispatch(Action.HomeShown)
        runCurrent()

        assertEquals(firstRunLayout, next.state.value.layout)
        assertNull(next.state.value.tutorial)
    }

    @Test
    @Verifies("FR-HOME-07")
    fun `a first run still gets a layout when the default apps can't be looked up`() = runTest {
        val failing = object : AppCatalog by catalog {
            override suspend fun defaultApps(): DefaultApps = error("No package manager")
        }

        val store = startedStore(fakePorts(appCatalog = failing, localStorage = storage))

        assertEquals(firstRunLayout, store.state.value.layout)
    }

    @Test
    @Verifies("FR-HOME-05", "FR-GEN-07")
    fun `Home edits are saved`() = runTest {
        save(savedLayout, savedSettings)
        catalog.installed.value = listOf(maps, clock)
        val store = startedStore()

        store.dispatch(Action.AddToHome(clock.id))
        store.dispatch(Action.MoveCover(clock.id, 0))
        runCurrent()

        assertEquals(listOf(clock.id, maps.id), LayoutDoc.decodeOrNull(storage.docs[DocName.Layout])!!.home)
    }

    @Test
    @Verifies("FR-HOME-02", "NFR-PWR-02")
    fun `the clock is followed only while the launcher is shown`() = runTest {
        fun at(minute: Long) = ClockInfo(Instant.ofEpochSecond(minute * 60), ZoneOffset.UTC, is24Hour = true)
        os.clock.value = at(1)
        val store = startedStore()
        assertNull(store.state.value.clock)

        store.dispatch(Action.HomeShown)
        runCurrent()
        assertEquals(at(1), store.state.value.clock)

        os.clock.value = at(2)
        runCurrent()
        assertEquals(at(2), store.state.value.clock)

        store.dispatch(Action.HomeHidden)
        runCurrent()
        os.clock.value = at(3)
        runCurrent()
        assertEquals(at(2), store.state.value.clock)
        assertEquals(0, os.clock.subscriptionCount.value)

        store.dispatch(Action.HomeShown)
        runCurrent()
        assertEquals(at(3), store.state.value.clock)
    }

    @Test
    @Verifies("FR-GEN-08")
    fun `port events wait for the restore and no scheduled run is lost`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val slowStorage = object : LocalStorage by storage {
            override suspend fun read(doc: DocName): String? {
                gate.await()
                return storage.read(doc)
            }
        }
        val store = startedStore(fakePorts(appCatalog = catalog, localStorage = slowStorage, wallpaperScheduler = scheduler))
        var finished = 0

        scheduler.trigger(ScheduledRun { finished++ })
        runCurrent()

        // Only the device's own settings arrive before the restore, so the
        // first frame is already in its theme (AC-01).
        assertEquals(LauncherState(systemDark = false), store.state.value)
        assertEquals(0, finished)

        gate.complete(Unit)
        runCurrent()

        assertTrue(store.state.value.restored)
        assertEquals(listOf(maps), store.state.value.apps)
        assertEquals(1, finished)
    }

    @Test
    @Verifies("FR-GEN-07")
    fun `changes are saved, and nothing is written at start`() = runTest {
        save(savedLayout, savedSettings)
        val store = startedStore()
        assertEquals(emptyList(), storage.writes)

        store.change(changedLayout, changedSettings)
        runCurrent()

        assertEquals(setOf(DocName.Layout, DocName.Settings), storage.writes.map { it.first }.toSet())
        assertEquals(changedLayout, LayoutDoc.decodeOrNull(storage.docs[DocName.Layout]))
        assertEquals(changedSettings, SettingsDoc.decode(storage.docs[DocName.Settings]))
    }

    @Test
    @Verifies("NFR-REL-03")
    fun `corrupt saved data falls back and is replaced on the next change`() = runTest {
        storage.docs[DocName.Layout] = "{\"grid\":"
        storage.docs[DocName.Settings] = "garbage"
        val store = startedStore()

        // Nothing usable saved is a first run, whose layout replaces the corrupt one.
        assertEquals(firstRunLayout, store.state.value.layout)
        assertEquals(firstRunLayout, LayoutDoc.decodeOrNull(storage.docs[DocName.Layout]))
        assertEquals(Settings(), store.state.value.settings)

        store.change(changedLayout, changedSettings)
        runCurrent()

        assertEquals(changedLayout, LayoutDoc.decodeOrNull(storage.docs[DocName.Layout]))
        assertEquals(changedSettings, SettingsDoc.decode(storage.docs[DocName.Settings]))
    }

    @Test
    @Verifies("NFR-REL-03")
    fun `a document that cannot be read is never overwritten`() = runTest {
        save(savedLayout, savedSettings)
        val failingLayout = object : LocalStorage by storage {
            override suspend fun read(doc: DocName): String? =
                if (doc == DocName.Layout) throw IOException("I/O error") else storage.read(doc)
        }
        val store = startedStore(fakePorts(appCatalog = catalog, localStorage = failingLayout, wallpaperScheduler = scheduler))

        // The launcher stays usable on a first-run layout, which is never saved over the unread file.
        assertEquals(firstRunLayout, store.state.value.layout)
        assertEquals(savedSettings, store.state.value.settings)
        assertEquals(listOf(maps), store.state.value.apps)

        store.change(changedLayout, changedSettings)
        runCurrent()

        assertEquals(listOf(DocName.Settings), storage.writes.map { it.first })
        assertEquals(savedLayout, LayoutDoc.decodeOrNull(storage.docs[DocName.Layout]))
    }

    // Events (FR-EVT-03 to FR-EVT-07, DESIGN §5.5)

    private val notes = FakeNotificationSource()
    private val withNotifications get() = fakePorts(appCatalog = catalog, localStorage = storage, osSettings = os, notificationSource = notes)
    private val message = NotificationItem("k1", "com.chat", 0, "Chat", "Ana", "Hello", 1, 1, clearable = true)

    @Test
    @Verifies("FR-EVT-07", "NFR-PWR-02")
    fun `notification access is checked each time the launcher comes on screen, and never while hidden`() = runTest {
        val store = startedStore(withNotifications)
        assertEquals(0, notes.granted.subscriptionCount.value)
        assertNull(store.state.value.notificationAccess)

        store.dispatch(Action.HomeShown)
        runCurrent()
        assertEquals(1, notes.granted.subscriptionCount.value)
        assertEquals(true, store.state.value.notificationAccess)

        store.dispatch(Action.HomeHidden)
        runCurrent()
        assertEquals(0, notes.granted.subscriptionCount.value)
    }

    @Test
    @Verifies("FR-EVT-04", "NFR-PRIV-05", "NFR-PWR-02")
    fun `notifications are followed only while shown, turned on and granted`() = runTest {
        notes.notifications.value = listOf(message)
        val store = startedStore(withNotifications)
        store.dispatch(Action.HomeShown)
        runCurrent()
        assertEquals(0, notes.notifications.subscriptionCount.value, "Off")

        store.dispatch(Action.TurnOnNotifications)
        runCurrent()
        assertEquals(1, notes.notifications.subscriptionCount.value)
        assertEquals(listOf(message), store.state.value.notifications)

        store.dispatch(Action.HomeHidden)
        runCurrent()
        assertEquals(0, notes.notifications.subscriptionCount.value)
        assertEquals(listOf(message), store.state.value.notifications, "Kept for the return")
    }

    @Test
    @Verifies("FR-EVT-07", "FR-ONB-04")
    fun `turning on without access opens the access screen, and turns on once it is granted`() = runTest {
        notes.granted.value = false
        val store = startedStore(withNotifications)
        store.dispatch(Action.HomeShown)
        runCurrent()

        store.dispatch(Action.TurnOnNotifications)
        store.dispatch(Action.HomeHidden)
        runCurrent()
        assertEquals(1, notes.accessRequests)

        notes.granted.value = true
        store.dispatch(Action.HomeShown)
        runCurrent()

        assertTrue(store.state.value.notificationsOn)
        assertEquals(true, SettingsDoc.decode(storage.docs[DocName.Settings]).notificationsInEvents)
    }

    @Test
    @Verifies("FR-SET-12", "FR-ONB-04")
    fun `the Settings switch explains first, and asks for access only on Continue`() = runTest {
        notes.granted.value = false
        save(savedLayout, savedSettings)
        val store = startedStore(withNotifications)
        store.dispatch(Action.HomeShown)
        store.dispatch(Action.OpenSettings)
        runCurrent()

        store.dispatch(Action.TurnOnFeature(AccessFeature.NotificationsInEvents))
        runCurrent()
        assertEquals(0, notes.accessRequests)

        store.dispatch(Action.RequestAccess(AccessFeature.NotificationsInEvents))
        runCurrent()
        assertEquals(1, notes.accessRequests)
    }

    @Test
    @Verifies("FR-ONB-04")
    fun `revoked access turns notifications off, and that is saved`() = runTest {
        save(savedLayout, Settings(notificationsInEvents = true))
        val store = startedStore(withNotifications)
        store.dispatch(Action.HomeShown)
        runCurrent()
        assertTrue(store.state.value.notificationsOn)

        notes.granted.value = false
        runCurrent()

        assertFalse(store.state.value.settings.notificationsInEvents)
        assertEquals(false, SettingsDoc.decode(storage.docs[DocName.Settings]).notificationsInEvents)
    }

    @Test
    @Verifies("FR-EVT-04", "FR-EVT-05")
    fun `opening and dismissing reach the notification source`() = runTest {
        save(savedLayout, Settings(notificationsInEvents = true))
        notes.notifications.value = listOf(message)
        val store = startedStore(withNotifications)
        store.dispatch(Action.HomeShown)
        runCurrent()
        val bounds = Bounds(0, 0, 10, 10)

        store.dispatch(Action.OpenNotification("k1", bounds))
        store.dispatch(Action.DismissNotification("k1"))
        runCurrent()

        assertEquals(listOf(FakeNotificationSource.Opened("k1", bounds)), notes.opened)
        assertEquals(listOf(setOf(message.ref)), notes.dismissed)
        assertEquals(emptyList(), store.state.value.notifications)
    }

    @Test
    @Verifies("FR-EVT-06", "FR-GEST-06")
    fun `clearing runs when its timer runs out, even after the launcher is hidden`() = runTest {
        save(savedLayout, Settings(notificationsInEvents = true))
        notes.notifications.value = listOf(message)
        val store = startedStore(withNotifications)
        store.dispatch(Action.HomeShown)
        runCurrent()

        store.dispatch(Action.ClearNotifications)
        store.dispatch(Action.HomeHidden)
        advanceTimeBy(REMORSE_DELAY - 1.milliseconds)
        runCurrent()
        assertEquals(emptyList(), notes.dismissed, "Still waiting")

        advanceTimeBy(1.milliseconds)
        runCurrent()

        assertEquals(listOf(setOf(message.ref)), notes.dismissed)
        assertEquals(emptyList(), notes.notifications.value)
    }

    @Test
    @Verifies("FR-EVT-03")
    fun `Learn more reaches the app catalog, and the card's image is read from storage`() = runTest {
        val store = startedStore()
        // Written after the start, which deletes images the cache index doesn't name.
        storage.writeImage(ImageId("today"), byteArrayOf(1, 2, 3))

        store.dispatch(Action.OpenLink("https://www.bing.com/"))
        runCurrent()

        assertEquals(listOf<SystemScreen>(SystemScreen.Link("https://www.bing.com/")), catalog.openedScreens)
        assertEquals(listOf<Byte>(1, 2, 3), store.wallpaperImage(ImageId("today"))?.toList())
        assertNull(store.wallpaperImage(ImageId("missing")))
    }

    @Test
    @Verifies("FR-TOP-04")
    fun `the System settings shortcut reaches the app catalog`() = runTest {
        val store = startedStore()

        store.dispatch(Action.OpenSystemSettings)
        runCurrent()

        assertEquals(listOf<SystemScreen>(SystemScreen.SystemSettings), catalog.openedScreens)
    }

    @Test
    @Verifies("FR-TOP-02", "FR-GEN-07")
    fun `the appearance chosen in the Top Menu is saved`() = runTest {
        save(savedLayout, Settings())
        val store = startedStore()

        store.dispatch(Action.SetAppearance(AppearanceMode.Light))
        runCurrent()

        assertEquals(AppearanceMode.Light, SettingsDoc.decode(storage.docs[DocName.Settings]).appearance)
    }

    @Test
    @Verifies("FR-SET-07", "FR-SET-09", "FR-SET-14", "FR-SET-15", "FR-GEN-07")
    fun `the settings changed in Settings are saved at once`() = runTest {
        save(savedLayout, Settings())
        val store = startedStore()

        store.dispatch(Action.OpenSettings)
        store.dispatch(Action.SetShowClock(false))
        store.dispatch(Action.SetGridColumns(3))
        store.dispatch(Action.SetHaptics(Haptics.Off))
        store.dispatch(Action.SetAnimations(Animations.Reduced))
        runCurrent()

        val expected = Settings(showClock = false, gridColumns = 3, haptics = Haptics.Off, animations = Animations.Reduced)
        assertEquals(expected, SettingsDoc.decode(storage.docs[DocName.Settings]))
    }

    @Test
    @Verifies("FR-SET-16", "FR-GEST-06", "FR-GEN-07")
    fun `Reset layout runs when its timer runs out, brings back the first-run layout with the default apps, and is saved`() = runTest {
        catalog.installed.value = listOf(maps, clock)
        catalog.defaults = DefaultApps(phone = clock.id)
        val custom = Layout(grid = listOf(GridItem.Folder(FolderId(1), "Tools", listOf(maps.id, clock.id))), home = listOf(maps.id))
        save(custom, savedSettings)
        val store = startedStore()

        store.dispatch(Action.ResetLayout)
        store.dispatch(Action.HomeHidden)
        advanceTimeBy(REMORSE_DELAY - 1.milliseconds)
        runCurrent()
        assertEquals(custom, store.state.value.layout, "Still waiting")

        advanceTimeBy(1.milliseconds)
        runCurrent()

        val firstRun = Layout(grid = listOf(GridItem.App(clock.id), GridItem.App(maps.id)), home = listOf(clock.id))
        assertEquals(firstRun, store.state.value.layout)
        assertFalse(store.state.value.resettingLayout)
        assertEquals(firstRun, LayoutDoc.decodeOrNull(storage.docs[DocName.Layout]))
        assertEquals(savedSettings, store.state.value.settings, "Settings are kept")
    }
}
