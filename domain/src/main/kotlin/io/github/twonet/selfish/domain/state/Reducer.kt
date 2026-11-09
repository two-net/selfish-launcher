package io.github.twonet.selfish.domain.state

import java.text.Collator

/** The result of [reduce]: the new state and the effects to run. */
data class Next(
    val state: LauncherState,
    val effects: List<Effect> = emptyList(),
)

/**
 * The launcher's only state transition: a pure function from the current
 * state and an action to the next state and its effects (AR-02, DESIGN §3.3).
 */
fun reduce(state: LauncherState, action: Action): Next = when (action) {
    is Action.Restored ->
        Next(state.copy(layout = action.layout, settings = action.settings, restored = true))

    is Action.CatalogChanged -> {
        val collator = Collator.getInstance()
        Next(state.copy(apps = action.apps.sortedWith(compareBy(collator) { it.label })))
    }

    is Action.Launch ->
        Next(state, listOf(Effect.LaunchApp(action.app, action.from)))

    // #14 adds the wallpaper check here; until then the run ends at once.
    is Action.ScheduledRunDue ->
        Next(state, listOf(Effect.FinishRun(action.run)))
}
