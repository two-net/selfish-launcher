package io.github.twonet.selfish.domain.layout

import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.AppId
import io.github.twonet.selfish.domain.model.FolderId
import io.github.twonet.selfish.domain.model.GridItem
import io.github.twonet.selfish.domain.model.Layout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Verifies("FR-GEN-06")
class CatalogReconcilerTest {
    private fun app(name: String) = AppId("pkg.$name", "Main", 0)
    private fun item(name: String) = GridItem.App(app(name))
    private fun folder(id: Long, vararg names: String) = GridItem.Folder(FolderId(id), "F$id", names.map(::app))
    private fun catalog(vararg names: String) = names.map { AppEntry(app(it), it) }

    private fun reconcile(layout: Layout, apps: List<AppEntry>) =
        CatalogReconciler.reconcile(layout, apps).also { assertInvariants(it, apps) }

    /** The invariants of DESIGN §4.2 that depend on the catalog. */
    private fun assertInvariants(layout: Layout, apps: List<AppEntry>) {
        val placed = layout.grid.flatMap { if (it is GridItem.Folder) it.apps else listOf((it as GridItem.App).id) }
        val launchable = apps.map { it.id }.toSet()
        assertEquals(placed.size, placed.toSet().size, "An app appears twice: $layout")
        assertEquals(launchable, placed.toSet(), "The grid must hold exactly the launchable apps")
        assertTrue(layout.grid.filterIsInstance<GridItem.Folder>().all { it.apps.size >= 2 }, "A folder with fewer than two apps")
        assertTrue(layout.home.all { it in launchable }, "A cover that isn't launchable")
        assertTrue(layout.home.size <= LayoutRules.MAX_COVERS)
    }

    @Test
    fun `a layout that matches the catalog is unchanged`() {
        val layout = Layout(grid = listOf(item("c"), folder(1, "a", "b")), home = listOf(app("b")))

        assertEquals(layout, reconcile(layout, catalog("a", "b", "c")))
    }

    @Test
    @Verifies("FR-GRID-09")
    fun `removed apps leave the grid, folders and Home without gaps`() {
        val layout = Layout(
            grid = listOf(item("a"), item("gone"), folder(1, "b", "gone2", "c"), item("d")),
            home = listOf(app("gone"), app("d"), app("gone2")),
        )

        val reconciled = reconcile(layout, catalog("a", "b", "c", "d"))

        assertEquals(
            Layout(grid = listOf(item("a"), folder(1, "b", "c"), item("d")), home = listOf(app("d"))),
            reconciled,
        )
    }

    @Test
    @Verifies("FR-FOLD-06")
    fun `a folder left with one app dissolves into it in place, and an empty one goes`() {
        val layout = Layout(grid = listOf(item("a"), folder(1, "b", "gone"), folder(2, "gone2", "gone3"), item("c")))

        assertEquals(
            Layout(grid = listOf(item("a"), item("b"), item("c"))),
            reconcile(layout, catalog("a", "b", "c")),
        )
    }

    @Test
    @Verifies("FR-GRID-05")
    fun `new apps are added at the end in catalog order`() {
        val layout = Layout(grid = listOf(item("m"), folder(1, "a", "z")))

        assertEquals(
            Layout(grid = listOf(item("m"), folder(1, "a", "z"), item("b"), item("y"))),
            reconcile(layout, catalog("a", "b", "m", "y", "z")),
        )
    }

    @Test
    fun `an app in a folder or on Home is not added again`() {
        val layout = Layout(grid = listOf(folder(1, "a", "b")), home = listOf(app("a")))

        assertEquals(layout, reconcile(layout, catalog("a", "b")))
    }

    @Test
    fun `an app listed twice by the catalog is added once`() {
        val apps = catalog("a") + catalog("a")

        assertEquals(Layout(grid = listOf(item("a"))), reconcile(Layout(), apps))
    }

    @Test
    fun `an empty catalog empties the layout`() {
        val layout = Layout(grid = listOf(item("a"), folder(1, "b", "c")), home = listOf(app("a")))

        assertEquals(Layout(), reconcile(layout, emptyList()))
    }

    @Test
    fun `reconciling twice changes nothing more`() {
        val layout = Layout(grid = listOf(item("gone"), folder(1, "a", "gone2"), item("b")), home = listOf(app("gone"), app("b")))
        val apps = catalog("a", "b", "new")

        val once = reconcile(layout, apps)

        assertEquals(once, reconcile(once, apps))
    }

    @Test
    fun `folder names and IDs are kept`() {
        val named = GridItem.Folder(FolderId(7), "Games", listOf(app("a"), app("b"), app("gone")))

        val reconciled = reconcile(Layout(grid = listOf(named)), catalog("a", "b"))

        assertEquals(listOf(GridItem.Folder(FolderId(7), "Games", listOf(app("a"), app("b")))), reconciled.grid)
    }
}
