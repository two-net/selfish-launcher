package io.github.twonet.selfish.domain.layout

import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.AppId
import io.github.twonet.selfish.domain.model.FolderId
import io.github.twonet.selfish.domain.model.GridItem
import io.github.twonet.selfish.domain.model.Layout
import io.github.twonet.selfish.domain.ports.DefaultApps

/**
 * Pure operations over a [Layout] (DESIGN §4.2). Folder editing (#8) joins
 * these.
 */
object LayoutRules {
    /** Home holds at most this many covers (FR-HOME-03, FR-HOME-05). */
    const val MAX_COVERS = 9

    /**
     * Restores the invariants that don't depend on which apps are installed,
     * for a layout read from storage (NFR-REL-03):
     * - each app appears at most once across the grid and its folders; the first wins;
     * - a folder holds at least two apps: an empty one is dropped, and a
     *   one-app folder dissolves into that app in its place (FR-FOLD-06);
     * - folder IDs are unique: a repeated one gets a fresh ID;
     * - Home lists each app at most once, and at most [MAX_COVERS] of them.
     *
     * Removing apps that are no longer installed is reconciliation with the
     * catalog (FR-GEN-06), not this.
     */
    fun normalize(layout: Layout): Layout {
        val placed = HashSet<AppId>()
        val folderIds = HashSet<FolderId>()
        var freshId = nextFolderId(layout).value
        val grid = layout.grid.mapNotNull { item ->
            when (item) {
                is GridItem.App -> item.takeIf { placed.add(it.id) }
                is GridItem.Folder -> {
                    val apps = item.apps.filter { placed.add(it) }
                    when (apps.size) {
                        0 -> null
                        1 -> GridItem.App(apps.single())
                        else -> {
                            val id = if (folderIds.add(item.id)) item.id else FolderId(freshId++).also { folderIds.add(it) }
                            item.copy(id = id, apps = apps)
                        }
                    }
                }
            }
        }
        return layout.copy(grid = grid, home = layout.home.distinct().take(MAX_COVERS))
    }

    /** The ID for a new folder: one more than the largest in use. */
    fun nextFolderId(layout: Layout): FolderId =
        FolderId((layout.grid.filterIsInstance<GridItem.Folder>().maxOfOrNull { it.id.value } ?: 0) + 1)

    /**
     * Adds [app] as the last cover, or returns [AddToHomeResult.HomeFull] when
     * Home already has [MAX_COVERS] (FR-HOME-05). An app already on Home stays
     * where it is.
     */
    fun addToHome(layout: Layout, app: AppId): AddToHomeResult = when {
        app in layout.home -> AddToHomeResult.Added(layout)
        layout.home.size >= MAX_COVERS -> AddToHomeResult.HomeFull
        else -> AddToHomeResult.Added(layout.copy(home = layout.home + app))
    }

    /** Removes [app]'s cover, if it has one; the others keep their order (FR-HOME-05). */
    fun removeFromHome(layout: Layout, app: AppId): Layout =
        if (app in layout.home) layout.copy(home = layout.home - app) else layout

    /**
     * Moves [app]'s cover to position [to], clamped to Home's bounds; the
     * other covers keep their order (FR-HOME-06). A cover not on Home
     * changes nothing.
     */
    fun moveCover(layout: Layout, app: AppId, to: Int): Layout {
        val from = layout.home.indexOf(app)
        if (from < 0) return layout
        val target = to.coerceIn(0, layout.home.lastIndex)
        if (target == from) return layout
        val home = layout.home.toMutableList().apply { removeAt(from); add(target, app) }
        return layout.copy(home = home)
    }

    /**
     * Moves the App Grid [item] to position [to], clamped to the grid's
     * bounds; everything else keeps its order (FR-GRID-05, FR-GRID-07). Apps
     * are matched by [AppId] and folders by [FolderId], so a folder renamed
     * meanwhile is still found. An item not in the grid changes nothing.
     */
    fun moveItem(layout: Layout, item: GridItem, to: Int): Layout {
        val from = layout.grid.indexOfFirst { it.sameAs(item) }
        if (from < 0) return layout
        val target = to.coerceIn(0, layout.grid.lastIndex)
        if (target == from) return layout
        val grid = layout.grid.toMutableList().apply { add(target, removeAt(from)) }
        return layout.copy(grid = grid)
    }

    /**
     * Drops [app] onto [target], making a folder named
     * [GridItem.Folder.DEFAULT_NAME] that holds both, in that order, in the
     * target's place (FR-GRID-08, FR-FOLD-01). Both must be apps on their own
     * in the grid, not in a folder, and different; otherwise nothing changes.
     * Home is left as it is: an app in a folder can still be a cover.
     */
    fun dropOnto(layout: Layout, app: AppId, target: AppId): Layout {
        if (app == target) return layout
        val loose = layout.grid.filterIsInstance<GridItem.App>().mapTo(HashSet()) { it.id }
        if (app !in loose || target !in loose) return layout
        val folder = GridItem.Folder(id = nextFolderId(layout), apps = listOf(target, app))
        val grid = layout.grid.mapNotNull { item ->
            when {
                item !is GridItem.App -> item
                item.id == target -> folder
                item.id == app -> null
                else -> item
            }
        }
        return layout.copy(grid = grid)
    }

    /** Whether [this] and [other] are the same grid item: the same app, or the same folder. */
    private fun GridItem.sameAs(other: GridItem): Boolean = when (this) {
        is GridItem.App -> other is GridItem.App && other.id == id
        is GridItem.Folder -> other is GridItem.Folder && other.id == id
    }

    /** Home's column count for [covers] covers: 2 for up to 4, 3 for 5 to 9 (FR-HOME-03). */
    fun coverColumns(covers: Int): Int = if (covers <= 4) 2 else 3

    /**
     * The layout of a first run (FR-HOME-07, FR-GRID-05, FR-ONB-01): every
     * app in the grid, in the order of [apps], which the store keeps in the
     * device locale's alphabetical order. Home gets the default phone,
     * messaging, browser and camera apps, in that order, each once and only
     * if it is in [apps].
     */
    fun firstRun(apps: List<AppEntry>, defaults: DefaultApps): Layout {
        val launchable = apps.mapTo(HashSet()) { it.id }
        val home = listOfNotNull(defaults.phone, defaults.messaging, defaults.browser, defaults.camera)
            .filter { it in launchable }
            .distinct()
        return Layout(grid = apps.map { GridItem.App(it.id) }.distinct(), home = home)
    }
}

/** The result of [LayoutRules.addToHome]. */
sealed interface AddToHomeResult {
    data class Added(val layout: Layout) : AddToHomeResult

    /** Home already has [LayoutRules.MAX_COVERS] covers (FR-HOME-05). */
    data object HomeFull : AddToHomeResult
}
