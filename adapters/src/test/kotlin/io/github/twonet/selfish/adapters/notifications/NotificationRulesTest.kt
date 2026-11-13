package io.github.twonet.selfish.adapters.notifications

import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.ports.NotificationRef
import kotlin.test.Test
import kotlin.test.assertEquals

/** Which notifications Events lists, and which a dismissal removes (DESIGN §5.1). */
class NotificationRulesTest {
    private fun posted(
        key: String,
        time: Long = 0,
        pkg: String = "com.chat",
        title: String = "Title",
        text: String = "Text",
        group: String? = "g:$key",
        summary: Boolean = false,
        clearable: Boolean = true,
        hidden: Boolean = false,
        postedAt: Long = time,
    ) = Posted(key, pkg, 0, group, summary, title, text, time, postedAt, clearable, hidden)

    @Test
    @Verifies("FR-EVT-04")
    fun `notifications are listed newest first`() {
        val shown = NotificationRules.shown(listOf(posted("a", 1), posted("b", 3), posted("c", 2)), excludedPackage = null)

        assertEquals(listOf("b", "c", "a"), shown.map { it.key })
    }

    @Test
    @Verifies("FR-EVT-04")
    fun `the launcher's own, hidden and empty notifications are left out`() {
        val shown = NotificationRules.shown(
            listOf(
                posted("own", pkg = "launcher"),
                posted("hidden", hidden = true),
                posted("empty", title = " ", text = ""),
                posted("title only", text = ""),
                posted("text only", title = ""),
            ),
            excludedPackage = "launcher",
        )

        assertEquals(setOf("title only", "text only"), shown.map { it.key }.toSet())
    }

    @Test
    @Verifies("FR-EVT-04")
    fun `a group's summary is left out while its notifications are listed`() {
        val shown = NotificationRules.shown(
            listOf(
                posted("summary", group = "g:mail", summary = true),
                posted("m1", group = "g:mail"),
                posted("lone summary", group = "g:news", summary = true),
            ),
            excludedPackage = null,
        )

        assertEquals(setOf("m1", "lone summary"), shown.map { it.key }.toSet())
    }

    @Test
    @Verifies("FR-EVT-05")
    fun `ongoing notifications are listed, but never cancelled`() {
        val ongoing = posted("ongoing", clearable = false)

        assertEquals(listOf(ongoing), NotificationRules.shown(listOf(ongoing), excludedPackage = null))
        assertEquals(emptyList(), NotificationRules.toCancel(listOf(ongoing), setOf(NotificationRef("ongoing", 0))))
    }

    @Test
    @Verifies("FR-EVT-05", "FR-EVT-06")
    fun `only notifications still as they were shown are cancelled`() {
        val current = listOf(posted("same", postedAt = 5), posted("updated", postedAt = 9), posted("unseen", postedAt = 7))
        val refs = setOf(NotificationRef("same", 5), NotificationRef("updated", 6))

        assertEquals(listOf("same"), NotificationRules.toCancel(current, refs))
    }
}
