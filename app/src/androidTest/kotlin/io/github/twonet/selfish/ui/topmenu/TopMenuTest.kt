package io.github.twonet.selfish.ui.topmenu

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.twonet.selfish.R
import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.AppearanceMode
import io.github.twonet.selfish.domain.model.Settings
import io.github.twonet.selfish.domain.model.WallpaperEntry
import io.github.twonet.selfish.domain.ports.ImageId
import io.github.twonet.selfish.domain.state.Action
import io.github.twonet.selfish.domain.state.Layer
import io.github.twonet.selfish.domain.state.LauncherState
import io.github.twonet.selfish.domain.state.MenuTarget
import io.github.twonet.selfish.ui.LauncherTags
import io.github.twonet.selfish.ui.TestLauncher
import io.github.twonet.selfish.ui.TestLauncher.Companion.app
import io.github.twonet.selfish.ui.TestLauncher.Companion.launcher
import io.github.twonet.selfish.ui.home.HomeTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Top Menu as the user sees it (level I, DESIGN §8.3), through
 * [TestLauncher]: pulling it down and putting it back, the appearance
 * switch, the Ambiences row, the shortcuts and the long-press on empty
 * Home. The long-press tick and the status-bar pull on a real device are
 * checked by hand (REVIEW-CHECKLIST.md).
 */
