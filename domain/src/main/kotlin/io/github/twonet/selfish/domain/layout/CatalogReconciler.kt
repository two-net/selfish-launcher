package io.github.twonet.selfish.domain.layout

import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.AppId
import io.github.twonet.selfish.domain.model.GridItem
import io.github.twonet.selfish.domain.model.Layout

/**
 * Brings a [Layout] in line with the installed apps (FR-GEN-06, DESIGN §4.2).
 * Runs on every catalog change, so installs and removals show while the
 * launcher is running.
 */
object CatalogReconciler {
    /**
     * Returns [layout] with:
     * - apps that are no longer in [apps] removed from the grid, folders and
     *   Home, without leaving gaps (FR-GRID-09);
     * - a folder left with one app dissolved into that app in its place, and
     *   an empty folder dropped (FR-FOLD-06);
     * - apps not yet placed appended to the end of the grid, in the order of
     *   [apps] (FR-GRID-05).
     *
     * Everything else keeps its order. Reconciling twice changes nothing.
     */
    fun reconcile(layout: Layout, apps: List<AppEntry>): Layout {
        val launchable = apps.mapTo(HashSet()) { it.id }
        val placed = HashSet<AppId>()
        val grid = layout.grid.mapNotNull { item ->
            when (item) {
                is GridItem.App -> item.takeIf { it.id in launchable }
                is GridItem.Folder -> {
                    val kept = item.apps.filter { it in launchable }
                    when (kept.size) {
                        0 -> null
                        1 -> GridItem.App(kept.single())
                        else -> if (kept.size == item.apps.size) item else item.copy(apps = kept)
                    }
                }
            }
        }
        grid.forEach { item ->
            when (item) {
                is GridItem.App -> placed += item.id
                is GridItem.Folder -> placed += item.apps
            }
        }
        val added = apps.filter { placed.add(it.id) }.map { GridItem.App(it.id) }
        return layout.copy(grid = grid + added, home = layout.home.filter { it in launchable })
    }
}
