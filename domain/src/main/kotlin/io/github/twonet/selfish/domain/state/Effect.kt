package io.github.twonet.selfish.domain.state

import io.github.twonet.selfish.domain.model.AppId
import io.github.twonet.selfish.domain.model.Bounds
import io.github.twonet.selfish.domain.ports.ScheduledRun
import kotlin.time.Duration

/** Work the reducer asks for, carried out through the ports by [LauncherStore]. */
sealed interface Effect {
    data class LaunchApp(val app: AppId, val from: Bounds?) : Effect

    data class FinishRun(val run: ScheduledRun) : Effect

    /** Shows the system's default-Home prompt, unless the launcher already is the default (FR-GEN-02). */
    data object OfferDefaultHome : Effect

    /**
     * Dispatches [action] after [delay], in the store's scope, so it fires
     * even after the user leaves the launcher. Tests run it in virtual time.
     */
    data class DispatchAfter(val delay: Duration, val action: Action) : Effect
}
