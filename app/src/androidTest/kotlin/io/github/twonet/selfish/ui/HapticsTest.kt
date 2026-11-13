package io.github.twonet.selfish.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.test.SemanticsMatcher
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
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.twonet.selfish.R
import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.FolderId
import io.github.twonet.selfish.domain.model.GridItem
import io.github.twonet.selfish.domain.model.Haptics
import io.github.twonet.selfish.domain.model.Settings
import io.github.twonet.selfish.domain.remorse.Destructive
import io.github.twonet.selfish.domain.state.Action
import io.github.twonet.selfish.domain.state.Layer
import io.github.twonet.selfish.domain.state.LauncherState
import io.github.twonet.selfish.domain.state.MenuTarget
import io.github.twonet.selfish.ui.TestLauncher.Companion.app
import io.github.twonet.selfish.ui.TestLauncher.Companion.launcher
import io.github.twonet.selfish.ui.folder.FolderTags
import io.github.twonet.selfish.ui.grid.AppGridTags
import io.github.twonet.selfish.ui.home.HomeTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The launcher's haptics (level I, DESIGN §8.3): every long-press ticks once
 * (FR-GEST-07), the pulley menu ticks as its highlight moves (FR-GEST-05),
 * and with Haptic feedback Off nothing ticks while everything still works
 * (FR-GEST-08, NFR-A11Y-06). [TestLauncher] records what reaches the
 * platform, which follows the system's touch-feedback setting itself; how
 * the ticks feel, and that setting, are checked by hand
 * (REVIEW-CHECKLIST.md).
 */
