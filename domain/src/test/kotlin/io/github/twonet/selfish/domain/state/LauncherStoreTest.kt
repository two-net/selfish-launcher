package io.github.twonet.selfish.domain.state

import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.fakes.FakeAppCatalog
import io.github.twonet.selfish.domain.fakes.FakeWallpaperScheduler
import io.github.twonet.selfish.domain.fakes.fakePorts
import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.AppId
import io.github.twonet.selfish.domain.model.Bounds
import io.github.twonet.selfish.domain.ports.AppCatalog
import io.github.twonet.selfish.domain.ports.NotificationSource
import io.github.twonet.selfish.domain.ports.ScheduledRun
import io.github.twonet.selfish.domain.ports.UsageSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** The store runs on fakes, with the optional sources disabled. */
@Verifies("AR-02", "AR-03", "AR-09")
@OptIn(ExperimentalCoroutinesApi::class)
class LauncherStoreTest {
    private val maps = AppEntry(AppId("com.maps", "Main", 0), "Maps")
    private val clock = AppEntry(AppId("com.clock", "Main", 0), "Clock")

    private val catalog = FakeAppCatalog(listOf(maps))
    private val scheduler = FakeWallpaperScheduler()
    private val ports = fakePorts(appCatalog = catalog, wallpaperScheduler = scheduler)

    private fun TestScope.startedStore() =
        LauncherStore(backgroundScope, ports).also {
            it.start()
            runCurrent()
        }

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
}
