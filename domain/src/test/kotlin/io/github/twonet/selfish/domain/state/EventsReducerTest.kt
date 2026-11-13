package io.github.twonet.selfish.domain.state

import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.AppId
import io.github.twonet.selfish.domain.model.Bounds
import io.github.twonet.selfish.domain.model.FolderId
import io.github.twonet.selfish.domain.model.GridItem
import io.github.twonet.selfish.domain.model.Layout
import io.github.twonet.selfish.domain.model.Settings
import io.github.twonet.selfish.domain.model.Market
import io.github.twonet.selfish.domain.model.PixelSize
import io.github.twonet.selfish.domain.model.WallpaperEntry
import io.github.twonet.selfish.domain.ports.ImageId
import io.github.twonet.selfish.domain.ports.NotificationItem
import io.github.twonet.selfish.domain.ports.SystemScreen
import io.github.twonet.selfish.domain.remorse.Destructive
import io.github.twonet.selfish.domain.remorse.PendingRemorse
import io.github.twonet.selfish.domain.remorse.REMORSE_DELAY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import java.time.LocalDate

/** The reducer's rules for Events (SRS §4.6, DESIGN §4.1). */
@Verifies("AR-02")
class EventsReducerTest {
    private val wallpaper = WallpaperEntry(
        ImageId("20261113_en-US"), "Lighthouse", "© Someone", "https://www.bing.com/search?q=lighthouse",
        LocalDate.of(2026, 11, 13), Market("en-US"), PixelSize(1080, 1920), bytes = 400_000,
    )

    private fun note(key: String, time: Long = 0, clearable: Boolean = true, posted: Long = time) =
        NotificationItem(key, "com.chat", 0, "Chat", "Title $key", "Text $key", time, posted, clearable)

    private val a = note("a", 3)
    private val b = note("b", 2)
    private val ongoing = note("ongoing", 1, clearable = false)

    private val home = LauncherState(restored = true, homeShown = true)
    private val events = home.copy(layer = Layer.Events)

    /** Events with notifications on and [items] listed. */
    private fun on(vararg items: NotificationItem) = events.copy(
        settings = Settings(notificationsInEvents = true),
        notificationAccess = true,
        notifications = items.toList(),
    )

    // Navigation (FR-EVT-01, FR-GEN-03, FR-GEN-04)

    @Test
    @Verifies("FR-EVT-01", "FR-GEN-03", "FR-GEN-04")
    fun `Events is a layer over Home that Back and Home close`() {
        val opened = reduce(home, Action.LayerSettled(Layer.Events)).state
        assertEquals(events, opened)

        assertEquals(home, reduce(events, Action.Back).state)
        assertEquals(home, reduce(events, Action.HomePressed).state)
        assertEquals(home, reduce(events, Action.LayerSettled(Layer.Home)).state)
    }

    // The wallpaper card (FR-EVT-03, FR-EVT-06)

    @Test
    @Verifies("FR-EVT-03", "FR-EVT-06")
    fun `Wallpaper details opens only for a wallpaper entry`() {
        assertSame(events, reduce(events, Action.OpenWallpaperDetails).state)

        val details = reduce(events.copy(wallpaper = wallpaper), Action.OpenWallpaperDetails).state

        assertEquals(Layer.WallpaperDetails, details.layer)
        assertEquals(home.copy(wallpaper = wallpaper), reduce(details, Action.Back).state)
    }

    @Test
    @Verifies("FR-EVT-03")
    fun `Learn more opens the link`() {
        val next = reduce(events, Action.OpenLink(wallpaper.link!!))

        assertSame(events, next.state)
        assertEquals(listOf<Effect>(Effect.OpenScreen(SystemScreen.Link(wallpaper.link!!))), next.effects)
    }

    // Access (FR-EVT-07, FR-ONB-04)

    @Test
    @Verifies("FR-EVT-07", "FR-ONB-04")
    fun `turning on without access asks for it first, and turns on once it is granted`() {
        val asked = reduce(events.copy(notificationAccess = false), Action.TurnOnNotifications)

        assertEquals(listOf<Effect>(Effect.RequestNotificationAccess), asked.effects)
        assertTrue(asked.state.awaitingNotificationAccess)
        assertFalse(asked.state.settings.notificationsInEvents, "Not on until access is granted")

        // The user comes back without granting it: still waiting, still off.
        val notYet = reduce(asked.state, Action.NotificationAccessChanged(false)).state
        assertFalse(notYet.settings.notificationsInEvents)
        assertTrue(notYet.showsAccessCard)

        val granted = reduce(notYet, Action.NotificationAccessChanged(true)).state
        assertTrue(granted.settings.notificationsInEvents)
        assertFalse(granted.awaitingNotificationAccess)
        assertTrue(granted.notificationsOn)
        assertFalse(granted.showsAccessCard)
    }

