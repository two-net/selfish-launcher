package io.github.twonet.selfish.domain.state

import io.github.twonet.selfish.domain.model.AppId
import io.github.twonet.selfish.domain.persist.LayoutDoc
import io.github.twonet.selfish.domain.persist.SettingsDoc
import io.github.twonet.selfish.domain.persist.WallpaperIndexDoc
import io.github.twonet.selfish.domain.persist.saveTo
import io.github.twonet.selfish.domain.ports.AppIconImage
import io.github.twonet.selfish.domain.ports.DefaultApps
import io.github.twonet.selfish.domain.ports.DocName
import io.github.twonet.selfish.domain.ports.ImageId
import io.github.twonet.selfish.domain.ports.NotificationSource
import io.github.twonet.selfish.domain.ports.Ports
import io.github.twonet.selfish.domain.wallpaper.CheckResult
import io.github.twonet.selfish.domain.wallpaper.WallpaperUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.Clock
import java.util.Locale

/**
 * The one store (AR-02). Holds the single [LauncherState], applies actions with
 * [reduce], and runs the resulting effects through [ports] in [scope].
 *
 * Ports report changes only by emitting on their flows, which the store turns
 * into actions; no adapter calls upward (AR-01).
 *
 * On [start] the store restores the saved layout, settings and wallpaper
 * cache index, then writes each document again whenever it changes
 * (FR-GEN-07, DESIGN §3.3).
 *
 * Call [dispatch] from [scope]'s thread. In the app that is the main thread
 * (DESIGN §3.3); reducers are small and pure.
 *
 * Besides [state] and [dispatch], the store answers two queries, [icon]
 * and [wallpaperImage]: they are pixels to draw, not launcher state
 * (DESIGN §3.3).
 *
 * [clock] tells the wallpaper checks the time and the day, and [locale] the
 * device's language and region for the Automatic market (FR-WP-02,
 * FR-WP-03); tests pass virtual ones.
 */
