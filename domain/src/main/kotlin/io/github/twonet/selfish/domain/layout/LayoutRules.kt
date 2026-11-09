package io.github.twonet.selfish.domain.layout

import io.github.twonet.selfish.domain.model.AppId
import io.github.twonet.selfish.domain.model.FolderId
import io.github.twonet.selfish.domain.model.GridItem
import io.github.twonet.selfish.domain.model.Layout

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
}
