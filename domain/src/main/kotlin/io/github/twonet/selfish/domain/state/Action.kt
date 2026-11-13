package io.github.twonet.selfish.domain.state

import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.AppId
import io.github.twonet.selfish.domain.model.AppearanceMode
import io.github.twonet.selfish.domain.model.Bounds
import io.github.twonet.selfish.domain.model.FolderId
import io.github.twonet.selfish.domain.model.GridItem
import io.github.twonet.selfish.domain.model.HighlightSetting
import io.github.twonet.selfish.domain.model.Layout
import io.github.twonet.selfish.domain.model.DownloadOn
import io.github.twonet.selfish.domain.model.Market
import io.github.twonet.selfish.domain.model.Rgb
import io.github.twonet.selfish.domain.model.Settings
import io.github.twonet.selfish.domain.model.WallpaperIndex
import io.github.twonet.selfish.domain.ports.ClockInfo
import io.github.twonet.selfish.domain.ports.DefaultApps
import io.github.twonet.selfish.domain.ports.ImageId
import io.github.twonet.selfish.domain.ports.NotificationItem
import io.github.twonet.selfish.domain.ports.ScheduledRun
import io.github.twonet.selfish.domain.ports.WallpaperId
import io.github.twonet.selfish.domain.wallpaper.CheckResult
import java.time.Instant
import java.time.LocalDate

/** Everything that can change [LauncherState]: user actions from Presentation and events from ports. */
sealed interface Action {
    /**
     * The saved layout, settings and wallpaper cache index were read at start
     * (FR-GEN-07, FR-GEN-08). [layout] is `null` when nothing usable was saved.
     */
    data class Restored(val layout: Layout?, val settings: Settings, val wallpapers: WallpaperIndex = WallpaperIndex()) : Action

    /** The app catalog reported its current list. */
    data class CatalogChanged(val apps: List<AppEntry>) : Action

    /** The user tapped an app; [from] is the tapped item's bounds on screen. */
    data class Launch(val app: AppId, val from: Bounds?) : Action

    /** The launch numbered [launch] has been in progress for [LAUNCH_TIMEOUT] (FR-GEN-05). */
    data class LaunchTimedOut(val launch: Int) : Action

    /** System Back (FR-GEN-04). */
    data object Back : Action

    /** The system Home button or gesture, while the launcher is the Home app (FR-GEN-03). */
    data object HomePressed : Action

    /** The launcher came on screen (its `onStart`). */
    data object HomeShown : Action

    /** The launcher left the screen (its `onStop`), for example because an app opened over it. */
    data object HomeHidden : Action

    /** The system ran the background job (AR-06). */
    data class ScheduledRunDue(val run: ScheduledRun) : Action

    /** The device's default apps were looked up for the first-run layout (FR-HOME-07). */
    data class DefaultAppsResolved(val defaults: DefaultApps) : Action

    /** The time, time zone or 12/24-hour setting changed while the launcher is shown (FR-HOME-02). */
    data class ClockChanged(val clock: ClockInfo) : Action

    /** The system's "remove animations" setting is now [off], or not (FR-ANIM-15). */
    data class SystemAnimationsChanged(val off: Boolean) : Action

    /**
     * The device's dark-theme setting is now [dark], or `null` if the device
     * has none (FR-THM-03, FR-THM-04).
     */
    data class SystemDarkChanged(val dark: Boolean?) : Action

    /** The user long-pressed [target]; its menu replaces any open one (FR-HOME-06, FR-GRID-06). */
    data class OpenContextMenu(val target: MenuTarget) : Action

    /** The user tapped outside the context menu, or started dragging its item. */
    data object CloseContextMenu : Action

    /** "Add to Home" (FR-HOME-05). Ignored when Home is full; the menu explains that instead. */
    data class AddToHome(val app: AppId) : Action

    /** "Remove from Home" (FR-HOME-05). */
    data class RemoveFromHome(val app: AppId) : Action

    /** A cover was dropped at position [to] (FR-HOME-06). */
    data class MoveCover(val app: AppId, val to: Int) : Action

    /**
     * The user moved to [layer]: a swipe was released past its threshold or
     * flung (FR-GRID-01, FR-GRID-04, FR-GEST-02), or a screen reader asked
     * for it (FR-GEST-09). Presentation owns the transition while the finger
     * is down; this is where it ends up (DESIGN §3.3).
     */
    data class LayerSettled(val layer: Layer) : Action

    /**
     * The text in the App Grid's search field is now [query] (FR-SRCH-01).
     * Ignored anywhere but the App Grid.
     */
    data class Search(val query: String) : Action

    /** An App Grid item was dropped at position [to] (FR-GRID-05, FR-GRID-07). */
    data class MoveGridItem(val item: GridItem, val to: Int) : Action

    /** [app] was dropped onto [target], which makes a folder of the two (FR-GRID-08, FR-FOLD-01). */
    data class DropOnto(val app: AppId, val target: AppId) : Action

    /** The user tapped [folder]'s tile, which opens its panel (FR-FOLD-03). */
    data class OpenFolder(val folder: FolderId) : Action

    /** The user tapped outside the open folder's panel (FR-FOLD-03). */
    data object CloseFolder : Action

    /** [app], on its own in the grid, was dropped onto [folder] (FR-FOLD-05). */
    data class AddToFolder(val app: AppId, val folder: FolderId) : Action

    /**
     * [app] was dragged out of the open folder's panel, which closes it; it
     * joins the grid at position [to], where the drag carries on (FR-FOLD-05).
     */
    data class TakeOutOfFolder(val app: AppId, val to: Int) : Action

