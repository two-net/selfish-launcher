package io.github.twonet.selfish.domain.state

import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.AppId
import io.github.twonet.selfish.domain.model.Bounds
import io.github.twonet.selfish.domain.model.GridItem
import io.github.twonet.selfish.domain.model.Layout
import io.github.twonet.selfish.domain.model.Settings
import io.github.twonet.selfish.domain.ports.ClockInfo
import io.github.twonet.selfish.domain.ports.DefaultApps
import io.github.twonet.selfish.domain.ports.ScheduledRun

/** Everything that can change [LauncherState]: user actions from Presentation and events from ports. */
sealed interface Action {
    /**
     * The saved layout and settings were read at start (FR-GEN-07, FR-GEN-08).
     * [layout] is `null` when nothing usable was saved.
     */
    data class Restored(val layout: Layout?, val settings: Settings) : Action

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

    /** An App Grid item was dropped at position [to] (FR-GRID-05, FR-GRID-07). */
    data class MoveGridItem(val item: GridItem, val to: Int) : Action

    /** [app] was dropped onto [target], which makes a folder of the two (FR-GRID-08, FR-FOLD-01). */
    data class DropOnto(val app: AppId, val target: AppId) : Action

    /** "App info" (FR-GRID-06). */
    data class OpenAppInfo(val app: AppId) : Action

    /** "Uninstall", which opens the system's confirmation (FR-GRID-06). */
    data class Uninstall(val app: AppId) : Action
}
