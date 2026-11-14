package io.github.twonet.selfish.domain.state

import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.AppId
import io.github.twonet.selfish.domain.model.GridItem
import io.github.twonet.selfish.domain.model.Layout
import io.github.twonet.selfish.domain.model.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** The reducer's rules for the first-run tutorial (SRS §4.14, DESIGN §4.1). */
@Verifies("AR-02")
class TutorialReducerTest {
    private val maps = AppEntry(AppId("pkg.maps", "Main", 0), "Maps")
    private val saved = Layout(grid = listOf(GridItem.App(maps.id)), home = listOf(maps.id))

    /** A first run, restored and on screen, with its tutorial at the first step. */
    private val firstRun = run {
        val restored = reduce(LauncherState(apps = listOf(maps)), Action.Restored(null, Settings())).state
        reduce(restored, Action.HomeShown).state
    }

    private fun LauncherState.after(vararg actions: Action): LauncherState = actions.fold(this) { state, action -> reduce(state, action).state }

    private fun step(step: TutorialStep, reached: Boolean = false) = Tutorial(step, reached)

    // Starting (FR-ONB-01, FR-ONB-02)

    @Test
    @Verifies("FR-ONB-01", "FR-ONB-02")
    fun `a first run starts the tutorial once restored and shown, in either order, with the default-Home offer`() {
        assertEquals(step(TutorialStep.Apps), firstRun.tutorial)

        val shownFirst = reduce(LauncherState(), Action.HomeShown)
        assertNull(shownFirst.state.tutorial, "Not before the restore")
        val restored = reduce(shownFirst.state, Action.Restored(null, Settings()))
        assertEquals(step(TutorialStep.Apps), restored.state.tutorial)
        assertEquals(listOf<Effect>(Effect.OfferDefaultHome), restored.effects.filter { it == Effect.OfferDefaultHome })

        val hidden = reduce(LauncherState(), Action.Restored(null, Settings())).state
        assertNull(hidden.tutorial, "Not before the launcher is on screen")
    }

    @Test
    @Verifies("FR-ONB-02")
    fun `the tutorial starts once per process, so a skip isn't undone by the next return`() {
        val skipped = firstRun.after(Action.SkipTutorial, Action.HomeHidden, Action.HomeShown)

        assertNull(skipped.tutorial)
    }

    @Test
    @Verifies("FR-ONB-02")
    fun `with a saved layout there is no tutorial`() {
        val restored = LauncherState(apps = listOf(maps)).after(Action.Restored(saved, Settings()), Action.HomeShown)

        assertNull(restored.tutorial)
    }

    // Steps (FR-ONB-02)

    @Test
    @Verifies("FR-ONB-02", "FR-GEN-04")
    fun `each step is reached when its view opens, and the next one comes once Home is back`() {
        val grid = firstRun.after(Action.LayerSettled(Layer.AppGrid))
        assertEquals(step(TutorialStep.Apps, reached = true), grid.tutorial)

        val events = grid.after(Action.LayerSettled(Layer.Home))
        assertEquals(step(TutorialStep.Events), events.tutorial)

        val eventsReached = events.after(Action.LayerSettled(Layer.Events))
        assertEquals(step(TutorialStep.Events, reached = true), eventsReached.tutorial)

        val topMenu = eventsReached.after(Action.Back)
        assertEquals(step(TutorialStep.TopMenu), topMenu.tutorial)

        val topMenuReached = topMenu.after(Action.LayerSettled(Layer.TopMenu))
        assertEquals(step(TutorialStep.TopMenu, reached = true), topMenuReached.tutorial)

        val done = topMenuReached.after(Action.LayerSettled(Layer.Home))
        assertNull(done.tutorial, "The last step ends the tutorial")
    }

    @Test
    @Verifies("FR-ONB-02")
    fun `another view doesn't reach the step, and the step waits for Home`() {
        val events = firstRun.after(Action.LayerSettled(Layer.Events))
        assertEquals(step(TutorialStep.Apps), events.tutorial)
        assertEquals(step(TutorialStep.Apps), events.after(Action.LayerSettled(Layer.Home)).tutorial)

        val settings = firstRun.after(Action.OpenSettings)
        assertEquals(step(TutorialStep.Apps), settings.tutorial)
    }