@RunWith(AndroidJUnit4::class)
class HapticsTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val launcher = TestLauncher(rule)
    private val state get() = launcher.state

    private val maps = app("Maps")
    private val mail = app("Mail")
    private val b = app("B")
    private val c = app("C")
    private val tools = GridItem.Folder(FolderId(1), "Tools", listOf(b.id, c.id))

    /** Home with Maps and Mail as covers, and the grid with them and the Tools folder. */
    private fun launcher(haptics: Haptics = Haptics.FollowSystem): LauncherState {
        val state = launcher(listOf(maps, mail, b, c), home = listOf(maps, mail))
        return state.copy(
            layout = state.layout!!.copy(grid = listOf(GridItem.App(maps.id), GridItem.App(mail.id), tools)),
            settings = Settings(haptics = haptics),
        )
    }

    private fun inGrid() = hasAnyAncestor(hasTestTag(AppGridTags.GRID))

    private fun inPanel() = hasAnyAncestor(hasTestTag(FolderTags.PANEL))

    private fun cover(label: String) = hasText(label) and hasClickAction() and hasAnyAncestor(hasTestTag(HomeTags.COVERS))

    private fun gridApp(label: String) = hasText(label) and hasClickAction() and inGrid()

    private fun folderTile(): SemanticsMatcher =
        hasContentDescription(context.resources.getQuantityString(R.plurals.folder_description, 2, "Tools", 2)) and inGrid()

    /** Long-presses [matcher] and returns the haptics it played. */
    private fun longPress(matcher: SemanticsMatcher): List<HapticFeedbackType> {
        launcher.haptics.clear()
        rule.onNode(matcher).performTouchInput { longClick() }
        rule.waitForIdle()
        return launcher.haptics.toList()
    }

    /** Long-presses an empty part of Home, beneath its covers, and returns the haptics it played. */
    private fun longPressEmptyHome(): List<HapticFeedbackType> {
        launcher.haptics.clear()
        rule.onNodeWithTag(HomeTags.SCREEN).performTouchInput { longClick(Offset(centerX, bottom - height / 10)) }
        rule.waitForIdle()
        return launcher.haptics.toList()
    }

    private fun go(action: Action) {
        launcher.dispatch(action)
        rule.waitForIdle()
    }

    private fun search(text: String) {
        val field = rule.onNodeWithTag(AppGridTags.SEARCH)
        field.performClick()
        field.performTextInput(text)
        rule.waitForIdle()
    }

    private fun openTools() {
        rule.onNode(folderTile()).performClick()
        rule.waitForIdle()
    }

    @Test
    @Verifies("FR-GEST-07")
    fun liftingACoverAnAppOrAFolderTicksOnce() {
        launcher.show(launcher())

        assertEquals(listOf(LONG_PRESS), longPress(cover("Maps")), "A cover")
        assertTrue(Action.OpenContextMenu(MenuTarget.Cover(maps.id)) in launcher.dispatched)
        go(Action.CloseContextMenu)

        go(Action.LayerSettled(Layer.AppGrid))
        assertEquals(listOf(LONG_PRESS), longPress(gridApp("Mail")), "An app")
        assertTrue(Action.OpenContextMenu(MenuTarget.App(mail.id)) in launcher.dispatched)
        go(Action.CloseContextMenu)

        assertEquals(listOf(LONG_PRESS), longPress(folderTile()), "A folder")
        openTools()
        assertEquals(listOf(LONG_PRESS), longPress(hasText("B") and hasClickAction() and inPanel()), "An app in a folder")
    }

    @Test
    @Verifies("FR-GEST-07")
    fun aLongPressedSearchResultTicksOnce() {
        launcher.show(launcher().copy(layer = Layer.AppGrid))
        search("ma")

        // The clickable's own long-press haptic, through the launcher's gate.
        assertEquals(listOf(LONG_PRESS), longPress(gridApp("Maps")))
        assertTrue(Action.OpenContextMenu(MenuTarget.App(maps.id)) in launcher.dispatched)
    }

    @Test
    @Verifies("FR-GEST-07", "FR-TOP-06")
    fun aLongPressOnAnEmptyPartOfHomeTicksOnce() {
        launcher.show(launcher())

        assertEquals(listOf(LONG_PRESS), longPressEmptyHome())
        assertEquals(Layer.TopMenu, state.layer)
    }

    @Test
    @Verifies("FR-GEST-05")
    fun thePulleyTicksWheneverItsHighlightMovesAndLettingGoAboveTheFirstItemCancels() {
        launcher.show(launcher().copy(layer = Layer.AppGrid))
        openTools()
        val dispatched = launcher.dispatched.size
        launcher.haptics.clear()

        // Down to "Remove folder", back up to "Rename", then above it.
        val depths = listOf(2.5f, 1.5f, 0.5f).map { launcher.pullDistance(rows = it, items = 2) }
        rule.onNode(hasText("Tools") and isHeading() and inPanel()).performTouchInput {
            down(center)
            var at = 0f
            for (depth in depths) {
                repeat(STEPS) { moveBy(Offset(0f, (depth - at) / STEPS)) }
                at = depth
            }
            moveBy(Offset.Zero, delayMillis = HOLD_MILLIS)
            up()
        }
        rule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        rule.waitForIdle()

        assertEquals(listOf(TICK, TICK, TICK), launcher.haptics, "Rename, Remove folder, then Rename again")
        assertEquals(emptyList(), launcher.dispatched.drop(dispatched), "Nothing was chosen")
        assertNull(state.remorse)
        rule.onNode(hasSetTextAction() and inPanel()).assertDoesNotExist()
    }

    @Test
    @Verifies("FR-GEST-08", "NFR-A11Y-06")
    fun withHapticFeedbackOffLongPressesStillWorkButNothingTicks() {
        launcher.show(launcher(Haptics.Off))

        assertEquals(emptyList(), longPress(cover("Maps")), "A cover")
        assertTrue(Action.OpenContextMenu(MenuTarget.Cover(maps.id)) in launcher.dispatched)
        go(Action.CloseContextMenu)

        assertEquals(emptyList(), longPressEmptyHome(), "Empty Home")
        assertEquals(Layer.TopMenu, state.layer)
        go(Action.Back)

        go(Action.LayerSettled(Layer.AppGrid))
        assertEquals(emptyList(), longPress(gridApp("Mail")), "An app")
        assertTrue(Action.OpenContextMenu(MenuTarget.App(mail.id)) in launcher.dispatched)
        go(Action.CloseContextMenu)

        search("ma")
        assertEquals(emptyList(), longPress(gridApp("Maps")), "A search result")
        assertTrue(Action.OpenContextMenu(MenuTarget.App(maps.id)) in launcher.dispatched)
    }

    @Test
    @Verifies("FR-GEST-08", "NFR-A11Y-06")
    fun withHapticFeedbackOffThePulleyStillChoosesButNothingTicks() {
        launcher.show(launcher(Haptics.Off).copy(layer = Layer.AppGrid))
        openTools()
        launcher.haptics.clear()

        launcher.pull(rule.onNode(hasText("Tools") and isHeading() and inPanel()), toItem = 1, items = 2)
        rule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        rule.waitForIdle()

        assertEquals(emptyList(), launcher.haptics)
        assertEquals(Destructive.RemoveFolder(tools.id), state.remorse?.action, "Dispatched: ${launcher.dispatched}")
    }

    private companion object {
        val LONG_PRESS = HapticFeedbackType.LongPress
        val TICK = HapticFeedbackType.SegmentTick

        const val STEPS = 10
        const val HOLD_MILLIS = 300L

        /** Longer than the pulley's flash and a settle. */
        const val SETTLE_MILLIS = 1_000L
    }
}
