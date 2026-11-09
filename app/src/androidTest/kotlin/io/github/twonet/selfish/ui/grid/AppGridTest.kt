package io.github.twonet.selfish.ui.grid

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.twonet.selfish.R
import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.FolderId
import io.github.twonet.selfish.domain.model.GridItem
import io.github.twonet.selfish.domain.model.Settings
import io.github.twonet.selfish.domain.state.Action
import io.github.twonet.selfish.domain.state.Layer
import io.github.twonet.selfish.ui.TestLauncher
import io.github.twonet.selfish.ui.TestLauncher.Companion.app
import io.github.twonet.selfish.ui.TestLauncher.Companion.launcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The App Grid as the user sees it (level I, DESIGN §8.3), opened, through
 * [TestLauncher]. Real installs and uninstalls are checked by hand
 * (REVIEW-CHECKLIST.md); here the catalog changes as the store would
 * report them.
 */
@RunWith(AndroidJUnit4::class)
class AppGridTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val launcher = TestLauncher(rule)
    private val state get() = launcher.state

    private fun string(id: Int) = context.getString(id)

    private fun open(apps: List<AppEntry>, home: List<AppEntry> = emptyList(), fontScale: Float? = null) =
        launcher.show(launcher(apps, home, layer = Layer.AppGrid), fontScale = fontScale)

    /** An app's item in the grid, as opposed to its cover on Home. */
    private fun item(label: String) = hasText(label) and hasClickAction() and hasAnyAncestor(hasTestTag(AppGridTags.GRID))

    private fun bounds(label: String): Rect = rule.onNode(item(label)).fetchSemanticsNode().boundsInRoot

    private fun columns(): Int = rule.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag(AppGridTags.GRID)))
        .fetchSemanticsNodes()
        .map { it.boundsInRoot.left.roundToInt() }
        .distinct()
        .size

    private fun longPress(label: String) = rule.onNode(item(label)).performTouchInput { longClick() }

    /** Long-presses [from], drags its center to [to] (in root coordinates), holds for [holdMillis], and lets go. */
    private fun drag(from: String, to: Offset, holdMillis: Long = HOLD_MILLIS) {
        val start = bounds(from)
        rule.onNode(item(from)).performTouchInput {
            down(center)
            moveBy(Offset.Zero, delayMillis = viewConfiguration.longPressTimeoutMillis + LONG_PRESS_MARGIN_MILLIS)
            val step = (to - start.center) / STEPS.toFloat()
            repeat(STEPS) { moveBy(step) }
            moveBy(Offset.Zero, delayMillis = holdMillis)
            up()
        }
        rule.waitForIdle()
    }

    @Test
    @Verifies("FR-GRID-02", "FR-ANIM-05")
    fun theGridHasFourColumnsByDefaultAndThreeToFiveWhenChosen() {
        open((1..12).map { app("App %02d".format(it)) })
        assertEquals(4, columns())

        listOf(3, 5).forEach { n ->
            launcher.state = state.copy(settings = Settings(gridColumns = n))
            rule.waitForIdle()
            assertEquals(n, columns())
        }
    }

    @Test
    @Verifies("FR-GRID-02", "NFR-A11Y-04")
    fun labelsTakeOneLine() {
        val long = "A rather long application name"
        open(listOf(app(long)))
        assertEquals(1, lines(long))
    }

    @Test
    @Verifies("FR-GRID-02", "NFR-A11Y-04")
    fun labelsTakeTwoLinesAtLargeFontSizes() {
        val long = "A rather long application name"
        open(listOf(app(long)), fontScale = LARGE_FONT)
        assertEquals(2, lines(long))
    }

    /** How many lines [label] is laid out on. */
    private fun lines(label: String): Int {
        val node = rule.onNode(hasText(label) and hasAnyAncestor(hasTestTag(AppGridTags.GRID)), useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        return layouts.single().lineCount
    }

    @Test
    @Verifies("FR-GRID-06")
    fun anAppsMenuOffersHomeAppInfoAndUninstall() {
        val maps = app("Maps")
        open(listOf(app("Clock"), maps))

        longPress("Maps")
        rule.onNodeWithText(string(R.string.menu_add_to_home)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.menu_uninstall)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.menu_app_info)).assertIsDisplayed().performClick()

        assertTrue(Action.OpenAppInfo(maps.id) in launcher.dispatched)
        rule.onNodeWithText(string(R.string.menu_app_info)).assertDoesNotExist()

        longPress("Maps")
        rule.onNodeWithText(string(R.string.menu_uninstall)).performClick()

        assertTrue(Action.Uninstall(maps.id) in launcher.dispatched)
        rule.onNodeWithText(string(R.string.menu_uninstall)).assertDoesNotExist()
    }

    @Test
    @Verifies("FR-GRID-06")
    fun aSystemAppIsNotOfferedForUninstalling() {
        open(listOf(app("Settings", removable = false)))

        longPress("Settings")

        rule.onNodeWithText(string(R.string.menu_app_info)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.menu_uninstall)).assertDoesNotExist()
    }

    @Test
    @Verifies("FR-HOME-05")
    fun anAppIsAddedToHomeFromItsMenu() {
        val maps = app("Maps")
        val clock = app("Clock")
        open(listOf(clock, maps), home = listOf(maps))

        longPress("Clock")
        rule.onNodeWithText(string(R.string.menu_add_to_home)).assertIsDisplayed().performClick()

        assertEquals(listOf(maps.id, clock.id), state.layout!!.home)
    }

    @Test
    @Verifies("FR-HOME-05")
    fun addToHomeExplainsThatAFullHomeIsFull() {
        val covers = (1..9).map { app("Cover $it") }
        open(covers + app("Extra"), home = covers)

        longPress("Extra")

        rule.onNodeWithText(string(R.string.menu_home_full)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.menu_add_to_home)).assertIsNotEnabled()
        assertTrue(launcher.dispatched.none { it is Action.AddToHome })
        assertEquals(9, state.layout!!.home.size)
    }

    @Test
    @Verifies("FR-GRID-05", "FR-GRID-06", "FR-GRID-07")
    fun aLongPressedAppIsDraggedToANewPlace() {
        val apps = listOf("A", "B", "C", "D").map { app(it) }
        open(apps)
        val b = bounds("B")

        // Beside B's center, where the dragged app takes B's place after a rest.
        drag("A", to = Offset(b.left + b.width * BESIDE, b.center.y))

        assertTrue(Action.MoveGridItem(GridItem.App(apps[0].id), 1) in launcher.dispatched, "Dispatched: ${launcher.dispatched}")
        assertEquals(listOf("B", "A", "C", "D").map { GridItem.App(app(it).id) }, state.layout!!.grid)
        assertTrue(state.overlays.isEmpty(), "Dragging closes the menu")
    }

    @Test
    @Verifies("FR-GRID-08", "FR-FOLD-01", "NFR-A11Y-01")
    fun droppingAnAppOntoAnotherMakesAFolder() {
        val apps = listOf("A", "B", "C").map { app(it) }
        open(apps)

        drag("A", to = bounds("B").center)

        assertTrue(Action.DropOnto(apps[0].id, apps[1].id) in launcher.dispatched, "Dispatched: ${launcher.dispatched}")
        val folder = GridItem.Folder(FolderId(1), GridItem.Folder.DEFAULT_NAME, listOf(apps[1].id, apps[0].id))
        assertEquals(listOf(folder, GridItem.App(apps[2].id)), state.layout!!.grid)
        val description = context.resources.getQuantityString(R.plurals.folder_description, 2, GridItem.Folder.DEFAULT_NAME, 2)
        rule.onNode(hasContentDescription(description) and hasAnyAncestor(hasTestTag(AppGridTags.GRID))).assertIsDisplayed()
        rule.onNode(item("A")).assertDoesNotExist()
    }

    @Test
    @Verifies("FR-GRID-07")
    fun holdingADraggedAppNearTheBottomScrollsTheGrid() {
        open((1..80).map { app("App %02d".format(it)) })
        val grid = rule.onNodeWithTag(AppGridTags.GRID).fetchSemanticsNode().boundsInRoot
        assertEquals(0f, scrollOffset())

        drag("App 01", to = Offset(grid.center.x, grid.bottom - EDGE_INSET_PX), holdMillis = AUTO_SCROLL_MILLIS)

        assertTrue(scrollOffset() > 0f, "The grid scrolled")
    }

    private fun scrollOffset(): Float =
        rule.onNodeWithTag(AppGridTags.GRID).fetchSemanticsNode().config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)!!.value()

    @Test
    @Verifies("AC-07", "FR-GRID-05", "NFR-REL-05")
    fun anAppInstalledWhileTheGridIsOpenIsAddedAtTheEndWithoutMovingIt() {
        val apps = (1..80).map { app("App %02d".format(it)) }
        open(apps)
        rule.onNodeWithTag(AppGridTags.GRID).performScrollToKey("app:${apps[40].id}")
        rule.waitForIdle()
        val before = bounds("App 41")

        // Sorts first, but a new app always joins at the end (FR-GRID-05).
        val installed = app("Aardvark")
        launcher.dispatch(Action.CatalogChanged(apps + installed))
        rule.waitForIdle()

        assertEquals(before, bounds("App 41"), "The grid didn't move")
        assertEquals(GridItem.App(installed.id), state.layout!!.grid.last())
        rule.onNodeWithTag(AppGridTags.GRID).performScrollToKey("app:${installed.id}")
        assertTrue(bounds("Aardvark").top >= bounds("App 80").top, "It is after the last app")
    }

    @Test
    @Verifies("FR-GRID-09", "FR-FOLD-06")
    fun anUninstalledAppLeavesNoGapAndAFolderLeftWithOneDissolves() {
        val apps = listOf("A", "B", "C", "E", "F").map { app(it) }
        launcher.show(
            launcher(apps, home = listOf(apps[1]), layer = Layer.AppGrid).let {
                it.copy(
                    layout = it.layout!!.copy(
                        grid = listOf(
                            GridItem.App(apps[0].id),
                            GridItem.App(apps[1].id),
                            GridItem.App(apps[2].id),
                            GridItem.Folder(FolderId(1), "Pair", listOf(apps[3].id, apps[4].id)),
                        ),
                    ),
                )
            },
        )
        val b = bounds("B")
        val c = bounds("C")

        launcher.dispatch(Action.CatalogChanged(listOf(apps[0], apps[2], apps[3])))
        rule.waitForIdle()

        rule.onNode(item("B")).assertDoesNotExist()
        assertEquals(b, bounds("C"), "C moved up into B's place")
        assertEquals(c, bounds("E"), "The folder dissolved into E, in its place")
        assertEquals(emptyList(), state.layout!!.home)
        assertNotEquals(b, c)
    }

    private companion object {
        const val LONG_PRESS_MARGIN_MILLIS = 100L
        const val STEPS = 10

        /** Longer than the reorder rest of 250 ms. */
        const val HOLD_MILLIS = 600L
        const val AUTO_SCROLL_MILLIS = 1_500L

        /** Where "beside" an app's center is, as a fraction of its width: outside the central half. */
        const val BESIDE = 0.9f
        const val LARGE_FONT = 2f
        const val EDGE_INSET_PX = 30f
    }
}
