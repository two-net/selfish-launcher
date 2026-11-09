package io.github.twonet.selfish.domain.state

import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.AppId
import io.github.twonet.selfish.domain.model.AppearanceMode
import io.github.twonet.selfish.domain.model.Bounds
import io.github.twonet.selfish.domain.model.FolderId
import io.github.twonet.selfish.domain.model.GridItem
import io.github.twonet.selfish.domain.model.Layout
import io.github.twonet.selfish.domain.model.Settings
import io.github.twonet.selfish.domain.ports.ScheduledRun
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@Verifies("AR-02")
class LauncherReducerTest {
    private fun app(label: String) = AppEntry(AppId("pkg.$label", "Main", 0), label)

    @Test
    fun `catalog changes replace the apps in alphabetical order`() {
        val next = reduce(LauncherState(), Action.CatalogChanged(listOf(app("Maps"), app("calendar"), app("Clock"))))

        assertEquals(listOf("calendar", "Clock", "Maps"), next.state.apps.map { it.label })
        assertEquals(emptyList(), next.effects)
    }

    @Test
    @Verifies("FR-GEN-05")
    fun `a launch starts the app and a timeout`() {
        val state = LauncherState(apps = listOf(app("Maps")))
        val bounds = Bounds(1, 2, 3, 4)

        val next = reduce(state, Action.Launch(app("Maps").id, bounds))

        assertEquals(state.copy(launching = true, launches = 1), next.state)
        assertEquals(
            listOf(Effect.LaunchApp(app("Maps").id, bounds), Effect.DispatchAfter(LAUNCH_TIMEOUT, Action.LaunchTimedOut(1))),
            next.effects,
        )
    }

    @Test
    @Verifies("FR-GEN-05")
    fun `taps while an app is launching launch nothing`() {
        val launching = reduce(LauncherState(), Action.Launch(app("Maps").id, null)).state

        val again = reduce(launching, Action.Launch(app("Maps").id, null))
        val other = reduce(launching, Action.Launch(app("Clock").id, null))

        assertSame(launching, again.state)
        assertEquals(emptyList(), again.effects)
        assertSame(launching, other.state)
        assertEquals(emptyList(), other.effects)
    }

    @Test
    @Verifies("FR-GEN-05")
    fun `hiding the launcher ends the launch`() {
        val launching = reduce(LauncherState(homeShown = true), Action.Launch(app("Maps").id, null)).state

        val hidden = reduce(launching, Action.HomeHidden).state

        assertFalse(hidden.launching)
        assertEquals(1, reduce(hidden, Action.Launch(app("Maps").id, null)).effects.filterIsInstance<Effect.LaunchApp>().size)
    }

    @Test
    @Verifies("FR-GEN-05")
    fun `the timeout ends the launch`() {
        val launching = reduce(LauncherState(), Action.Launch(app("Maps").id, null)).state

        assertFalse(reduce(launching, Action.LaunchTimedOut(1)).state.launching)
    }

    @Test
    @Verifies("FR-GEN-05")
    fun `a stale timeout does not end a newer launch`() {
        var state = reduce(LauncherState(), Action.Launch(app("Maps").id, null)).state
        state = reduce(state, Action.HomeHidden).state
        state = reduce(state, Action.Launch(app("Clock").id, null)).state

        val next = reduce(state, Action.LaunchTimedOut(1))

        assertSame(state, next.state)
        assertTrue(next.state.launching)
    }

    @Test
    fun `a scheduled run is finished`() {
        val run = ScheduledRun { }

        val next = reduce(LauncherState(), Action.ScheduledRunDue(run))

        assertEquals(listOf<Effect>(Effect.FinishRun(run)), next.effects)
    }

    @Test
    fun `restoring sets the saved layout and settings`() {
        val layout = Layout(grid = listOf(GridItem.App(app("Maps").id)), home = listOf(app("Maps").id))
        val settings = Settings(appearance = AppearanceMode.Dark)

        val next = reduce(LauncherState(apps = listOf(app("Maps"))), Action.Restored(layout, settings))

        assertEquals(LauncherState(apps = listOf(app("Maps")), layout = layout, settings = settings, restored = true), next.state)
        assertEquals(emptyList(), next.effects)
    }

    @Test
    fun `restoring without a saved layout leaves it for the first run`() {
        val next = reduce(LauncherState(), Action.Restored(null, Settings()))

        assertEquals(null, next.state.layout)
        assertEquals(true, next.state.restored)
        assertEquals(true, next.state.firstRun)
    }

