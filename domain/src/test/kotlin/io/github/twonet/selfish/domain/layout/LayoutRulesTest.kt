package io.github.twonet.selfish.domain.layout

import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.AppId
import io.github.twonet.selfish.domain.model.FolderId
import io.github.twonet.selfish.domain.model.GridItem
import io.github.twonet.selfish.domain.model.Layout
import io.github.twonet.selfish.domain.ports.DefaultApps
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LayoutRulesTest {
    private fun app(name: String) = AppId("pkg.$name", "Main", 0)
    private fun item(name: String) = GridItem.App(app(name))
    private fun folder(id: Long, vararg names: String) = GridItem.Folder(FolderId(id), "F$id", names.map(::app))

    @Test
    @Verifies("NFR-REL-03")
    fun `a valid layout is unchanged`() {
        val layout = Layout(grid = listOf(item("a"), folder(1, "b", "c")), home = listOf(app("b"), app("a")))

        assertEquals(layout, LayoutRules.normalize(layout))
    }

    @Test
    @Verifies("NFR-REL-03")
    fun `each app is kept once, where it first appears`() {
        val layout = Layout(grid = listOf(item("a"), folder(1, "a", "b", "c"), item("b"), folder(2, "c", "d", "e")))

        assertEquals(
            Layout(grid = listOf(item("a"), folder(1, "b", "c"), folder(2, "d", "e"))),
            LayoutRules.normalize(layout),
        )
    }

    @Test
    @Verifies("FR-FOLD-06", "NFR-REL-03")
    fun `a folder is never empty and a one-app folder dissolves in place`() {
        val layout = Layout(grid = listOf(item("a"), folder(1), folder(2, "b"), folder(3, "a", "c"), item("d")))

        assertEquals(Layout(grid = listOf(item("a"), item("b"), item("c"), item("d"))), LayoutRules.normalize(layout))
    }

    @Test
    @Verifies("NFR-REL-03")
    fun `a repeated folder ID is replaced with a fresh one`() {
        val layout = Layout(grid = listOf(folder(4, "a", "b"), folder(4, "c", "d"), folder(2, "e", "f")))

        val ids = LayoutRules.normalize(layout).grid.map { (it as GridItem.Folder).id }

        assertEquals(listOf(FolderId(4), FolderId(5), FolderId(2)), ids)
    }

    @Test
    @Verifies("NFR-REL-03")
    fun `Home keeps each app once and at most nine`() {
        val home = listOf("a", "b", "a") + (1..10).map { "x$it" }

        val normalized = LayoutRules.normalize(Layout(home = home.map(::app)))

        assertEquals((listOf("a", "b") + (1..7).map { "x$it" }).map(::app), normalized.home)
    }

    @Test
    fun `a new folder ID is one more than the largest in use`() {
        assertEquals(FolderId(1), LayoutRules.nextFolderId(Layout()))
        assertEquals(FolderId(8), LayoutRules.nextFolderId(Layout(grid = listOf(folder(7, "a", "b"), folder(3, "c", "d")))))
    }

    // Home (FR-HOME-03, FR-HOME-05, FR-HOME-06, FR-HOME-07)

    private fun home(vararg names: String) = Layout(grid = names.map(::item), home = names.map(::app))

    private fun added(result: AddToHomeResult): Layout = (result as AddToHomeResult.Added).layout

    /** The invariants of DESIGN §4.2 that Home's operations must keep. */
    private fun assertHomeInvariants(layout: Layout) {
        assertEquals(layout.home.distinct(), layout.home, "No cover twice")
        assertTrue(layout.home.size <= LayoutRules.MAX_COVERS, "At most nine covers")
    }

    @Test
    @Verifies("FR-HOME-05")
    fun `adding puts the app last on Home`() {
        val layout = added(LayoutRules.addToHome(home("a", "b"), app("c")))

        assertEquals(listOf("a", "b", "c").map(::app), layout.home)
        assertHomeInvariants(layout)
    }

    @Test
    @Verifies("FR-HOME-05")
    fun `adding an app already on Home leaves it where it is`() {
        val layout = home("a", "b")

        assertEquals(AddToHomeResult.Added(layout), LayoutRules.addToHome(layout, app("a")))
    }

    @Test
    @Verifies("FR-HOME-05")
    fun `a tenth cover is refused because Home is full`() {
        val full = home(*(1..9).map { "x$it" }.toTypedArray())

        assertEquals(AddToHomeResult.HomeFull, LayoutRules.addToHome(full, app("y")))
        assertEquals(AddToHomeResult.Added(full), LayoutRules.addToHome(full, app("x3")))
    }

    @Test
    @Verifies("FR-HOME-05")
    fun `removing a cover keeps the others in order`() {
        val layout = LayoutRules.removeFromHome(home("a", "b", "c"), app("b"))

        assertEquals(listOf(app("a"), app("c")), layout.home)
        assertEquals(home("a", "b", "c").grid, layout.grid)
        assertEquals(layout, LayoutRules.removeFromHome(layout, app("b")))
    }

    @Test
    @Verifies("FR-HOME-06")
    fun `moving a cover keeps the order of the others`() {
        val layout = home("a", "b", "c", "d")

        assertEquals(listOf("c", "a", "b", "d").map(::app), LayoutRules.moveCover(layout, app("c"), 0).home)
        assertEquals(listOf("b", "c", "d", "a").map(::app), LayoutRules.moveCover(layout, app("a"), 3).home)
        assertEquals(listOf("a", "c", "b", "d").map(::app), LayoutRules.moveCover(layout, app("b"), 2).home)
        listOf(0, 1, 2, 3).forEach { assertHomeInvariants(LayoutRules.moveCover(layout, app("b"), it)) }
    }

    @Test
    @Verifies("FR-HOME-06")
    fun `a move past either end stops at that end, and a stranger changes nothing`() {
        val layout = home("a", "b", "c")

        assertEquals(listOf("b", "c", "a").map(::app), LayoutRules.moveCover(layout, app("a"), 99).home)
        assertEquals(listOf("c", "a", "b").map(::app), LayoutRules.moveCover(layout, app("c"), -1).home)
        assertEquals(layout, LayoutRules.moveCover(layout, app("z"), 0))
    }

    @Test
    @Verifies("FR-HOME-03")
    fun `one to four covers take two columns, five to nine take three`() {
        assertEquals(listOf(2, 2, 2, 2, 3, 3, 3, 3, 3), (1..9).map(LayoutRules::coverColumns))
    }

    // App Grid (FR-GRID-05, FR-GRID-07, FR-GRID-08)

    /** The invariants of DESIGN §4.2 that the App Grid's operations must keep. */
    private fun assertGridInvariants(layout: Layout) {
        val apps = layout.grid.flatMap {
            when (it) {
                is GridItem.App -> listOf(it.id)
                is GridItem.Folder -> it.apps
            }
        }
        assertEquals(apps.distinct(), apps, "No app twice")
        assertTrue(layout.grid.filterIsInstance<GridItem.Folder>().all { it.apps.size >= 2 }, "No empty or one-app folder")
        assertHomeInvariants(layout)
    }

    @Test
    @Verifies("FR-GRID-05", "FR-GRID-07")
    fun `moving a grid item keeps the order of the others`() {
        val layout = Layout(grid = listOf(item("a"), folder(1, "b", "c"), item("d"), item("e")))

        assertEquals(
            listOf(item("d"), item("a"), folder(1, "b", "c"), item("e")),
            LayoutRules.moveItem(layout, item("d"), 0).grid,
        )
        assertEquals(
            listOf(item("a"), item("d"), item("e"), folder(1, "b", "c")),
            LayoutRules.moveItem(layout, folder(1, "b", "c"), 3).grid,
        )
        listOf(0, 1, 2, 3).forEach { assertGridInvariants(LayoutRules.moveItem(layout, item("a"), it)) }
    }

    @Test
    @Verifies("FR-GRID-05")
    fun `a folder is moved by its ID, a move past either end stops there, and a stranger changes nothing`() {
        val layout = Layout(grid = listOf(item("a"), folder(1, "b", "c"), item("d")))

        // Renamed since the drag began: still the same folder.
        val renamed = GridItem.Folder(FolderId(1), "Renamed", listOf(app("b"), app("c")))
        assertEquals(listOf(folder(1, "b", "c"), item("a"), item("d")), LayoutRules.moveItem(layout, renamed, -5).grid)
        assertEquals(listOf(folder(1, "b", "c"), item("d"), item("a")), LayoutRules.moveItem(layout, item("a"), 99).grid)
        assertEquals(layout, LayoutRules.moveItem(layout, item("z"), 0))
        assertEquals(layout, LayoutRules.moveItem(layout, folder(9, "x", "y"), 0))
        assertEquals(layout, LayoutRules.moveItem(layout, item("d"), 2))
    }

    @Test
    @Verifies("FR-GRID-08", "FR-FOLD-01")
    fun `dropping an app onto another makes a folder of both in the target's place`() {
        val layout = Layout(grid = listOf(item("a"), folder(4, "x", "y"), item("b"), item("c")), home = listOf(app("a")))

        val dropped = LayoutRules.dropOnto(layout, app("a"), app("c"))

        val made = GridItem.Folder(FolderId(5), GridItem.Folder.DEFAULT_NAME, listOf(app("c"), app("a")))
        assertEquals(listOf(folder(4, "x", "y"), item("b"), made), dropped.grid)
        assertEquals(layout.home, dropped.home, "An app in a folder can still be a cover")
        assertEquals("Folder", made.name)
        assertGridInvariants(dropped)

        // Backwards: the dragged app comes after the target.
        val backwards = LayoutRules.dropOnto(layout, app("c"), app("b"))
        assertEquals(listOf(item("a"), folder(4, "x", "y"), made.copy(apps = listOf(app("b"), app("c")))), backwards.grid)
        assertGridInvariants(backwards)
    }

    @Test
    @Verifies("FR-GRID-08", "FR-FOLD-08")
    fun `only a loose app onto another loose app makes a folder`() {
        val layout = Layout(grid = listOf(item("a"), folder(1, "b", "c"), item("d")))

        assertEquals(layout, LayoutRules.dropOnto(layout, app("a"), app("a")))
        assertEquals(layout, LayoutRules.dropOnto(layout, app("a"), app("b")), "Into a folder is #8's")
        assertEquals(layout, LayoutRules.dropOnto(layout, app("b"), app("d")), "Out of a folder is #8's")
        assertEquals(layout, LayoutRules.dropOnto(layout, app("z"), app("a")))
        assertEquals(layout, LayoutRules.dropOnto(layout, app("a"), app("z")))
    }

    private fun entry(name: String) = AppEntry(app(name), name)

    @Test
    @Verifies("FR-HOME-07", "FR-ONB-01")
    fun `a first run puts every app in the grid and the default apps on Home`() {
        val apps = listOf("browser", "camera", "maps", "messages", "phone").map(::entry)
        val defaults = DefaultApps(phone = app("phone"), messaging = app("messages"), browser = app("browser"), camera = app("camera"))

        val layout = LayoutRules.firstRun(apps, defaults)

        assertEquals(apps.map { GridItem.App(it.id) }, layout.grid)
        assertEquals(listOf("phone", "messages", "browser", "camera").map(::app), layout.home)
        assertHomeInvariants(layout)
    }

    @Test
    @Verifies("FR-HOME-07")
    fun `a first run skips defaults that are missing or not launchable, and a shared default once`() {
        val apps = listOf("browser", "dialer").map(::entry)
        val defaults = DefaultApps(phone = app("dialer"), messaging = app("dialer"), browser = app("browser"), camera = app("gone"))

        val layout = LayoutRules.firstRun(apps, defaults)

        assertEquals(listOf(app("dialer"), app("browser")), layout.home)
        assertEquals(emptyList(), LayoutRules.firstRun(apps, DefaultApps()).home)
    }
}
