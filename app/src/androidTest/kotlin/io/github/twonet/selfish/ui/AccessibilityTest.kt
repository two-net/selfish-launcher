package io.github.twonet.selfish.ui

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.twonet.selfish.R
import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.Settings
import io.github.twonet.selfish.domain.ports.ClockInfo
import io.github.twonet.selfish.domain.ports.NotificationItem
import io.github.twonet.selfish.domain.remorse.Destructive
import io.github.twonet.selfish.domain.state.Action
import io.github.twonet.selfish.domain.state.Layer
import io.github.twonet.selfish.ui.TestLauncher.Companion.app
import io.github.twonet.selfish.ui.TestLauncher.Companion.launcher
import io.github.twonet.selfish.ui.events.EventsTags
import io.github.twonet.selfish.ui.grid.AppGridTags
import io.github.twonet.selfish.ui.home.HomeTags
import io.github.twonet.selfish.ui.topmenu.TopMenuTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The launcher with a screen reader (level I, DESIGN §8.3, §6.7): every
 * gesture-only action is also an accessibility action (FR-GEST-09,
 * NFR-A11Y-02), so a TalkBack user gets everywhere without a swipe (AC-10).
 * These tests use only what a screen reader can do, semantics actions, and
 * no touch input. The other views' own actions are checked in their tests:
 * folders' in `FolderTest`, notifications' in `EventsTest`. TalkBack
 * itself is checked by hand (REVIEW-CHECKLIST.md).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AccessibilityTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val launcher = TestLauncher(rule)
    private val state get() = launcher.state

    private fun string(id: Int) = context.getString(id)

    private val maps = app("Maps")
    private val mail = app("Mail")
    private val apps = listOf(maps, mail)
    private val now = Instant.parse("2026-11-13T14:05:00Z")

    /** The launcher on [layer] with Maps as a cover and today's clock, which Events' header shows. */
    private fun at(layer: Layer = Layer.Home) =
        launcher(apps, home = listOf(maps), layer = layer).copy(clock = ClockInfo(now, ZoneId.of("UTC"), is24Hour = true))

    private fun note(key: String, title: String, clearable: Boolean = true) = now.toEpochMilli().let { time ->
        NotificationItem(key, mail.id.packageName, mail.id.userSerial, mail.label, title, "About $title", time, time, clearable)
    }

    private val invoice = note("invoice", "Invoice")
    private val download = note("download", "Downloading", clearable = false)

    private fun gridApp(label: String): SemanticsMatcher =
        hasText(label) and hasClickAction() and hasAnyAncestor(hasTestTag(AppGridTags.GRID))

    /** Performs the custom accessibility action labeled [label] on the node tagged [tag], as from TalkBack's actions menu. */
    private fun action(tag: String, label: Int) {
        rule.onNodeWithTag(tag).performCustomAccessibilityActionWithLabel(string(label))
        rule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        rule.waitForIdle()
    }

    @Test
    @Verifies("AC-10", "FR-GEST-09", "NFR-A11Y-02")
    fun aScreenReaderOpensTheAppGridAndLaunchesAnAppWithoutSwiping() {
        launcher.show(at())

        action(HomeTags.SCREEN, R.string.action_show_apps)
        assertEquals(Layer.AppGrid, state.layer)

        // A double-tap: the app's click action.
        rule.onNode(gridApp("Mail")).assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()

        assertTrue(launcher.dispatched.any { it is Action.Launch && it.app == mail.id }, "Dispatched: ${launcher.dispatched}")
    }

    @Test
    @Verifies("AC-10", "FR-GEST-09", "NFR-A11Y-02")
    fun aScreenReaderOpensAndClosesEventsAndTheTopMenuWithoutSwiping() {
        launcher.show(at())

        action(HomeTags.SCREEN, R.string.action_show_events)
        assertEquals(Layer.Events, state.layer)
        rule.onNodeWithTag(EventsTags.HEADER).assertIsDisplayed()
        action(EventsTags.SCREEN, R.string.action_show_home)
        assertEquals(Layer.Home, state.layer)

        action(HomeTags.SCREEN, R.string.action_show_top_menu)
        assertEquals(Layer.TopMenu, state.layer)
        rule.onNodeWithTag(TopMenuTags.SHEET).assertIsDisplayed()
        action(TopMenuTags.MENU, R.string.action_show_home)
        assertEquals(Layer.Home, state.layer)
    }

    @Test
    @Verifies("FR-GEST-09", "FR-GEST-06", "NFR-A11Y-02")
    fun aScreenReaderChoosesAPulleyItemAndCancelsItsRemorseTimer() {
        launcher.show(
            at(Layer.Events).copy(
                settings = Settings(notificationsInEvents = true),
                notificationAccess = true,
                notifications = listOf(invoice, download),
            ),
        )

        // The pulley menu's item, from the header's actions.
        action(EventsTags.HEADER, R.string.menu_clear_notifications)
        assertEquals(Destructive.ClearNotifications(setOf(invoice.ref)), state.remorse?.action)

        // The banner is a button named for what it cancels, announced as it appears.
        val cancel = context.getString(R.string.remorse_cancel, string(R.string.remorse_clear_notifications))
        val banner = rule.onNode(hasContentDescription(cancel) and hasTestTag(LauncherTags.REMORSE)).assertIsDisplayed()
        val config = banner.fetchSemanticsNode().config
        assertEquals(Role.Button, config[SemanticsProperties.Role])
        assertEquals(LiveRegionMode.Polite, config[SemanticsProperties.LiveRegion])

        banner.performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()

        assertNull(state.remorse)
        assertEquals(listOf(invoice, download), state.notifications, "Nothing was cleared")
        rule.onNodeWithTag(LauncherTags.REMORSE).assertDoesNotExist()
    }

    private companion object {
        /** Longer than a settle and the pulley's flash. */
        const val SETTLE_MILLIS = 1_000L
    }
}
