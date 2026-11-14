package io.github.twonet.selfish.domain.state

import io.github.twonet.selfish.domain.layout.AddToHomeResult
import io.github.twonet.selfish.domain.layout.CatalogReconciler
import io.github.twonet.selfish.domain.layout.LayoutRules
import io.github.twonet.selfish.domain.model.GridItem
import io.github.twonet.selfish.domain.model.Layout
import io.github.twonet.selfish.domain.ports.SystemScreen
import io.github.twonet.selfish.domain.remorse.Destructive
import io.github.twonet.selfish.domain.remorse.REMORSE_DELAY
import io.github.twonet.selfish.domain.remorse.RemorseQueue
import io.github.twonet.selfish.domain.wallpaper.CheckTrigger
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
    val kept = keepRules(next.state)
    return if (kept === next.state) next else next.copy(state = kept)
}

/**
 * Rules that hold after every action, whichever action led there. Returns
 * [state] itself when they already hold.
 */
private fun keepRules(state: LauncherState): LauncherState {
    var next = state
    // Leaving the App Grid, however it happens, clears its search (FR-SRCH-05).
    if (next.layer != Layer.AppGrid && next.query.isNotEmpty()) next = next.copy(query = "")
    // Access that is gone, even if it went while the process was dead,
    // turns the feature off (FR-ONB-04, DESIGN §5.5). The setting only ever
    // turns on with access granted, so this never undoes "Turn on".
    if (next.notificationAccess == false && next.settings.notificationsInEvents) {
        next = next.copy(settings = next.settings.copy(notificationsInEvents = false))
    }
    // Notifications are kept only while they are shown (NFR-PRIV-05), and a
    // clear can't run without them.
    if (!next.notificationsOn) {
        if (next.notifications.isNotEmpty() || next.dismissing.isNotEmpty()) {
            next = next.copy(notifications = emptyList(), dismissing = emptySet())
        }
        if (next.remorse?.action is Destructive.ClearNotifications) next = next.copy(remorse = null)
    }
    // Wallpaper details has nothing to show without a wallpaper, for example
    // once Daily Bing wallpaper is turned off (FR-WP-13).
    if (next.layer == Layer.WallpaperDetails && next.wallpaper == null) next = next.copy(layer = Layer.Home)
    // Last, so it sees where every rule above left the layer (FR-ONB-02).
    return keepTutorial(next)
}

