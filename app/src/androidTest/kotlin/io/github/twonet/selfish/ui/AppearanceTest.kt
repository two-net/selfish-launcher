package io.github.twonet.selfish.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.twonet.selfish.R
import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.Appearance
import io.github.twonet.selfish.domain.model.AppearanceMode
import io.github.twonet.selfish.domain.model.AppliedWallpaper
import io.github.twonet.selfish.domain.model.FolderId
import io.github.twonet.selfish.domain.model.GridItem
import io.github.twonet.selfish.domain.model.Settings
import io.github.twonet.selfish.domain.model.WallpaperStatus
import io.github.twonet.selfish.domain.ports.WallpaperId
import io.github.twonet.selfish.domain.state.AccessFeature
import io.github.twonet.selfish.domain.state.Action
import io.github.twonet.selfish.domain.state.Layer
import io.github.twonet.selfish.domain.state.LauncherState
import io.github.twonet.selfish.domain.state.MenuTarget
import io.github.twonet.selfish.ui.TestLauncher.Companion.app
import io.github.twonet.selfish.ui.TestLauncher.Companion.launcher
import io.github.twonet.selfish.ui.events.EventsTags
import io.github.twonet.selfish.ui.folder.FolderTags
import io.github.twonet.selfish.ui.grid.AppGridTags
import io.github.twonet.selfish.ui.home.HomeTags
import io.github.twonet.selfish.ui.onboarding.AccessTags
import io.github.twonet.selfish.ui.onboarding.TutorialTags
import io.github.twonet.selfish.ui.settings.SettingsTags
import io.github.twonet.selfish.ui.tokens.DarkColors
import io.github.twonet.selfish.ui.tokens.LauncherColors
import io.github.twonet.selfish.ui.tokens.LightColors
import io.github.twonet.selfish.ui.tokens.Motion
import io.github.twonet.selfish.ui.topmenu.TopMenuTags
import io.github.twonet.selfish.ui.wallpaper.WallpaperDetailsTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Light, Dark and System appearance (SRS §4.12, level I, DESIGN §8.3),
 * with the device's dark theme given in state, as the store reports it.
 * That the device's setting reaches the store is `AndroidOsSettingsTest`'s
 * and `LauncherStoreTest`'s job; how it looks over real wallpapers is
 * checked by hand (REVIEW-CHECKLIST.md).
 */
