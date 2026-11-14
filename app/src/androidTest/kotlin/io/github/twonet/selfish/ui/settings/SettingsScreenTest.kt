package io.github.twonet.selfish.ui.settings

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.twonet.selfish.BuildConfig
import io.github.twonet.selfish.R
import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.Animations
import io.github.twonet.selfish.domain.model.AppearanceMode
import io.github.twonet.selfish.domain.model.DownloadOn
import io.github.twonet.selfish.domain.model.FolderId
import io.github.twonet.selfish.domain.model.GridItem
import io.github.twonet.selfish.domain.model.Haptics
import io.github.twonet.selfish.domain.model.HighlightSetting
import io.github.twonet.selfish.domain.model.Market
import io.github.twonet.selfish.domain.ports.ClockInfo
import io.github.twonet.selfish.domain.ports.DefaultApps
import io.github.twonet.selfish.domain.state.Action
import io.github.twonet.selfish.domain.state.Layer
import io.github.twonet.selfish.domain.state.LauncherState
import io.github.twonet.selfish.domain.state.Overlay
import io.github.twonet.selfish.domain.state.reducedMotion
import io.github.twonet.selfish.domain.wallpaper.MarketResolver
import io.github.twonet.selfish.ui.LauncherTags
import io.github.twonet.selfish.ui.TestLauncher
import io.github.twonet.selfish.ui.TestLauncher.Companion.app
import io.github.twonet.selfish.ui.TestLauncher.Companion.launcher
import io.github.twonet.selfish.ui.components.ClockFormat
import io.github.twonet.selfish.ui.components.displayName
import io.github.twonet.selfish.ui.tokens.Sizes
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Settings page and About (SRS §4.13), level I (DESIGN §8.3). Actions go
 * through the real reducer, as in the store; effects such as the remorse
 * timers and the default-apps lookup are played by hand.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val launcher = TestLauncher(rule)
    private val state get() = launcher.state
    private val dispatched get() = launcher.dispatched

    private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

    private val locale: Locale get() = context.resources.configuration.locales[0]

    private val books = app("Books")
    private val chat = app("Chat")
    private val maps = app("Maps")
    private val phone = app("Phone")
    private val zoom = app("Zoom")
    private val apps = listOf(books, chat, maps, phone, zoom)

    private val today = TestLauncher.wallpaper("20261113_en-US", "A lighthouse at dusk", "Lighthouse, Maine (© A Photographer)")
    private val clock = ClockInfo(Instant.parse("2026-11-14T14:05:00Z"), ZoneId.of("UTC"), is24Hour = true)

    /** A grid the user arranged: out of alphabetical order, with a folder. */
    private val arranged = listOf(GridItem.App(zoom.id), GridItem.Folder(FolderId(1), "Work", listOf(maps.id, chat.id)), GridItem.App(books.id), GridItem.App(phone.id))

    /** Settings over Home, with the arranged grid, two covers, the clock and today's wallpaper. */
    private fun settings(): LauncherState {
        val base = launcher(apps, home = listOf(zoom, maps), layer = Layer.Settings)
        return base.copy(layout = base.layout!!.copy(grid = arranged), clock = clock, wallpapers = listOf(today), wallpaper = today)
    }

    private fun show(state: LauncherState = settings(), fontScale: Float? = null) = launcher.show(state, fontScale = fontScale)

    private fun inSettings() = hasAnyAncestor(hasTestTag(SettingsTags.LIST))

    private fun inAbout() = hasAnyAncestor(hasTestTag(SettingsTags.ABOUT))

    /** A control of the page by the text it shows, such as a switch's label or an option. */
    private fun control(text: String) = hasText(text) and hasClickAction() and inSettings()

    private fun control(id: Int) = control(string(id))

    private fun swatch(name: Int) = hasContentDescription(string(name)) and hasClickAction() and inSettings()

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    private fun tap(matcher: SemanticsMatcher) {
        rule.onNode(matcher).performScrollTo().performClick()
        rule.waitForIdle()
    }

    private fun go(action: Action) {
        rule.runOnIdle { launcher.dispatch(action) }
        rule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        rule.waitForIdle()
    }

    private fun pulleyLabels(): List<String> =
        rule.onNodeWithTag(SettingsTags.HEADER).fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }

    // Opening and closing (FR-TOP-04, FR-GEN-03, FR-GEN-04)

    @Test
    @Verifies("FR-TOP-04", "UI-02", "FR-GEN-04")
    fun openedFromTheTopMenuItShowsItsHeaderOverHomeAndBackClosesIt() {
        show(settings().copy(layer = Layer.TopMenu))
        rule.onNodeWithTag(SettingsTags.PAGE).assertDoesNotExist()

        go(Action.OpenSettings)
        rule.onNode(hasText(string(R.string.settings_title)) and isHeading() and inSettings()).assertIsDisplayed()

        go(Action.Back)
        assertEquals(Layer.Home, state.layer)
        rule.onNodeWithTag(SettingsTags.PAGE).assertDoesNotExist()
    }

    @Test
    @Verifies("FR-GEN-03", "FR-TOP-04")
    fun whileOpenSwipesDontReachTheLayersBeneathAndHomeClosesIt() {
        show()

        rule.onNodeWithTag(LauncherTags.ROOT).performTouchInput { swipeUp() }
        rule.onNodeWithTag(LauncherTags.ROOT).performTouchInput { swipeLeft() }
        rule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        rule.waitForIdle()
        assertEquals(Layer.Settings, state.layer)
        assertTrue(dispatched.none { it is Action.LayerSettled })

        go(Action.HomePressed)
        assertEquals(Layer.Home, state.layer)
        rule.onNodeWithTag(SettingsTags.PAGE).assertDoesNotExist()
    }

    // About (FR-SET-17)

    @Test
    @Verifies("FR-SET-17", "FR-GEST-05", "FR-GEST-09")
    fun thePulleyMenuAndTheHeaderOfferAboutWhichBackLeavesForSettings() {
        show()
        assertEquals(listOf(string(R.string.menu_about)), pulleyLabels())

        launcher.pull(rule.onNodeWithTag(SettingsTags.PAGE), toItem = 0, items = 1)
        rule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        rule.waitForIdle()

        assertTrue(Action.OpenAbout in dispatched)
        assertEquals(listOf<Overlay>(Overlay.About), state.overlays)
        rule.onNode(hasText(string(R.string.menu_about)) and isHeading() and inAbout()).assertIsDisplayed()
        rule.onNodeWithTag(SettingsTags.LIST).assertDoesNotExist()

        go(Action.Back)
        assertEquals(Layer.Settings, state.layer)
        rule.onNodeWithTag(SettingsTags.HEADER).assertIsDisplayed()
        rule.onNodeWithTag(SettingsTags.ABOUT).assertDoesNotExist()
    }

    @Test
    @Verifies("FR-SET-17", "FR-GEN-01", "FR-WP-10")
    fun aboutShowsTheNameVersionNoticeImageCreditsAndLicenses() {
        show()
        tap(hasTestTag(SettingsTags.ABOUT_ROW))
        assertEquals(listOf<Overlay>(Overlay.About), state.overlays)

        val shown = listOf(
            string(R.string.app_name),
            string(R.string.about_version, BuildConfig.VERSION_NAME),
            string(R.string.about_not_affiliated),
            string(R.string.about_images_attribution),
            string(R.string.about_current_image, today.copyright),
            string(R.string.about_licenses_intro),
        ) + Licenses.COMPONENTS.map { string(it.name) }
        for (text in shown) rule.onNode(hasText(text) and inAbout()).performScrollTo().assertIsDisplayed()

        rule.onNodeWithTag(SettingsTags.LICENSE_TEXT, useUnmergedTree = true).assertDoesNotExist()
        rule.onNode(hasText(string(R.string.about_show_license)) and hasClickAction() and inAbout()).performScrollTo().performClick()
        rule.waitUntil(LOAD_MILLIS) {
            rule.onAllNodes(hasTestTag(SettingsTags.LICENSE_TEXT), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNode(hasTestTag(SettingsTags.LICENSE_TEXT) and hasText("Apache License", substring = true), useUnmergedTree = true).assertExists()
    }

    // Appearance and highlight (FR-SET-01, FR-SET-06)

    @Test
    @Verifies("FR-SET-01", "FR-SET-06", "FR-THM-09", "FR-AMB-05", "NFR-A11Y-01")
    fun appearanceAndHighlightColorAreChosenAsRadioButtons() {
        show()
        rule.onNode(control(R.string.appearance_system) and hasRole(Role.RadioButton)).assertIsSelected()
        rule.onNode(control(R.string.highlight_automatic) and hasRole(Role.RadioButton)).assertIsSelected()

        tap(control(R.string.appearance_dark))
        assertEquals(AppearanceMode.Dark, state.settings.appearance)
        rule.onNode(control(R.string.appearance_dark)).assertIsSelected()
        rule.onNode(control(R.string.appearance_system)).assertIsNotSelected()

        tap(swatch(R.string.highlight_teal))
        assertEquals(HighlightSetting.Teal, state.settings.highlight)
        rule.onNode(swatch(R.string.highlight_teal)).assertIsSelected()
        rule.onNode(control(R.string.highlight_automatic)).assertIsNotSelected()
        val presets = listOf(
            R.string.highlight_red, R.string.highlight_orange, R.string.highlight_yellow, R.string.highlight_green,
            R.string.highlight_teal, R.string.highlight_blue, R.string.highlight_purple, R.string.highlight_pink,
        )
        for (preset in presets) rule.onNode(swatch(preset) and hasRole(Role.RadioButton)).assertExists()

        tap(control(R.string.highlight_automatic))
        assertEquals(HighlightSetting.Automatic, state.settings.highlight)
    }

    // Wallpaper (FR-SET-02 to FR-SET-05)

    @Test
    @Verifies("FR-SET-02", "FR-WP-13", "NFR-A11Y-01")
    fun turningDailyWallpaperOffHidesTheSettingsThatNeedIt() {
        show()
        val daily = control(R.string.settings_daily_wallpaper) and hasRole(Role.Switch)
        rule.onNode(daily).assertIsOn()
        rule.onNodeWithTag(SettingsTags.MARKET).performScrollTo().assertIsDisplayed()

        tap(daily)
        assertTrue(Action.SetDailyWallpaper(false) in dispatched)
        rule.onNode(daily).assertIsOff()
        rule.onNodeWithTag(SettingsTags.MARKET).assertDoesNotExist()
        rule.onNode(control(R.string.settings_lock_screen)).assertDoesNotExist()

        tap(daily)
        rule.onNode(daily).assertIsOn()
        rule.onNodeWithTag(SettingsTags.MARKET).performScrollTo().assertIsDisplayed()
    }

    @Test
    @Verifies("FR-SET-03", "FR-WP-03")
    fun theMarketListOpensBeneathItsRowAndChoosingOneClosesIt() {
        show()
        val automatic = string(R.string.settings_market_automatic, MarketResolver.resolve(null, locale).displayName(locale))
        val japan = Market("ja-JP")
        val japanese = japan.displayName(locale)
        rule.onNode(hasTestTag(SettingsTags.MARKET) and hasText(automatic)).performScrollTo().assertIsDisplayed()
        rule.onNode(control(japanese)).assertDoesNotExist()

        tap(hasTestTag(SettingsTags.MARKET))
        rule.onNode(control(automatic) and hasRole(Role.RadioButton)).assertIsSelected()
        assertEquals(MarketResolver.SUPPORTED.size + 1, rule.onAllNodes(hasRole(Role.RadioButton) and hasAnyAncestor(hasTestTag(SettingsTags.MARKET_LIST))).fetchSemanticsNodes().size)
        tap(control(japanese))

        assertEquals(japan, state.settings.market)
        rule.onNode(control(japanese) and hasRole(Role.RadioButton)).assertDoesNotExist()
        rule.onNode(hasTestTag(SettingsTags.MARKET) and hasText(japanese)).assertIsDisplayed()

        tap(hasTestTag(SettingsTags.MARKET))
        tap(control(automatic) and hasRole(Role.RadioButton))
        assertNull(state.settings.market)
    }

    @Test
    @Verifies("FR-SET-04", "FR-SET-05")
    fun lockScreenAndDownloadOnTakeEffectAtOnce() {
        show()
        val lockScreen = control(R.string.settings_lock_screen) and hasRole(Role.Switch)
        rule.onNode(lockScreen).performScrollTo().assertIsOff()
        rule.onNode(control(R.string.download_any_network)).performScrollTo().assertIsSelected()

        tap(lockScreen)
        assertTrue(state.settings.lockScreen)
        rule.onNode(lockScreen).assertIsOn()

        tap(control(R.string.download_wifi_only))
        assertEquals(DownloadOn.WifiOnly, state.settings.downloadOn)
        rule.onNode(control(R.string.download_wifi_only)).assertIsSelected()
    }

    // Home and the App Grid (FR-SET-07, FR-SET-09, FR-SET-10)

    @Test
    @Verifies("FR-SET-07", "FR-HOME-02")
    fun hidingTheTimeAndDateTakesThemOffHome() {
        show()
        val time = ClockFormat.formatter("Hm", locale, clock.zone).format(Date.from(clock.now))

        tap(control(R.string.settings_show_clock))
        assertEquals(false, state.settings.showClock)
        rule.onNode(control(R.string.settings_show_clock)).assertIsOff()

        go(Action.Back)
        rule.onNodeWithText(time).assertDoesNotExist()
    }

    @Test
    @Verifies("FR-SET-09")
    fun theAppGridColumnsAreChosenFromThreeToFive() {
        show()
        rule.onNode(control(String.format(locale, "%d", 4))).performScrollTo().assertIsSelected()

        tap(control(String.format(locale, "%d", 3)))
        assertEquals(3, state.settings.gridColumns)
        tap(control(String.format(locale, "%d", 5)))
        assertEquals(5, state.settings.gridColumns)
    }

    @Test
    @Verifies("FR-SET-10", "FR-GEST-06")
    fun sortAZWaitsUnderItsBannerWhichCancelsItAndThenSorts() {
        show()

        tap(control(R.string.settings_sort_az))
        val banner = rule.onNodeWithTag(LauncherTags.REMORSE)
        banner.assertIsDisplayed()
        banner.assert(hasContentDescription(string(R.string.remorse_cancel, string(R.string.remorse_sort_az))))
        assertEquals(arranged, state.layout?.grid, "Nothing changes until the timer runs out")

        banner.performClick()
        rule.waitForIdle()
        assertNull(state.remorse)
        assertEquals(arranged, state.layout?.grid)

        tap(control(R.string.settings_sort_az))
        go(Action.RemorseDue(state.remorse!!.number))

        val sorted = listOf(
            GridItem.App(books.id),
            GridItem.App(phone.id),
            GridItem.Folder(FolderId(1), "Work", listOf(chat.id, maps.id)),
            GridItem.App(zoom.id),
        )
        assertEquals(sorted, state.layout?.grid)
        assertEquals(listOf(zoom.id, maps.id), state.layout?.home, "Home keeps its order")
    }

    // Events (FR-SET-12)

    @Test
    @Verifies("FR-SET-12", "FR-ONB-04")
    fun notificationsInEventsAskForAccessFirstAndTurnOffAtOnce() {
        show(settings().copy(notificationAccess = false))
        val notifications = control(R.string.settings_notifications_in_events) and hasRole(Role.Switch)

        tap(notifications)
        assertTrue(Action.TurnOnNotifications in dispatched)
        assertTrue(state.awaitingNotificationAccess, "Access is asked for first")
        rule.onNode(notifications).assertIsOff()

        go(Action.NotificationAccessChanged(granted = true))
        rule.onNode(notifications).assertIsOn()

        tap(notifications)
        assertTrue(Action.TurnOffNotifications in dispatched)
        assertEquals(false, state.settings.notificationsInEvents)
        rule.onNode(notifications).assertIsOff()
    }

    // Touch and motion (FR-SET-14, FR-SET-15)

    @Test
    @Verifies("FR-SET-14", "FR-GEST-08", "NFR-A11Y-06")
    fun hapticFeedbackOffSilencesThePulleyWhichStillWorks() {
        show()

        tap(control(R.string.haptics_off))
        assertEquals(Haptics.Off, state.settings.haptics)
        rule.onNode(control(R.string.haptics_off)).assertIsSelected()
        launcher.haptics.clear()

        // Back to the top, where the pulley menu is.
        rule.onNodeWithTag(SettingsTags.LIST).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -SCROLL_TO_TOP) }
        rule.waitForIdle()
        launcher.pull(rule.onNodeWithTag(SettingsTags.PAGE), toItem = 0, items = 1)
        rule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        rule.waitForIdle()

        assertEquals(listOf<Overlay>(Overlay.About), state.overlays)
        assertEquals(emptyList(), launcher.haptics)
    }

    @Test
    @Verifies("FR-SET-15", "FR-ANIM-15")
    fun animationsReducedReducesMotionAtOnce() {
        show()
        val followSystem = rule.onAllNodesWithText(string(R.string.settings_follow_system))
        assertEquals(2, followSystem.fetchSemanticsNodes().size, "Haptic feedback and Animations")

        tap(control(R.string.animations_reduced))
        assertEquals(Animations.Reduced, state.settings.animations)
        assertTrue(state.reducedMotion)
        rule.onNode(control(R.string.animations_reduced)).assertIsSelected()
    }

    // Reset layout (FR-SET-16)

    @Test
    @Verifies("FR-SET-16", "FR-GEST-06")
    fun resetLayoutWaitsUnderItsBannerAndThenBringsBackTheFirstRunLayoutKeepingTheSettings() {
        show()
        tap(control(R.string.settings_show_clock))
        val changed = state.settings

        tap(control(R.string.settings_reset_layout))
        rule.onNodeWithTag(LauncherTags.REMORSE).assertIsDisplayed()
            .assert(hasContentDescription(string(R.string.remorse_cancel, string(R.string.remorse_reset_layout))))
        assertEquals(arranged, state.layout?.grid)

        go(Action.RemorseDue(state.remorse!!.number))
        assertTrue(state.resettingLayout, "Waiting for the default apps")
        go(Action.DefaultAppsResolved(DefaultApps(phone = phone.id)))

        assertEquals(apps.map { GridItem.App(it.id) }, state.layout?.grid)
        assertEquals(listOf(phone.id), state.layout?.home)
        assertEquals(changed, state.settings, "Settings are kept")
        assertEquals(today, state.wallpaper, "The wallpaper is kept")
    }

    // Accessibility (NFR-A11Y-04, UI-06)

    @Test
    @Verifies("NFR-A11Y-04", "UI-06")
    fun atTheLargestFontScaleEveryControlCanBeReachedAndIsAtLeastATouchTarget() {
        show(fontScale = LARGEST_FONT_SCALE)

        val controls = listOf(
            R.string.appearance_system, R.string.appearance_light, R.string.appearance_dark, R.string.highlight_automatic,
            R.string.settings_daily_wallpaper, R.string.settings_lock_screen, R.string.download_any_network, R.string.download_wifi_only,
            R.string.settings_show_clock, R.string.settings_sort_az, R.string.settings_notifications_in_events,
            R.string.haptics_off, R.string.animations_reduced, R.string.settings_reset_layout, R.string.menu_about,
        ).map(::control) + listOf(swatch(R.string.highlight_pink), hasTestTag(SettingsTags.MARKET))
        for (control in controls) rule.onNode(control).performScrollTo().assertIsDisplayed()

        val minimum = with(rule.density) { Sizes.MinTouchTarget.toPx() }
        val small = rule.onAllNodes(hasClickAction() and inSettings()).fetchSemanticsNodes()
            .filter { it.size.width < minimum - 1 || it.size.height < minimum - 1 }
            .map { it.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString() + " ${it.size}" }
        assertEquals(emptyList(), small, "Smaller than ${Sizes.MinTouchTarget}")
    }

    @Test
    @Verifies("NFR-A11Y-01")
    fun switchesAreReadWithTheirStateAndChoicesAsRadioButtons() {
        show()

        rule.onNode(control(R.string.settings_daily_wallpaper)).assert(
            hasRole(Role.Switch) and SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On),
        )
        rule.onNode(control(R.string.settings_show_clock)).assert(hasRole(Role.Switch))
        rule.onNode(control(R.string.haptics_off)).assert(hasRole(Role.RadioButton))
        rule.onNode(control(R.string.settings_reset_layout)).assert(hasRole(Role.Button))
    }

    private companion object {
        const val SETTLE_MILLIS = 1_000L
        const val LOAD_MILLIS = 5_000L
        const val LARGEST_FONT_SCALE = 2f
        const val SCROLL_TO_TOP = 1_000_000f
    }
}
