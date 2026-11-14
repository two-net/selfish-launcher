package io.github.twonet.selfish.domain.state

import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.Animations
import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.AppId
import io.github.twonet.selfish.domain.model.AppearanceMode
import io.github.twonet.selfish.domain.model.FolderId
import io.github.twonet.selfish.domain.model.GridItem
import io.github.twonet.selfish.domain.model.Haptics
import io.github.twonet.selfish.domain.model.HighlightSetting
import io.github.twonet.selfish.domain.model.Layout
import io.github.twonet.selfish.domain.model.Market
import io.github.twonet.selfish.domain.model.PixelSize
import io.github.twonet.selfish.domain.model.WallpaperEntry
import io.github.twonet.selfish.domain.ports.DefaultApps
import io.github.twonet.selfish.domain.ports.ImageId
import io.github.twonet.selfish.domain.remorse.Destructive
import io.github.twonet.selfish.domain.remorse.REMORSE_DELAY
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The reducer's rules for the Settings page (SRS §4.13, DESIGN §4.1). */
@Verifies("AR-02")
class SettingsReducerTest {
    private fun app(label: String) = AppEntry(AppId("pkg.${label.lowercase()}", "Main", 0), label)

    private val books = app("Books")
    private val camera = app("Camera")
    private val chat = app("Chat")
    private val maps = app("Maps")
    private val phone = app("Phone")
    private val zoom = app("Zoom")

    private val today = WallpaperEntry(ImageId("20261113_en-US"), "Lighthouse", "© Someone", null, LocalDate.of(2026, 11, 13), Market("en-US"), PixelSize(1080, 1920), 400_000)

    /** A folder the user made, out of alphabetical order, with the apps alphabetical in the catalog. */
    private val folder = GridItem.Folder(FolderId(1), "Work", listOf(maps.id, chat.id))
    private val custom = Layout(
        grid = listOf(GridItem.App(zoom.id), folder, GridItem.App(phone.id), GridItem.App(books.id), GridItem.App(camera.id)),
        home = listOf(zoom.id),
    )

    private val home = LauncherState(
        apps = listOf(books, camera, chat, maps, phone, zoom),
        layout = custom,
        restored = true,
        homeShown = true,
        wallpapers = listOf(today),
        wallpaper = today,
        pinned = today.image,
    )
    private val settings = home.copy(layer = Layer.Settings)
    private val about = settings.copy(overlays = listOf(Overlay.About))

    // Navigation (FR-TOP-04, FR-WP-09, FR-SET-17, FR-GEN-03, FR-GEN-04)

    @Test
    @Verifies("FR-TOP-04", "FR-WP-09")
    fun `Settings opens over Home from the Top Menu and from Wallpaper details`() {
        assertEquals(settings, reduce(home.copy(layer = Layer.TopMenu), Action.OpenSettings).state)
        assertEquals(settings, reduce(home.copy(layer = Layer.WallpaperDetails), Action.OpenSettings).state)
        assertSame(settings, reduce(settings, Action.OpenSettings).state)
        assertTrue(Layer.Settings.isPage)
        assertTrue(Layer.WallpaperDetails.isPage)
        assertFalse(Layer.TopMenu.isPage)
    }

    @Test
    @Verifies("FR-SET-17", "FR-GEN-03", "FR-GEN-04")
    fun `About opens over Settings, Back returns to Settings and then Home, and Home closes both`() {
        assertEquals(about, reduce(settings, Action.OpenAbout).state)
        assertSame(about, reduce(about, Action.OpenAbout).state)

        assertEquals(settings, reduce(about, Action.Back).state)
        assertEquals(home, reduce(settings, Action.Back).state)
        assertEquals(home, reduce(about, Action.HomePressed).state)
    }

    @Test
    @Verifies("FR-SET-17")
    fun `About opens only over Settings`() {
        assertSame(home, reduce(home, Action.OpenAbout).state)
        val topMenu = home.copy(layer = Layer.TopMenu)
        assertSame(topMenu, reduce(topMenu, Action.OpenAbout).state)
    }

    // Settings that take effect at once (FR-SET-01 to FR-SET-15)

    @Test
    @Verifies("FR-SET-01", "FR-THM-09")
    fun `Appearance changes in Settings, which stays open`() {
        val dark = reduce(settings, Action.SetAppearance(AppearanceMode.Dark)).state

        assertEquals(AppearanceMode.Dark, dark.settings.appearance)
        assertEquals(Layer.Settings, dark.layer)
    }

