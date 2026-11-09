package io.github.twonet.selfish.domain.state

import io.github.twonet.selfish.domain.layout.CatalogReconciler
import java.text.Collator
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** The result of [reduce]: the new state and the effects to run. */
data class Next(
    val state: LauncherState,
    val effects: List<Effect> = emptyList(),
)

/**
 * How long a launch blocks further launches if the launcher isn't hidden
 * first (FR-GEN-05, DESIGN §4.1).
 */
val LAUNCH_TIMEOUT: Duration = 1.seconds

/**
 * The launcher's only state transition: a pure function from the current
 * state and an action to the next state and its effects (AR-02, DESIGN §3.3).
 */
fun reduce(state: LauncherState, action: Action): Next = when (action) {
    is Action.Restored ->
        offerDefaultHome(
            state.copy(layout = action.layout, settings = action.settings, restored = true, firstRun = action.layout == null),
        )

    is Action.CatalogChanged -> {
        val collator = Collator.getInstance()
        val apps = action.apps.sortedWith(compareBy(collator) { it.label })
        Next(state.copy(apps = apps, layout = state.layout?.let { CatalogReconciler.reconcile(it, apps) }))
    }

    // One launch at a time: repeated taps while an app is starting are
    // ignored (FR-GEN-05).
    is Action.Launch ->
        if (state.launching) {
            Next(state)
        } else {
            val launch = state.launches + 1
            Next(
                state.copy(launching = true, launches = launch),
                listOf(
                    Effect.LaunchApp(action.app, action.from),
                    Effect.DispatchAfter(LAUNCH_TIMEOUT, Action.LaunchTimedOut(launch)),
                ),
            )
        }

    // A timer from an earlier launch must not end a newer one early.
    is Action.LaunchTimedOut ->
        if (state.launching && action.launch == state.launches) Next(state.copy(launching = false)) else Next(state)

    // The topmost overlay first, then the layer; on bare Home, nothing (FR-GEN-04).
    Action.Back -> when {
        state.overlays.isNotEmpty() -> Next(state.copy(overlays = state.overlays.dropLast(1)))
        state.layer != Layer.Home -> Next(state.copy(layer = Layer.Home))
        else -> Next(state)
    }

    // Everything closes at once; on bare Home, nothing (FR-GEN-03).
    Action.HomePressed ->
        if (state.layer == Layer.Home && state.overlays.isEmpty()) {
            Next(state)
        } else {
            Next(state.copy(layer = Layer.Home, overlays = emptyList()))
        }

    Action.HomeShown -> offerDefaultHome(state.copy(homeShown = true))

    // The launched app is now in front, so the launch is over (FR-GEN-05).
    Action.HomeHidden -> Next(state.copy(homeShown = false, launching = false))

    // #14 adds the wallpaper check here; until then the run ends at once.
    is Action.ScheduledRunDue ->
        Next(state, listOf(Effect.FinishRun(action.run)))
}

/**
 * On a first run, offers once per process to become the default Home app
 * (FR-GEN-02). It waits until the launcher is on screen, because the prompt
 * needs a visible Activity and the process may have started for the
 * background job instead.
 */
private fun offerDefaultHome(state: LauncherState): Next =
    if (state.restored && state.homeShown && state.firstRun && !state.defaultHomeOffered) {
        Next(state.copy(defaultHomeOffered = true), listOf(Effect.OfferDefaultHome))
    } else {
        Next(state)
    }
