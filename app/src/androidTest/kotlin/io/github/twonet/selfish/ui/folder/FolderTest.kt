package io.github.twonet.selfish.ui.folder

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.twonet.selfish.R
import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.FolderId
import io.github.twonet.selfish.domain.model.GridItem
import io.github.twonet.selfish.domain.remorse.Destructive
import io.github.twonet.selfish.domain.state.Action
import io.github.twonet.selfish.domain.state.Layer
import io.github.twonet.selfish.domain.state.Overlay
import io.github.twonet.selfish.ui.LauncherTags
import io.github.twonet.selfish.ui.TestLauncher
import io.github.twonet.selfish.ui.TestLauncher.Companion.app
import io.github.twonet.selfish.ui.TestLauncher.Companion.launcher
import io.github.twonet.selfish.ui.components.FolderIconTags
import io.github.twonet.selfish.ui.grid.AppGridTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Folders as the user sees them (level I, DESIGN §8.3), in the opened App
 * Grid, through [TestLauncher]. The look of the expand and collapse
 * animation and how the pulley's ticks feel are checked by hand
 * (REVIEW-CHECKLIST.md); `HapticsTest` checks that they play.
 */
@RunWith(AndroidJUnit4::class)
class FolderTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val launcher = TestLauncher(rule)
    private val state get() = launcher.state

    private fun string(id: Int) = context.getString(id)

    private val a = app("A")
    private val b = app("B")
    private val c = app("C")
    private val d = app("D")
    private val tools = GridItem.Folder(FolderId(1), "Tools", listOf(b.id, c.id))

    /** The App Grid, open, with [grid] as its items. */
    private fun open(grid: List<GridItem>, apps: List<AppEntry> = listOf(a, b, c, d)) =
        launcher.show(launcher(apps, layer = Layer.AppGrid).let { it.copy(layout = it.layout!!.copy(grid = grid)) })

    private fun inGrid() = hasAnyAncestor(hasTestTag(AppGridTags.GRID))

    private fun inPanel() = hasAnyAncestor(hasTestTag(FolderTags.PANEL))

    private fun gridApp(label: String) = hasText(label) and hasClickAction() and inGrid()

    private fun panelApp(label: String) = hasText(label) and hasClickAction() and inPanel()

    private fun folderTile(name: String, apps: Int): SemanticsMatcher =
        hasContentDescription(context.resources.getQuantityString(R.plurals.folder_description, apps, name, apps)) and inGrid()

    private fun header(name: String) = hasText(name) and isHeading() and inPanel()

    private fun bounds(matcher: SemanticsMatcher): Rect = rule.onNode(matcher).fetchSemanticsNode().boundsInRoot

    private fun openTools() {
        rule.onNode(folderTile("Tools", 2)).performClick()
        rule.waitForIdle()
    }

    /** Long-presses [from], drags its center to [to] (in root coordinates), holds for [holdMillis], and lets go. */
    private fun drag(from: SemanticsMatcher, to: Offset, holdMillis: Long = HOLD_MILLIS) {
        val start = bounds(from)
        rule.onNode(from).performTouchInput {
            down(center)
            moveBy(Offset.Zero, delayMillis = viewConfiguration.longPressTimeoutMillis + LONG_PRESS_MARGIN_MILLIS)
            val step = (to - start.center) / STEPS.toFloat()
            repeat(STEPS) { moveBy(step) }
            moveBy(Offset.Zero, delayMillis = holdMillis)
            up()
        }
        rule.waitForIdle()
    }

    /**
     * Long-presses [from] in the open folder's panel and drags it out over
     * the grid, just above the panel, keeping the finger down. Once it is
     * out, the App Grid has it, and the items after it have made room.
     */
    private fun dragOutOfPanel(from: SemanticsMatcher) {
        val start = bounds(from)
        val panel = rule.onNodeWithTag(FolderTags.PANEL).fetchSemanticsNode().boundsInRoot
        val out = Offset(start.center.x, panel.top - with(rule.density) { OUTSIDE.toPx() })
        rule.onNode(from).performTouchInput {
            down(center)
            moveBy(Offset.Zero, delayMillis = viewConfiguration.longPressTimeoutMillis + LONG_PRESS_MARGIN_MILLIS)
            val step = (out - start.center) / STEPS.toFloat()
            repeat(STEPS) { moveBy(step) }
        }
        rule.waitForIdle()
    }

    /** Carries the drag in progress on to [to] (in root coordinates), holds for [holdMillis], and lets go. */
    private fun finishDrag(to: Offset, holdMillis: Long = HOLD_MILLIS) {
        rule.onNodeWithTag(LauncherTags.ROOT).performTouchInput {
            val from = currentPosition()!!
            repeat(STEPS) { moveTo(from + (to - from) * ((it + 1) / STEPS.toFloat())) }
            moveBy(Offset.Zero, delayMillis = holdMillis)
            up()
        }
        rule.waitForIdle()
    }

    /** Chooses the pulley menu item labeled [label] through the folder name's accessibility actions (FR-GEST-09). */
    private fun choose(label: String) {
        val actions = rule.onNode(header("Tools")).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        rule.runOnIdle { actions.single { it.label == label }.action() }
        rule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        rule.waitForIdle()
    }

    @Test
    @Verifies("FR-FOLD-02", "FR-GRID-02")
    fun aFolderTilePreviewsUpToFourOfItsApps() {
        val many = (1..6).map { app("App $it") }
        open(listOf(tools, GridItem.Folder(FolderId(2), "Many", many.map { it.id })), apps = listOf(a, b, c, d) + many)

        fun previews(name: String, apps: Int) = rule.onAllNodes(
            hasTestTag(FolderIconTags.PREVIEW) and hasAnyAncestor(folderTile(name, apps)),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().size

        assertEquals(2, previews("Tools", 2))
        assertEquals(4, previews("Many", 6))
    }

    @Test
    @Verifies("FR-FOLD-03", "UI-02", "NFR-A11Y-01")
    fun tappingAFolderOpensItsPanelWithItsNameAndApps() {
        open(listOf(GridItem.App(a.id), tools))

        openTools()

        assertEquals(listOf<Overlay>(Overlay.Folder(tools.id)), state.overlays)
        rule.onNodeWithTag(FolderTags.PANEL).assertIsDisplayed()
        rule.onNode(header("Tools")).assertIsDisplayed()
        rule.onNode(panelApp("B")).assertIsDisplayed()
        rule.onNode(panelApp("C")).assertIsDisplayed()
        rule.onNode(panelApp("A")).assertDoesNotExist()
    }

    @Test
    @Verifies("FR-FOLD-03", "FR-GEN-03", "FR-GEN-04")
    fun backHomeAndATapOutsideCloseTheFolder() {
        open(listOf(GridItem.App(a.id), tools))

        openTools()
        launcher.dispatch(Action.Back)
        rule.waitForIdle()
        rule.onNodeWithTag(FolderTags.PANEL).assertDoesNotExist()
        assertEquals(Layer.AppGrid, state.layer)

        openTools()
        val panel = rule.onNodeWithTag(FolderTags.PANEL).fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag(FolderTags.DIM).performTouchInput { click(Offset(centerX, panel.top / 2f)) }
        rule.waitForIdle()
        assertTrue(Action.CloseFolder in launcher.dispatched)
        rule.onNodeWithTag(FolderTags.PANEL).assertDoesNotExist()

        openTools()
        launcher.dispatch(Action.HomePressed)
        rule.waitForIdle()
        rule.onNodeWithTag(FolderTags.PANEL).assertDoesNotExist()
        assertEquals(Layer.Home, state.layer)
    }

    @Test
    @Verifies("FR-FOLD-04")
    fun tappingTheNameRenamesTheFolder() {
        open(listOf(GridItem.App(a.id), tools))
        openTools()

        rule.onNode(header("Tools")).performClick()
        val field = rule.onNode(hasSetTextAction() and inPanel())
        field.performTextReplacement("Games")
        field.performImeAction()
        rule.waitForIdle()

        assertTrue(Action.RenameFolder(tools.id, "Games") in launcher.dispatched, "Dispatched: ${launcher.dispatched}")
        assertEquals("Games", (state.layout!!.grid[1] as GridItem.Folder).name)
        rule.onNode(hasText("Games") and isHeading() and inPanel()).assertIsDisplayed()
    }

    @Test
    @Verifies("FR-FOLD-07", "FR-GEST-05", "FR-GEST-06")
    fun pullingTheFolderDownToRemoveFolderWaitsAndTappingTheBannerCancels() {
        open(listOf(GridItem.App(a.id), tools))
        openTools()

        // Slowly down to the middle of the second item, "Remove folder".
        launcher.pull(rule.onNode(header("Tools")), toItem = 1, items = 2)
        rule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        rule.waitForIdle()

        assertEquals(Destructive.RemoveFolder(tools.id), state.remorse?.action, "Dispatched: ${launcher.dispatched}")
        assertEquals(tools, state.layout!!.grid[1], "The folder stays until the timer runs out")
        rule.onNodeWithTag(FolderTags.PANEL).assertDoesNotExist()

        rule.onNodeWithTag(LauncherTags.REMORSE).assertIsDisplayed().performClick()
        rule.waitForIdle()

        assertTrue(Action.CancelRemorse in launcher.dispatched)
        assertNull(state.remorse)
        rule.onNodeWithTag(LauncherTags.REMORSE).assertDoesNotExist()
        assertEquals(tools, state.layout!!.grid[1])
    }

    @Test
    @Verifies("FR-FOLD-07", "FR-GEST-06", "FR-GEST-09")
    fun whenItsTimerRunsOutARemovedFolderReturnsItsAppsInPlace() {
        open(listOf(GridItem.App(a.id), tools, GridItem.App(d.id)))
        openTools()

        choose(string(R.string.menu_remove_folder))
        val banner = context.getString(R.string.remorse_cancel, string(R.string.remorse_remove_folder))
        rule.onNode(hasContentDescription(banner) and hasTestTag(LauncherTags.REMORSE)).assertIsDisplayed()
        // TestLauncher doesn't run effects, so the timer is run by hand.
        launcher.dispatch(Action.RemorseDue(state.remorse!!.number))
        rule.waitForIdle()

        assertEquals(listOf(a, b, c, d).map { GridItem.App(it.id) }, state.layout!!.grid)
        rule.onNode(gridApp("B")).assertIsDisplayed()
        rule.onNodeWithTag(LauncherTags.REMORSE).assertDoesNotExist()
    }

    @Test
    @Verifies("FR-FOLD-04", "FR-GEST-09")
    fun renameIsAlsoAnActionOnTheName() {
        open(listOf(GridItem.App(a.id), tools))
        openTools()

        choose(string(R.string.menu_rename))

        rule.onNode(hasSetTextAction() and inPanel()).assertIsDisplayed()
    }

    @Test
    @Verifies("FR-FOLD-05")
    fun anAppDroppedOntoAFolderJoinsIt() {
        open(listOf(GridItem.App(a.id), tools, GridItem.App(d.id)))

        drag(gridApp("D"), to = bounds(folderTile("Tools", 2)).center)

        assertTrue(Action.AddToFolder(d.id, tools.id) in launcher.dispatched, "Dispatched: ${launcher.dispatched}")
        assertEquals(listOf(GridItem.App(a.id), tools.copy(apps = listOf(b.id, c.id, d.id))), state.layout!!.grid)
        rule.onNode(folderTile("Tools", 3)).assertIsDisplayed()
    }

    @Test
    @Verifies("AC-08", "FR-FOLD-01", "FR-FOLD-05", "FR-FOLD-06")
    fun droppingAOntoBMakesAFolderAndDraggingBBackOutLeavesAInItsPlace() {
        open(listOf(a, b, c, d).map { GridItem.App(it.id) })
        drag(gridApp("A"), to = bounds(gridApp("B")).center)
        val made = GridItem.Folder(FolderId(1), GridItem.Folder.DEFAULT_NAME, listOf(b.id, a.id))
        assertEquals(listOf(made, GridItem.App(c.id), GridItem.App(d.id)), state.layout!!.grid)

        rule.onNode(folderTile(GridItem.Folder.DEFAULT_NAME, 2)).performClick()
        rule.waitForIdle()
        dragOutOfPanel(panelApp("B"))

        assertTrue(launcher.dispatched.any { it is Action.TakeOutOfFolder && it.app == b.id }, "Dispatched: ${launcher.dispatched}")
        assertTrue(state.layout!!.grid.none { it is GridItem.Folder }, "The folder dissolved: ${state.layout!!.grid}")
        assertEquals(emptyList(), state.overlays, "The panel closed")
        rule.onNodeWithTag(FolderTags.PANEL).assertDoesNotExist()

        // Still dragging: beside D, where B takes D's place after a rest.
        val target = bounds(gridApp("D")).let { Offset(it.left + it.width * BESIDE, it.center.y) }
        finishDrag(target)

        val grid = state.layout!!.grid
        assertEquals(GridItem.App(a.id), grid.first(), "A is left in the folder's place: $grid")
        assertEquals(setOf(a, b, c, d).map { GridItem.App(it.id) }.toSet(), grid.toSet())
        assertTrue(bounds(gridApp("B")).contains(target), "B went where it was dropped: $grid")
    }

    @Test
    @Verifies("FR-FOLD-05")
    fun anAppDraggedOutOfAFolderKeepsBeingDraggedAndCanMakeAnotherFolder() {
        val more = GridItem.Folder(FolderId(1), "Tools", listOf(b.id, c.id, d.id))
        open(listOf(GridItem.App(a.id), more), apps = listOf(a, b, c, d))
        rule.onNode(folderTile("Tools", 3)).performClick()
        rule.waitForIdle()

        dragOutOfPanel(panelApp("D"))
        finishDrag(bounds(gridApp("A")).center)

        assertEquals(
            listOf(GridItem.Folder(FolderId(2), GridItem.Folder.DEFAULT_NAME, listOf(a.id, d.id)), more.copy(apps = listOf(b.id, c.id))),
            state.layout!!.grid,
        )
    }

    @Test
    @Verifies("FR-FOLD-05", "FR-GRID-06")
    fun anAppInAFolderHasItsMenuAndMovesWithinIt() {
        open(listOf(GridItem.App(a.id), tools))
        openTools()

        rule.onNode(panelApp("C")).performTouchInput { longClick() }
        rule.onNode(hasText(string(R.string.menu_add_to_home)) and inPanel()).assertIsDisplayed().performClick()
        assertEquals(listOf(c.id), state.layout!!.home)
        assertEquals(listOf<Overlay>(Overlay.Folder(tools.id)), state.overlays, "The folder stays open")

        val bAt = bounds(panelApp("B"))
        drag(panelApp("C"), to = Offset(bAt.left + bAt.width * (1f - BESIDE), bAt.center.y))

        assertEquals(listOf(c.id, b.id), (state.layout!!.grid[1] as GridItem.Folder).apps)
    }

    @Test
    @Verifies("FR-FOLD-08")
    fun aFolderIsNeverDroppedIntoAnything() {
        open(listOf(tools, GridItem.App(a.id)))

        drag(folderTile("Tools", 2), to = bounds(gridApp("A")).center)

        assertTrue(launcher.dispatched.none { it is Action.DropOnto || it is Action.AddToFolder }, "Dispatched: ${launcher.dispatched}")
        assertEquals(setOf(tools, GridItem.App(a.id)), state.layout!!.grid.toSet())
    }

    private companion object {
        const val LONG_PRESS_MARGIN_MILLIS = 100L
        const val STEPS = 10

        /** Longer than the reorder rest of 250 ms. */
        const val HOLD_MILLIS = 600L

        /** Longer than the pulley's flash and the folder's collapse. */
        const val SETTLE_MILLIS = 1_000L

        /** Where "beside" an app's center is, as a fraction of its width: outside the central half. */
        const val BESIDE = 0.9f

        /** How far above the panel a dragged app is taken out of it. */
        val OUTSIDE = 24.dp
    }
}
