package io.github.twonet.selfish.domain.state

import io.github.twonet.selfish.domain.layout.LayoutRules
import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.FolderId
import io.github.twonet.selfish.domain.model.GridItem
import io.github.twonet.selfish.domain.model.Layout
import io.github.twonet.selfish.domain.model.Settings
import io.github.twonet.selfish.domain.ports.ClockInfo
import io.github.twonet.selfish.domain.remorse.Destructive
import io.github.twonet.selfish.domain.remorse.PendingRemorse

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
    /** Whether this process has already offered the default Home role (FR-GEN-02). */
    val defaultHomeOffered: Boolean = false,
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
