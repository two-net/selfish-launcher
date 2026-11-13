package io.github.twonet.selfish.ui.events

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.twonet.selfish.R
import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.Settings
import io.github.twonet.selfish.domain.model.WallpaperEntry
import io.github.twonet.selfish.domain.ports.ClockInfo
import io.github.twonet.selfish.domain.ports.ImageId
import io.github.twonet.selfish.domain.ports.NotificationItem
import io.github.twonet.selfish.domain.remorse.Destructive
import io.github.twonet.selfish.domain.state.Action
import io.github.twonet.selfish.domain.state.Layer
import io.github.twonet.selfish.domain.state.LauncherState
import io.github.twonet.selfish.ui.LauncherTags
import io.github.twonet.selfish.ui.TestLauncher
import io.github.twonet.selfish.ui.TestLauncher.Companion.app
import io.github.twonet.selfish.ui.TestLauncher.Companion.launcher
import io.github.twonet.selfish.ui.components.ClockFormat
import io.github.twonet.selfish.ui.home.HomeTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Events as the user sees it (level I, DESIGN §8.3), through
 * [TestLauncher]: the sideways swipes, the header, the wallpaper card, the
 * notifications and the pulley menu. How the pulley's ticks feel, and real
 * notifications, are checked by hand (REVIEW-CHECKLIST.md).
 */