    /** [app] was dropped at position [to] within its folder's panel. */
    data class MoveInFolder(val app: AppId, val to: Int) : Action

    /** The user renamed [folder] to [name] (FR-FOLD-04). */
    data class RenameFolder(val folder: FolderId, val name: String) : Action

    /** "Remove folder", which runs once its remorse timer runs out (FR-FOLD-07, FR-GEST-06). */
    data class RemoveFolder(val folder: FolderId) : Action

    /** The user tapped the remorse banner, so the waiting action never runs (FR-GEST-06). */
    data object CancelRemorse : Action

    /** The remorse timer numbered [number] ran out (FR-GEST-06). */
    data class RemorseDue(val number: Int) : Action

    /** "App info" (FR-GRID-06). */
    data class OpenAppInfo(val app: AppId) : Action

    /** "Uninstall", which opens the system's confirmation (FR-GRID-06). */
    data class Uninstall(val app: AppId) : Action

    /**
     * The Events card or pulley item "Wallpaper details" (FR-EVT-03,
     * FR-EVT-06). Ignored without a wallpaper entry, since there is nothing
     * to show.
     */
    data object OpenWallpaperDetails : Action

    /** "Learn more" about the wallpaper, which opens [url] in the browser (FR-EVT-03). */
    data class OpenLink(val url: String) : Action

    /** The notification access check answered [granted] (FR-EVT-07, FR-ONB-04). */
    data class NotificationAccessChanged(val granted: Boolean) : Action

    /** The current notifications, newest first (FR-EVT-04). */
    data class NotificationsChanged(val items: List<NotificationItem>) : Action

    /**
     * "Turn on" in the Events card (FR-EVT-07). Without access, it asks for
     * it first (FR-ONB-04).
     */
    data object TurnOnNotifications : Action

    /** The Events card about notifications was dismissed, which hides it for good (FR-EVT-07). */
    data object HideNotificationsCard : Action

    /** The user tapped the notification [key]; [from] is its row's bounds on screen (FR-EVT-04). */
    data class OpenNotification(val key: String, val from: Bounds?) : Action

    /** The notification [key] was swiped away (FR-EVT-05). */
    data class DismissNotification(val key: String) : Action

    /** "Clear notifications", which runs once its remorse timer runs out (FR-EVT-06, FR-GEST-06). */
    data object ClearNotifications : Action

    /** The appearance switch chose [mode] (FR-TOP-02, FR-THM-09). It is saved with the settings. */
    data class SetAppearance(val mode: AppearanceMode) : Action

    /**
     * An ambience was chosen: the cached [image] becomes the wallpaper and
     * stays until "Daily" is chosen again (FR-TOP-03, FR-WP-08). Ignored for
     * an image that isn't cached, or with Daily Bing wallpaper off.
     */
    data class PinWallpaper(val image: ImageId) : Action

    /** "Daily" was chosen: the newest image is used again, and daily changes resume (FR-TOP-03, FR-WP-08). */
    data object ResumeDaily : Action

    /** The shortcut to the system Settings app (FR-TOP-04). */
    data object OpenSystemSettings : Action

    /** "Refresh now" in Wallpaper details: check for a new image now (FR-WP-09). */
    data object RefreshWallpaper : Action

    /**
     * The wallpaper check numbered [number] ended with [result] at [at], on
     * [day] in the device's time zone. [run] is the background job's run it
     * belongs to, if any, which ends now (DESIGN §4.3).
     */
    data class WallpaperChecked(
        val number: Int,
        val result: CheckResult,
        val at: Instant,
        val day: LocalDate,
        val run: ScheduledRun? = null,
    ) : Action

    /**
     * Presentation is ready for the due wallpaper: no gesture is in progress,
     * and the outgoing image covers the screen for the crossfade (FR-ANIM-10,
     * DESIGN §6.6).
     */
    data object ApplyWallpaperNow : Action

    /** The system set [image] as the home screen wallpaper, as [id] (FR-WP-01). */
    data class WallpaperApplied(val image: ImageId, val id: WallpaperId) : Action

    /** The system couldn't set [image] as the wallpaper; the current one stays (FR-WP-11). */
    data class WallpaperApplyFailed(val image: ImageId) : Action

    /** "Daily Bing wallpaper" turned [on] or off (FR-SET-02, FR-WP-13). Settings (#18) sends it. */
    data class SetDailyWallpaper(val on: Boolean) : Action

    /** The Bing market, or Automatic for `null` (FR-SET-03, FR-WP-03). Settings (#18) sends it. */
    data class SetMarket(val market: Market?) : Action

    /** "Also use on lock screen" turned [on] or off (FR-SET-04, FR-WP-05). Settings (#18) sends it. */
    data class SetWallpaperLockScreen(val on: Boolean) : Action

    /** "Download on" (FR-SET-05, FR-WP-06). Settings (#18) sends it. */
    data class SetDownloadOn(val downloadOn: DownloadOn) : Action

    /**
     * The system reported its home screen wallpaper as [id], with [colors],
     * for the read numbered [number] (FR-AMB-03). A stale read is ignored.
     */
    data class SystemWallpaperRead(val number: Int, val id: WallpaperId?, val colors: List<Rgb>) : Action

    /** "Highlight color": Automatic or a preset (FR-SET-06, FR-AMB-05). Settings (#18) sends it. */
    data class SetHighlight(val setting: HighlightSetting) : Action
}