    // Navigation (FR-GEN-03, FR-GEN-04)

    private val folder = Overlay.Folder(FolderId(1))
    private val otherFolder = Overlay.Folder(FolderId(2))

    @Test
    @Verifies("FR-GEN-04")
    fun `back closes the topmost overlay, then the layer`() {
        var state = LauncherState(layer = Layer.AppGrid, overlays = listOf(folder, otherFolder))

        state = reduce(state, Action.Back).state
        assertEquals(LauncherState(layer = Layer.AppGrid, overlays = listOf(folder)), state)

        state = reduce(state, Action.Back).state
        assertEquals(LauncherState(layer = Layer.AppGrid), state)

        state = reduce(state, Action.Back).state
        assertEquals(LauncherState(layer = Layer.Home), state)
    }

    @Test
    @Verifies("FR-GEN-04")
    fun `back closes every layer to Home`() {
        for (layer in listOf(Layer.AppGrid, Layer.Events, Layer.TopMenu)) {
            assertEquals(Layer.Home, reduce(LauncherState(layer = layer), Action.Back).state.layer)
        }
    }

    @Test
    @Verifies("FR-GEN-04")
    fun `back on Home does nothing`() {
        val home = LauncherState(apps = listOf(app("Maps")))

        val next = reduce(home, Action.Back)

        assertSame(home, next.state)
        assertEquals(emptyList(), next.effects)
    }

    @Test
    @Verifies("FR-GEN-03")
    fun `home closes every overlay and layer at once`() {
        val state = LauncherState(layer = Layer.AppGrid, overlays = listOf(folder, otherFolder))

        val next = reduce(state, Action.HomePressed)

        assertEquals(LauncherState(), next.state)
        assertEquals(emptyList(), next.effects)
    }

    @Test
    @Verifies("FR-GEN-03")
    fun `home on Home does nothing`() {
        val home = LauncherState(apps = listOf(app("Maps")))

        val next = reduce(home, Action.HomePressed)

        assertSame(home, next.state)
        assertEquals(emptyList(), next.effects)
    }

    // Default Home role (FR-GEN-02)

    private fun offers(next: Next) = next.effects.count { it == Effect.OfferDefaultHome }

    @Test
    @Verifies("FR-GEN-02")
    fun `a first run offers the default Home role once the launcher is shown`() {
        val restored = reduce(LauncherState(), Action.Restored(null, Settings()))
        assertEquals(0, offers(restored))

        val shown = reduce(restored.state, Action.HomeShown)

        assertEquals(1, offers(shown))
        assertTrue(shown.state.defaultHomeOffered)
    }

    @Test
    @Verifies("FR-GEN-02")
    fun `a first run shown before the restore offers when it is restored`() {
        val shown = reduce(LauncherState(), Action.HomeShown)
        assertEquals(0, offers(shown))

        assertEquals(1, offers(reduce(shown.state, Action.Restored(null, Settings()))))
    }

    @Test
    @Verifies("FR-GEN-02")
    fun `the offer is made once per process`() {
        var state = reduce(reduce(LauncherState(), Action.Restored(null, Settings())).state, Action.HomeShown).state
        state = reduce(state, Action.HomeHidden).state

        assertEquals(0, offers(reduce(state, Action.HomeShown)))
    }

    @Test
    @Verifies("FR-GEN-02")
    fun `with a saved layout there is no offer`() {
        val restored = reduce(LauncherState(), Action.Restored(Layout(), Settings())).state

        assertEquals(0, offers(reduce(restored, Action.HomeShown)))
    }

    // Catalog (FR-GEN-06)

    @Test
    @Verifies("FR-GEN-06")
    fun `catalog changes reconcile the saved layout`() {
        val state = LauncherState(
            layout = Layout(grid = listOf(GridItem.App(app("Gone").id), GridItem.App(app("Maps").id)), home = listOf(app("Gone").id)),
            restored = true,
        )

        val next = reduce(state, Action.CatalogChanged(listOf(app("Maps"), app("Clock"))))

        assertEquals(Layout(grid = listOf(GridItem.App(app("Maps").id), GridItem.App(app("Clock").id))), next.state.layout)
    }

    @Test
    fun `catalog changes leave a missing layout for the first run`() {
        val next = reduce(LauncherState(restored = true, firstRun = true), Action.CatalogChanged(listOf(app("Maps"))))

        assertEquals(null, next.state.layout)
    }
}