class LauncherStore(
    private val scope: CoroutineScope,
    private val ports: Ports,
    initial: LauncherState = LauncherState(),
    clock: Clock = Clock.systemDefaultZone(),
    locale: () -> Locale = { Locale.getDefault() },
) {
    private val mutableState = MutableStateFlow(initial)

    private val wallpapers = WallpaperUpdater(ports, clock, locale)

    /** The running wallpaper check, so turning Daily off can stop it before deleting the cache. */
    private var checkJob: Job? = null

    val state: StateFlow<LauncherState> = mutableState.asStateFlow()

    /**
     * Restores the saved state and starts listening to the ports. Call once.
     *
     * The port flows are subscribed right away, because the scheduler's runs
     * are lost if nobody is listening. Their events are held back until the
     * saved state is restored, so features always see it.
     *
     * The clock is the exception: it is followed only while the launcher is
     * on screen, so it never wakes the launcher in the background
     * (FR-HOME-02, NFR-PWR-02). So are notification access, checked afresh
     * each time the launcher comes on screen, for example back from the
     * system's access screen, and the notifications themselves, followed
     * only while Events may show them (FR-EVT-04, DESIGN §5.5). Hidden, the
     * launcher keeps the last list, so it never shows "No new events" for a
     * moment on return.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        scope.launch { restore() }
        ports.appCatalog.apps()
            .onEach { awaitRestored(); dispatch(Action.CatalogChanged(it)) }
            .launchIn(scope)
        ports.wallpaperScheduler.runs
            .onEach { awaitRestored(); dispatch(Action.ScheduledRunDue(it)) }
            .launchIn(scope)
        ports.osSettings.animationsOff
            .onEach { dispatch(Action.SystemAnimationsChanged(it)) }
            .launchIn(scope)
        state.map { it.homeShown }
            .distinctUntilChanged()
            .flatMapLatest { shown -> if (shown) ports.osSettings.clock else emptyFlow() }
            .onEach { dispatch(Action.ClockChanged(it)) }
            .launchIn(scope)
        state.map { it.homeShown }
            .distinctUntilChanged()
            .flatMapLatest { shown -> if (shown) ports.notificationSource.granted else emptyFlow() }
            .onEach { awaitRestored(); dispatch(Action.NotificationAccessChanged(it)) }
            .launchIn(scope)
        state.map { it.homeShown && it.notificationsOn }
            .distinctUntilChanged()
            .flatMapLatest { shown -> if (shown) ports.notificationSource.notifications else emptyFlow() }
            .onEach { dispatch(Action.NotificationsChanged(it)) }
            .launchIn(scope)
    }

    fun dispatch(action: Action) {
        val next = reduce(mutableState.value, action)
        mutableState.value = next.state
        next.effects.forEach { effect -> scope.launch { run(effect) } }
    }

    /**
     * [app]'s icon at [sizePx] square, or `null` when it can't be rendered,
     * in which case Presentation shows a fallback (UI-05). Main-safe.
     */
    suspend fun icon(app: AppId, sizePx: Int): AppIconImage? = try {
        ports.appCatalog.icon(app, sizePx)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /**
     * The bytes of the cached wallpaper image [image], or `null` if it isn't
     * cached or can't be read; Presentation decodes them for the Events
     * card's thumbnail (FR-EVT-03). Main-safe.
     */
    suspend fun wallpaperImage(image: ImageId): ByteArray? = try {
        ports.localStorage.readImage(image)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /**
     * The notification source while notifications are on, and the disabled
     * one otherwise, so nothing reaches notifications that aren't shown
     * (DESIGN §5.5).
     */
    private fun notificationSource(): NotificationSource =
        if (state.value.notificationsOn) ports.notificationSource else NotificationSource.Disabled

    private suspend fun awaitRestored() {
        if (!state.value.restored) state.first { it.restored }
    }

    private suspend fun restore() {
        val layoutText = read(DocName.Layout)
        val settingsText = read(DocName.Settings)
        val indexText = read(DocName.WallpaperIndex)
        val layout = LayoutDoc.decodeOrNull(layoutText.getOrNull())
        val settings = SettingsDoc.decode(settingsText.getOrNull())
        val savedIndex = WallpaperIndexDoc.decode(indexText.getOrNull())
        // Matching the index with the files deletes the ones it doesn't
        // name, so it waits for an index that was actually read.
        val index = if (indexText.isSuccess) wallpapers.reconcile(savedIndex) else savedIndex
        dispatch(Action.Restored(layout, settings, index))

        // A document that couldn't be read is never written in this process,
        // so a passing storage error can't replace the user's file with
        // defaults. Unreadable *content* is different: it is replaced on the
        // next change (NFR-REL-03).
        if (layoutText.isSuccess) {
            scope.launch {
                state.map { it.layout }.saveTo(ports.localStorage, DocName.Layout, LayoutDoc, alreadySaved = layout)
            }
        }
        if (settingsText.isSuccess) {
            scope.launch {
                state.map { it.settings }.saveTo(ports.localStorage, DocName.Settings, SettingsDoc, alreadySaved = settings)
            }
        }
        // Nothing is written while Daily Bing wallpaper is off, so the index
        // isn't written back after the cache is deleted (FR-WP-13).
        if (indexText.isSuccess) {
            scope.launch {
                state.map { if (it.settings.dailyWallpaper) it.wallpaperIndex else null }
                    .saveTo(ports.localStorage, DocName.WallpaperIndex, WallpaperIndexDoc, alreadySaved = savedIndex)
            }
        }
    }

    /**
     * Runs a wallpaper check and reports how it ended (DESIGN §4.3). A check
     * stopped by turning Daily off still reports, so its job run ends.
     */
    private suspend fun checkWallpaper(effect: Effect.CheckWallpaper) {
        checkJob = currentCoroutineContext()[Job]
        fun report(result: CheckResult) =
            dispatch(Action.WallpaperChecked(effect.number, result, wallpapers.now(), wallpapers.today(), effect.run))
        val result = try {
            wallpapers.check(effect.trigger, effect.market, effect.wifiOnly, effect.cached, effect.status)
        } catch (e: CancellationException) {
            report(CheckResult.Skipped)
            throw e
        } catch (_: Exception) {
            CheckResult.Checked(emptyList(), succeeded = false)
        }
        report(result)
    }

    /** Applies a wallpaper; the home screen's outcome is reported back (FR-WP-01, FR-WP-11). */
    private suspend fun applyWallpaper(effect: Effect.ApplyWallpaper) {
        val image = effect.entry.image
        val id = try {
            wallpapers.apply(effect.entry, effect.screens)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            if (effect.screens.home) dispatch(Action.WallpaperApplyFailed(image))
            return
        }
        if (effect.screens.home) dispatch(Action.WallpaperApplied(image, id))
    }

    /** The device's default apps, or none if they can't be looked up: a first run must still get its layout. */
    private suspend fun defaultApps(): DefaultApps = try {
        ports.appCatalog.defaultApps()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        DefaultApps()
    }

    /** The saved text of [doc], or a failure if storage couldn't be read. */
    private suspend fun read(doc: DocName): Result<String?> = try {
        Result.success(ports.localStorage.read(doc))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private suspend fun run(effect: Effect) {
        try {
            when (effect) {
                is Effect.LaunchApp -> ports.appCatalog.launch(effect.app, effect.from, effect.animate)
                is Effect.FinishRun -> effect.run.finished()
                is Effect.OpenAppInfo -> ports.appCatalog.openAppInfo(effect.app)
                is Effect.RequestUninstall -> ports.appCatalog.requestUninstall(effect.app)
                Effect.OfferDefaultHome ->
                    if (!ports.osSettings.isDefaultHome()) ports.osSettings.requestDefaultHome()
                Effect.ResolveDefaultApps -> dispatch(Action.DefaultAppsResolved(defaultApps()))
                is Effect.OpenScreen -> ports.appCatalog.open(effect.screen)
                Effect.RequestNotificationAccess -> ports.notificationSource.requestAccess()
                is Effect.OpenNotification -> notificationSource().open(effect.key, effect.from, effect.animate)
                is Effect.DismissNotifications -> notificationSource().dismiss(effect.refs)
                is Effect.DispatchAfter -> {
                    delay(effect.delay)
                    dispatch(effect.action)
                }
                is Effect.CheckWallpaper -> checkWallpaper(effect)
                is Effect.ApplyWallpaper -> applyWallpaper(effect)
                is Effect.DeleteWallpaperImages -> wallpapers.delete(effect.images)
                Effect.ClearWallpaperCache -> {
                    checkJob?.cancelAndJoin()
                    wallpapers.clear()
                }
                is Effect.ScheduleWallpaperJob -> ports.wallpaperScheduler.schedulePeriodic(effect.wifiOnly)
                is Effect.ScheduleWallpaperRetry -> ports.wallpaperScheduler.scheduleRetry(effect.delay, effect.wifiOnly)
                Effect.CancelWallpaperJob -> ports.wallpaperScheduler.cancel()
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