    @Test
    @Verifies("FR-SET-06", "FR-SET-07", "FR-SET-09", "FR-SET-14", "FR-SET-15")
    fun `each setting changes only its own value, and choosing the current value changes nothing`() {
        val cases = listOf(
            Action.SetShowClock(false) to settings.settings.copy(showClock = false),
            Action.SetGridColumns(5) to settings.settings.copy(gridColumns = 5),
            Action.SetHaptics(Haptics.Off) to settings.settings.copy(haptics = Haptics.Off),
            Action.SetAnimations(Animations.Reduced) to settings.settings.copy(animations = Animations.Reduced),
            Action.SetHighlight(HighlightSetting.Teal) to settings.settings.copy(highlight = HighlightSetting.Teal),
        )
        for ((action, expected) in cases) {
            val next = reduce(settings, action)
            assertEquals(expected, next.state.settings, "$action")
            assertEquals(emptyList(), next.effects, "$action")
            assertSame(next.state, reduce(next.state, action).state, "$action again")
        }
    }

    @Test
    @Verifies("FR-SET-09")
    fun `the App Grid can't be given a column count outside 3 to 5`() {
        assertSame(settings, reduce(settings, Action.SetGridColumns(2)).state)
        assertSame(settings, reduce(settings, Action.SetGridColumns(6)).state)
        assertEquals(3, reduce(settings, Action.SetGridColumns(3)).state.settings.gridColumns)
    }

    @Test
    @Verifies("FR-SET-15", "FR-ANIM-15")
    fun `Animations Reduced reduces motion, and Follow system follows the system again`() {
        val reduced = reduce(settings, Action.SetAnimations(Animations.Reduced)).state
        assertTrue(reduced.reducedMotion)

        val following = reduce(reduced, Action.SetAnimations(Animations.FollowSystem)).state
        assertFalse(following.reducedMotion)
        assertTrue(following.copy(systemAnimationsOff = true).reducedMotion)
    }

    @Test
    @Verifies("FR-SET-12", "FR-ONB-04")
    fun `Notifications in Events turns on through the access check and off at once`() {
        val granted = settings.copy(notificationAccess = true)

        val on = reduce(granted, Action.TurnOnNotifications).state
        assertTrue(on.notificationsOn)

        val off = reduce(on, Action.TurnOffNotifications).state
        assertFalse(off.settings.notificationsInEvents)
        assertFalse(off.notificationsOn)
        assertSame(off, reduce(off, Action.TurnOffNotifications).state)
    }

    @Test
    @Verifies("FR-SET-12", "FR-ONB-04")
    fun `turning notifications off while waiting for access means access granted later doesn't turn them on`() {
        val asked = reduce(settings.copy(notificationAccess = false), Action.TurnOnNotifications)
        assertTrue(asked.state.awaitingNotificationAccess)
        assertEquals(listOf<Effect>(Effect.RequestNotificationAccess), asked.effects)

        val off = reduce(asked.state, Action.TurnOffNotifications).state
        assertFalse(off.awaitingNotificationAccess)

        val granted = reduce(off, Action.NotificationAccessChanged(granted = true)).state
        assertFalse(granted.settings.notificationsInEvents)
    }

    // The access explanation (FR-ONB-04, FR-SET-12)

    private val notifications = AccessFeature.NotificationsInEvents
    private val explanation = Overlay.AccessExplanation(notifications)

    @Test
    @Verifies("FR-SET-12", "FR-ONB-04")
    fun `the switch without access explains first and asks nothing, and with access turns on at once`() {
        val explained = reduce(settings.copy(notificationAccess = false), Action.TurnOnFeature(notifications))
        assertEquals(listOf<Overlay>(explanation), explained.state.overlays)
        assertEquals(emptyList(), explained.effects)
        assertFalse(explained.state.awaitingNotificationAccess)
        assertFalse(explained.state.settings.notificationsInEvents)
        assertSame(explained.state, reduce(explained.state, Action.TurnOnFeature(notifications)).state)

        val unchecked = reduce(settings, Action.TurnOnFeature(notifications)).state
        assertEquals(listOf<Overlay>(explanation), unchecked.overlays, "Access not checked yet counts as missing")

        val granted = reduce(settings.copy(notificationAccess = true), Action.TurnOnFeature(notifications))
        assertTrue(granted.state.notificationsOn)
        assertEquals(emptyList(), granted.state.overlays)
        assertEquals(emptyList(), granted.effects)
    }

