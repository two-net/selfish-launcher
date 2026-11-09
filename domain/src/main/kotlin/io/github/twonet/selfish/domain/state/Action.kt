package io.github.twonet.selfish.domain.state

import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.AppId
import io.github.twonet.selfish.domain.model.Bounds
import io.github.twonet.selfish.domain.ports.ScheduledRun

/** Everything that can change [LauncherState]: user actions from Presentation and events from ports. */
sealed interface Action {
    /** The app catalog reported its current list. */
    data class CatalogChanged(val apps: List<AppEntry>) : Action

    /** The user tapped an app; [from] is the tapped item's bounds on screen. */
    data class Launch(val app: AppId, val from: Bounds?) : Action

    /** The system ran the background job (AR-06). */
    data class ScheduledRunDue(val run: ScheduledRun) : Action
}
