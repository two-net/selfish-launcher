package io.github.twonet.selfish.domain.state

import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.AppId
import io.github.twonet.selfish.domain.model.AppearanceMode
import io.github.twonet.selfish.domain.model.Layout
import io.github.twonet.selfish.domain.model.Settings
import io.github.twonet.selfish.domain.model.WallpaperEntry
import io.github.twonet.selfish.domain.ports.ImageId
import io.github.twonet.selfish.domain.ports.SystemScreen
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The reducer's rules for the Top Menu (SRS §4.7, DESIGN §4.1). */
@Verifies("AR-02")
class TopMenuReducerTest {
    private val maps = AppEntry(AppId("com.maps", "Main", 0), "Maps")

    private val today = WallpaperEntry(ImageId("20261113_en-US"), "Lighthouse", "© Someone", null)
    private val yesterday = WallpaperEntry(ImageId("20261112_en-US"), "Glacier", "© Someone else", null)

    private val home = LauncherState(
        apps = listOf(maps),
        layout = Layout(home = listOf(maps.id)),
        restored = true,
        homeShown = true,
    )
    private val topMenu = home.copy(layer = Layer.TopMenu)

    /** The Top Menu with [today] and [yesterday] cached, newest first, and [today] in use. */
    private val cached = topMenu.copy(wallpapers = listOf(today, yesterday), wallpaper = today)

    // Navigation (FR-TOP-01, FR-TOP-05, FR-TOP-06, FR-GEN-03, FR-GEN-04)

    @Test
    @Verifies("FR-TOP-01", "FR-TOP-05", "FR-TOP-06", "FR-GEN-03", "FR-GEN-04")
    fun `the Top Menu is a layer over Home that Back, Home and a swipe up close`() {
        assertEquals(topMenu, reduce(home, Action.LayerSettled(Layer.TopMenu)).state)

        assertEquals(home, reduce(topMenu, Action.Back).state)
        assertEquals(home, reduce(topMenu, Action.HomePressed).state)
        assertEquals(home, reduce(topMenu, Action.LayerSettled(Layer.Home)).state)
    }

    @Test
    @Verifies("FR-TOP-06", "FR-HOME-06")
    fun `opening the Top Menu closes a cover's context menu`() {
        val menu = home.copy(overlays = listOf(Overlay.ContextMenu(MenuTarget.Cover(maps.id))))

        assertEquals(topMenu, reduce(menu, Action.LayerSettled(Layer.TopMenu)).state)
    }

    // Appearance (FR-TOP-02)

    @Test
    @Verifies("FR-TOP-02", "FR-THM-09")
    fun `the appearance switch changes the setting`() {
        val dark = reduce(topMenu, Action.SetAppearance(AppearanceMode.Dark))

        assertEquals(AppearanceMode.Dark, dark.state.settings.appearance)
        assertEquals(topMenu.settings.copy(appearance = AppearanceMode.Dark), dark.state.settings)
        assertEquals(emptyList(), dark.effects)
        assertSame(dark.state, reduce(dark.state, Action.SetAppearance(AppearanceMode.Dark)).state)
        assertEquals(AppearanceMode.System, reduce(dark.state, Action.SetAppearance(AppearanceMode.System)).state.settings.appearance)
    }

    // Ambiences (FR-TOP-03)

    @Test
    @Verifies("FR-TOP-03", "FR-WP-08")
    fun `choosing an ambience pins that image and puts it in use`() {
        val pinned = reduce(cached, Action.PinWallpaper(yesterday.image)).state

        assertEquals(yesterday.image, pinned.pinned)
        assertEquals(yesterday, pinned.wallpaper)
        assertSame(pinned, reduce(pinned, Action.PinWallpaper(yesterday.image)).state)
    }

    @Test
    @Verifies("FR-TOP-03", "FR-WP-08")
    fun `the image in use can be pinned too`() {
        val pinned = reduce(cached, Action.PinWallpaper(today.image)).state

        assertEquals(today.image, pinned.pinned)
        assertEquals(today, pinned.wallpaper)
    }

    @Test
    @Verifies("FR-TOP-03")
    fun `only a cached image can be pinned, and only with Daily Bing wallpaper on`() {
        assertSame(cached, reduce(cached, Action.PinWallpaper(ImageId("gone"))).state)

        val off = cached.copy(settings = Settings(dailyWallpaper = false))
        assertSame(off, reduce(off, Action.PinWallpaper(yesterday.image)).state)
    }

    @Test
    @Verifies("FR-TOP-03", "FR-WP-08")
    fun `Daily unpins and puts the newest image back in use`() {
        val pinned = cached.copy(pinned = yesterday.image, wallpaper = yesterday)

        val daily = reduce(pinned, Action.ResumeDaily).state

        assertNull(daily.pinned)
        assertEquals(today, daily.wallpaper)
        assertSame(daily, reduce(daily, Action.ResumeDaily).state)
    }

    @Test
    @Verifies("FR-TOP-03", "FR-WP-13")
    fun `the Ambiences row shows only with Daily Bing wallpaper on and images cached`() {
        assertTrue(cached.showsAmbiences)
        assertFalse(topMenu.showsAmbiences, "Nothing cached yet")
        assertFalse(cached.copy(settings = Settings(dailyWallpaper = false)).showsAmbiences)
    }

    // Shortcuts (FR-TOP-04)

    @Test
    @Verifies("FR-TOP-04")
    fun `the System settings shortcut opens the Settings app and keeps the menu`() {
        val next = reduce(topMenu, Action.OpenSystemSettings)

        assertSame(topMenu, next.state)
        assertEquals(listOf<Effect>(Effect.OpenScreen(SystemScreen.SystemSettings)), next.effects)
    }

    @Test
    @Verifies("FR-TOP-04")
    fun `the Wallpaper details shortcut opens the page only once there is a wallpaper`() {
        assertSame(topMenu, reduce(topMenu, Action.OpenWallpaperDetails).state)

        val details = reduce(cached, Action.OpenWallpaperDetails).state
        assertEquals(Layer.WallpaperDetails, details.layer)
    }
}
