package io.github.twonet.selfish.domain.state

import io.github.twonet.selfish.domain.model.AppId
import io.github.twonet.selfish.domain.model.Bounds
import io.github.twonet.selfish.domain.model.Market
import io.github.twonet.selfish.domain.model.WallpaperEntry
import io.github.twonet.selfish.domain.model.WallpaperStatus
import io.github.twonet.selfish.domain.ports.ImageId
import io.github.twonet.selfish.domain.ports.NotificationRef
import io.github.twonet.selfish.domain.ports.ScheduledRun
import io.github.twonet.selfish.domain.ports.SystemScreen
import io.github.twonet.selfish.domain.ports.WallpaperScreens
import io.github.twonet.selfish.domain.wallpaper.CheckTrigger
import kotlin.time.Duration

/** Work the reducer asks for, carried out through the ports by [LauncherStore]. */
sealed interface Effect {
    /**
     * Starts [app], growing its window from [from] when [animate], or with no
     * window animation under reduced motion (FR-ANIM-02, FR-ANIM-15).
     */
    data class LaunchApp(val app: AppId, val from: Bounds?, val animate: Boolean = true) : Effect

    data class FinishRun(val run: ScheduledRun) : Effect

    /** Opens the system's app-info screen for [app] (FR-GRID-06). */
    data class OpenAppInfo(val app: AppId) : Effect

    /** Opens the system's uninstall confirmation for [app] (FR-GRID-06). */
    data class RequestUninstall(val app: AppId) : Effect

    /** Shows the system's default-Home prompt, unless the launcher already is the default (FR-GEN-02). */
    data object OfferDefaultHome : Effect

    /**
     * Looks up the device's default apps and dispatches
     * [Action.DefaultAppsResolved], with none if the lookup fails, so a first
     * run always gets its layout (FR-HOME-07).
     */
    data object ResolveDefaultApps : Effect

    /**
     * Dispatches [action] after [delay], in the store's scope, so it fires
     * even after the user leaves the launcher. Tests run it in virtual time.
     */
    data class DispatchAfter(val delay: Duration, val action: Action) : Effect

    /** Opens a screen owned by the system or another app, such as a web page (FR-EVT-03). */
    data class OpenScreen(val screen: SystemScreen) : Effect

    /** Opens the system's notification access screen (FR-EVT-07, FR-ONB-04). */
    data object RequestNotificationAccess : Effect

    /** Opens what the notification [key] points to (FR-EVT-04), animated like [LaunchApp]. */
    data class OpenNotification(val key: String, val from: Bounds?, val animate: Boolean = true) : Effect

    /** Dismisses [refs], as they were when shown (FR-EVT-05, FR-EVT-06). */
    data class DismissNotifications(val refs: Set<NotificationRef>) : Effect

    /**
     * Checks for new wallpapers and stores them, then dispatches
     * [Action.WallpaperChecked] with [number] and [run] (FR-WP-02, DESIGN
     * §4.3). [cached] and [status] are the cache as it was when the check
     * started.
     */
    data class CheckWallpaper(
        val number: Int,
        val trigger: CheckTrigger,
        val market: Market?,
        val wifiOnly: Boolean,
        val cached: List<WallpaperEntry>,
        val status: WallpaperStatus,
        val run: ScheduledRun? = null,
    ) : Effect

    /**
     * Sets [entry] as the wallpaper of [screens] (FR-WP-01, FR-WP-05). For
     * the home screen, dispatches [Action.WallpaperApplied] or
     * [Action.WallpaperApplyFailed].
     */
    data class ApplyWallpaper(val entry: WallpaperEntry, val screens: WallpaperScreens) : Effect

    /** Deletes cached images that left the cache (FR-WP-07). */
    data class DeleteWallpaperImages(val images: Set<ImageId>) : Effect

    /** Stops any running check and deletes the whole wallpaper cache (FR-WP-13). */
    data object ClearWallpaperCache : Effect

    /** Schedules the periodic background check, on unmetered networks only when [wifiOnly] (FR-WP-02, FR-WP-06). */
    data class ScheduleWallpaperJob(val wifiOnly: Boolean) : Effect

    /** Schedules a retry after [delay] (FR-WP-11). */
    data class ScheduleWallpaperRetry(val delay: Duration, val wifiOnly: Boolean) : Effect

    /** Cancels every scheduled check (FR-WP-13). */
    data object CancelWallpaperJob : Effect
}
