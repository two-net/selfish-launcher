package io.github.twonet.selfish.domain.state

import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.fakes.FakeAppCatalog
import io.github.twonet.selfish.domain.fakes.FakeLocalStorage
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
import io.github.twonet.selfish.domain.ports.DocName
import io.github.twonet.selfish.domain.ports.LocalStorage
import io.github.twonet.selfish.domain.ports.NotificationSource
import io.github.twonet.selfish.domain.ports.Ports
import io.github.twonet.selfish.domain.ports.ScheduledRun
import io.github.twonet.selfish.domain.ports.UsageSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The store runs on fakes, with the optional sources disabled. */
@Verifies("AR-02", "AR-03", "AR-09")
@OptIn(ExperimentalCoroutinesApi::class)
class LauncherStoreTest {
    private val maps = AppEntry(AppId("com.maps", "Main", 0), "Maps")
    private val clock = AppEntry(AppId("com.clock", "Main", 0), "Clock")

    private val catalog = FakeAppCatalog(listOf(maps))
    private val scheduler = FakeWallpaperScheduler()
    private val storage = FakeLocalStorage()
    private val ports = fakePorts(appCatalog = catalog, localStorage = storage, wallpaperScheduler = scheduler)

    private val savedLayout = Layout(grid = listOf(GridItem.App(clock.id), GridItem.App(maps.id)), home = listOf(maps.id))
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
     * Changes the layout and settings. No editing actions exist yet, so tests
     * dispatch another [Action.Restored]; savers can't tell the difference.
     */
    private fun LauncherStore.change(layout: Layout, settings: Settings) = dispatch(Action.Restored(layout, settings))

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
    @Verifies("FR-GEN-07")
    fun `with nothing saved the layout waits for the first run and nothing is written`() = runTest {
        val state = startedStore().state.value

        assertNull(state.layout)
        assertEquals(Settings(), state.settings)
        assertTrue(state.restored)
        assertEquals(emptyList(), storage.writes)
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

        assertNull(store.state.value.layout)
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

        assertNull(store.state.value.layout)
        assertEquals(savedSettings, store.state.value.settings)
        assertEquals(listOf(maps), store.state.value.apps)

        store.change(changedLayout, changedSettings)
        runCurrent()

        assertEquals(listOf(DocName.Settings), storage.writes.map { it.first })
        assertEquals(savedLayout, LayoutDoc.decodeOrNull(storage.docs[DocName.Layout]))
    }
}