    @Test
    @Verifies("FR-EVT-07")
    fun `turning on with access granted turns on at once`() {
        val next = reduce(events.copy(notificationAccess = true), Action.TurnOnNotifications)

        assertTrue(next.state.notificationsOn)
        assertEquals(emptyList(), next.effects)
        assertSame(next.state, reduce(next.state, Action.TurnOnNotifications).state)
    }

    @Test
    @Verifies("FR-EVT-07")
    fun `access granted in system settings, not through the card, doesn't turn notifications on`() {
        val granted = reduce(events, Action.NotificationAccessChanged(true)).state

        assertFalse(granted.notificationsOn)
        assertTrue(granted.showsAccessCard, "The card still offers to turn them on")
    }

    @Test
    @Verifies("FR-ONB-04", "NFR-PRIV-05")
    fun `revoked access turns notifications off and forgets them`() {
        val revoked = reduce(on(a, b), Action.NotificationAccessChanged(false)).state

        assertFalse(revoked.settings.notificationsInEvents)
        assertEquals(emptyList(), revoked.notifications)
        assertTrue(revoked.showsAccessCard)
    }

    @Test
    @Verifies("FR-ONB-04")
    fun `access revoked while the launcher wasn't running turns the saved setting off once checked`() {
        val restored = reduce(LauncherState(), Action.Restored(null, Settings(notificationsInEvents = true))).state
        assertTrue(restored.settings.notificationsInEvents, "Kept until access has been checked")

        val checked = reduce(restored, Action.NotificationAccessChanged(false)).state

        assertFalse(checked.settings.notificationsInEvents)
    }

    @Test
    @Verifies("FR-EVT-07")
    fun `dismissing the card hides it for good`() {
        val hidden = reduce(events, Action.HideNotificationsCard).state

        assertTrue(hidden.settings.notificationsCardDismissed)
        assertFalse(hidden.showsAccessCard)
        assertSame(hidden, reduce(hidden, Action.HideNotificationsCard).state)
    }

    // The list (FR-EVT-04, FR-EVT-05)

    @Test
    @Verifies("FR-EVT-04", "NFR-PRIV-05")
    fun `notifications are listed only while they are on`() {
        assertSame(events, reduce(events, Action.NotificationsChanged(listOf(a))).state)

        val listed = reduce(on(), Action.NotificationsChanged(listOf(a, b))).state
        assertEquals(listOf(a, b), listed.notifications)
        assertSame(listed, reduce(listed, Action.NotificationsChanged(listOf(a, b))).state)
    }

    @Test
    @Verifies("FR-EVT-04", "FR-GEN-05")
    fun `opening a notification is a launch`() {
        val bounds = Bounds(1, 2, 3, 4)

        val next = reduce(on(a), Action.OpenNotification("a", bounds))

        assertTrue(next.state.launching)
        assertEquals(
            listOf(Effect.OpenNotification("a", bounds), Effect.DispatchAfter(LAUNCH_TIMEOUT, Action.LaunchTimedOut(1))),
            next.effects,
        )
        assertEquals(emptyList(), reduce(next.state, Action.OpenNotification("a", bounds)).effects, "A second tap waits")
        assertEquals(emptyList(), reduce(on(a), Action.OpenNotification("gone", null)).effects)
    }

    @Test
    @Verifies("FR-ANIM-15")
    fun `under reduced motion a notification opens without growing from its row`() {
        val bounds = Bounds(1, 2, 3, 4)

        val next = reduce(on(a).copy(systemAnimationsOff = true), Action.OpenNotification("a", bounds))

        assertEquals(Effect.OpenNotification("a", bounds, animate = false), next.effects.first())
    }

