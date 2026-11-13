package io.github.twonet.selfish.ui.home

import android.icu.text.DateFormat
import android.icu.util.TimeZone
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.twonet.selfish.R
import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.Settings
import io.github.twonet.selfish.domain.ports.ClockInfo
import io.github.twonet.selfish.domain.state.Action
import io.github.twonet.selfish.ui.TestLauncher
import io.github.twonet.selfish.ui.TestLauncher.Companion.app
import io.github.twonet.selfish.ui.TestLauncher.Companion.launcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Home as the user sees it (level I, DESIGN §8.3), through [TestLauncher].
 * The App Grid's own tests are in `AppGridTest`.
 */
@RunWith(AndroidJUnit4::class)
class HomeTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val launcher = TestLauncher(rule)
    private val dispatched get() = launcher.dispatched
    private var state
        get() = launcher.state
        set(value) {
            launcher.state = value
        }

    private val maps = app("Maps")
    private val clock = app("Clock")

    private fun showHome(apps: List<AppEntry>, home: List<AppEntry>, clockInfo: ClockInfo? = null) =
        launcher.show(launcher(apps, home).copy(clock = clockInfo))

    private fun setHome(home: List<AppEntry>) {
        state = state.copy(layout = state.layout!!.copy(home = home.map { it.id }))
    }

    /** A cover on Home, as opposed to the same app in the App Grid. */
    private fun cover(label: String) = hasText(label) and hasClickAction() and hasAnyAncestor(hasTestTag(HomeTags.COVERS))

    /** How many columns the covers are in: the number of distinct left edges. */
    private fun coverColumns(): Int = rule.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag(HomeTags.COVERS)))
        .fetchSemanticsNodes()
        .map { it.boundsInRoot.left.roundToInt() }
        .distinct()
        .size

    private fun string(id: Int) = context.getString(id)

    @Test
    @Verifies("FR-HOME-02")
    fun theTimeAndDateShowInLargeTypeUnlessHidden() {
        val now = ClockInfo(Instant.parse("2026-11-09T14:05:00Z"), ZoneId.of("UTC"), is24Hour = true)
        showHome(listOf(maps), home = listOf(maps), clockInfo = now)
        val time = format("Hm", now)
        val date = format("EEEEMMMMd", now)

        rule.onNodeWithText(time).assertIsDisplayed()
        rule.onNodeWithText(date).assertIsDisplayed()

        state = state.copy(settings = Settings(showClock = false))

        rule.onNodeWithText(time).assertDoesNotExist()
        rule.onNodeWithText(date).assertDoesNotExist()
    }

    @Test
    @Verifies("FR-HOME-02")
    fun theTwelveHourSettingIsFollowed() {
        val now = ClockInfo(Instant.parse("2026-11-09T14:05:00Z"), ZoneId.of("UTC"), is24Hour = false)
        showHome(listOf(maps), home = listOf(maps), clockInfo = now)

        rule.onNodeWithText(format("hm", now)).assertIsDisplayed()
    }

    private fun format(skeleton: String, clock: ClockInfo): String =
        DateFormat.getInstanceForSkeleton(skeleton, context.resources.configuration.locales[0])
            .apply { timeZone = TimeZone.getTimeZone(clock.zone.id) }
            .format(Date.from(clock.now))

    @Test
    @Verifies("FR-HOME-03")
    fun upToFourCoversTakeTwoColumnsAndFiveToNineTakeThree() {
        val apps = (1..9).map { app("App $it") }
        showHome(apps, home = apps.take(2))
        assertEquals(2, coverColumns())

        setHome(apps.take(4))
        assertEquals(2, coverColumns())

        setHome(apps.take(5))
        assertEquals(3, coverColumns())

        setHome(apps)
        assertEquals(3, coverColumns())
        apps.forEach { rule.onNode(cover(it.label)).assertExists() }
    }

    @Test
    @Verifies("FR-HOME-04")
    fun tappingACoverLaunchesItsAppFromTheCover() {
        showHome(listOf(clock, maps), home = listOf(maps, clock))

        rule.onNode(cover("Maps")).performClick()

        val launch = dispatched.filterIsInstance<Action.Launch>().single()
        assertEquals(maps.id, launch.app)
        val from = assertNotNull(launch.from, "The launch grows from the cover's bounds")
        assertTrue(from.right > from.left && from.bottom > from.top, "Empty bounds: $from")
    }

    @Test
    @Verifies("FR-HOME-05", "FR-HOME-06")
    fun longPressingACoverOpensItsMenuWhichRemovesItFromHome() {
        showHome(listOf(clock, maps), home = listOf(maps, clock))

        rule.onNode(cover("Maps")).performTouchInput { longClick() }
        rule.onNodeWithText(string(R.string.menu_remove_from_home)).assertIsDisplayed().performClick()

        assertTrue(Action.RemoveFromHome(maps.id) in dispatched)
        rule.onNode(cover("Maps")).assertDoesNotExist()
        rule.onNode(cover("Clock")).assertExists()
        rule.onNodeWithText(string(R.string.menu_remove_from_home)).assertDoesNotExist()
    }

    @Test
    @Verifies("FR-HOME-06")
    fun aLongPressedCoverIsDraggedToANewPlace() {
        val apps = listOf("A", "B", "C", "D").map(::app)
        showHome(apps, home = apps)
        val a = rule.onNode(cover("A")).fetchSemanticsNode().boundsInRoot
        val b = rule.onNode(cover("B")).fetchSemanticsNode().boundsInRoot

        rule.onNode(cover("A")).performTouchInput {
            down(center)
            // Hold still past the long-press timeout, then drag onto B.
            moveBy(Offset.Zero, delayMillis = viewConfiguration.longPressTimeoutMillis + LONG_PRESS_MARGIN_MILLIS)
            val step = (b.center - a.center) / STEPS.toFloat()
            repeat(STEPS) { moveBy(step) }
            up()
        }
        rule.waitForIdle()

        assertTrue(Action.MoveCover(apps[0].id, 1) in dispatched, "Dispatched: $dispatched")
        assertEquals(listOf("B", "A", "C", "D").map { app(it).id }, state.layout!!.home)
        assertTrue(state.overlays.isEmpty(), "Dragging closes the menu")
    }

    @Test
    @Verifies("FR-HOME-08", "FR-ANIM-13")
    fun withNoCoversHomeShowsTheWayToTheApps() {
        showHome(listOf(maps), home = emptyList())
        val hint = string(R.string.home_hint_no_covers)

        rule.onNodeWithText(hint).assertIsDisplayed()
        rule.onNodeWithTag(HomeTags.HINT).assertIsDisplayed()

        setHome(listOf(maps))
        rule.onNodeWithText(hint).assertDoesNotExist()
        rule.onNodeWithTag(HomeTags.HINT).assertDoesNotExist()
    }

    @Test
    fun aFirstRunStillBuildingItsLayoutShowsNoHint() {
        showHome(listOf(maps), home = emptyList())
        state = state.copy(layout = null)

        rule.onNodeWithText(string(R.string.home_hint_no_covers)).assertDoesNotExist()
        rule.onNode(cover("Maps")).assertDoesNotExist()
    }

    private companion object {
        const val LONG_PRESS_MARGIN_MILLIS = 100L
        const val STEPS = 10
    }
}