    @Test
    @Verifies("FR-SET-12", "FR-ONB-04")
    fun `Continue closes the explanation and asks for access once, and access granted turns the feature on`() {
        val explained = reduce(settings.copy(notificationAccess = false), Action.TurnOnFeature(notifications)).state

        val asked = reduce(explained, Action.RequestAccess(notifications))
        assertEquals(settings.overlays, asked.state.overlays)
        assertTrue(asked.state.awaitingNotificationAccess)
        assertEquals(listOf<Effect>(Effect.RequestNotificationAccess), asked.effects)

        val again = reduce(asked.state, Action.RequestAccess(notifications))
        assertSame(asked.state, again.state, "A second tap as it closes asks nothing more")
        assertEquals(emptyList(), again.effects)

        val granted = reduce(asked.state, Action.NotificationAccessChanged(granted = true)).state
        assertTrue(granted.notificationsOn)
    }

    @Test
    @Verifies("FR-SET-12", "FR-ONB-04", "FR-GEN-03", "FR-GEN-04")
    fun `Not now and Back close the explanation and leave the feature off, and Home closes Settings too`() {
        val explained = reduce(settings.copy(notificationAccess = false), Action.TurnOnFeature(notifications)).state

        val back = reduce(explained, Action.Back).state
        assertEquals(Layer.Settings, back.layer)
        assertEquals(emptyList(), back.overlays)
        assertFalse(back.settings.notificationsInEvents)
        assertFalse(back.awaitingNotificationAccess)

        val home = reduce(explained, Action.HomePressed).state
        assertEquals(Layer.Home, home.layer)
        assertEquals(emptyList(), home.overlays)
    }

    @Test
    @Verifies("FR-ONB-04")
    fun `the explanation opens only over Settings, and About doesn't open over it`() {
        val events = home.copy(layer = Layer.Events, notificationAccess = false)
        assertSame(events, reduce(events, Action.TurnOnFeature(notifications)).state)
        assertSame(about, reduce(about, Action.TurnOnFeature(notifications)).state)

        val explained = reduce(settings.copy(notificationAccess = false), Action.TurnOnFeature(notifications)).state
        assertSame(explained, reduce(explained, Action.OpenAbout).state)
        assertSame(settings, reduce(settings, Action.RequestAccess(notifications)).state, "Continue without its explanation")
    }

    @Test
    @Verifies("FR-EVT-07", "FR-ONB-04")
    fun `the Events card explains the feature itself, so its Turn on asks at once`() {
        val events = home.copy(layer = Layer.Events, notificationAccess = false)

        val asked = reduce(events, Action.TurnOnNotifications)

        assertEquals(emptyList(), asked.state.overlays)
        assertEquals(listOf<Effect>(Effect.RequestNotificationAccess), asked.effects)
    }

    // Sort A–Z (FR-SET-10, FR-GEST-06)

    private val sortedGrid = listOf(
        GridItem.App(books.id),
        GridItem.App(camera.id),
        GridItem.App(phone.id),
        folder.copy(apps = listOf(chat.id, maps.id)),
        GridItem.App(zoom.id),
    )

    @Test
    @Verifies("FR-SET-10", "FR-GEST-06")
    fun `Sort A–Z waits for its remorse timer, then sorts the grid and leaves Home alone`() {
        val started = reduce(settings, Action.SortAppGrid)

        assertEquals(custom, started.state.layout, "Nothing changes until the timer runs out")
        assertEquals(Destructive.SortAppGrid, started.state.remorse?.action)
        val number = started.state.remorse!!.number
        assertEquals(listOf<Effect>(Effect.DispatchAfter(REMORSE_DELAY, Action.RemorseDue(number))), started.effects)

        val sorted = reduce(started.state, Action.RemorseDue(number)).state
        assertEquals(sortedGrid, sorted.layout?.grid)
        assertEquals(custom.home, sorted.layout?.home)
        assertNull(sorted.remorse)
    }

    @Test
    @Verifies("FR-SET-10", "FR-GEST-06")
    fun `cancelling Sort A–Z keeps the order, and choosing it again while it waits changes nothing`() {
        val started = reduce(settings, Action.SortAppGrid).state

        assertSame(started, reduce(started, Action.SortAppGrid).state)

        val cancelled = reduce(started, Action.CancelRemorse).state
        assertEquals(custom, cancelled.layout)
        assertSame(cancelled, reduce(cancelled, Action.RemorseDue(started.remorse!!.number)).state)
    }

