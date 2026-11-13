package io.github.twonet.selfish.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeWithVelocity
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.twonet.selfish.R
import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.Animations
import io.github.twonet.selfish.domain.state.Action
import io.github.twonet.selfish.domain.state.LauncherState
import io.github.twonet.selfish.domain.state.Layer
import io.github.twonet.selfish.ui.TestLauncher.Companion.app
import io.github.twonet.selfish.ui.TestLauncher.Companion.launcher
import io.github.twonet.selfish.ui.grid.AppGridTags
import io.github.twonet.selfish.ui.home.HomeTags
import io.github.twonet.selfish.ui.tokens.Motion
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Moving between Home and the App Grid (level I, DESIGN §8.3): the swipes,
 * their thresholds and the screen-reader way in, and catching a transition
 * in flight. Events' own swipes are in `EventsTest`, and the Top Menu's in
 * `TopMenuTest`.
 */
@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val launcher = TestLauncher(rule)
    private val apps = (1..60).map { app("App %02d".format(it)) }

    private fun root() = rule.onNodeWithTag(LauncherTags.ROOT)

    private fun gridApp(label: String) = hasText(label) and hasClickAction() and hasAnyAncestor(hasTestTag(AppGridTags.GRID))

    /** Drags by [fraction] of the screen's height (negative is up), then holds still so the release has no speed. */
    private fun dragAndHold(fraction: Float) = root().performTouchInput {
        down(center)
        val steps = 10
        repeat(steps) { moveBy(Offset(0f, height * fraction / steps)) }
        moveBy(Offset.Zero, delayMillis = HOLD_MILLIS)
        up()
    }

    @Test
    @Verifies("FR-GRID-01", "FR-GEST-01")
    fun swipingUpOnHomeOpensTheAppGrid() {
        launcher.show(launcher(apps, home = apps.take(2)))

        root().performTouchInput { swipeUp(startY = centerY, endY = top + height / 10) }
        rule.waitForIdle()

        assertEquals(Layer.AppGrid, launcher.state.layer)
        rule.onNodeWithTag(AppGridTags.SEARCH).assertIsDisplayed()
        rule.onNode(gridApp("App 01")).assertIsDisplayed()
    }

    @Test
    @Verifies("FR-GRID-03")
    fun theGridOpensAtTheTopWithTheSearchFieldAboveTheFirstRow() {
        launcher.show(launcher(apps, layer = Layer.AppGrid))
        rule.onNodeWithTag(AppGridTags.GRID).performTouchInput { swipeUp() }
        rule.onNodeWithTag(AppGridTags.GRID).performTouchInput { swipeUp() }
        rule.waitForIdle()
        rule.onNode(gridApp("App 01")).assertDoesNotExist()

        launcher.dispatch(Action.Back)
        rule.waitForIdle()
        root().performTouchInput { swipeUp(startY = centerY, endY = top + height / 10) }
        rule.waitForIdle()

        val search = rule.onNodeWithTag(AppGridTags.SEARCH).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val first = rule.onNode(gridApp("App 01")).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(search.bottom <= first.top, "The search field is above the first row: $search, $first")
    }

    @Test
    @Verifies("FR-GRID-04")
    fun draggingDownAtTheTopClosesTheGrid() {
        launcher.show(launcher(apps, home = apps.take(1), layer = Layer.AppGrid))

        rule.onNodeWithTag(AppGridTags.GRID).performTouchInput { swipeDown(startY = top + height / 5, endY = bottom) }
        rule.waitForIdle()

        assertEquals(Layer.Home, launcher.state.layer)
    }

    @Test
    @Verifies("FR-GRID-04", "FR-GEST-02", "AC-05")
    fun aShortSlowDragPeeksAndReturnsWhileALongOneCompletes() {
        launcher.show(launcher(apps, home = apps.take(1)))

        dragAndHold(-0.2f)
        rule.waitForIdle()
        assertEquals(Layer.Home, launcher.state.layer, "20% and a slow release return to Home")
        rule.onNodeWithTag(HomeTags.COVERS).assertIsDisplayed()

        dragAndHold(-0.4f)
        rule.waitForIdle()
        assertEquals(Layer.AppGrid, launcher.state.layer, "40% opens the grid")

        dragAndHold(0.2f)
        rule.waitForIdle()
        assertEquals(Layer.AppGrid, launcher.state.layer, "20% down peeks and stays open")

        dragAndHold(0.4f)
        rule.waitForIdle()
        assertEquals(Layer.Home, launcher.state.layer, "40% down closes")
    }

    @Test
    @Verifies("FR-GEST-02", "AC-05")
    fun aShortFlingUpOpensTheGrid() {
        launcher.show(launcher(apps, home = apps.take(1)))

        // Well short of 30%, but fast.
        root().performTouchInput {
            swipeWithVelocity(start = center, end = Offset(centerX, centerY - height * 0.1f), endVelocity = FLING_VELOCITY)
        }
        rule.waitForIdle()

        assertEquals(Layer.AppGrid, launcher.state.layer)
        rule.onNode(gridApp("App 01")).assertIsDisplayed()
    }

    @Test
    @Verifies("FR-GRID-01", "FR-GEST-04")
    fun aSwipeThatStartsInASystemGestureAreaIsLeftToTheSystem() {
        launcher.show(launcher(apps, home = apps.take(1)), gestureInsets = WindowInsets(bottom = GESTURE_AREA))

        root().performTouchInput { swipeUp(startY = bottom - 1f, endY = top + height / 10) }
        rule.waitForIdle()

        assertEquals(Layer.Home, launcher.state.layer)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    @Verifies("FR-GEST-09", "NFR-A11Y-02")
    fun screenReadersOpenTheGridWithoutSwiping() {
        launcher.show(launcher(apps, home = apps.take(1)))

        rule.onNodeWithTag(HomeTags.SCREEN).performCustomAccessibilityActionWithLabel(context.getString(R.string.action_show_apps))
        rule.waitForIdle()

        assertEquals(Layer.AppGrid, launcher.state.layer)
        rule.onNode(gridApp("App 01")).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    @Verifies("FR-GEST-03", "FR-EVT-01")
    fun touchingATransitionInFlightCatchesItAndDragsItBack() {
        launcher.show(launcher(apps, home = apps.take(1)))
        rule.mainClock.autoAdvance = false

        // Events starts sliding in, and moments later is about a quarter of the way.
        rule.onNodeWithTag(HomeTags.SCREEN).performCustomAccessibilityActionWithLabel(context.getString(R.string.action_show_events))
        rule.mainClock.advanceTimeBy(IN_FLIGHT_MILLIS)

        // A short drag back: from a resting Events it would only peek.
        root().performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(width * 0.025f, 0f)) }
            moveBy(Offset.Zero, delayMillis = HOLD_MILLIS)
            up()
        }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()

        assertEquals(Layer.Home, launcher.state.layer)
        rule.onNodeWithTag(HomeTags.COVERS).assertIsDisplayed()
    }

    @Test
    @Verifies("FR-GEST-03", "FR-GRID-04")
    fun touchingTheGridAsItClosesCatchesItAndPushesItBackUp() {
        launcher.show(launcher(apps, home = apps.take(1), layer = Layer.AppGrid))
        rule.mainClock.autoAdvance = false

        // Back starts the grid sliding down; moments later it is still mostly open.
        launcher.dispatch(Action.Back)
        rule.mainClock.advanceTimeBy(IN_FLIGHT_MILLIS)

        // A short drag up: caught mid-flight, it goes back the other way.
        root().performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(0f, -height * 0.01f)) }
            moveBy(Offset.Zero, delayMillis = HOLD_MILLIS)
            up()
        }
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()

        assertEquals(Layer.AppGrid, launcher.state.layer)
        rule.onNode(gridApp("App 01")).assertIsDisplayed()
    }

    @Test
    @Verifies("FR-ANIM-15", "NFR-A11Y-05", "AC-09", "FR-GEST-01")
    fun withTheSystemsAnimationsRemovedDragsFollowTheFingerAndViewsChangeAtOnce() =
        dragsFollowAndViewsChangeAtOnce(launcher(apps, home = apps.take(1)).copy(systemAnimationsOff = true))

    @Test
    @Verifies("FR-ANIM-15", "NFR-A11Y-05")
    fun withAnimationsReducedDragsFollowTheFingerAndViewsChangeAtOnce() =
        dragsFollowAndViewsChangeAtOnce(launcher(apps, home = apps.take(1)).let { it.copy(settings = it.settings.copy(animations = Animations.Reduced)) })

    /**
     * Under reduced motion (AC-09): the App Grid follows the finger as before,
     * and once released it is open within 100 ms; Back closes it as fast.
     */
    private fun dragsFollowAndViewsChangeAtOnce(state: LauncherState) {
        launcher.show(state)
        val screen = root().fetchSemanticsNode().boundsInRoot
        rule.mainClock.autoAdvance = false

        root().performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(0f, -height * 0.04f)) }
        }
        rule.mainClock.advanceTimeByFrame()
        val following = rule.onNodeWithTag(AppGridTags.GRID).fetchSemanticsNode().boundsInRoot.top
        assertEquals(screen.height * 0.6f, following, screen.height * 0.01f, "40% open, under the finger")

        root().performTouchInput { up() }
        rule.mainClock.advanceTimeBy(Motion.REDUCED_MILLIS.toLong())
        assertEquals(Layer.AppGrid, launcher.state.layer)
        assertEquals(0f, rule.onNodeWithTag(AppGridTags.GRID).fetchSemanticsNode().boundsInRoot.top, "Open within 100 ms")

        launcher.dispatch(Action.Back)
        rule.mainClock.advanceTimeBy(Motion.REDUCED_MILLIS.toLong())
        assertEquals(0f, rule.onNodeWithTag(HomeTags.SCREEN).fetchSemanticsNode().boundsInRoot.top, "Home is back within 100 ms")
    }

    private companion object {
        const val HOLD_MILLIS = 300L

        /** Faster than any device's minimum fling velocity, in pixels per second. */
        const val FLING_VELOCITY = 5_000f

        /** How long a settling transition runs before a test touches it. */
        const val IN_FLIGHT_MILLIS = 50L
        val GESTURE_AREA = 48.dp
    }
}
