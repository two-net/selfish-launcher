package io.github.twonet.selfish.domain.state

import io.github.twonet.selfish.domain.events.NotificationGroup
import io.github.twonet.selfish.domain.events.NotificationGroups
import io.github.twonet.selfish.domain.layout.LayoutRules
import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.Animations
import io.github.twonet.selfish.domain.model.Appearance
import io.github.twonet.selfish.domain.model.FolderId
import io.github.twonet.selfish.domain.model.GridItem
import io.github.twonet.selfish.domain.model.Layout
import io.github.twonet.selfish.domain.model.Rgb
import io.github.twonet.selfish.domain.model.Settings
import io.github.twonet.selfish.domain.model.DownloadOn
import io.github.twonet.selfish.domain.model.WallpaperEntry
import io.github.twonet.selfish.domain.model.WallpaperIndex
import io.github.twonet.selfish.domain.model.WallpaperStatus
import io.github.twonet.selfish.domain.ports.ClockInfo
import io.github.twonet.selfish.domain.ports.ImageId
import io.github.twonet.selfish.domain.ports.NotificationItem
import io.github.twonet.selfish.domain.ports.NotificationRef
import io.github.twonet.selfish.domain.ports.WallpaperId
import io.github.twonet.selfish.domain.ports.WallpaperScreens
import io.github.twonet.selfish.domain.remorse.Destructive
import io.github.twonet.selfish.domain.remorse.PendingRemorse
import io.github.twonet.selfish.domain.theme.Highlight
import io.github.twonet.selfish.domain.theme.HighlightColor
import io.github.twonet.selfish.domain.theme.resolveAppearance
import io.github.twonet.selfish.domain.wallpaper.CheckTrigger

/**
 * The single source of truth (AR-02). Presentation renders this and nothing
 * else; feature issues add their fields here (DESIGN §3.3).
 */
