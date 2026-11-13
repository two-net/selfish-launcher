package io.github.twonet.selfish.ui.search

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.twonet.selfish.R
import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.FolderId
import io.github.twonet.selfish.domain.model.GridItem
import io.github.twonet.selfish.domain.state.Action
import io.github.twonet.selfish.domain.state.Layer
import io.github.twonet.selfish.domain.state.MenuTarget
import io.github.twonet.selfish.ui.LauncherScreen
import io.github.twonet.selfish.ui.TestLauncher
import io.github.twonet.selfish.ui.TestLauncher.Companion.app
import io.github.twonet.selfish.ui.TestLauncher.Companion.launcher
import io.github.twonet.selfish.ui.grid.AppGridTags
import io.github.twonet.selfish.ui.tokens.LauncherTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Searching the App Grid as the user sees it (level I, DESIGN §8.3), through
 * [TestLauncher]. Ranking itself is `SearchRankerTest`'s; the on-screen
 * keyboard and its Back are checked by hand (REVIEW-CHECKLIST.md).
 */
@RunWith(AndroidJUnit4::class)
class SearchTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val launcher = TestLauncher(rule)
    private val state get() = launcher.state

    private fun string(id: Int) = context.getString(id)

    private val bitmap = app("Bitmap Studio")
    private val clock = app("Clock")
    private val googleMaps = app("Google Maps")
    private val maps = app("Maps")
    private val notes = app("Notes")
    private val all = listOf(bitmap, clock, googleMaps, maps, notes)

    /** The App Grid, open, with [apps] in that order, or [grid] as its items. */
    private fun open(apps: List<AppEntry> = all, grid: List<GridItem>? = null) =
        launcher.show(launcher(apps, layer = Layer.AppGrid).let { if (grid == null) it else it.copy(layout = it.layout!!.copy(grid = grid)) })

    private fun field() = rule.onNodeWithTag(AppGridTags.SEARCH)

    private fun inGrid() = hasAnyAncestor(hasTestTag(AppGridTags.GRID))

    private fun gridApp(label: String): SemanticsMatcher = hasText(label) and hasClickAction() and inGrid()

    /**
     * The labels of the items the grid shows, row by row, each from the
     * start. Sorted by their centers, which a lifted item's scale leaves where
     * they are.
     */
    private fun shown(): List<String> = rule.onAllNodes(hasClickAction() and !hasSetTextAction() and inGrid())
        .fetchSemanticsNodes()
        .sortedWith(compareBy({ it.boundsInRoot.center.y.roundToInt() }, { it.boundsInRoot.center.x }))
        .map { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text } }

    private fun type(text: String) {
        field().performClick()
        field().performTextInput(text)
        rule.waitForIdle()
    }

    @Test
    @Verifies("FR-SRCH-01", "FR-SRCH-02")
    fun typingFiltersTheGridToTheMatchesBestFirst() {
        open()
        val placeholder = string(R.string.grid_search_placeholder)
        rule.onNode(hasText(placeholder) and hasAnyAncestor(hasTestTag(AppGridTags.SEARCH)), useUnmergedTree = true).assertIsDisplayed()

        field().performClick()
        field().assertIsFocused()
        field().performTextInput("ma")
        rule.waitForIdle()

        assertEquals("ma", state.query)
        assertEquals(listOf("Google Maps", "Maps", "Bitmap Studio"), shown())
        rule.onNode(hasText(placeholder), useUnmergedTree = true).assertDoesNotExist()

        field().performTextClearance()
        rule.waitForIdle()
        assertEquals(all.map { it.label }, shown(), "Clearing the field shows the grid again")
    }

    @Test
    @Verifies("FR-SRCH-02")
    fun appsInsideFoldersAreFound() {
        val tools = GridItem.Folder(FolderId(1), "Tools", listOf(clock.id, maps.id))
        open(listOf(clock, maps, notes), grid = listOf(GridItem.App(notes.id), tools))

        type("clo")

        assertEquals(listOf("Clock"), shown())
    }

    @Test
    @Verifies("FR-SRCH-03")
    fun theKeyboardsActionKeyLaunchesTheFirstResult() {
        open()

        type("ma")
        field().performImeAction()
        rule.waitForIdle()

        assertTrue(Action.Launch(googleMaps.id, null) in launcher.dispatched, "Dispatched: ${launcher.dispatched}")
    }

    @Test
    @Verifies("FR-SRCH-03")
    fun resultsComeBackFromALaunchedAppWithoutTheKeyboard() {
        val owner = TestLifecycleOwner()
        rule.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        launcher.state = launcher(all, layer = Layer.AppGrid)
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                LauncherTheme { LauncherScreen(launcher.state, launcher::dispatch, TestLauncher.NO_INSETS) }
            }
        }
        type("clo")
        field().assertIsFocused()

        // The launched app comes in front: the launcher stops, then starts again on Back.
        rule.runOnUiThread { owner.registry.currentState = Lifecycle.State.CREATED }
        rule.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        rule.waitForIdle()

        field().assertIsNotFocused()
        assertEquals("clo", state.query)
        assertEquals(listOf("Clock"), shown())
    }

    /** A lifecycle the test moves by hand, as the system moves the launcher's Activity. */
    private class TestLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    @Verifies("FR-SRCH-03", "FR-SRCH-04")
    fun withoutMatchesTheGridSaysNoAppsFoundAndTheActionKeyLaunchesNothing() {
        open()

        type("zzz")

        rule.onNodeWithText(string(R.string.search_no_results)).assertIsDisplayed()
        assertEquals(emptyList(), shown())
        field().performImeAction()
        rule.waitForIdle()
        assertTrue(launcher.dispatched.none { it is Action.Launch })

        field().performTextClearance()
        rule.waitForIdle()
        rule.onNodeWithText(string(R.string.search_no_results)).assertDoesNotExist()
    }

    @Test
    @Verifies("FR-SRCH-05")
    fun closingTheGridClearsTheQueryAndTheKeyboard() {
        open()

        listOf(Action.Back, Action.HomePressed, Action.LayerSettled(Layer.Home)).forEach { close ->
            type("ma")
            field().assertIsFocused()

            launcher.dispatch(close)
            rule.waitForIdle()
            assertEquals(Layer.Home, state.layer)
            assertEquals("", state.query, "After $close")

            launcher.dispatch(Action.LayerSettled(Layer.AppGrid))
            rule.waitForIdle()
            field().assertIsNotFocused()
            assertEquals("", field().fetchSemanticsNode().config[SemanticsProperties.EditableText].text, "After $close")
            assertEquals(all.map { it.label }, shown(), "After $close")
        }
    }

    @Test
    @Verifies("FR-SRCH-01", "FR-GRID-06")
    fun aLongPressedResultOpensItsMenuButIsNotDragged() {
        open()
        type("ma")
        val grid = state.layout!!.grid

        rule.onNode(gridApp("Maps")).performTouchInput { longClick() }
        rule.waitForIdle()
        assertTrue(Action.OpenContextMenu(MenuTarget.App(maps.id)) in launcher.dispatched)
        rule.onNodeWithText(string(R.string.menu_app_info)).assertIsDisplayed()

        // Long-press Maps again and drag it onto Google Maps, beside it.
        val from = rule.onNode(gridApp("Maps")).fetchSemanticsNode().boundsInRoot
        val to = rule.onNode(gridApp("Google Maps")).fetchSemanticsNode().boundsInRoot
        rule.onNode(gridApp("Maps")).performTouchInput {
            down(center)
            moveBy(Offset.Zero, delayMillis = viewConfiguration.longPressTimeoutMillis + LONG_PRESS_MARGIN_MILLIS)
            val step = (to.center - from.center) / STEPS.toFloat()
            repeat(STEPS) { moveBy(step) }
            moveBy(Offset.Zero, delayMillis = HOLD_MILLIS)
            up()
        }
        rule.waitForIdle()

        assertTrue(launcher.dispatched.none { it is Action.MoveGridItem || it is Action.DropOnto }, "Dispatched: ${launcher.dispatched}")
        assertEquals(grid, state.layout!!.grid)
        assertEquals(listOf("Google Maps", "Maps", "Bitmap Studio"), shown().filter { it in all.map(AppEntry::label) })
    }

    private companion object {
        const val LONG_PRESS_MARGIN_MILLIS = 100L
        const val STEPS = 10

        /** Longer than the 250 ms a dragged app rests before it takes another's place. */
        const val HOLD_MILLIS = 600L
    }
}
