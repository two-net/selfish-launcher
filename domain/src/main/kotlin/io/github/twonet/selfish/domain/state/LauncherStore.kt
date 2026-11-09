package io.github.twonet.selfish.domain.state

import io.github.twonet.selfish.domain.ports.Ports
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * The one store (AR-02). Holds the single [LauncherState], applies actions with
 * [reduce], and runs the resulting effects through [ports] in [scope].
 *
 * Ports report changes only by emitting on their flows, which the store turns
 * into actions; no adapter calls upward (AR-01).
 *
 * Call [dispatch] from [scope]'s thread. In the app that is the main thread
 * (DESIGN §3.3); reducers are small and pure.
 */
class LauncherStore(
    private val scope: CoroutineScope,
    private val ports: Ports,
    initial: LauncherState = LauncherState(),
) {
    private val mutableState = MutableStateFlow(initial)

    val state: StateFlow<LauncherState> = mutableState.asStateFlow()

    /** Starts listening to the ports. Call once. */
    fun start() {
        ports.appCatalog.apps()
            .onEach { dispatch(Action.CatalogChanged(it)) }
            .launchIn(scope)
        ports.wallpaperScheduler.runs
            .onEach { dispatch(Action.ScheduledRunDue(it)) }
            .launchIn(scope)
    }

    fun dispatch(action: Action) {
        val next = reduce(mutableState.value, action)
        mutableState.value = next.state
        next.effects.forEach { effect -> scope.launch { run(effect) } }
    }

    private suspend fun run(effect: Effect) {
        try {
            when (effect) {
                is Effect.LaunchApp -> ports.appCatalog.launch(effect.app, effect.from)
                is Effect.FinishRun -> effect.run.finished()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // A failed effect, such as launching an app removed a moment ago,
            // must not take the launcher down. Feature issues turn failures
            // they care about into actions.
        }
    }
}
