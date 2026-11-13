package io.github.twonet.selfish.domain.events

import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.AppId
import io.github.twonet.selfish.domain.ports.NotificationItem
import kotlin.test.Test
import kotlin.test.assertEquals

@Verifies("FR-EVT-04")
class NotificationGroupsTest {
    private fun note(key: String, pkg: String, time: Long, label: String = "", user: Long = 0) =
        NotificationItem(key, pkg, user, label, "Title", "Text", time, time, clearable = true)

    private val chat = AppEntry(AppId("com.chat", "Main", 0), "Chat")
    private val mail = AppEntry(AppId("com.mail", "Inbox", 0), "Mail")

    @Test
    fun `notifications are grouped by app, the newest group first and the newest first within it`() {
        val groups = NotificationGroups.of(
            listOf(note("c1", "com.chat", 1), note("m1", "com.mail", 3), note("c2", "com.chat", 5), note("m2", "com.mail", 2)),
            listOf(chat, mail),
        )

        assertEquals(listOf("com.chat", "com.mail"), groups.map { it.packageName })
        assertEquals(listOf("c2", "c1"), groups[0].items.map { it.key })
        assertEquals(listOf("m1", "m2"), groups[1].items.map { it.key })
        assertEquals(listOf(chat, mail), groups.map { it.app })
    }

    @Test
    fun `each profile's notifications are a group of their own`() {
        val work = AppEntry(AppId("com.chat", "Main", 10), "Chat")

        val groups = NotificationGroups.of(listOf(note("a", "com.chat", 2), note("b", "com.chat", 1, user = 10)), listOf(chat, work))

        assertEquals(listOf(chat, work), groups.map { it.app })
    }

    @Test
    fun `a group is named by the system's name for the app, else by the catalog, else by the package`() {
        val groups = NotificationGroups.of(
            listOf(note("a", "com.chat", 3, label = "Chat Pro"), note("b", "com.mail", 2), note("c", "android", 1)),
            listOf(chat, mail),
        )

        assertEquals(listOf("Chat Pro", "Mail", "android"), groups.map { it.label })
        assertEquals(listOf(chat, mail, null), groups.map { it.app })
    }

    @Test
    fun `a package with several apps takes the icon of the one the system names`() {
        val phone = AppEntry(AppId("com.dialer", "Phone", 0), "Phone")
        val contacts = AppEntry(AppId("com.dialer", "Contacts", 0), "Contacts")

        val group = NotificationGroups.of(listOf(note("a", "com.dialer", 1, label = "Phone")), listOf(contacts, phone)).single()

        assertEquals(phone, group.app)
    }
}