@RunWith(AndroidJUnit4::class)
class EventsTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val launcher = TestLauncher(rule)
    private val state get() = launcher.state

    private fun string(id: Int) = context.getString(id)

    private val apps = listOf(app("Chat"), app("Mail"))
    private val chat = apps[0]
    private val mail = apps[1]
    private val now = Instant.parse("2026-11-13T14:05:00Z")
    private val clock = ClockInfo(now, ZoneId.of("UTC"), is24Hour = true)
    private val wallpaper = WallpaperEntry(ImageId("20261113"), "A lighthouse at dusk", "© A Photographer", "https://www.bing.com/search?q=lighthouse")

    private fun note(key: String, app: AppEntry, title: String, minutesAgo: Long, clearable: Boolean = true): NotificationItem {
        val time = now.minusSeconds(minutesAgo * 60).toEpochMilli()
        return NotificationItem(key, app.id.packageName, app.id.userSerial, app.label, title, "About $title", time, time, clearable)
    }

    private val hello = note("hello", chat, "Hello", 1)
    private val lunch = note("lunch", chat, "Lunch?", 30)
    private val invoice = note("invoice", mail, "Invoice", 10)
    private val music = note("music", mail, "Now playing", 60, clearable = false)

    /** The launcher on [layer], restored, on screen and with today's [clock]. */
    private fun at(layer: Layer = Layer.Events) = launcher(apps, home = apps, layer = layer).copy(clock = clock)

    /** Events with notifications on, listing [items]. */
    private fun withNotifications(vararg items: NotificationItem) = at().copy(
        settings = Settings(notificationsInEvents = true),
        notificationAccess = true,
        notifications = items.toList(),
    )

    private fun show(state: LauncherState) = launcher.show(state, gestureInsets = TestLauncher.NO_INSETS)

    private fun root() = rule.onNodeWithTag(LauncherTags.ROOT)

    private fun inEvents() = hasAnyAncestor(hasTestTag(EventsTags.SCREEN))

    /** A notification's row, one node with all its text, as screen readers read it. */
    private fun row(title: String): SemanticsMatcher = hasTestTag(EventsTags.NOTIFICATION) and hasClickAction() and hasText(title)

    private fun top(matcher: SemanticsMatcher): Float = rule.onNode(matcher).fetchSemanticsNode().boundsInRoot.top

    private fun swipe(left: Boolean) {
        root().performTouchInput {
            if (left) swipeLeft(startX = width * 0.8f, endX = width * 0.2f) else swipeRight(startX = width * 0.2f, endX = width * 0.8f)
        }
        rule.waitForIdle()
    }

    /** Drags sideways by [fraction] of the screen's width (negative is left), then holds still so the release has no speed. */
    private fun dragAndHold(fraction: Float) = root().performTouchInput {
        down(center)
        repeat(STEPS) { moveBy(Offset(width * fraction / STEPS, 0f)) }
        moveBy(Offset.Zero, delayMillis = HOLD_MILLIS)
        up()
    }

    // Moving between Home and Events (FR-EVT-01)

    @Test
    @Verifies("FR-EVT-01", "FR-GEST-01")
    fun swipingLeftOrRightOnHomeOpensEventsAndEitherWayOnEventsReturns() {
        show(at(Layer.Home))

        swipe(left = true)
        assertEquals(Layer.Events, state.layer, "Left on Home")
        rule.onNodeWithTag(EventsTags.HEADER).assertIsDisplayed()

        swipe(left = true)
        assertEquals(Layer.Home, state.layer, "Left on Events")
        rule.onNodeWithTag(HomeTags.COVERS).assertIsDisplayed()

        swipe(left = false)
        assertEquals(Layer.Events, state.layer, "Right on Home")

        swipe(left = false)
        assertEquals(Layer.Home, state.layer, "Right on Events")
    }

    @Test
    @Verifies("FR-EVT-01", "FR-GEST-02")
    fun aShortSidewaysDragPeeksBackAndALongerOneOpensEvents() {
        show(at(Layer.Home))

        dragAndHold(-0.2f)
        rule.waitForIdle()
        assertEquals(Layer.Home, state.layer, "20% peeks")

        dragAndHold(0.4f)
        rule.waitForIdle()
        assertEquals(Layer.Events, state.layer, "40% opens Events")

        dragAndHold(0.2f)
        rule.waitForIdle()
        assertEquals(Layer.Events, state.layer, "20% on Events peeks")
    }

    @Test
    @Verifies("FR-EVT-01", "FR-GEST-04")
    fun aSidewaysSwipeFromASystemGestureAreaIsLeftToTheSystem() {
        launcher.show(at(Layer.Home), gestureInsets = WindowInsets(left = GESTURE_AREA, right = GESTURE_AREA))

        root().performTouchInput { swipeLeft(startX = right - 1f, endX = width * 0.2f) }
        rule.waitForIdle()

        assertEquals(Layer.Home, state.layer)
    }

    @Test
    @Verifies("FR-EVT-01", "FR-GRID-01")
    fun upOnHomeStillOpensTheAppGridAndNotEvents() {
        show(at(Layer.Home))

        root().performTouchInput { swipeUp(startY = centerY, endY = top + height / 10) }
        rule.waitForIdle()

        assertEquals(Layer.AppGrid, state.layer)
    }

    @Test
    @Verifies("FR-EVT-01", "FR-GEN-03", "FR-GEN-04")
    fun backAndHomeReturnFromEvents() {
        show(at())

        launcher.dispatch(Action.Back)
        rule.waitForIdle()
        assertEquals(Layer.Home, state.layer)
        rule.onNodeWithTag(HomeTags.COVERS).assertIsDisplayed()

        launcher.dispatch(Action.LayerSettled(Layer.Events))
        launcher.dispatch(Action.HomePressed)
        rule.waitForIdle()
        assertEquals(Layer.Home, state.layer)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    @Verifies("FR-GEST-09", "NFR-A11Y-02", "AC-10")
    fun screenReadersOpenEventsAndReturnWithoutSwiping() {
        show(at(Layer.Home))

        rule.onNodeWithTag(HomeTags.SCREEN).performCustomAccessibilityActionWithLabel(string(R.string.action_show_events))
        rule.waitForIdle()
        assertEquals(Layer.Events, state.layer)
        rule.onNodeWithTag(EventsTags.HEADER).assertIsDisplayed()

        rule.onNodeWithTag(EventsTags.SCREEN).performCustomAccessibilityActionWithLabel(string(R.string.action_show_home))
        rule.waitForIdle()
        assertEquals(Layer.Home, state.layer)
    }

    // The header and the wallpaper card (FR-EVT-02, FR-EVT-03)

    @Test
    @Verifies("FR-EVT-02", "UI-02", "NFR-L10N-02")
    fun theHeaderIsTodaysDate() {
        show(at())

        val locale = context.resources.configuration.locales[0]
        val today = ClockFormat.formatter(ClockFormat.DATE, locale, clock.zone).format(Date.from(now))
        rule.onNode(hasText(today) and isHeading() and inEvents()).assertIsDisplayed()
    }

    @Test
    @Verifies("FR-EVT-03", "FR-WP-10")
    fun theWallpaperCardShowsTheImagesTitleAndCopyrightAndLearnMoreOpensItsLink() {
        show(at().copy(wallpaper = wallpaper))

        rule.onNodeWithText(string(R.string.events_wallpaper_title)).assertIsDisplayed()
        rule.onNodeWithText(wallpaper.title).assertIsDisplayed()
        rule.onNodeWithText(wallpaper.copyright).assertIsDisplayed()
        rule.onNodeWithTag(EventsTags.THUMBNAIL, useUnmergedTree = true).assertIsDisplayed()

        rule.onNodeWithText(string(R.string.events_learn_more)).performClick()
        rule.waitForIdle()

        assertTrue(Action.OpenLink(wallpaper.link!!) in launcher.dispatched)
        assertEquals(Layer.Events, state.layer)
    }

    @Test
    @Verifies("FR-EVT-03")
    fun tappingTheWallpaperCardOpensWallpaperDetails() {
        show(at().copy(wallpaper = wallpaper))

        rule.onNodeWithTag(EventsTags.WALLPAPER).performClick()
        rule.waitForIdle()

        assertEquals(Layer.WallpaperDetails, state.layer)
    }

    @Test
    @Verifies("FR-EVT-03")
    fun withoutAWallpaperEntryThereIsNoCard() {
        show(at())

        rule.onNodeWithTag(EventsTags.WALLPAPER).assertDoesNotExist()
    }

    // Notifications (FR-EVT-04, FR-EVT-05)

    @Test
    @Verifies("FR-EVT-04")
    fun notificationsAreListedByAppWithTheNewestFirst() {
        show(withNotifications(hello, lunch, invoice, music))

        rule.onNode(hasText("Chat") and inEvents()).assertIsDisplayed()
        rule.onNode(hasText("Mail") and inEvents()).assertIsDisplayed()
        val order = listOf("Chat", "Hello", "Lunch?", "Mail", "Invoice", "Now playing").map { label ->
            top(hasText(label) and inEvents())
        }
        assertEquals(order.sorted(), order, "Chat has the newest; within each app, the newest first")
        rule.onNode(hasText("About Hello") and inEvents()).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.events_no_new_events)).assertDoesNotExist()
    }

    @Test
    @Verifies("FR-EVT-04")
    fun tappingANotificationOpensIt() {
        show(withNotifications(hello))

        rule.onNode(row("Hello")).performClick()
        rule.waitForIdle()

        val opened = launcher.dispatched.filterIsInstance<Action.OpenNotification>().single()
        assertEquals("hello", opened.key)
        assertTrue(opened.from != null, "It grows from the row")
    }

    @Test
    @Verifies("FR-EVT-05")
    fun swipingADismissibleNotificationSidewaysDismissesIt() {
        show(withNotifications(hello, invoice))

        rule.onNode(row("Hello")).performTouchInput { swipeLeft() }
        rule.waitForIdle()

        assertTrue(Action.DismissNotification("hello") in launcher.dispatched)
        assertEquals(Layer.Events, state.layer, "It stays on Events")
        rule.onNode(row("Hello")).assertDoesNotExist()
        rule.onNode(row("Invoice")).assertIsDisplayed()
    }

    @Test
    @Verifies("FR-EVT-05", "FR-EVT-01")
    fun aSidewaysSwipeThatStartsOutsideADismissibleNotificationSwitchesViews() {
        show(withNotifications(hello, music))

        rule.onNode(row("Now playing")).performTouchInput { swipeLeft(startX = width * 0.8f, endX = width * 0.1f) }
        rule.waitForIdle()

        assertEquals(Layer.Home, state.layer, "An ongoing notification can't be dismissed")
        assertTrue(launcher.dispatched.none { it is Action.DismissNotification })
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    @Verifies("FR-EVT-05", "FR-GEST-09")
    fun screenReadersDismissANotificationWithAnAction() {
        show(withNotifications(hello))

        rule.onNode(row("Hello")).performCustomAccessibilityActionWithLabel(string(R.string.action_dismiss_notification))
        rule.waitForIdle()

        assertTrue(Action.DismissNotification("hello") in launcher.dispatched)
    }

    // The pulley menu (FR-EVT-06, AC-06)

    @Test
    @Verifies("AC-06", "FR-EVT-06", "FR-GEST-05", "FR-GEST-06")
    fun pullingSlowlyToClearNotificationsWaitsAndTappingTheBannerCancels() {
        show(withNotifications(hello, invoice, music).copy(wallpaper = wallpaper))

        // Slowly down to the middle of the second item, "Clear notifications".
        launcher.pull(rule.onNodeWithTag(EventsTags.HEADER), toItem = 1, items = 2)
        rule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        rule.waitForIdle()

        val pending = state.remorse?.action
        assertEquals(Destructive.ClearNotifications(setOf(hello.ref, invoice.ref)), pending, "Dispatched: ${launcher.dispatched}")
        assertEquals(listOf(HapticFeedbackType.SegmentTick, HapticFeedbackType.SegmentTick), launcher.haptics, "A tick for each item")
        rule.onNode(row("Hello")).assertIsDisplayed()
        val banner = context.getString(R.string.remorse_cancel, string(R.string.remorse_clear_notifications))
        rule.onNode(hasContentDescription(banner) and hasTestTag(LauncherTags.REMORSE)).assertIsDisplayed().performClick()
        rule.waitForIdle()

        assertNull(state.remorse)
        assertEquals(listOf(hello, invoice, music), state.notifications, "Nothing was cleared")
        rule.onNodeWithTag(LauncherTags.REMORSE).assertDoesNotExist()
    }

    @Test
    @Verifies("FR-EVT-06", "FR-GEST-09")
    fun wallpaperDetailsAndClearNotificationsAreActionsOnTheHeader() {
        show(withNotifications(hello).copy(wallpaper = wallpaper))
        val actions = rule.onNodeWithTag(EventsTags.HEADER).fetchSemanticsNode().config[SemanticsActions.CustomActions]

        assertEquals(listOf(string(R.string.menu_wallpaper_details), string(R.string.menu_clear_notifications)), actions.map { it.label })

        rule.runOnIdle { actions.first().action() }
        rule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        rule.waitForIdle()
        assertEquals(Layer.WallpaperDetails, state.layer)
    }

    @Test
    @Verifies("FR-EVT-06")
    fun withNothingToClearOrDetailThereIsNoPulleyMenu() {
        show(withNotifications(music))

        val config = rule.onNodeWithTag(EventsTags.HEADER).fetchSemanticsNode().config
        assertTrue(SemanticsActions.CustomActions !in config)
    }

    // Notification access and "No new events" (FR-EVT-07, FR-EVT-08)

    @Test
    @Verifies("FR-EVT-07", "FR-ONB-04")
    fun withoutNotificationsACardOffersToTurnThemOn() {
        show(at().copy(notificationAccess = false))

        rule.onNodeWithText(string(R.string.events_notifications_card)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.events_notifications_turn_on)).performClick()
        rule.waitForIdle()

        assertTrue(Action.TurnOnNotifications in launcher.dispatched)
        assertTrue(state.awaitingNotificationAccess, "It asks for access first")

        // Access granted: the list replaces the card.
        launcher.dispatch(Action.NotificationAccessChanged(true))
        rule.waitForIdle()
        rule.onNodeWithText(string(R.string.events_notifications_card)).assertDoesNotExist()
        rule.onNodeWithText(string(R.string.events_no_new_events)).assertIsDisplayed()
    }

    @Test
    @Verifies("FR-EVT-07", "FR-EVT-08")
    fun hidingTheCardHidesItForGoodAndThenThereAreNoNewEvents() {
        show(at().copy(wallpaper = wallpaper))
        rule.onNodeWithText(string(R.string.events_no_new_events)).assertDoesNotExist()

        rule.onNodeWithText(string(R.string.events_notifications_hide)).performClick()
        rule.waitForIdle()

        assertTrue(state.settings.notificationsCardDismissed)
        rule.onNodeWithText(string(R.string.events_notifications_card)).assertDoesNotExist()
        rule.onNodeWithText(string(R.string.events_no_new_events)).assertIsDisplayed()
    }

    @Test
    @Verifies("FR-EVT-08")
    fun withNotificationsOnButNoneThereAreNoNewEvents() {
        show(withNotifications())

        rule.onNodeWithText(string(R.string.events_no_new_events)).assertIsDisplayed()
    }

    private companion object {
        const val STEPS = 10
        const val HOLD_MILLIS = 300L

        /** Longer than the pulley's flash and a settle. */
        const val SETTLE_MILLIS = 1_000L

        val GESTURE_AREA = 48.dp
    }
}