    @Test
    @Verifies("FR-EVT-05")
    fun `a dismissed notification leaves at once and stays away until the system reports it gone`() {
        val next = reduce(on(a, b), Action.DismissNotification("a"))

        assertEquals(listOf(b), next.state.notifications)
        assertEquals(listOf<Effect>(Effect.DismissNotifications(setOf(a.ref))), next.effects)

        // The system hasn't removed it yet.
        val stale = reduce(next.state, Action.NotificationsChanged(listOf(a, b))).state
        assertEquals(listOf(b), stale.notifications)

        // Now it has; nothing more to hide.
        val gone = reduce(stale, Action.NotificationsChanged(listOf(b))).state
        assertEquals(emptySet(), gone.dismissing)

        // Updated meanwhile, it is new, and shows again.
        val updated = note("a", 5, posted = 5)
        val back = reduce(stale, Action.NotificationsChanged(listOf(updated, b))).state
        assertEquals(listOf(updated, b), back.notifications)
    }

    @Test
    @Verifies("FR-EVT-05")
    fun `an ongoing notification can't be dismissed`() {
        val state = on(ongoing)

        val next = reduce(state, Action.DismissNotification("ongoing"))

        assertSame(state, next.state)
        assertEquals(emptyList(), next.effects)
    }

    // Clear notifications (FR-EVT-06, FR-GEST-06, AC-06)

    @Test
    @Verifies("FR-EVT-06", "FR-GEST-06", "AC-06")
    fun `clearing waits five seconds, then dismisses the dismissible notifications that were shown`() {
        val next = reduce(on(a, b, ongoing), Action.ClearNotifications)

        val pending = PendingRemorse(1, Destructive.ClearNotifications(setOf(a.ref, b.ref)))
        assertEquals(pending, next.state.remorse)
        assertEquals(setOf(a.ref, b.ref), next.state.pendingClear)
        assertEquals(listOf(a, b, ongoing), next.state.notifications, "Nothing is cleared yet")
        assertEquals(listOf<Effect>(Effect.DispatchAfter(REMORSE_DELAY, Action.RemorseDue(1))), next.effects)
        assertEquals(emptyList(), next.state.clearable, "Nothing more to clear")

        // One arrives meanwhile: it wasn't seen, so it stays.
        val c = note("c", 9)
        val arrived = reduce(next.state, Action.NotificationsChanged(listOf(c, a, b, ongoing))).state

        val due = reduce(arrived, Action.RemorseDue(1))

        assertNull(due.state.remorse)
        assertEquals(listOf(c, ongoing), due.state.notifications)
        assertEquals(listOf<Effect>(Effect.DismissNotifications(setOf(a.ref, b.ref))), due.effects)
    }

    @Test
    @Verifies("FR-EVT-06", "FR-GEST-06", "AC-06")
    fun `a cancelled clear dismisses nothing, and its timer runs nothing`() {
        val waiting = reduce(on(a, b), Action.ClearNotifications).state

        val cancelled = reduce(waiting, Action.CancelRemorse).state

        assertEquals(on(a, b).copy(remorses = 1), cancelled)
        val stale = reduce(cancelled, Action.RemorseDue(1))
        assertSame(cancelled, stale.state)
        assertEquals(emptyList(), stale.effects)
    }

    @Test
    @Verifies("FR-EVT-06")
    fun `nothing to clear, no timer`() {
        val state = on(ongoing)

        assertSame(state, reduce(state, Action.ClearNotifications).state)
        assertSame(events, reduce(events, Action.ClearNotifications).state)
    }

    @Test
    @Verifies("FR-EVT-06", "FR-GEST-06")
    fun `another destructive action clears at once`() {
        val tools = GridItem.Folder(FolderId(1), "Tools", listOf(AppId("p.a", "A", 0), AppId("p.b", "B", 0)))
        val apps = tools.apps.map { AppEntry(it, it.packageName) }
        val state = on(a, b).copy(apps = apps, layout = Layout(grid = listOf(tools)))
        val clearing = reduce(state, Action.ClearNotifications).state

        val removing = reduce(clearing, Action.RemoveFolder(tools.id))

        assertEquals(emptyList(), removing.state.notifications)
        assertEquals(PendingRemorse(2, Destructive.RemoveFolder(tools.id)), removing.state.remorse)
        assertEquals(
            listOf(Effect.DismissNotifications(setOf(a.ref, b.ref)), Effect.DispatchAfter(REMORSE_DELAY, Action.RemorseDue(2))),
            removing.effects,
        )
    }

    @Test
    @Verifies("FR-ONB-04")
    fun `a clear waiting when access is revoked is dropped`() {
        val waiting = reduce(on(a), Action.ClearNotifications).state

        val revoked = reduce(waiting, Action.NotificationAccessChanged(false)).state

        assertNull(revoked.remorse)
        assertEquals(emptyList(), reduce(revoked, Action.RemorseDue(1)).effects)
    }
}