data class LauncherState(
    /** Every launchable app, in the device locale's alphabetical order. */
    val apps: List<AppEntry> = emptyList(),
    /**
     * The saved arrangement. `null` until [restored], and after that when
     * nothing usable was saved, which is a first run: then the first-run
     * layout is built from the catalog (FR-HOME-07, FR-ONB-01).
     */
    val layout: Layout? = null,
    val settings: Settings = Settings(),
    /**
     * Whether the saved layout and settings have been read. Until then Home
     * shows only the wallpaper, so it never flashes empty (FR-GEN-08).
     */
    val restored: Boolean = false,
    /**
     * Whether nothing usable was saved when this process started. Unlike
     * `layout == null`, it stays true after the first-run layout is built.
     */
    val firstRun: Boolean = false,
    /**
     * The layer on screen. Not saved: after the process is killed, the
     * launcher opens on Home (FR-GEN-08, DESIGN §3.4).
     */
    val layer: Layer = Layer.Home,
    /** What is open over [layer], bottom to top (FR-GEN-04). */
    val overlays: List<Overlay> = emptyList(),
    /**
     * What is typed in the App Grid's search field (FR-SRCH-01). Empty
     * whenever the App Grid isn't open: closing it clears the query
     * (FR-SRCH-05). The keyboard is not state: it belongs to the field.
     */
    val query: String = "",
    /** Whether the launcher is on screen: between its `onStart` and `onStop`. */
    val homeShown: Boolean = false,
    /**
     * Whether this process has already welcomed a first run: offered the
     * default Home role (FR-GEN-02) and started the tutorial (FR-ONB-02).
     */
    val welcomed: Boolean = false,
    /**
     * The tutorial in progress (FR-ONB-02), or `null`. Not saved: after the
     * process is killed it doesn't resume, and Settings can replay it
     * (FR-ONB-03).
     */
    val tutorial: Tutorial? = null,
    /**
     * Whether an app is launching. Further launches are ignored until the
     * launcher is hidden or [LAUNCH_TIMEOUT] passes (FR-GEN-05).
     */
    val launching: Boolean = false,
    /** How many launches have started, so a stale timeout can be told apart. */
    val launches: Int = 0,
    /**
     * Whether the default apps are being looked up for the first-run layout,
     * so the lookup starts only once (FR-HOME-07).
     */
    val resolvingDefaults: Boolean = false,
    /**
     * Whether "Reset layout" ran and is waiting for the default apps, so the
     * first-run layout replaces the current one when they arrive (FR-SET-16).
     * The current layout stays until then, so nothing flashes empty. Not
     * saved: a process that dies first keeps the user's layout.
     */
    val resettingLayout: Boolean = false,
    /**
     * The time for Home's clock (FR-HOME-02). Updated only while the launcher
     * is shown, so the clock doesn't wake it in the background (NFR-PWR-02).
     */
    val clock: ClockInfo? = null,
    /**
     * The destructive action waiting for its remorse timer, if any
     * (FR-GEST-06, DESIGN §4.6). Not saved: a process that dies first never
     * runs it, which errs on the side of keeping the user's things.
     */
    val remorse: PendingRemorse? = null,
    /** How many remorse timers have started, so a stale one can be told apart. */
    val remorses: Int = 0,
    /**
     * The image of the day in use as the wallpaper, for the Events card and
     * Wallpaper details (FR-EVT-03, FR-WP-09, FR-WP-10): the [pinned] one,
     * or else the newest. `null` until the first image is downloaded, and
     * while Daily Bing wallpaper is off; then Events shows no card and the
     * system wallpaper is left as it is (FR-WP-12, FR-WP-13).
     */
    val wallpaper: WallpaperEntry? = null,
    /**
     * The cached images of the day, newest first, which the Top Menu offers
     * as ambiences (FR-TOP-03, FR-WP-07). Saved in the cache index.
     */
    val wallpapers: List<WallpaperEntry> = emptyList(),
    /**
     * The cached image chosen as an ambience, which stops daily changes
     * until "Daily" is chosen again (FR-TOP-03, FR-WP-08), or `null` for
     * Daily. Saved in the cache index.
     */
    val pinned: ImageId? = null,
    /**
     * When the wallpaper checks last ran, how many ran today and which image
     * was applied (DESIGN §4.3). Saved in the cache index.
     */
    val wallpaperStatus: WallpaperStatus = WallpaperStatus(),
    /** Wallpaper work in progress in this process. Not saved. */
    val wallpaperWork: WallpaperWork = WallpaperWork(),
    /**
     * What the system last reported about its home screen wallpaper, read
     * each time the launcher comes on screen: its ID, which tells one set
     * elsewhere apart, and its colors (FR-AMB-03). `null` until first read.
     * Not saved.
     */
    val systemWallpaper: SystemWallpaperReport? = null,
    /** How many reads of [systemWallpaper] were asked for, so a stale one can be told apart. */
    val systemWallpaperReads: Int = 0,
    /**
     * Whether the user has granted notification access (FR-EVT-07), or
     * `null` until it has been checked, which happens each time the launcher
     * comes on screen.
     */
    val notificationAccess: Boolean? = null,
    /**
     * Whether "Turn on" sent the user to the system's access screen, so the
     * setting turns on once access is granted (FR-EVT-07, FR-ONB-04). Not
     * saved.
     */
    val awaitingNotificationAccess: Boolean = false,
    /**
     * The current notifications, newest first, while they are shown in
     * Events (FR-EVT-04). In memory only, never saved (NFR-PRIV-05), and
     * empty while the feature is off.
     */
    val notifications: List<NotificationItem> = emptyList(),
    /**
     * Notifications dismissed in Events that the system hasn't reported
     * gone yet. They stay hidden meanwhile, so a dismissed one never comes
     * back for a moment (FR-EVT-05).
     */
    val dismissing: Set<NotificationRef> = emptySet(),
    /**
     * Whether the system's "remove animations" setting is on: its animator
     * duration scale is 0 (FR-ANIM-15). Followed from the OS, never saved.
     */
    val systemAnimationsOff: Boolean = false,
    /**
     * The device's dark-theme setting, including scheduled and Battery Saver
     * changes (FR-THM-03), or `null` if the device has none (FR-THM-04) or
     * hasn't reported it yet. Followed from the OS, never saved.
     */
    val systemDark: Boolean? = null,
)

/**
 * Home's covers, in the user's order (FR-HOME-03). A cover whose app the
 * catalog hasn't reported yet is left out until it does.
 */
val LauncherState.covers: List<AppEntry>
    get() {
        val home = layout?.home.orEmpty()
        if (home.isEmpty()) return emptyList()
        val byId = apps.associateBy { it.id }
        return home.mapNotNull { byId[it] }
    }

/**
 * The App Grid's items in the user's order (FR-GRID-02, FR-GRID-05), with
 * each app's catalog entry. Like [covers], an app the catalog hasn't
 * reported yet is left out, and so is a folder left with none.
 */