    @Test
    @Verifies("FR-SET-10", "FR-SET-16")
    fun `before the first-run layout is built there is nothing to sort or reset`() {
        val noLayout = settings.copy(layout = null)

        assertSame(noLayout, reduce(noLayout, Action.SortAppGrid).state)
        assertSame(noLayout, reduce(noLayout, Action.ResetLayout).state)
    }

    // Reset layout (FR-SET-16, FR-GEST-06)

    private val defaults = DefaultApps(phone = phone.id, camera = camera.id)
    private val firstRun = Layout(grid = listOf(books, camera, chat, maps, phone, zoom).map { GridItem.App(it.id) }, home = listOf(phone.id, camera.id))

    @Test
    @Verifies("FR-SET-16", "FR-GEST-06")
    fun `Reset layout waits for its timer, looks up the default apps, and keeps the layout until they arrive`() {
        val changed = settings.copy(settings = settings.settings.copy(gridColumns = 5, showClock = false))
        val started = reduce(changed, Action.ResetLayout)
        assertEquals(custom, started.state.layout)
        assertEquals(Destructive.ResetLayout, started.state.remorse?.action)

        val due = reduce(started.state, Action.RemorseDue(started.state.remorse!!.number))
        assertEquals(listOf<Effect>(Effect.ResolveDefaultApps), due.effects)
        assertTrue(due.state.resettingLayout)
        assertEquals(custom, due.state.layout, "Nothing flashes empty while the defaults are looked up")

        val reset = reduce(due.state, Action.DefaultAppsResolved(defaults)).state
        assertEquals(firstRun, reset.layout)
        assertFalse(reset.resettingLayout)
        assertFalse(reset.resolvingDefaults)
        // Settings and the wallpaper are kept.
        assertEquals(changed.settings, reset.settings)
        assertEquals(changed.wallpaper, reset.wallpaper)
        assertEquals(changed.wallpapers, reset.wallpapers)
        assertEquals(changed.pinned, reset.pinned)
    }

    @Test
    @Verifies("FR-SET-16", "FR-FOLD-03")
    fun `a folder open when the reset lands closes, since the first-run grid has none`() {
        val resetting = settings.copy(layer = Layer.AppGrid, overlays = listOf(Overlay.Folder(folder.id)), resettingLayout = true, resolvingDefaults = true)

        val reset = reduce(resetting, Action.DefaultAppsResolved(defaults)).state

        assertEquals(firstRun, reset.layout)
        assertEquals(emptyList(), reset.overlays)
    }

    @Test
    @Verifies("FR-SET-16", "FR-FOLD-07")
    fun `a folder removal started while the reset looks up the defaults is dropped with the folder`() {
        val resetting = settings.copy(resettingLayout = true, resolvingDefaults = true, layer = Layer.AppGrid)
        val removing = reduce(resetting, Action.RemoveFolder(folder.id)).state
        assertEquals(folder.id, removing.pendingRemoval)

        val reset = reduce(removing, Action.DefaultAppsResolved(defaults)).state

        assertEquals(firstRun, reset.layout)
        assertNull(reset.remorse)
    }

    @Test
    @Verifies("FR-SET-16")
    fun `a reset that lands while the defaults are already being looked up waits for that lookup`() {
        val looking = settings.copy(resolvingDefaults = true)
        val started = reduce(looking, Action.ResetLayout).state

        val due = reduce(started, Action.RemorseDue(started.remorse!!.number))

        assertEquals(emptyList(), due.effects)
        assertTrue(due.state.resettingLayout)
        assertEquals(firstRun, reduce(due.state, Action.DefaultAppsResolved(defaults)).state.layout)
    }

    @Test
    @Verifies("FR-SET-10", "FR-SET-16", "FR-GEST-06")
    fun `starting Reset layout while Sort A–Z waits runs the sort at once`() {
        val sorting = reduce(settings, Action.SortAppGrid).state

        val resetting = reduce(sorting, Action.ResetLayout)

        assertEquals(sortedGrid, resetting.state.layout?.grid)
        assertEquals(Destructive.ResetLayout, resetting.state.remorse?.action)
    }
}
