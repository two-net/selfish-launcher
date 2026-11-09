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
        assertEquals(layout, LayoutRules.dropOnto(layout, app("a"), app("b")), "Into a folder is addToFolder")
        assertEquals(layout, LayoutRules.dropOnto(layout, app("b"), app("d")), "Out of a folder is removeFromFolder")
        assertEquals(layout, LayoutRules.dropOnto(layout, app("z"), app("a")))
        assertEquals(layout, LayoutRules.dropOnto(layout, app("a"), app("z")))
    }

    // Folders (FR-FOLD-04 to FR-FOLD-08, AC-08)

    @Test
    @Verifies("FR-FOLD-05", "FR-FOLD-08")
    fun `an app dropped onto a folder joins it last and leaves no gap`() {
        val layout = Layout(grid = listOf(item("a"), folder(1, "b", "c"), item("d")), home = listOf(app("a")))

        val added = LayoutRules.addToFolder(layout, app("d"), FolderId(1))
        assertEquals(listOf(item("a"), folder(1, "b", "c", "d")), added.grid)
        assertEquals(layout.home, added.home)
        assertGridInvariants(added)

        val first = LayoutRules.addToFolder(layout, app("a"), FolderId(1))
        assertEquals(listOf(folder(1, "b", "c", "a"), item("d")), first.grid)
        assertGridInvariants(first)
    }

    @Test
    @Verifies("FR-FOLD-05", "FR-FOLD-08")
    fun `only a loose app goes into a folder that exists`() {
        val layout = Layout(grid = listOf(item("a"), folder(1, "b", "c"), folder(2, "d", "e")))

        assertEquals(layout, LayoutRules.addToFolder(layout, app("d"), FolderId(1)), "Already in another folder")
        assertEquals(layout, LayoutRules.addToFolder(layout, app("b"), FolderId(1)), "Already in this one")
        assertEquals(layout, LayoutRules.addToFolder(layout, app("a"), FolderId(9)), "No such folder")
        assertEquals(layout, LayoutRules.addToFolder(layout, app("z"), FolderId(1)), "Not in the grid")
    }

    @Test
    @Verifies("FR-FOLD-05")
    fun `an app taken out of a folder goes where it is dropped`() {
        val layout = Layout(grid = listOf(item("a"), folder(1, "b", "c", "d"), item("e")))

        val out = LayoutRules.removeFromFolder(layout, app("c"), 3)
        assertEquals(listOf(item("a"), folder(1, "b", "d"), item("e"), item("c")), out.grid)
        assertGridInvariants(out)

        assertEquals(listOf(item("b"), item("a"), folder(1, "c", "d"), item("e")), LayoutRules.removeFromFolder(layout, app("b"), -4).grid)
        assertEquals(listOf(item("a"), folder(1, "b", "c"), item("e"), item("d")), LayoutRules.removeFromFolder(layout, app("d"), 99).grid)
        assertEquals(layout, LayoutRules.removeFromFolder(layout, app("a"), 0), "Not in a folder")
        assertEquals(layout, LayoutRules.removeFromFolder(layout, app("z"), 0), "Not in the grid")
    }

    @Test
    @Verifies("AC-08", "FR-FOLD-01", "FR-FOLD-06")
    fun `taking one app out of a two-app folder dissolves it into the other, in its place`() {
        // A onto B makes a folder of both; dragging A back out leaves B where the folder was.
        val made = LayoutRules.dropOnto(Layout(grid = listOf(item("x"), item("a"), item("b"), item("y"))), app("a"), app("b"))
        assertEquals(listOf(item("x"), folder(1, "b", "a").copy(name = GridItem.Folder.DEFAULT_NAME), item("y")), made.grid)

        val out = LayoutRules.removeFromFolder(made, app("a"), 3)

        assertEquals(listOf(item("x"), item("b"), item("y"), item("a")), out.grid)
        assertGridInvariants(out)
    }

    @Test
    @Verifies("FR-FOLD-05")
    fun `an app moves within its folder and the others keep their order`() {
        val layout = Layout(grid = listOf(item("a"), folder(1, "b", "c", "d")))

        assertEquals(listOf(item("a"), folder(1, "d", "b", "c")), LayoutRules.moveInFolder(layout, app("d"), 0).grid)
        assertEquals(listOf(item("a"), folder(1, "c", "d", "b")), LayoutRules.moveInFolder(layout, app("b"), 99).grid)
        assertEquals(layout, LayoutRules.moveInFolder(layout, app("c"), 1))
        assertEquals(layout, LayoutRules.moveInFolder(layout, app("a"), 0), "Not in a folder")
    }

    @Test
    @Verifies("FR-FOLD-04")
    fun `renaming trims the name, and a blank name keeps the old one`() {
        val layout = Layout(grid = listOf(item("a"), folder(1, "b", "c")))

        assertEquals("Games", LayoutRules.findFolder(LayoutRules.renameFolder(layout, FolderId(1), "  Games "), FolderId(1))!!.name)
        assertEquals(layout, LayoutRules.renameFolder(layout, FolderId(1), "   "))
        assertEquals(layout, LayoutRules.renameFolder(layout, FolderId(1), "F1"))
        assertEquals(layout, LayoutRules.renameFolder(layout, FolderId(9), "Games"))
    }

    @Test
    @Verifies("FR-FOLD-07")
    fun `removing a folder puts its apps back in its place, in order`() {
        val layout = Layout(grid = listOf(item("a"), folder(1, "b", "c", "d"), item("e")), home = listOf(app("c")))

        val removed = LayoutRules.removeFolder(layout, FolderId(1))

        assertEquals(listOf("a", "b", "c", "d", "e").map(::item), removed.grid)
        assertEquals(layout.home, removed.home)
        assertGridInvariants(removed)
        assertEquals(layout, LayoutRules.removeFolder(layout, FolderId(9)))
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
