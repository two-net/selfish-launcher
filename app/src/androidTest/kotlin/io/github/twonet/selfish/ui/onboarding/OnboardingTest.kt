package io.github.twonet.selfish.ui.onboarding

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.twonet.selfish.R
import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.GridItem
import io.github.twonet.selfish.domain.model.Settings
import io.github.twonet.selfish.domain.ports.DefaultApps
import io.github.twonet.selfish.domain.state.AccessFeature
import io.github.twonet.selfish.domain.state.Action
import io.github.twonet.selfish.domain.state.Layer
import io.github.twonet.selfish.domain.state.LauncherState
import io.github.twonet.selfish.domain.state.Tutorial
import io.github.twonet.selfish.domain.state.TutorialStep
import io.github.twonet.selfish.ui.LauncherTags
import io.github.twonet.selfish.ui.TestLauncher
import io.github.twonet.selfish.ui.TestLauncher.Companion.app
import io.github.twonet.selfish.ui.TestLauncher.Companion.launcher
import io.github.twonet.selfish.ui.events.EventsTags
import io.github.twonet.selfish.ui.grid.AppGridTags
import io.github.twonet.selfish.ui.home.HomeTags
import io.github.twonet.selfish.ui.settings.SettingsTags
import io.github.twonet.selfish.ui.topmenu.TopMenuTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The first run and its tutorial (SRS §4.14), level I (DESIGN §8.3). Actions
 * go through the real reducer, as in the store; effects such as the
 * default-apps lookup and the access request are played by hand or only
 * recorded.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class OnboardingTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val launcher = TestLauncher(rule)
    private val state get() = launcher.state

    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    private val books = app("Books")
    private val maps = app("Maps")
    private val phone = app("Phone")
    private val zoom = app("Zoom")

    private fun root() = rule.onNodeWithTag(LauncherTags.ROOT)

    private fun go(action: Action) {
        rule.runOnIdle { launcher.dispatch(action) }
        rule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        rule.waitForIdle()
    }

    /**
     * A first run on screen, as the store would play it: nothing saved, the
     * catalog out of order, then the default apps (FR-ONB-01).
     */
    private fun firstRun(defaults: DefaultApps = DefaultApps(phone = phone.id)) {
        launcher.show(LauncherState())
        go(Action.Restored(null, Settings()))
        go(Action.CatalogChanged(listOf(zoom, phone, books, maps)))
        go(Action.DefaultAppsResolved(defaults))
        go(Action.HomeShown)
    }

    /** The tutorial card shows [text] at [step] of 3. */
    private fun assertCard(text: Int, step: Int) {
        rule.onNode(hasText(string(text)) and inCard()).assertIsDisplayed()
        rule.onNode(hasText(string(R.string.tutorial_progress, step, 3)) and inCard()).assertIsDisplayed()
        rule.onNodeWithTag(TutorialTags.HINT).assertExists()
    }

    private fun inCard() = hasAnyAncestor(hasTestTag(TutorialTags.CARD))

    private fun swipeUpOnHome() {
        root().performTouchInput { swipeUp(startY = centerY, endY = top + height / 10) }
        rule.waitForIdle()
    }

    private fun swipeSideways(left: Boolean) {
        root().performTouchInput {
            if (left) swipeLeft(startX = width * 0.8f, endX = width * 0.2f) else swipeRight(startX = width * 0.2f, endX = width * 0.8f)
        }
        rule.waitForIdle()
    }

    private fun swipeDownOnHome() {
        root().performTouchInput { swipeDown(startY = centerY, endY = bottom - height / 10) }
        rule.waitForIdle()
    }

    /** Performs the custom accessibility action labeled [label] on the node tagged [tag], as from TalkBack's actions menu. */
    private fun action(tag: String, label: Int) {
        rule.onNodeWithTag(tag).performCustomAccessibilityActionWithLabel(string(label))
        rule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        rule.waitForIdle()
    }

    // First run (FR-ONB-01)

    @Test
    @Verifies("FR-ONB-01", "FR-GRID-05", "FR-HOME-07", "FR-ONB-02")
    fun aFirstRunSortsTheGridFillsHomeAndStartsTheTutorial() {
        firstRun()

        assertEquals(listOf(books, maps, phone, zoom).map { GridItem.App(it.id) }, state.layout?.grid)
        rule.onNode(hasText("Phone") and hasAnyAncestor(hasTestTag(HomeTags.COVERS))).assertIsDisplayed()
        assertCard(R.string.tutorial_step_apps, step = 1)
    }

    @Test
    @Verifies("FR-ONB-02", "FR-HOME-08")
    fun onAnEmptyHomeTheTutorialTakesThePlaceOfTheSwipeUpHint() {
        firstRun(defaults = DefaultApps())

        rule.onNodeWithTag(HomeTags.HINT).assertDoesNotExist()
        assertCard(R.string.tutorial_step_apps, step = 1)

        rule.onNodeWithTag(TutorialTags.SKIP).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(HomeTags.HINT).assertExists()
    }

    // The steps (FR-ONB-02)

    @Test
    @Verifies("FR-ONB-02", "FR-ANIM-13", "FR-GRID-01", "FR-EVT-01", "FR-TOP-01")
    fun eachStepIsDoneWithItsRealGestureAndTheViewSaysHowToGoBack() {
        firstRun()

        swipeUpOnHome()
        assertEquals(Layer.AppGrid, state.layer)
        assertCard(R.string.tutorial_back_apps, step = 1)
        rule.onNodeWithTag(AppGridTags.GRID).performTouchInput { swipeDown(startY = top + height / 5, endY = bottom) }
        rule.waitForIdle()
        assertEquals(Layer.Home, state.layer)
        assertCard(R.string.tutorial_step_events, step = 2)

        swipeSideways(left = true)
        assertEquals(Layer.Events, state.layer)
        assertCard(R.string.tutorial_back_events, step = 2)
        swipeSideways(left = false)
        assertEquals(Layer.Home, state.layer)
        assertCard(R.string.tutorial_step_top_menu, step = 3)

        swipeDownOnHome()
        assertEquals(Layer.TopMenu, state.layer)
        assertCard(R.string.tutorial_back_top_menu, step = 3)
        // From where the card is: the swipe goes to the Top Menu all the same.
        root().performTouchInput { swipeUp(startY = bottom - height / 10, endY = top + height / 10) }
        rule.waitForIdle()

        assertEquals(Layer.Home, state.layer)
        assertNull(state.tutorial)
        rule.onNodeWithTag(TutorialTags.CARD).assertDoesNotExist()
    }

    @Test
    @Verifies("FR-ONB-02", "FR-GEST-02")
    fun aPeekOrAnotherViewDoesNotDoTheStep() {
        firstRun()

        root().performTouchInput {
            down(center)
            repeat(STEPS) { moveBy(Offset(0f, -height * PEEK / STEPS)) }
            moveBy(Offset.Zero, delayMillis = HOLD_MILLIS)
            up()
        }
        rule.waitForIdle()
        assertEquals(Layer.Home, state.layer)
        assertEquals(Tutorial(TutorialStep.Apps), state.tutorial)

        swipeSideways(left = true)
        assertEquals(Layer.Events, state.layer)
        rule.onNodeWithTag(TutorialTags.CARD).assertDoesNotExist()

        go(Action.Back)
        assertCard(R.string.tutorial_step_apps, step = 1)
    }

    @Test
    @Verifies("FR-ONB-02", "FR-GEST-04")
    fun aSwipeThatStartsOnTheCardStillOpensTheAppGrid() {
        firstRun()
        val screen = root().fetchSemanticsNode().size.height

        rule.onNodeWithTag(TutorialTags.CARD).performTouchInput { swipeUp(startY = centerY, endY = centerY - screen * 0.6f) }
        rule.waitForIdle()

        assertEquals(Layer.AppGrid, state.layer)
        assertEquals(Tutorial(TutorialStep.Apps, reached = true), state.tutorial)
    }

    @Test
    @Verifies("FR-ONB-02", "FR-GEN-03", "FR-GEN-04")
    fun backAndHomeBringTheNextStep() {
        firstRun()
        swipeUpOnHome()

        go(Action.Back)
        assertCard(R.string.tutorial_step_events, step = 2)

        swipeSideways(left = false)
        assertEquals(Layer.Events, state.layer)
        go(Action.HomePressed)
        assertCard(R.string.tutorial_step_top_menu, step = 3)
    }

    @Test
    @Verifies("FR-ONB-02")
    fun skipEndsTheTutorialOnHomeAndOnAStepsView() {
        firstRun()
        rule.onNodeWithTag(TutorialTags.SKIP).performClick()
        rule.waitForIdle()
        assertNull(state.tutorial)
        rule.onNodeWithTag(TutorialTags.CARD).assertDoesNotExist()

        go(Action.ReplayTutorial)
        swipeUpOnHome()
        go(Action.Back)
        swipeSideways(left = true)
        go(Action.Back)
        swipeDownOnHome()
        assertEquals(Layer.TopMenu, state.layer)

        // Over the Top Menu's catch layer, which would close it.
        rule.onNodeWithTag(TutorialTags.SKIP).performClick()
        rule.waitForIdle()
        assertNull(state.tutorial)
        assertEquals(Layer.TopMenu, state.layer, "Skipping leaves the menu open")
    }

    @Test
    @Verifies("FR-ONB-02", "FR-GEST-09", "NFR-A11Y-02")
    fun aScreenReaderDoesEachStepWithHomesActionsAndHearsEachOne() {
        firstRun()
        rule.onNode(hasText(string(R.string.tutorial_step_apps)) and inCard())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))

        action(HomeTags.SCREEN, R.string.action_show_apps)
        assertEquals(Tutorial(TutorialStep.Apps, reached = true), state.tutorial)
        go(Action.Back)

        action(HomeTags.SCREEN, R.string.action_show_events)
        assertCard(R.string.tutorial_back_events, step = 2)
        action(EventsTags.SCREEN, R.string.action_show_home)

        action(HomeTags.SCREEN, R.string.action_show_top_menu)
        assertCard(R.string.tutorial_back_top_menu, step = 3)
        action(TopMenuTags.MENU, R.string.action_show_home)

        assertNull(state.tutorial)
    }

    // Replay (FR-ONB-03)

    @Test
    @Verifies("FR-ONB-03", "FR-GEST-05")
    fun settingsPulleyMenuReplaysTheTutorialOnHome() {
        launcher.show(launcher(listOf(books, maps), home = listOf(maps), layer = Layer.Settings))

        launcher.pull(rule.onNodeWithTag(SettingsTags.PAGE), toItem = 0, items = 2)
        rule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        rule.waitForIdle()

        assertTrue(Action.ReplayTutorial in launcher.dispatched)
        assertEquals(Layer.Home, state.layer)
        rule.onNodeWithTag(SettingsTags.PAGE).assertDoesNotExist()
        assertCard(R.string.tutorial_step_apps, step = 1)
    }

    @Test
    @Verifies("FR-ONB-03", "FR-GEST-09")
    fun settingsHeaderActionAndRowReplayTheTutorial() {
        launcher.show(launcher(listOf(books, maps), home = listOf(maps), layer = Layer.Settings))
        action(SettingsTags.HEADER, R.string.menu_replay_tutorial)
        assertEquals(Tutorial(TutorialStep.Apps), state.tutorial)
        assertEquals(Layer.Home, state.layer)

        go(Action.SkipTutorial)
        go(Action.OpenSettings)
        rule.onNodeWithTag(SettingsTags.REPLAY_ROW).performScrollTo().performClick()
        rule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        rule.waitForIdle()
        assertCard(R.string.tutorial_step_apps, step = 1)
    }

    // Special access (FR-ONB-04)

    @Test
    @Verifies("FR-ONB-04", "FR-SET-12", "FR-GEN-04")
    fun turningNotificationsOnExplainsTheAccessBeforeAskingAndNotNowOrBackAskNothing() {
        launcher.show(launcher(listOf(books, maps), layer = Layer.Settings).copy(notificationAccess = false))
        val switch = hasText(string(R.string.settings_notifications_in_events)) and hasClickAction() and
            hasAnyAncestor(hasTestTag(SettingsTags.LIST))
        fun turnOn() {
            rule.onNode(switch).performScrollTo().performClick()
            rule.mainClock.advanceTimeBy(SETTLE_MILLIS)
            rule.waitForIdle()
        }

        turnOn()
        rule.onNode(hasText(string(R.string.settings_notifications_in_events)) and hasAnyAncestor(hasTestTag(AccessTags.PAGE)))
            .assertIsDisplayed()
        rule.onNode(hasText(string(R.string.access_notifications_body))).assertIsDisplayed()
        rule.onNode(hasText(string(R.string.access_notifications_privacy))).assertIsDisplayed()
        assertFalse(state.awaitingNotificationAccess, "Nothing is asked yet")

        rule.onNodeWithTag(AccessTags.NOT_NOW).performClick()
        rule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        rule.waitForIdle()
        rule.onNodeWithTag(AccessTags.PAGE).assertDoesNotExist()
        rule.onNode(switch).assertIsOff()
        assertFalse(state.awaitingNotificationAccess)

        turnOn()
        go(Action.Back)
        rule.onNodeWithTag(AccessTags.PAGE).assertDoesNotExist()
        assertEquals(Layer.Settings, state.layer)
        assertFalse(state.awaitingNotificationAccess)

        turnOn()
        rule.onNodeWithTag(AccessTags.CONTINUE).performClick()
        rule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        rule.waitForIdle()
        assertTrue(Action.RequestAccess(AccessFeature.NotificationsInEvents) in launcher.dispatched)
        assertTrue(state.awaitingNotificationAccess, "Asked on Continue")

        go(Action.NotificationAccessChanged(granted = true))
        assertTrue(state.settings.notificationsInEvents)
    }

    private companion object {
        const val SETTLE_MILLIS = 1_000L
        const val STEPS = 10
        const val HOLD_MILLIS = 300L

        /** A drag of 20% of the screen, short of the 30% that completes it (FR-GEST-02). */
        const val PEEK = 0.2f
    }
}
