package io.github.twonet.selfish.domain.state

import io.github.twonet.selfish.domain.model.AppliedWallpaper
import io.github.twonet.selfish.domain.model.DownloadOn
import io.github.twonet.selfish.domain.model.Market
import io.github.twonet.selfish.domain.model.WallpaperIndex
import io.github.twonet.selfish.domain.model.WallpaperStatus
import io.github.twonet.selfish.domain.ports.ScheduledRun
import io.github.twonet.selfish.domain.ports.WallpaperScreens
import io.github.twonet.selfish.domain.wallpaper.CheckResult
import io.github.twonet.selfish.domain.wallpaper.CheckTrigger
import io.github.twonet.selfish.domain.wallpaper.WallpaperPolicy

// The Daily Bing wallpaper transitions of the reducer (FR-WP-01 to FR-WP-14,
// DESIGN §4.3). The decisions are WallpaperPolicy's; these functions keep
// LauncherState in step with them and ask for the I/O as effects.

/** This result followed by [then]'s, run on its state: the last state, with both lists of effects. */
internal inline fun Next.then(then: (LauncherState) -> Next): Next {
    val next = then(state)
    return Next(next.state, effects + next.effects)
}

/**
 * The saved cache [index], at start (FR-WP-07, FR-WP-08). With Daily Bing
 * wallpaper on, the job is scheduled (the adapter keeps one already
 * scheduled as it is) and an image downloaded but never applied is applied.
 * With it off, perhaps turned off while the process was dead, nothing stays
 * scheduled or cached (FR-WP-13).
 */
internal fun restoreWallpapers(state: LauncherState, index: WallpaperIndex): Next {
    if (!state.settings.dailyWallpaper) {
        val effects = listOfNotNull(Effect.CancelWallpaperJob, Effect.ClearWallpaperCache.takeIf { index != WallpaperIndex() })
        return Next(withoutWallpapers(state), effects)
    }
    val pinned = index.pinned?.takeIf { pin -> index.entries.any { it.image == pin } }
    val restored = state.copy(
        wallpapers = index.entries,
        pinned = pinned,
        wallpaper = WallpaperPolicy.inUse(index.entries, pinned),
        wallpaperStatus = index.status,
    )
    return Next(restored, listOf(Effect.ScheduleWallpaperJob(restored.wifiOnly)))
        .then(::applyIfHidden)
        .then { if (it.homeShown) startCheck(it, CheckTrigger.HomeShown) else Next(it) }
}

/**
 * Starts a check (FR-WP-02), one at a time and only with Daily Bing
 * wallpaper on. Whether it is due is decided when it runs, where the time is
 * known. A [run] of the background job that starts no check ends at once.
 * A [refresh] asked for during another check runs when that one ends.
 */
internal fun startCheck(state: LauncherState, trigger: CheckTrigger, run: ScheduledRun? = null, refresh: Boolean = false): Next {
    val finish = listOfNotNull(run?.let(Effect::FinishRun))
    if (!state.restored || !state.settings.dailyWallpaper) return Next(state, finish)
    val work = state.wallpaperWork
    if (work.checking != null) {
        return if (refresh && !work.refreshing) Next(state.copy(wallpaperWork = work.copy(refreshing = true)), finish) else Next(state, finish)
    }
    val number = work.checks + 1
    return Next(
        state.copy(wallpaperWork = work.copy(checking = number, trigger = trigger, checks = number, refreshing = refresh)),
        listOf(
            Effect.CheckWallpaper(
                number = number,
                trigger = trigger,
                market = state.settings.market,
                wifiOnly = state.wifiOnly,
                cached = state.wallpapers,
                status = state.wallpaperStatus,
                run = run,
            ),
        ),
    )
}

/**
 * A check ended (FR-WP-02, FR-WP-11). New images join the cache, the oldest
 * leave it, and the newest is put in use unless one is pinned (FR-WP-07,
 * FR-WP-08). A failure keeps the current wallpaper and schedules a retry.
 */
internal fun wallpaperChecked(state: LauncherState, action: Action.WallpaperChecked): Next {
    val finish = listOfNotNull(action.run?.let(Effect::FinishRun))
    val work = state.wallpaperWork
    if (action.number != work.checking || !state.settings.dailyWallpaper) {
        // A check that outlived its cache, for example one running when Daily
        // was turned off: what it stored isn't part of the cache.
        val cached = state.wallpapers.mapTo(HashSet()) { it.image }
        val orphans = (action.result as? CheckResult.Checked)?.stored.orEmpty().mapTo(HashSet()) { it.image } - cached
        return Next(state, finish + listOfNotNull(orphans.takeIf { it.isNotEmpty() }?.let(Effect::DeleteWallpaperImages)))
    }
    val refreshNext = work.refreshing && work.trigger != CheckTrigger.User
    val ended = state.copy(wallpaperWork = work.copy(checking = null, trigger = null, refreshing = false))
    val checked = when (val result = action.result) {
        CheckResult.Skipped -> Next(ended, finish)
        is CheckResult.Checked -> Next(ended, finish).then { stored(it, result, action) }
    }
    return checked
        .then(::applyIfHidden)
        .then { if (refreshNext) startCheck(it, CheckTrigger.User, refresh = true) else Next(it) }
}