    @Test
    @Verifies("FR-ONB-02", "FR-GEN-03", "FR-GEN-05")
    fun `every way back to Home moves on, also returning from an app launched from the grid`() {
        val grid = firstRun.after(Action.LayerSettled(Layer.AppGrid))
        val reached = step(TutorialStep.Apps, reached = true)

        assertEquals(step(TutorialStep.Events), grid.after(Action.Back).tutorial)
        assertEquals(step(TutorialStep.Events), grid.after(Action.HomePressed).tutorial)

        // Hiding the launcher leaves the App Grid open; the step waits for Home.
        val returned = grid.after(Action.Launch(maps.id, null), Action.HomeHidden, Action.HomeShown)
        assertEquals(Layer.AppGrid, returned.layer)
        assertEquals(reached, returned.tutorial)
        assertEquals(step(TutorialStep.Events), returned.after(Action.HomePressed).tutorial)
    }

    @Test
    @Verifies("FR-ONB-02", "FR-GEN-03", "FR-GEN-04")
    fun `Back and Home on bare Home still do nothing during the tutorial`() {
        assertSame(firstRun, reduce(firstRun, Action.Back).state)
        assertSame(firstRun, reduce(firstRun, Action.HomePressed).state)
    }

    @Test
    @Verifies("FR-ONB-02")
    fun `Skip ends the tutorial on Home and on a step's view`() {
        assertNull(firstRun.after(Action.SkipTutorial).tutorial)

        val topMenu = firstRun.copy(tutorial = step(TutorialStep.TopMenu)).after(Action.LayerSettled(Layer.TopMenu), Action.SkipTutorial)
        assertNull(topMenu.tutorial)
        assertEquals(Layer.TopMenu, topMenu.layer, "Skipping leaves the view open")

        val skipped = firstRun.after(Action.SkipTutorial)
        assertSame(skipped, reduce(skipped, Action.SkipTutorial).state)
    }

    // Replay (FR-ONB-03)

    @Test
    @Verifies("FR-ONB-03")
    fun `Replay tutorial closes Settings, and About over it, and starts again on Home`() {
        val home = LauncherState(apps = listOf(maps), layout = saved, restored = true, homeShown = true)
        val settings = home.copy(layer = Layer.Settings)

        val replayed = settings.after(Action.ReplayTutorial)
        assertEquals(home.copy(tutorial = step(TutorialStep.Apps)), replayed)

        val fromAbout = settings.after(Action.OpenAbout, Action.ReplayTutorial)
        assertEquals(home.copy(tutorial = step(TutorialStep.Apps)), fromAbout)

        val midway = settings.copy(tutorial = step(TutorialStep.TopMenu)).after(Action.ReplayTutorial)
        assertEquals(step(TutorialStep.Apps), midway.tutorial, "From the first step")

        assertSame(replayed, reduce(replayed, Action.ReplayTutorial).state)
    }

    // What shows where (FR-ONB-02)

    @Test
    @Verifies("FR-ONB-02")
    fun `the tutorial shows on Home before each step and on the step's view after it, and nowhere else`() {
        assertEquals(step(TutorialStep.Apps), firstRun.tutorialShown)

        val grid = firstRun.after(Action.LayerSettled(Layer.AppGrid))
        assertEquals(step(TutorialStep.Apps, reached = true), grid.tutorialShown)

        val events = firstRun.after(Action.LayerSettled(Layer.Events))
        assertNull(events.tutorialShown, "Not on another view")

        assertNull(firstRun.after(Action.OpenSettings).tutorialShown, "Not on a page")

        val menu = firstRun.after(Action.OpenContextMenu(MenuTarget.Cover(maps.id)))
        assertNull(menu.tutorialShown, "Not under an overlay")

        assertNull(firstRun.after(Action.SkipTutorial).tutorialShown)
    }
}