val LauncherState.gridCells: List<GridCell>
    get() {
        val grid = layout?.grid.orEmpty()
        if (grid.isEmpty()) return emptyList()
        val byId = apps.associateBy { it.id }
        return grid.mapNotNull { item ->
            when (item) {
                is GridItem.App -> byId[item.id]?.let(GridCell::App)
                is GridItem.Folder -> item.apps.mapNotNull { byId[it] }.takeIf { it.isNotEmpty() }?.let { GridCell.Folder(item, it) }
            }
        }
    }

/** One App Grid item as shown: an app, or a folder with its apps, each with its catalog entry. */
sealed interface GridCell {
    /** The layout item this cell shows. */
    val item: GridItem

    data class App(val entry: AppEntry) : GridCell {
        override val item: GridItem get() = GridItem.App(entry.id)
    }

    data class Folder(val folder: GridItem.Folder, val apps: List<AppEntry>) : GridCell {
        override val item: GridItem get() = folder
    }
}

/**
 * Whether the App Grid shows search results instead of its items: the query
 * has something besides spaces in it (FR-SRCH-01).
 */
val LauncherState.searching: Boolean
    get() = query.isNotBlank()

/** What the open context menu belongs to, if one is open (FR-HOME-06, FR-GRID-06). */
val LauncherState.contextMenu: MenuTarget?
    get() = overlays.filterIsInstance<Overlay.ContextMenu>().lastOrNull()?.target

/**
 * The folder open over the App Grid, with its apps (FR-FOLD-03), if one is
 * open and the catalog knows any of its apps.
 */
val LauncherState.openFolder: GridCell.Folder?
    get() {
        val id = overlays.filterIsInstance<Overlay.Folder>().lastOrNull()?.id ?: return null
        return gridCells.firstOrNull { it is GridCell.Folder && it.folder.id == id } as GridCell.Folder?
    }

/** The folder whose removal is waiting for its remorse timer, if any (FR-FOLD-07). */
val LauncherState.pendingRemoval: FolderId?
    get() = (remorse?.action as? Destructive.RemoveFolder)?.folder

/** Whether Home has no room for another cover, so "Add to Home" explains instead (FR-HOME-05). */
val LauncherState.homeFull: Boolean
    get() = (layout?.home?.size ?: 0) >= LayoutRules.MAX_COVERS

/**
 * Whether Events lists notifications: "Notifications in Events" is on and
 * access is granted (FR-EVT-04, FR-SET-12).
 */
val LauncherState.notificationsOn: Boolean
    get() = settings.notificationsInEvents && notificationAccess == true

/**
 * Whether Events shows the card that explains notifications and offers to
 * turn them on: they aren't shown, and the card wasn't dismissed (FR-EVT-07).
 */
val LauncherState.showsAccessCard: Boolean
    get() = !notificationsOn && !settings.notificationsCardDismissed

/**
 * The tutorial as it shows now, if it does (FR-ONB-02): on Home, the step to
 * do next, and on the view the step just opened, how to go back. It waits,
 * unseen, everywhere else: on other layers, on pages and under overlays.
 */
val LauncherState.tutorialShown: Tutorial?
    get() {
        val tutorial = tutorial ?: return null
        if (overlays.isNotEmpty()) return null
        val here = if (tutorial.reached) tutorial.step.target else Layer.Home
        return tutorial.takeIf { layer == here }
    }

/**
 * Whether the Top Menu shows its Ambiences row: Daily Bing wallpaper is on
 * and there are cached images to choose from (FR-TOP-03, FR-WP-13).
 */
val LauncherState.showsAmbiences: Boolean
    get() = settings.dailyWallpaper && wallpapers.isNotEmpty()

/**
 * Whether transitions are instant or fades of at most 100 ms: the system's
 * animations are removed, or the launcher's Animations setting is Reduced
 * (FR-ANIM-15, NFR-A11Y-05). Drags still follow the finger.
 */
val LauncherState.reducedMotion: Boolean
    get() = systemAnimationsOff || settings.animations == Animations.Reduced

/**
 * The appearance every surface is drawn in (FR-THM-01, FR-THM-07): the
 * chosen mode, with System following the device (DESIGN §4.4).
 */
val LauncherState.appearance: Appearance
    get() = resolveAppearance(settings.appearance, systemDark)

/** The notifications whose clearing is waiting for its remorse timer, if any (FR-EVT-06). */
val LauncherState.pendingClear: Set<NotificationRef>
    get() = (remorse?.action as? Destructive.ClearNotifications)?.refs.orEmpty()

/** The notifications Events lists, grouped by app (FR-EVT-04). */
val LauncherState.notificationGroups: List<NotificationGroup>
    get() = NotificationGroups.of(notifications, apps)