@RunWith(AndroidJUnit4::class)
class AppearanceTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val launcher = TestLauncher(rule)

    private fun string(id: Int) = rule.activity.getString(id)

    private val apps = (1..40).map { app("App %02d".format(it)) }
    private val tools = GridItem.Folder(FolderId(1), "Tools", listOf(apps[0].id, apps[1].id))
    private val today = TestLauncher.wallpaper("20261113", "A lighthouse at dusk", "© A Photographer")

    /**
     * The launcher on [layer] with a folder of two apps and the others in the
     * App Grid, three covers on Home, and today's image applied.
     */
    private fun state(layer: Layer = Layer.Home, mode: AppearanceMode = AppearanceMode.System, systemDark: Boolean?): LauncherState {
        val base = launcher(apps, home = apps.subList(2, 5), layer = layer)
        return base.copy(
            layout = base.layout!!.copy(grid = listOf(tools) + apps.drop(2).map { GridItem.App(it.id) }),
            settings = Settings(appearance = mode),
            systemDark = systemDark,
            wallpapers = listOf(today),
            wallpaper = today,
            wallpaperStatus = WallpaperStatus(applied = AppliedWallpaper(today.image, WallpaperId(1))),
        )
    }

    private fun go(action: Action) {
        rule.runOnIdle { launcher.dispatch(action) }
        rule.waitForIdle()
    }

    /** The color [text] is drawn in, inside the node tagged [inside]. */
    private fun textColor(text: String, inside: String): Color {
        val node = rule.onNode(hasText(text) and hasAnyAncestor(hasTestTag(inside)), useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        return layouts.single().layoutInput.style.color
    }

    /** How light the node tagged [tag] looks on average, from 0 (black) to 1 (white). */
    private fun meanLuminance(tag: String): Float {
        val pixels = rule.onNodeWithTag(tag).captureToImage().toPixelMap()
        var sum = 0f
        var count = 0
        for (y in 0 until pixels.height step SAMPLE_STEP) {
            for (x in 0 until pixels.width step SAMPLE_STEP) {
                sum += pixels[x, y].luminance()
                count++
            }
        }
        return sum / count
    }

    private fun assertTextIn(colors: LauncherColors, text: String, inside: String, surface: String) {
        val color = textColor(text, inside)
        assertTrue(color == colors.textPrimary || color == colors.textSecondary, "$surface: \"$text\" is drawn in $color")
    }

    /**
     * Visits every surface and checks that it is drawn in [appearance]:
     * Home, the App Grid, a context menu, a folder, the remorse banner,
     * Events, the Top Menu, Wallpaper details, Settings, About, the access
     * explanation and the tutorial (FR-THM-07).
     */
    private fun everySurfaceIs(appearance: Appearance, state: LauncherState) {
        val colors = if (appearance == Appearance.Dark) DarkColors else LightColors
        launcher.show(state)

        assertTextIn(colors, "App 03", HomeTags.COVERS, "Home")

        go(Action.LayerSettled(Layer.AppGrid))
        assertTextIn(colors, "App 05", AppGridTags.GRID, "App Grid")

        go(Action.OpenContextMenu(MenuTarget.App(apps[4].id)))
        assertTextIn(colors, string(R.string.menu_app_info), AppGridTags.GRID, "Context menu")
        go(Action.CloseContextMenu)

        go(Action.OpenFolder(tools.id))
        assertTextIn(colors, "App 01", FolderTags.GRID, "Folder")

        go(Action.RemoveFolder(tools.id))
        val banner = meanLuminance(LauncherTags.REMORSE)
        if (appearance == Appearance.Dark) {
            assertTrue(banner < HALF, "Remorse banner: dark glass, mean luminance $banner")
        } else {
            assertTrue(banner > HALF, "Remorse banner: light glass, mean luminance $banner")
        }
        go(Action.CancelRemorse)

        go(Action.LayerSettled(Layer.Home))
        go(Action.LayerSettled(Layer.Events))
        assertTextIn(colors, string(R.string.events_notifications_card), EventsTags.SCREEN, "Events")

        go(Action.LayerSettled(Layer.Home))
        go(Action.LayerSettled(Layer.TopMenu))
        assertTextIn(colors, string(R.string.appearance_system), TopMenuTags.SHEET, "Top Menu")

        go(Action.LayerSettled(Layer.Home))
        go(Action.OpenWallpaperDetails)
        assertTextIn(colors, today.title, WallpaperDetailsTags.SCREEN, "Wallpaper details")

        go(Action.OpenSettings)
        assertTextIn(colors, string(R.string.settings_show_clock), SettingsTags.PAGE, "Settings")

        go(Action.OpenAbout)
        assertTextIn(colors, string(R.string.about_not_affiliated), SettingsTags.ABOUT, "About")

        go(Action.Back)
        go(Action.TurnOnFeature(AccessFeature.NotificationsInEvents))
        assertTextIn(colors, string(R.string.access_notifications_body), AccessTags.PAGE, "Access explanation")

        go(Action.ReplayTutorial)
        assertTextIn(colors, string(R.string.tutorial_step_apps), TutorialTags.CARD, "Tutorial")
    }

    @Test
    @Verifies("FR-THM-06", "FR-THM-07", "AC-02")
    fun lightStaysLightOnEverySurfaceWhileTheDeviceIsDark() {
        everySurfaceIs(Appearance.Light, state(mode = AppearanceMode.Light, systemDark = true))
    }

    @Test
    @Verifies("FR-THM-05", "FR-THM-06", "FR-THM-07")
    fun darkStaysDarkOnEverySurfaceWhileTheDeviceIsLight() {
        everySurfaceIs(Appearance.Dark, state(mode = AppearanceMode.Dark, systemDark = false))
    }

    @Test
    @Verifies("FR-THM-02", "FR-THM-03", "AC-01")
    fun inSystemModeTheLauncherCrossfadesWithTheDeviceWithinASecondAndStaysWhereItWas() {
        // A fresh install: Appearance is System, and the device is dark.
        launcher.show(state(layer = Layer.AppGrid, systemDark = true))
        val last = apps.last()
        rule.onNodeWithTag(AppGridTags.GRID).performScrollToKey("app:${last.id}")
        rule.waitForIdle()
        assertEquals(DarkColors.textPrimary, textColor(last.label, AppGridTags.GRID))
        val scrolled = rule.onNode(hasText(last.label) and hasClickAction()).fetchSemanticsNode().boundsInRoot

        rule.mainClock.autoAdvance = false
        rule.runOnIdle { launcher.dispatch(Action.SystemDarkChanged(dark = false)) }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(Motion.LONG_MILLIS + FRAME_MILLIS)

        assertTrue(Motion.LONG_MILLIS + 2 * FRAME_MILLIS < SWITCH_LIMIT_MILLIS, "Within a second (FR-THM-03)")
        assertEquals(LightColors.textPrimary, textColor(last.label, AppGridTags.GRID), "Light after the Long duration")
        assertEquals(Layer.AppGrid, launcher.state.layer)
        assertEquals(scrolled, rule.onNode(hasText(last.label) and hasClickAction()).fetchSemanticsNode().boundsInRoot, "Still scrolled")
        rule.mainClock.autoAdvance = true

        // The search field is the grid's first row.
        rule.onNodeWithTag(AppGridTags.GRID).performScrollToIndex(0)
        rule.onNodeWithTag(AppGridTags.SEARCH).performTextInput("App 1")
        rule.waitForIdle()
        go(Action.SystemDarkChanged(dark = true))

        assertEquals("App 1", launcher.state.query)
        rule.onNode(hasTestTag(AppGridTags.SEARCH) and hasText("App 1")).assertExists("The field keeps what was typed")
        assertEquals(DarkColors.textPrimary, textColor("App 12", AppGridTags.GRID), "The results are Dark again")
    }

    @Test
    @Verifies("FR-THM-09", "FR-TOP-02", "FR-ANIM-11")
    fun choosingAnAppearanceInTheTopMenuTakesEffectAtOnce() {
        launcher.show(state(layer = Layer.TopMenu, systemDark = false))
        assertTextIn(LightColors, string(R.string.appearance_dark), TopMenuTags.SHEET, "Top Menu")

        rule.mainClock.autoAdvance = false
        rule.onNode(hasText(string(R.string.appearance_dark)) and hasClickAction()).performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(Motion.LONG_MILLIS + FRAME_MILLIS)

        assertEquals(AppearanceMode.Dark, launcher.state.settings.appearance)
        assertEquals(DarkColors.textPrimary, textColor(string(R.string.appearance_dark), TopMenuTags.SHEET), "Selected, and Dark")
        assertEquals(Layer.TopMenu, launcher.state.layer, "The menu stays open")
    }

    @Test
    @Verifies("FR-THM-09", "FR-SET-01", "FR-ANIM-11")
    fun choosingAnAppearanceInSettingsTakesEffectAtOnce() {
        launcher.show(state(layer = Layer.Settings, systemDark = false))
        assertTextIn(LightColors, string(R.string.appearance_dark), SettingsTags.PAGE, "Settings")

        rule.mainClock.autoAdvance = false
        rule.onNode(hasText(string(R.string.appearance_dark)) and hasClickAction() and hasAnyAncestor(hasTestTag(SettingsTags.PAGE))).performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(Motion.LONG_MILLIS + FRAME_MILLIS)

        assertEquals(AppearanceMode.Dark, launcher.state.settings.appearance)
        assertEquals(DarkColors.textPrimary, textColor(string(R.string.appearance_dark), SettingsTags.PAGE), "Selected, and Dark")
        assertEquals(Layer.Settings, launcher.state.layer, "Settings stays open")
        rule.mainClock.autoAdvance = true
    }

    @Test
    @Verifies("FR-THM-08")
    fun systemBarIconsAreDarkInLightAndLightInDark() {
        launcher.show(state(systemDark = false))
        assertBarIcons(dark = true, "System, device light")

        go(Action.SystemDarkChanged(dark = true))
        assertBarIcons(dark = false, "System, device dark")

        go(Action.SetAppearance(AppearanceMode.Light))
        assertBarIcons(dark = true, "Light, device dark")

        go(Action.SystemDarkChanged(dark = false))
        go(Action.SetAppearance(AppearanceMode.Dark))
        assertBarIcons(dark = false, "Dark, device light")
    }

    /** Whether the status-bar and navigation-bar icons are [dark], over light glass (FR-THM-08). */
    private fun assertBarIcons(dark: Boolean, case: String) = rule.runOnIdle {
        val window = rule.activity.window
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        assertEquals(dark, bars.isAppearanceLightStatusBars, "$case: status bar")
        assertEquals(dark, bars.isAppearanceLightNavigationBars, "$case: navigation bar")
    }

    private companion object {
        const val FRAME_MILLIS = 16L
        const val SWITCH_LIMIT_MILLIS = 1_000L
        const val SAMPLE_STEP = 4
        const val HALF = 0.5f
    }
}
