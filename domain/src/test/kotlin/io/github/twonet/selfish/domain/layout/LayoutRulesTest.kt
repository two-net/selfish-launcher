package io.github.twonet.selfish.domain.layout

import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.AppId
import io.github.twonet.selfish.domain.model.FolderId
import io.github.twonet.selfish.domain.model.GridItem
import io.github.twonet.selfish.domain.model.Layout
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
