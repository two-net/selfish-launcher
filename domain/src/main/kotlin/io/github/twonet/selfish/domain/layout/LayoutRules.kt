package io.github.twonet.selfish.domain.layout

import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.AppId
import io.github.twonet.selfish.domain.model.FolderId
import io.github.twonet.selfish.domain.model.GridItem
import io.github.twonet.selfish.domain.model.Layout
import io.github.twonet.selfish.domain.ports.DefaultApps

/**
 * Pure operations over a [Layout] (DESIGN §4.2). The editing operations of
 * Home, the App Grid and folders join these.
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
