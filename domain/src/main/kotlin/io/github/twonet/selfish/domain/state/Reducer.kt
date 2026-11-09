package io.github.twonet.selfish.domain.state

import io.github.twonet.selfish.domain.layout.AddToHomeResult
import io.github.twonet.selfish.domain.layout.CatalogReconciler
import io.github.twonet.selfish.domain.layout.LayoutRules
import io.github.twonet.selfish.domain.model.Layout
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
        val launchable = apps.mapTo(HashSet()) { it.id }
        // A menu whose app was just uninstalled has nothing left to act on.
        val overlays = state.overlays.filter { it !is Overlay.ContextMenu || it.target.app in launchable }
        val next = state.copy(
            apps = apps,
            layout = state.layout?.let { CatalogReconciler.reconcile(it, apps) },
            overlays = if (overlays.size == state.overlays.size) state.overlays else overlays,
        )
        // Without a saved layout this is a first run: its layout needs the
        // default apps, looked up once the catalog has something in it
        // (FR-HOME-07, FR-ONB-01).
        if (next.restored && next.layout == null && apps.isNotEmpty() && !next.resolvingDefaults) {
            Next(next.copy(resolvingDefaults = true), listOf(Effect.ResolveDefaultApps))
        } else {
            Next(next)
        }
    }

    is Action.DefaultAppsResolved ->
        if (state.layout == null) {
            Next(state.copy(layout = LayoutRules.firstRun(state.apps, action.defaults), resolvingDefaults = false))
        } else {
            Next(state.copy(resolvingDefaults = false))
        }

    // One launch at a time: repeated taps while an app is starting are
    // ignored (FR-GEN-05). Launching closes any context menu, so it isn't
    // still open on return.
    is Action.Launch ->
        if (state.launching) {
            Next(state)
        } else {
            val launch = state.launches + 1
            Next(
                closeContextMenu(state).copy(launching = true, launches = launch),
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

    is Action.ClockChanged -> Next(state.copy(clock = action.clock))

    is Action.OpenContextMenu ->
        Next(state.copy(overlays = state.overlays.filterNot { it is Overlay.ContextMenu } + Overlay.ContextMenu(action.target)))

    Action.CloseContextMenu -> Next(closeContextMenu(state))

    // Choosing a menu item closes the menu. When Home is full, "Add to Home"
    // explains that instead of adding, so the menu stays open (FR-HOME-05).
    is Action.AddToHome -> {
        val layout = state.layout
        when (val result = layout?.let { LayoutRules.addToHome(it, action.app) }) {
            null, AddToHomeResult.HomeFull -> Next(state)
            is AddToHomeResult.Added -> Next(closeContextMenu(state.withLayout(result.layout)))
        }
    }

    is Action.RemoveFromHome ->
        Next(closeContextMenu(state.editLayout { LayoutRules.removeFromHome(it, action.app) }))

    is Action.MoveCover ->
        Next(closeContextMenu(state.editLayout { LayoutRules.moveCover(it, action.app, action.to) }))

    // A menu belongs to the layer it was opened on, so moving to another
    // layer closes it.
    is Action.LayerSettled ->
        if (action.layer == state.layer) Next(state) else Next(state.copy(layer = action.layer, overlays = emptyList()))

    is Action.MoveGridItem ->
        Next(closeContextMenu(state.editLayout { LayoutRules.moveItem(it, action.item, action.to) }))

    is Action.DropOnto ->
        Next(closeContextMenu(state.editLayout { LayoutRules.dropOnto(it, action.app, action.target) }))

    is Action.OpenAppInfo -> Next(closeContextMenu(state), listOf(Effect.OpenAppInfo(action.app)))

    is Action.Uninstall -> Next(closeContextMenu(state), listOf(Effect.RequestUninstall(action.app)))
}

/** [this] with every context menu closed, or [this] itself if none is open. */
private fun closeContextMenu(state: LauncherState): LauncherState =
    if (state.overlays.none { it is Overlay.ContextMenu }) {
        state
    } else {
        state.copy(overlays = state.overlays.filterNot { it is Overlay.ContextMenu })
    }

/** Applies [edit] to the layout; without a layout (before the first run is built) nothing changes. */
private inline fun LauncherState.editLayout(edit: (Layout) -> Layout): LauncherState =
    layout?.let { withLayout(edit(it)) } ?: this

/** [this] with [layout], keeping the same instance when nothing changed. */
private fun LauncherState.withLayout(layout: Layout): LauncherState =
    if (layout == this.layout) this else copy(layout = layout)

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