/**
 * The shown notifications that "Clear notifications" would dismiss now:
 * those the user may dismiss that aren't already waiting to be cleared
 * (FR-EVT-06).
 */
val LauncherState.clearable: List<NotificationItem>
    get() {
        val pending = pendingClear
        return notifications.filter { it.clearable && it.ref !in pending }
    }

/**
 * Wallpaper work in progress in this process (DESIGN §4.3). Not saved: a
 * process that dies mid-check simply checks again.
 */
data class WallpaperWork(
    /** The number of the check running now, if one is; a result with another number is stale. */
    val checking: Int? = null,
    /** What started the running check. */
    val trigger: CheckTrigger? = null,
    /** How many checks have started in this process, so a stale result can be told apart. */
    val checks: Int = 0,
    /**
     * Whether the user asked for "Refresh now" and it hasn't finished, so
     * Wallpaper details says it is checking (FR-WP-09). One asked for during
     * another check runs when that one ends.
     */
    val refreshing: Boolean = false,
    /** The image being set as the home screen wallpaper, if any. */
    val applying: ImageId? = null,
    /** The image the system last refused, so it isn't tried again until something changes. */
    val applyFailed: ImageId? = null,
)

/**
 * The cached image that should be the home screen wallpaper but isn't yet,
 * if any (FR-WP-01, FR-WP-08): the one in use, unless it is the one applied,
 * one is being applied, or the system refused it. While the launcher is on
 * screen, Presentation applies it once no gesture is in progress, with a
 * crossfade; while hidden, the reducer applies it at once (FR-ANIM-10).
 */
val LauncherState.wallpaperDue: WallpaperEntry?
    get() {
        val entry = wallpaper ?: return null
        val work = wallpaperWork
        return entry.takeIf {
            settings.dailyWallpaper &&
                work.applying == null &&
                entry.image != wallpaperStatus.applied?.image &&
                entry.image != work.applyFailed &&
                wallpapers.any { it.image == entry.image }
        }
    }

/** What the system reported about its home screen wallpaper (FR-AMB-03). */
data class SystemWallpaperReport(
    /** The system's ID for it, or `null` if it gave none. */
    val id: WallpaperId?,
    /** Its colors, most prominent first; none for the launcher's own image, which has its derived color. */
    val colors: List<Rgb>,
)

/**
 * The cached image the launcher set as the home screen wallpaper, while the
 * system still shows it (FR-AMB-01). `null` once the system reports another
 * ID, which means the wallpaper was set elsewhere (FR-AMB-03), and while
 * nothing the launcher applied is cached, as with Daily Bing wallpaper off.
 * Until the system's ID is known, the launcher's own is assumed.
 */
val LauncherState.launcherWallpaper: WallpaperEntry?
    get() {
        val applied = wallpaperStatus.applied ?: return null
        val shown = systemWallpaper?.id
        if (shown != null && shown != applied.id) return null
        return wallpapers.firstOrNull { it.image == applied.image }
    }

/**
 * The ambience's highlight color for each appearance (SRS §4.11, DESIGN
 * §4.4), or `null` for the appearance's default token:
 *
 * 1. a preset, if one is chosen (FR-AMB-05);
 * 2. else, while the launcher's image is the wallpaper, the color derived
 *    from it (FR-AMB-01), which follows the image *applied*, not the one
 *    due, so it moves as the new image crossfades in (FR-ANIM-10);
 * 3. else the colors the system reports for the wallpaper (FR-AMB-03).
 *
 * Every one is adjusted for contrast in both appearances (FR-AMB-02).
 */
val LauncherState.highlight: Highlight?
    get() {
        val source = settings.highlight.color
            ?: when (val own = launcherWallpaper) {
                null -> HighlightColor.fromSystem(systemWallpaper?.colors.orEmpty())
                else -> own.highlight
            }
        return source?.let(Highlight::of)
    }

/** The cache index as saved in `wallpapers.json` (SRS §8). */
val LauncherState.wallpaperIndex: WallpaperIndex
    get() = WallpaperIndex(entries = wallpapers, pinned = pinned, status = wallpaperStatus)

/** The screens a wallpaper is set on: the lock screen too with "Also use on lock screen" (FR-WP-05). */
val LauncherState.wallpaperScreens: WallpaperScreens
    get() = if (settings.lockScreen) WallpaperScreens.HomeAndLock else WallpaperScreens.Home

/** Whether wallpaper downloads are limited to unmetered networks (FR-WP-06). */
val LauncherState.wifiOnly: Boolean
    get() = settings.downloadOn == DownloadOn.WifiOnly
