package io.github.twonet.selfish.domain.state

import io.github.twonet.selfish.domain.model.AppId
import io.github.twonet.selfish.domain.model.Bounds
import io.github.twonet.selfish.domain.ports.ScheduledRun

/** Work the reducer asks for, carried out through the ports by [LauncherStore]. */
sealed interface Effect {
    data class LaunchApp(val app: AppId, val from: Bounds?) : Effect

    data class FinishRun(val run: ScheduledRun) : Effect
}