private fun transition(state: LauncherState, action: Action): Next = when (action) {
    is Action.Restored ->
        welcome(
            state.copy(layout = action.layout, settings = action.settings, restored = true, firstRun = action.layout == null),
        ).then { restoreWallpapers(it, action.wallpapers) }

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

    // A first run, or "Reset layout", which keeps the settings and the
    // wallpaper (FR-HOME-07, FR-SET-16). A folder the reset removed closes,
    // and a removal waiting for its timer is dropped with it, so it can't
    // take a new folder that reuses its ID.
    is Action.DefaultAppsResolved ->
        if (state.layout == null || state.resettingLayout) {
            val remorse = state.remorse.takeUnless { state.resettingLayout && it?.action is Destructive.RemoveFolder }
            Next(
                state.copy(resolvingDefaults = false, resettingLayout = false, remorse = remorse)
                    .withLayout(LayoutRules.firstRun(state.apps, action.defaults)),
            )
        } else {
            Next(state.copy(resolvingDefaults = false))
        }

    // One launch at a time: repeated taps while an app is starting are
    // ignored (FR-GEN-05). Launching closes any context menu, so it isn't
    // still open on return.
    // Under reduced motion the app opens without growing (FR-ANIM-15).
    is Action.Launch -> startLaunch(state) { Effect.LaunchApp(action.app, action.from, animate = !state.reducedMotion) }

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

    // Showing Home checks for a new wallpaper if the last check is over
    // 3 hours old (FR-WP-02); the check itself decides, knowing the time.
    // It also asks the system which wallpaper it shows, since the user may
    // have set one elsewhere meanwhile (FR-AMB-03).
    Action.HomeShown ->
        welcome(state.copy(homeShown = true))
            .then { startCheck(it, CheckTrigger.HomeShown) }
            .then(::readSystemWallpaper)

    // The launched app is now in front, so the launch is over (FR-GEN-05).
    // A wallpaper waiting for a gesture to end can be applied now.
    Action.HomeHidden -> applyIfHidden(state.copy(homeShown = false, launching = false))

    // The background job checks; its run ends with the check (FR-WP-02).
    is Action.ScheduledRunDue -> startCheck(state, CheckTrigger.Scheduled, action.run)

    is Action.ClockChanged -> Next(state.copy(clock = action.clock))

    is Action.SystemAnimationsChanged ->
        if (action.off == state.systemAnimationsOff) Next(state) else Next(state.copy(systemAnimationsOff = action.off))

    // In System mode the appearance follows it (FR-THM-03, DESIGN §4.4).
    is Action.SystemDarkChanged ->
        if (action.dark == state.systemDark) Next(state) else Next(state.copy(systemDark = action.dark))

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
            val started = startRemorse(state, Destructive.RemoveFolder(action.folder))
            started.copy(state = closeFolder(started.state))
        }
    }

    Action.CancelRemorse -> if (state.remorse == null) Next(state) else Next(state.copy(remorse = null))

    // A timer whose action was cancelled, or committed early, runs nothing.
    is Action.RemorseDue ->
        RemorseQueue.due(state.remorse, action.number)?.let { commit(state.copy(remorse = null), it) } ?: Next(state)

    // There is nothing to detail before the first image is downloaded.
    Action.OpenWallpaperDetails ->
        if (state.wallpaper == null || state.layer == Layer.WallpaperDetails) {
            Next(state)
        } else {
            Next(state.copy(layer = Layer.WallpaperDetails, overlays = emptyList()))
        }

    is Action.OpenLink -> Next(state, listOf(Effect.OpenScreen(SystemScreen.Link(action.url))))

    // Access granted after "Turn on" sent the user to ask for it turns the
    // feature on (FR-EVT-07, FR-ONB-04). Losing it turns the feature off,
    // in keepRules.
    is Action.NotificationAccessChanged ->
        if (action.granted && state.awaitingNotificationAccess) {
            Next(
                state.copy(
                    notificationAccess = true,
                    awaitingNotificationAccess = false,
                    settings = state.settings.copy(notificationsInEvents = true),
                ),
            )
        } else if (action.granted == state.notificationAccess) {
            Next(state)
        } else {
            Next(state.copy(notificationAccess = action.granted))
        }

    // The Events card explains the feature itself, so it asks at once
    // (FR-EVT-07, FR-ONB-04).
    Action.TurnOnNotifications -> turnOnNotifications(state)

    Action.HideNotificationsCard ->
        if (state.settings.notificationsCardDismissed) {
            Next(state)
        } else {
            Next(state.copy(settings = state.settings.copy(notificationsCardDismissed = true)))
        }

    // Notifications dismissed here stay hidden until the system reports them
    // gone; one updated since then is new, and shows (FR-EVT-05).
    is Action.NotificationsChanged -> {
        if (!state.notificationsOn) {
            Next(state)
        } else {
            val present = action.items.mapTo(HashSet()) { it.ref }
            val dismissing = state.dismissing.filterTo(HashSet()) { it in present }
            val shown = if (dismissing.isEmpty()) action.items else action.items.filter { it.ref !in dismissing }
            if (shown == state.notifications && dismissing == state.dismissing) {
                Next(state)
            } else {
                Next(state.copy(notifications = shown, dismissing = dismissing))
            }
        }
    }

    // Opening a notification is a launch, so a second tap waits (FR-GEN-05).
    is Action.OpenNotification ->
        if (state.notifications.none { it.key == action.key }) {
            Next(state)
        } else {
            startLaunch(state) { Effect.OpenNotification(action.key, action.from, animate = !state.reducedMotion) }
        }

    is Action.DismissNotification -> {
        val item = state.notifications.firstOrNull { it.key == action.key && it.clearable }
        if (item == null) {
            Next(state)
        } else {
            Next(
                state.copy(notifications = state.notifications - item, dismissing = state.dismissing + item.ref),
                listOf(Effect.DismissNotifications(setOf(item.ref))),
            )
        }
    }

    // What is cleared is what was shown when it was chosen (FR-EVT-06).
    Action.ClearNotifications -> {
        val refs = state.clearable.mapTo(LinkedHashSet()) { it.ref }
        if (!state.notificationsOn || refs.isEmpty()) Next(state) else startRemorse(state, Destructive.ClearNotifications(refs))
    }

    // Saved with the settings, and the theme follows it at once through
    // `appearance`; Light or Dark overrides the device (FR-TOP-02, FR-THM-06,
    // FR-THM-09).
    is Action.SetAppearance ->
        if (action.mode == state.settings.appearance) {
            Next(state)
        } else {
            Next(state.copy(settings = state.settings.copy(appearance = action.mode)))
        }

    // The chosen image is the one in use until "Daily" is chosen again; the
    // pin is saved in the cache index (FR-TOP-03, FR-WP-08). Choosing it
    // again tries an image the system refused before.
    is Action.PinWallpaper -> {
        val entry = state.wallpapers.firstOrNull { it.image == action.image }
        if (entry == null || !state.settings.dailyWallpaper || (state.pinned == entry.image && state.wallpaper == entry)) {
            Next(state)
        } else {
            applyIfHidden(state.copy(pinned = entry.image, wallpaper = entry, wallpaperWork = state.wallpaperWork.copy(applyFailed = null)))
        }
    }

    // Daily again: the newest cached image is the one in use (DESIGN §4.3).
    Action.ResumeDaily ->
        if (state.pinned == null) {
            Next(state)
        } else {
            applyIfHidden(
                state.copy(
                    pinned = null,
                    wallpaper = state.wallpapers.firstOrNull() ?: state.wallpaper,
                    wallpaperWork = state.wallpaperWork.copy(applyFailed = null),
                ),
            )
        }

    Action.OpenSystemSettings -> Next(state, listOf(Effect.OpenScreen(SystemScreen.SystemSettings)))

    Action.RefreshWallpaper -> startCheck(state, CheckTrigger.User, refresh = true)

    is Action.WallpaperChecked -> wallpaperChecked(state, action)

    // Presentation is ready: no gesture, and the outgoing image covers the
    // change (FR-ANIM-10).
    Action.ApplyWallpaperNow -> applyDue(state)

    is Action.WallpaperApplied -> wallpaperApplied(state, action)

    is Action.WallpaperApplyFailed -> wallpaperApplyFailed(state, action)

    is Action.SetDailyWallpaper -> setDailyWallpaper(state, action.on)

    is Action.SetMarket -> setMarket(state, action.market)

    is Action.SetWallpaperLockScreen -> setLockScreen(state, action.on)

    is Action.SetDownloadOn -> setDownloadOn(state, action.downloadOn)

    is Action.SystemWallpaperRead -> systemWallpaperRead(state, action)

    is Action.SetHighlight -> setHighlight(state, action.setting)

    Action.OpenSettings -> openSettings(state)

    Action.OpenAbout -> openAbout(state)

    is Action.SetShowClock -> setShowClock(state, action.show)

    is Action.SetGridColumns -> setGridColumns(state, action.columns)

    is Action.SetHaptics -> setHaptics(state, action.haptics)

    is Action.SetAnimations -> setAnimations(state, action.animations)

    Action.TurnOffNotifications -> turnOffNotifications(state)

    // Nothing changes until the timer runs out, so the banner can cancel it;
    // the same action already waiting is left to run (FR-SET-10, FR-SET-16,
    // FR-GEST-06). Before the first-run layout is built there is nothing to
    // sort or reset.
    Action.SortAppGrid -> startLayoutRemorse(state, Destructive.SortAppGrid)

    Action.ResetLayout -> startLayoutRemorse(state, Destructive.ResetLayout)

    is Action.TurnOnFeature -> turnOnFeature(state, action.feature)

    is Action.RequestAccess -> requestAccess(state, action.feature)

    Action.SkipTutorial -> skipTutorial(state)

    Action.ReplayTutorial -> replayTutorial(state)
}

