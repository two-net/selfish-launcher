package io.github.twonet.selfish.domain.state

import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.AppId
import io.github.twonet.selfish.domain.model.Bounds
import io.github.twonet.selfish.domain.model.Layout
import io.github.twonet.selfish.domain.model.Settings
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
}