@RunWith(AndroidJUnit4::class)
class TopMenuTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val launcher = TestLauncher(rule)
    private val state get() = launcher.state
    private val dispatched get() = launcher.dispatched

    private fun string(id: Int) = context.getString(id)

    private val apps = ('A'..'I').map { app("App $it") }

    private val today = WallpaperEntry(ImageId("20261113"), "A lighthouse at dusk", "© A Photographer", null)
    private val yesterday = WallpaperEntry(ImageId("20261112"), "A glacier at noon", "© Another Photographer", null)
    private val older = WallpaperEntry(ImageId("20261111"), "A desert at dawn", "© A Third Photographer", null)

    /** The launcher on [layer] with nine covers. */
    private fun at(layer: Layer = Layer.Home) = launcher(apps, home = apps, layer = layer)

    /** The Top Menu with three cached images, newest first, and the newest in use. */
    private fun withAmbiences() = at(Layer.TopMenu).copy(wallpapers = listOf(today, yesterday, older), wallpaper = today)

    private fun show(state: LauncherState, gestureInsets: WindowInsets = TestLauncher.NO_INSETS, fontScale: Float? = null) =
        launcher.show(state, gestureInsets, fontScale)

    private fun root() = rule.onNodeWithTag(LauncherTags.ROOT)

    private fun sheet() = rule.onNodeWithTag(TopMenuTags.SHEET)

    private fun inMenu() = hasAnyAncestor(hasTestTag(TopMenuTags.SHEET))

    /** An option of the menu that can be chosen, such as an appearance, by its text. */
    private fun option(text: String) = hasText(text) and hasClickAction() and inMenu()

    private fun appearance(id: Int) = option(string(id))

    private fun ambience(entry: WallpaperEntry) = hasContentDescription(entry.title) and hasClickAction() and inMenu()

    private fun daily() = option(string(R.string.ambience_daily))

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    private fun cover(label: String) = hasText(label) and hasClickAction() and hasAnyAncestor(hasTestTag(HomeTags.COVERS))

    private fun left(matcher: SemanticsMatcher): Float = rule.onNode(matcher).fetchSemanticsNode().boundsInRoot.left

    private fun dispatchAndSettle(action: Action) {
        rule.runOnIdle { launcher.dispatch(action) }
        rule.waitForIdle()
    }

    /** The sheet's height in pixels, measured with the menu open, and the launcher back on Home. */
    private fun sheetHeight(): Float {
        dispatchAndSettle(Action.LayerSettled(Layer.TopMenu))
        val height = sheet().fetchSemanticsNode().size.height.toFloat()
        dispatchAndSettle(Action.Back)
        return height
    }

    /** Drags vertically by [distance] pixels (negative is up), then holds still so the release has no speed. */
    private fun dragAndHold(distance: Float) = root().performTouchInput {
        down(center)
        repeat(STEPS) { moveBy(Offset(0f, distance / STEPS)) }
        moveBy(Offset.Zero, delayMillis = HOLD_MILLIS)
        up()
    }

    // Pulling it down (FR-TOP-01)

    @Test
    @Verifies("FR-TOP-01", "FR-GEST-01")
    fun swipingDownOnHomePullsTheTopMenuDown() {
        show(at())
        sheet().assertDoesNotExist()

        root().performTouchInput { swipeDown(startY = centerY, endY = bottom - height / 10) }
        rule.waitForIdle()

        assertEquals(Layer.TopMenu, state.layer)
        sheet().assertIsDisplayed()
        assertEquals(0f, sheet().fetchSemanticsNode().boundsInRoot.top, "It hangs from the top edge")
        rule.onNode(appearance(R.string.appearance_system)).assertIsDisplayed()
    }

    @Test
    @Verifies("FR-TOP-01", "FR-TOP-05", "FR-GEST-02", "AC-05")
    fun aShortSlowPullPeeksBackWhileALongerOneOpensAndTheSameGoesForClosing() {
        show(at())
        val sheet = sheetHeight()

        dragAndHold(sheet * 0.2f)
        rule.waitForIdle()
        assertEquals(Layer.Home, state.layer, "20% of the menu and a slow release return to Home")
        rule.onNodeWithTag(HomeTags.COVERS).assertIsDisplayed()

        dragAndHold(sheet * 0.4f)
        rule.waitForIdle()
        assertEquals(Layer.TopMenu, state.layer, "40% opens it")

        dragAndHold(-sheet * 0.2f)
        rule.waitForIdle()
        assertEquals(Layer.TopMenu, state.layer, "20% back up peeks and stays open")

        dragAndHold(-sheet * 0.4f)
        rule.waitForIdle()
        assertEquals(Layer.Home, state.layer, "40% back up closes it")
    }

    @Test
    @Verifies("FR-TOP-01", "FR-GEST-04")
    fun aPullThatStartsOnTheStatusBarIsLeftToTheSystem() {
        show(at(), gestureInsets = WindowInsets(top = STATUS_BAR))

        root().performTouchInput { swipeDown(startY = top + 1f, endY = bottom - height / 10) }
        rule.waitForIdle()
        assertEquals(Layer.Home, state.layer, "The notification shade's")

        root().performTouchInput { swipeDown(startY = centerY, endY = bottom - height / 10) }
        rule.waitForIdle()
        assertEquals(Layer.TopMenu, state.layer, "Below the status bar it is the Top Menu's")
    }

    // Closing it (FR-TOP-05)

    @Test
    @Verifies("FR-TOP-05", "FR-GEN-03", "FR-GEN-04")
    fun swipingUpBackAndHomeCloseIt() {
        show(at(Layer.TopMenu))
        sheet().assertIsDisplayed()

        root().performTouchInput { swipeUp(startY = bottom - height / 10, endY = top + height / 10) }
        rule.waitForIdle()
        assertEquals(Layer.Home, state.layer, "Swipe up")
        sheet().assertDoesNotExist()

        dispatchAndSettle(Action.LayerSettled(Layer.TopMenu))
        dispatchAndSettle(Action.Back)
        assertEquals(Layer.Home, state.layer, "Back")
        sheet().assertDoesNotExist()

        dispatchAndSettle(Action.LayerSettled(Layer.TopMenu))
        dispatchAndSettle(Action.HomePressed)
        assertEquals(Layer.Home, state.layer, "Home")
        sheet().assertDoesNotExist()
        rule.onNodeWithTag(HomeTags.COVERS).assertIsDisplayed()
    }

    @Test
    @Verifies("FR-TOP-05")
    fun aTapBeneathTheMenuClosesItWithoutPressingWhatIsUnderneath() {
        show(at())
        // The last cover, at the bottom, which the open menu doesn't reach.
        val last = rule.onNode(cover("App I")).fetchSemanticsNode().boundsInRoot
        dispatchAndSettle(Action.LayerSettled(Layer.TopMenu))
        val sheetBottom = sheet().fetchSemanticsNode().boundsInRoot.bottom
        assertTrue(last.center.y > sheetBottom, "The cover lies beneath the menu: $last, sheet bottom $sheetBottom")

        rule.onNodeWithTag(TopMenuTags.OUTSIDE).performTouchInput { click(last.center) }
        rule.waitForIdle()

        assertEquals(Layer.Home, state.layer)
        assertFalse(dispatched.any { it is Action.Launch }, "Nothing launched: $dispatched")
        sheet().assertDoesNotExist()
    }

    // Appearance (FR-TOP-02)

    @Test
    @Verifies("FR-TOP-02", "FR-THM-09", "NFR-A11Y-01")
    fun theAppearanceSwitchShowsTheModeAndChangesIt() {
        show(at(Layer.TopMenu))
        rule.onNode(hasText(string(R.string.top_menu_appearance)) and isHeading()).assertIsDisplayed()
        rule.onNode(appearance(R.string.appearance_system)).assertIsSelected()
        rule.onNode(appearance(R.string.appearance_light)).assertIsNotSelected()
        rule.onNode(appearance(R.string.appearance_dark) and hasRole(Role.RadioButton)).assertIsNotSelected()

        rule.onNode(appearance(R.string.appearance_dark)).performClick()
        rule.waitForIdle()

        assertTrue(Action.SetAppearance(AppearanceMode.Dark) in dispatched)
        assertEquals(AppearanceMode.Dark, state.settings.appearance)
        assertEquals(Layer.TopMenu, state.layer, "The menu stays open")
        rule.onNode(appearance(R.string.appearance_dark)).assertIsSelected()
        rule.onNode(appearance(R.string.appearance_system)).assertIsNotSelected()
    }

    // Ambiences (FR-TOP-03)

    @Test
    @Verifies("FR-TOP-03", "NFR-A11Y-01")
    fun theAmbiencesRowShowsDailyThenTheCachedImagesNewestFirstWithTheActiveOneChosen() {
        show(withAmbiences())
        rule.onNodeWithTag(TopMenuTags.AMBIENCES).assertIsDisplayed()

        val lefts = listOf(daily(), ambience(today), ambience(yesterday), ambience(older)).map(::left)
        assertEquals(lefts.sorted(), lefts, "Daily, then newest first: $lefts")
        rule.onNode(daily() and hasRole(Role.RadioButton)).assertIsSelected()
        rule.onNode(ambience(today)).assertIsNotSelected()
    }

    @Test
    @Verifies("FR-TOP-03", "FR-WP-08")
    fun choosingAnImagePinsItAndDailyResumesDailyChanges() {
        show(withAmbiences())

        rule.onNode(ambience(yesterday)).performClick()
        rule.waitForIdle()
        assertTrue(Action.PinWallpaper(yesterday.image) in dispatched)
        assertEquals(yesterday.image, state.pinned)
        rule.onNode(ambience(yesterday)).assertIsSelected()
        rule.onNode(daily()).assertIsNotSelected()

        rule.onNode(daily()).performClick()
        rule.waitForIdle()
        assertTrue(Action.ResumeDaily in dispatched)
        assertEquals(null, state.pinned)
        rule.onNode(daily()).assertIsSelected()
        rule.onNode(ambience(yesterday)).assertIsNotSelected()
    }

    @Test
    @Verifies("FR-TOP-03", "FR-WP-13")
    fun theAmbiencesRowIsHiddenWithBingWallpaperOffOrNothingCached() {
        show(withAmbiences().copy(settings = Settings(dailyWallpaper = false)))
        rule.onNodeWithTag(TopMenuTags.AMBIENCES).assertDoesNotExist()
        rule.onNodeWithText(string(R.string.top_menu_ambiences)).assertDoesNotExist()
        rule.onNode(appearance(R.string.appearance_system)).assertIsDisplayed()

        launcher.state = at(Layer.TopMenu)
        rule.waitForIdle()
        rule.onNodeWithTag(TopMenuTags.AMBIENCES).assertDoesNotExist()

        launcher.state = withAmbiences()
        rule.waitForIdle()
        rule.onNodeWithTag(TopMenuTags.AMBIENCES).assertIsDisplayed()
    }

    // Shortcuts (FR-TOP-04)

    @Test
    @Verifies("FR-TOP-04")
    fun theSystemSettingsShortcutOpensTheSettingsApp() {
        show(at(Layer.TopMenu))
        rule.onNode(option(string(R.string.menu_wallpaper_details))).assertDoesNotExist()

        rule.onNode(option(string(R.string.top_menu_system_settings)) and hasRole(Role.Button)).performClick()
        rule.waitForIdle()

        assertTrue(Action.OpenSystemSettings in dispatched)
        assertEquals(Layer.TopMenu, state.layer, "Back from Settings returns to the menu")
    }

    @Test
    @Verifies("FR-TOP-04")
    fun theWallpaperDetailsShortcutShowsOnceThereIsAWallpaper() {
        show(withAmbiences())

        rule.onNode(option(string(R.string.menu_wallpaper_details))).performClick()
        rule.waitForIdle()

        assertTrue(Action.OpenWallpaperDetails in dispatched)
        assertEquals(Layer.WallpaperDetails, state.layer)
    }

    // Long-press on empty Home (FR-TOP-06)

    @Test
    @Verifies("FR-TOP-06", "FR-GEST-07")
    fun longPressingAnEmptyPartOfHomeOpensTheTopMenu() {
        show(launcher(apps, home = apps.take(1)))

        rule.onNodeWithTag(HomeTags.SCREEN).performTouchInput { longClick(Offset(centerX, bottom - height / 10)) }
        rule.waitForIdle()

        assertEquals(Layer.TopMenu, state.layer)
        sheet().assertIsDisplayed()
    }

    @Test
    @Verifies("FR-TOP-06", "FR-HOME-06")
    fun longPressingACoverOpensItsMenuInstead() {
        show(launcher(apps, home = apps.take(1)))

        rule.onNode(cover("App A")).performTouchInput { longClick() }
        rule.waitForIdle()

        assertEquals(Layer.Home, state.layer)
        assertTrue(Action.OpenContextMenu(MenuTarget.Cover(apps[0].id)) in dispatched, "Dispatched: $dispatched")
        assertFalse(Action.LayerSettled(Layer.TopMenu) in dispatched)
    }

    @Test
    @Verifies("FR-TOP-06")
    fun aTapOnAnEmptyPartOfHomeDoesNotOpenIt() {
        show(launcher(apps, home = apps.take(1)))

        rule.onNodeWithTag(HomeTags.SCREEN).performTouchInput { click(Offset(centerX, bottom - height / 10)) }
        rule.waitForIdle()

        assertEquals(Layer.Home, state.layer)
    }

    // Accessibility (FR-GEST-09, NFR-A11Y-04)

    @OptIn(ExperimentalTestApi::class)
    @Test
    @Verifies("FR-GEST-09", "NFR-A11Y-02")
    fun screenReadersOpenAndCloseTheTopMenuWithoutSwiping() {
        show(at())

        rule.onNodeWithTag(HomeTags.SCREEN).performCustomAccessibilityActionWithLabel(string(R.string.action_show_top_menu))
        rule.waitForIdle()
        assertEquals(Layer.TopMenu, state.layer)
        sheet().assertIsDisplayed()

        rule.onNodeWithTag(TopMenuTags.MENU).performCustomAccessibilityActionWithLabel(string(R.string.action_show_home))
        rule.waitForIdle()
        assertEquals(Layer.Home, state.layer)
        sheet().assertDoesNotExist()
    }

    @Test
    @Verifies("NFR-A11Y-04")
    fun atTheLargestFontScaleEveryPartCanBeReached() {
        show(withAmbiences(), fontScale = LARGEST_FONT_SCALE)

        val parts = listOf(R.string.appearance_system, R.string.appearance_light, R.string.appearance_dark).map(::appearance) +
            listOf(daily(), ambience(older), option(string(R.string.top_menu_system_settings)), option(string(R.string.menu_wallpaper_details)))
        for (part in parts) rule.onNode(part).performScrollTo().assertIsDisplayed()
    }

    private companion object {
        const val STEPS = 10
        const val HOLD_MILLIS = 300L
        const val LARGEST_FONT_SCALE = 2f
        val STATUS_BAR = 48.dp
    }
}