/**
 * Starts a launch whose [effect] opens something, unless one is already in
 * progress: repeated taps while an app is starting are ignored (FR-GEN-05).
 * It closes any context menu, so it isn't still open on return.
 */
private inline fun startLaunch(state: LauncherState, effect: () -> Effect): Next =
    if (state.launching) {
        Next(state)
    } else {
        val launch = state.launches + 1
        Next(
            closeContextMenu(state).copy(launching = true, launches = launch),
            listOf(effect(), Effect.DispatchAfter(LAUNCH_TIMEOUT, Action.LaunchTimedOut(launch))),
        )
    }

/**
 * Starts the remorse timer of [action] (FR-GEST-06, DESIGN §4.6). A
 * destructive action already waiting runs at once, with its effects.
 */
private fun startRemorse(state: LauncherState, action: Destructive): Next {
    val number = state.remorses + 1
    val started = RemorseQueue.start(state.remorse, action, number)
    val committed = started.committed?.let { commit(state, it) } ?: Next(state)
    return Next(
        committed.state.copy(remorse = started.pending, remorses = number),
        committed.effects + Effect.DispatchAfter(REMORSE_DELAY, Action.RemorseDue(number)),
    )
}

/** Starts the remorse timer of a destructive action on the whole layout, unless it is already waiting. */
private fun startLayoutRemorse(state: LauncherState, action: Destructive): Next =
    if (state.layout == null || state.remorse?.action == action) Next(state) else startRemorse(state, action)

/** Runs the destructive [action] whose remorse timer is over (FR-GEST-06). */
private fun commit(state: LauncherState, action: Destructive): Next = when (action) {
    is Destructive.RemoveFolder -> Next(state.editLayout { LayoutRules.removeFolder(it, action.folder) })
    // They leave the list at once, and stay hidden until the system reports
    // them gone (FR-EVT-06).
    is Destructive.ClearNotifications ->
        Next(
            state.copy(
                notifications = state.notifications.filter { it.ref !in action.refs },
                dismissing = state.dismissing + action.refs,
            ),
            listOf(Effect.DismissNotifications(action.refs)),
        )
    // In the device locale's alphabetical order, as on a first run (FR-SET-10).
    Destructive.SortAppGrid -> Next(state.editLayout { LayoutRules.sortAlphabetically(it, state.apps, Collator.getInstance()) })
    Destructive.ResetLayout -> resetLayout(state)
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
 * On a first run, once per process, offers to become the default Home app
 * (FR-GEN-02, FR-ONB-01) and starts the tutorial (FR-ONB-02). It waits until
 * the state is restored and the launcher is on screen, whichever comes last:
 * the prompt needs a visible Activity, and the process may have started for
 * the background job instead.
 */
private fun welcome(state: LauncherState): Next =
    if (state.restored && state.homeShown && state.firstRun && !state.welcomed) {
        Next(startTutorial(state.copy(welcomed = true)), listOf(Effect.OfferDefaultHome))
    } else {
        Next(state)
    }