private fun stored(state: LauncherState, result: CheckResult.Checked, action: Action.WallpaperChecked): Next {
    val after = WallpaperPolicy.afterCheck(state.wallpaperStatus, result.succeeded, action.at, action.day)
    val merged = WallpaperPolicy.merge(state.wallpapers, result.stored)
    val inUse = WallpaperPolicy.inUse(merged, state.pinned)
    val protected = setOfNotNull(state.pinned, inUse?.image, state.wallpaperStatus.applied?.image, state.wallpaperWork.applying)
    val (kept, evicted) = WallpaperPolicy.evict(merged, protected)
    val effects = buildList {
        if (evicted.isNotEmpty()) add(Effect.DeleteWallpaperImages(evicted.mapTo(HashSet()) { it.image }))
        after.retry?.let { add(Effect.ScheduleWallpaperRetry(it, state.wifiOnly)) }
    }
    return Next(
        state.copy(wallpapers = kept, wallpaper = WallpaperPolicy.inUse(kept, state.pinned), wallpaperStatus = after.status),
        effects,
    )
}

/**
 * Applies the due wallpaper at once while the launcher is hidden, where no
 * gesture can be in progress. While it is shown, Presentation sends
 * [Action.ApplyWallpaperNow] once gestures are over (FR-ANIM-10).
 */
internal fun applyIfHidden(state: LauncherState): Next = if (state.homeShown) Next(state) else applyDue(state)

/** Applies the due wallpaper, if any (FR-WP-01, FR-WP-05). */
internal fun applyDue(state: LauncherState): Next {
    val due = state.wallpaperDue ?: return Next(state)
    return Next(
        state.copy(wallpaperWork = state.wallpaperWork.copy(applying = due.image)),
        listOf(Effect.ApplyWallpaper(due, state.wallpaperScreens)),
    )
}

/** The system set an image as the home screen wallpaper; it is recorded, and anything due next follows. */
internal fun wallpaperApplied(state: LauncherState, action: Action.WallpaperApplied): Next {
    val work = state.wallpaperWork
    val status = state.wallpaperStatus
    return applyIfHidden(
        state.copy(
            wallpaperStatus = if (state.settings.dailyWallpaper) status.copy(applied = AppliedWallpaper(action.image, action.id)) else status,
            wallpaperWork = work.copy(
                applying = work.applying.takeUnless { it == action.image },
                applyFailed = work.applyFailed.takeUnless { it == action.image },
            ),
        ),
    )
}

/** The system refused an image: the current wallpaper stays, and it isn't tried again until something changes (FR-WP-11). */
internal fun wallpaperApplyFailed(state: LauncherState, action: Action.WallpaperApplyFailed): Next {
    val work = state.wallpaperWork
    return Next(state.copy(wallpaperWork = work.copy(applying = work.applying.takeUnless { it == action.image }, applyFailed = action.image)))
}

/**
 * Turning Daily Bing wallpaper off stops every check and download, deletes
 * the cache and hides the Ambiences row, leaving the system wallpaper as it
 * is (FR-WP-13). Turning it on schedules the job and checks at once.
 */
internal fun setDailyWallpaper(state: LauncherState, on: Boolean): Next {
    if (on == state.settings.dailyWallpaper) return Next(state)
    if (!on) {
        val off = withoutWallpapers(state.copy(settings = state.settings.copy(dailyWallpaper = false)))
        return Next(off, listOf(Effect.CancelWallpaperJob, Effect.ClearWallpaperCache))
    }
    val next = state.copy(settings = state.settings.copy(dailyWallpaper = true))
    return Next(next, listOf(Effect.ScheduleWallpaperJob(next.wifiOnly))).then { startCheck(it, CheckTrigger.User) }
}

/** A new market's image is looked for at once (FR-WP-03). */
internal fun setMarket(state: LauncherState, market: Market?): Next {
    if (market == state.settings.market) return Next(state)
    val next = state.copy(settings = state.settings.copy(market = market))
    return startCheck(next, CheckTrigger.User, refresh = next.wallpaperWork.checking != null)
}

/** Turning "Also use on lock screen" on puts the current image on the lock screen too (FR-WP-05). */
internal fun setLockScreen(state: LauncherState, on: Boolean): Next {
    if (on == state.settings.lockScreen) return Next(state)
    val next = state.copy(settings = state.settings.copy(lockScreen = on))
    val applied = next.wallpaperStatus.applied?.image?.let { image -> next.wallpapers.firstOrNull { it.image == image } }
    return if (on && next.settings.dailyWallpaper && applied != null) {
        Next(next, listOf(Effect.ApplyWallpaper(applied, WallpaperScreens.Lock)))
    } else {
        Next(next)
    }
}

/** "Wi-Fi only" changes the job's network condition (FR-WP-06). */
internal fun setDownloadOn(state: LauncherState, downloadOn: DownloadOn): Next {
    if (downloadOn == state.settings.downloadOn) return Next(state)
    val next = state.copy(settings = state.settings.copy(downloadOn = downloadOn))
    return if (next.settings.dailyWallpaper) Next(next, listOf(Effect.ScheduleWallpaperJob(next.wifiOnly))) else Next(next)
}

/** [state] with no cached images and nothing in use, applied or in progress (FR-WP-13). */
private fun withoutWallpapers(state: LauncherState): LauncherState =
    state.copy(
        wallpapers = emptyList(),
        pinned = null,
        wallpaper = null,
        wallpaperStatus = WallpaperStatus(),
        wallpaperWork = WallpaperWork(checks = state.wallpaperWork.checks),
    )
