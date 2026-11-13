package io.github.twonet.selfish.ui.wallpaper

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.twonet.selfish.R
import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.AppliedWallpaper
import io.github.twonet.selfish.domain.model.WallpaperStatus
import io.github.twonet.selfish.domain.ports.ClockInfo
import io.github.twonet.selfish.domain.ports.WallpaperId
import io.github.twonet.selfish.domain.state.Action
import io.github.twonet.selfish.domain.state.Layer
import io.github.twonet.selfish.domain.state.LauncherState
import io.github.twonet.selfish.ui.LauncherTags
import io.github.twonet.selfish.ui.TestLauncher
import io.github.twonet.selfish.ui.TestLauncher.Companion.app
import io.github.twonet.selfish.ui.TestLauncher.Companion.launcher
import io.github.twonet.selfish.ui.components.ClockFormat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Wallpaper details (FR-WP-09, FR-WP-10), level I (DESIGN §8.3). The image
 * itself shows its glass placeholder here: the test has no cached images.
 */
@RunWith(AndroidJUnit4::class)
class WallpaperDetailsTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val launcher = TestLauncher(rule)
    private val state get() = launcher.state

    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    private val locale: Locale get() = context.resources.configuration.locales[0]

    private val apps = listOf(app("Chat"), app("Mail"))
    private val today = TestLauncher.wallpaper(
        "20261113_en-US",
        "A lighthouse at dusk",
        "Lighthouse, Maine (© A Photographer)",
        "https://www.bing.com/search?q=lighthouse",
    )
    private val yesterday = TestLauncher.wallpaper("20261112_en-US", "A glacier at noon", "© Another Photographer", date = LocalDate.of(2026, 11, 12))
    private val lastSuccess = Instant.parse("2026-11-13T09:15:00Z")
    private val clock = ClockInfo(Instant.parse("2026-11-16T14:05:00Z"), ZoneId.of("UTC"), is24Hour = true)

    /** Wallpaper details over Home, with today's image in use and applied, last updated three days ago (AC-04). */
    private fun details(): LauncherState = launcher(apps, home = apps, layer = Layer.WallpaperDetails).copy(
        clock = clock,
        wallpapers = listOf(today, yesterday),
        wallpaper = today,
        wallpaperStatus = WallpaperStatus(lastSuccess = lastSuccess, applied = AppliedWallpaper(today.image, WallpaperId(1))),
    )

    private fun inDetails() = hasAnyAncestor(hasTestTag(WallpaperDetailsTags.SCREEN))

    private fun pulleyLabels(): List<String> =
        rule.onNodeWithTag(WallpaperDetailsTags.HEADER).fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }

    private fun choose(label: String) {
        val action = rule.onNodeWithTag(WallpaperDetailsTags.HEADER).fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .single { it.label == label }
        rule.runOnIdle { action.action() }
        rule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        rule.waitForIdle()
    }

    @Test
    @Verifies("FR-WP-09", "FR-WP-10", "AC-04", "NFR-L10N-02")
    fun theImageItsTitleCopyrightDateMarketAndLastUpdateAreShown() {
        launcher.show(details())

        rule.onNode(hasText(string(R.string.menu_wallpaper_details)) and isHeading() and inDetails()).assertIsDisplayed()
        rule.onNodeWithTag(WallpaperDetailsTags.PREVIEW, useUnmergedTree = true).assertIsDisplayed()
        rule.onNode(hasText(today.title) and inDetails()).assertIsDisplayed()
        rule.onNode(hasText(today.copyright) and inDetails()).assertIsDisplayed()
        val published = ClockFormat.formatter(ClockFormat.LONG_DATE, locale, ZoneOffset.UTC)
            .format(Date.from(today.date.atStartOfDay(ZoneOffset.UTC).toInstant()))
        rule.onNode(hasText(published) and inDetails()).assertIsDisplayed()
        val market = Locale.forLanguageTag("en-US").getDisplayName(locale)
        rule.onNode(hasText(string(R.string.wallpaper_market, market)) and inDetails()).assertIsDisplayed()
        val updated = ClockFormat.formatter(ClockFormat.SHORT_DATE + ClockFormat.TIME_24, locale, clock.zone).format(Date.from(lastSuccess))
        rule.onNodeWithTag(WallpaperDetailsTags.STATUS, useUnmergedTree = true).assertIsDisplayed()
        rule.onNode(hasText(string(R.string.wallpaper_updated, updated)) and inDetails()).assertIsDisplayed()
    }

    @Test
    @Verifies("FR-WP-09")
    fun learnMoreOpensTheImagesLink() {
        launcher.show(details())

        rule.onNode(hasText(string(R.string.events_learn_more)) and inDetails()).performClick()
        rule.waitForIdle()

        assertTrue(Action.OpenLink(today.link!!) in launcher.dispatched)
    }

    @Test
    @Verifies("FR-WP-08", "FR-WP-09", "FR-GEST-09")
    fun thePulleyMenuKeepsThisWallpaperAndThenResumesDaily() {
        launcher.show(details())
        assertEquals(listOf(string(R.string.menu_refresh_now), string(R.string.menu_keep_wallpaper)), pulleyLabels())

        choose(string(R.string.menu_keep_wallpaper))
        assertEquals(today.image, state.pinned)
        assertEquals(listOf(string(R.string.menu_refresh_now), string(R.string.menu_resume_daily)), pulleyLabels())

        choose(string(R.string.menu_resume_daily))
        assertNull(state.pinned)
    }

    @Test
    @Verifies("FR-WP-09", "FR-GEST-05")
    fun pullingToRefreshNowChecksAndSaysSo() {
        launcher.show(details())

        launcher.pull(rule.onNodeWithTag(WallpaperDetailsTags.SCREEN), toItem = 0, items = 2)
        rule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        rule.waitForIdle()

        assertTrue(Action.RefreshWallpaper in launcher.dispatched)
        assertTrue(state.wallpaperWork.refreshing)
        rule.onNode(hasText(string(R.string.wallpaper_checking)) and inDetails()).assertIsDisplayed()
    }

    @Test
    @Verifies("FR-GEN-04", "FR-WP-09")
    fun backClosesItOntoHome() {
        launcher.show(details())

        launcher.dispatch(Action.Back)
        rule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        rule.waitForIdle()

        assertEquals(Layer.Home, state.layer)
        rule.onNodeWithTag(WallpaperDetailsTags.SCREEN).assertDoesNotExist()
    }

    @Test
    @Verifies("FR-WP-09")
    fun whileOpenSwipesDontReachTheLayersBeneath() {
        launcher.show(details())

        rule.onNodeWithTag(LauncherTags.ROOT).performTouchInput { swipeUp() }
        rule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        rule.waitForIdle()

        assertEquals(Layer.WallpaperDetails, state.layer)
        assertTrue(launcher.dispatched.none { it is Action.LayerSettled })
    }

    @Test
    @Verifies("FR-EVT-03", "FR-WP-09")
    fun openedFromEventsItReplacesEventsAndShowsTheWallpaperInUse() {
        launcher.show(details().copy(layer = Layer.Events))
        rule.onNodeWithTag(WallpaperDetailsTags.SCREEN).assertDoesNotExist()

        launcher.dispatch(Action.OpenWallpaperDetails)
        rule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        rule.waitForIdle()

        rule.onNodeWithTag(WallpaperDetailsTags.SCREEN).assertIsDisplayed()
        rule.onNode(hasText(today.title) and inDetails()).assertIsDisplayed()
    }

    private companion object {
        const val SETTLE_MILLIS = 1_000L
    }
}
