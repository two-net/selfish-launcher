package io.github.twonet.selfish.domain.state

import io.github.twonet.selfish.domain.layout.AddToHomeResult
import io.github.twonet.selfish.domain.layout.CatalogReconciler
import io.github.twonet.selfish.domain.layout.LayoutRules
import io.github.twonet.selfish.domain.model.GridItem
import io.github.twonet.selfish.domain.model.Layout
import io.github.twonet.selfish.domain.remorse.Destructive
import io.github.twonet.selfish.domain.remorse.REMORSE_DELAY
import io.github.twonet.selfish.domain.remorse.RemorseQueue
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
fun reduce(state: LauncherState, action: Action): Next {
    val next = transition(state, action)
    // Leaving the App Grid, however it happens, clears its search (FR-SRCH-05).
    return if (next.state.layer != Layer.AppGrid && next.state.query.isNotEmpty()) {
        next.copy(state = next.state.copy(query = ""))
    } else {
        next
    }
}

private fun transition(state: LauncherState, action: Action): Next = when (action) {
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
        // A folder dissolved by the change closes too (FR-FOLD-06).
        val next = pruneOverlays(
            state.copy(
                apps = apps,
                layout = state.layout?.let { CatalogReconciler.reconcile(it, apps) },
                overlays = if (overlays.size == state.overlays.size) state.overlays else overlays,
            ),
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

    // Only the App Grid has a search field, so a late keystroke after it
    // closed changes nothing. The results replace the grid's items, so a
    // menu opened on one of them closes as the query changes (FR-SRCH-01).
    is Action.Search ->
        if (state.layer != Layer.AppGrid || action.query == state.query) {
            Next(state)
        } else {
            Next(closeContextMenu(state).copy(query = action.query))
        }

    is Action.MoveGridItem ->
        Next(closeContextMenu(state.editLayout { LayoutRules.moveItem(it, action.item, action.to) }))

    is Action.DropOnto ->
        Next(closeContextMenu(state.editLayout { LayoutRules.dropOnto(it, action.app, action.target) }))

    is Action.OpenAppInfo -> Next(closeContextMenu(state), listOf(Effect.OpenAppInfo(action.app)))

    is Action.Uninstall -> Next(closeContextMenu(state), listOf(Effect.RequestUninstall(action.app)))

    // Only in the App Grid, and not while the folder's removal is waiting
    // (FR-FOLD-03, FR-FOLD-07). One folder is open at a time.
    is Action.OpenFolder -> {
        val exists = state.layout?.let { LayoutRules.findFolder(it, action.folder) } != null
        val open = state.overlays.lastOrNull() == Overlay.Folder(action.folder)
        if (state.layer != Layer.AppGrid || !exists || open || state.pendingRemoval == action.folder) {
            Next(state)
        } else {
            val closed = closeFolder(closeContextMenu(state))
            Next(closed.copy(overlays = closed.overlays + Overlay.Folder(action.folder)))
        }
    }

    Action.CloseFolder -> Next(closeFolder(state))

    is Action.AddToFolder ->
        if (state.pendingRemoval == action.folder) {
            Next(state)
        } else {
            Next(closeContextMenu(state.editLayout { LayoutRules.addToFolder(it, action.app, action.folder) }))
        }

    // The panel closes as the app leaves it; the drag carries on in the grid.
    is Action.TakeOutOfFolder ->
        Next(closeFolder(state).editLayout { LayoutRules.removeFromFolder(it, action.app, action.to) })

    is Action.MoveInFolder ->
        Next(closeContextMenu(state.editLayout { LayoutRules.moveInFolder(it, action.app, action.to) }))

    is Action.RenameFolder -> Next(state.editLayout { LayoutRules.renameFolder(it, action.folder, action.name) })

    // The folder stays until the timer runs out, so the banner can cancel
    // it; a removal already waiting runs at once (FR-FOLD-07, DESIGN §4.6).
    is Action.RemoveFolder -> {
        val exists = state.layout?.let { LayoutRules.findFolder(it, action.folder) } != null
        if (!exists || state.pendingRemoval == action.folder) {
            Next(state)
        } else {
            val number = state.remorses + 1
            val started = RemorseQueue.start(state.remorse, Destructive.RemoveFolder(action.folder), number)
            val committed = started.committed?.let { commit(state, it) } ?: state
            Next(
                closeFolder(committed).copy(remorse = started.pending, remorses = number),
                listOf(Effect.DispatchAfter(REMORSE_DELAY, Action.RemorseDue(number))),
            )
        }
    }

    Action.CancelRemorse -> if (state.remorse == null) Next(state) else Next(state.copy(remorse = null))

    // A timer whose action was cancelled, or committed early, runs nothing.
    is Action.RemorseDue ->
        RemorseQueue.due(state.remorse, action.number)?.let { Next(commit(state.copy(remorse = null), it)) } ?: Next(state)
}

/** Runs the destructive [action] whose remorse timer is over (FR-GEST-06). */
private fun commit(state: LauncherState, action: Destructive): LauncherState = when (action) {
    is Destructive.RemoveFolder -> state.editLayout { LayoutRules.removeFolder(it, action.folder) }
}

/**
 * [state] with the topmost folder closed, along with anything open over it,
 * or [state] itself if no folder is open.
 */
private fun closeFolder(state: LauncherState): LauncherState {
    val at = state.overlays.indexOfLast { it is Overlay.Folder }
    return if (at < 0) state else state.copy(overlays = state.overlays.take(at))
}

/**
 * [state] without the overlay of a folder that no longer exists, for
 * example one left with a single app, which dissolved (FR-FOLD-06), and
 * without anything open over it.
 */
private fun pruneOverlays(state: LauncherState): LauncherState {
    if (state.overlays.none { it is Overlay.Folder }) return state
    val folders = state.layout?.grid.orEmpty().mapNotNullTo(HashSet()) { (it as? GridItem.Folder)?.id }
    val gone = state.overlays.indexOfFirst { it is Overlay.Folder && it.id !in folders }
    return if (gone < 0) state else state.copy(overlays = state.overlays.take(gone))
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

/**
 * [this] with [layout], keeping the same instance when nothing changed. A
 * folder that the change removed closes.
 */
private fun LauncherState.withLayout(layout: Layout): LauncherState =
    if (layout == this.layout) this else pruneOverlays(copy(layout = layout))

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
