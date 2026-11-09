package io.github.twonet.selfish.domain.state

import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.fakes.FakeAppCatalog
import io.github.twonet.selfish.domain.fakes.FakeLocalStorage
import io.github.twonet.selfish.domain.fakes.FakeOsSettings
import io.github.twonet.selfish.domain.fakes.FakeWallpaperScheduler
import io.github.twonet.selfish.domain.fakes.fakePorts
import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.AppId
import io.github.twonet.selfish.domain.model.AppearanceMode
import io.github.twonet.selfish.domain.model.Bounds
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
import io.github.twonet.selfish.domain.ports.LocalStorage
import io.github.twonet.selfish.domain.ports.NotificationSource
import io.github.twonet.selfish.domain.ports.Ports
import io.github.twonet.selfish.domain.ports.ScheduledRun
import io.github.twonet.selfish.domain.ports.UsageSource
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
     * Replaces the layout and settings at once. No settings action exists
     * yet (#18), so tests dispatch another [Action.Restored]; savers can't
     * tell the difference.
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

        assertEquals(listOf<Pair<AppId, Bounds?>>(maps.id to bounds), catalog.launched)
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
            override suspend fun launch(app: AppId, from: Bounds?) = error("App removed")
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

        assertEquals(LauncherState(), store.state.value)
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
}
