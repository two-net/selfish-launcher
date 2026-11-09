package io.github.twonet.selfish.domain.persist

import io.github.twonet.selfish.domain.Verifies
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Verifies("AR-07", "NFR-REL-03")
class VersionedDocumentTest {
    @Serializable
    enum class Mode { Light, Dark, System }

    /** Version 2 of a sample document. Version 1 called `name` `title`. */
    @Serializable
    data class Sample(val name: String = "default", val mode: Mode = Mode.System, val count: Int = 0) {
        init {
            require(count >= 0)
        }
    }

    @Serializable
    data class Item(val label: String, val size: Int = 1)

    @Serializable
    data class Bag(
        @Serializable(with = LenientListSerializer::class)
        val items: List<Item> = emptyList(),
        val owner: String = "nobody",
    )

    private val renameTitle: (JsonObject) -> JsonObject = { v1 ->
        JsonObject(v1.mapKeys { (key, _) -> if (key == "title") "name" else key })
    }

    private val doc = VersionedDocument(Sample.serializer(), default = Sample(), migrations = listOf(renameTitle))

    @Test
    fun `the current version is one more than the number of migrations`() {
        assertEquals(2, doc.currentVersion)
    }

    @Test
    fun `encoding stamps the version and decoding round-trips`() {
        val value = Sample("Mine", Mode.Dark, 3)

        val text = doc.encode(value)

        assertTrue(text.startsWith("""{"schemaVersion":2"""), text)
        assertEquals(value, doc.decode(text))
    }

    @Test
    fun `older documents are migrated forward`() {
        assertEquals(Sample(name = "Old"), doc.decode("""{"schemaVersion":1,"title":"Old"}"""))
    }

    @Test
    fun `a document without a version is treated as version 1`() {
        assertEquals(Sample(name = "Old"), doc.decode("""{"title":"Old"}"""))
    }

    @Test
    fun `a document from a newer app falls back to defaults`() {
        assertEquals(Sample(), doc.decode("""{"schemaVersion":3,"name":"Future"}"""))
    }

    @Test
    fun `unreadable text falls back to defaults`() {
        for (text in listOf(null, "", "not json", "[1,2]", """{"schemaVersion":"two"}""", """{"name":""")) {
            assertEquals(Sample(), doc.decode(text), "for $text")
        }
    }

    @Test
    fun `unknown keys are ignored and missing fields take defaults`() {
        assertEquals(Sample(name = "A"), doc.decode("""{"schemaVersion":2,"name":"A","extra":true}"""))
    }

    @Test
    fun `an invalid enum value falls back to its default`() {
        assertEquals(Sample(name = "A", mode = Mode.System), doc.decode("""{"schemaVersion":2,"name":"A","mode":"Purple"}"""))
    }

    @Test
    fun `nothing usable saved decodes to null`() {
        for (text in listOf(null, "", "not json", "[1,2]", """{"schemaVersion":"two"}""", """{"schemaVersion":0}""", """{"schemaVersion":3}""")) {
            assertNull(doc.decodeOrNull(text), "for $text")
        }
    }

    @Test
    fun `a field that does not decode keeps its default and the others are kept`() {
        assertEquals(Sample(name = "A", count = 0), doc.decode("""{"schemaVersion":2,"name":"A","count":"many"}"""))
        assertEquals(Sample(name = "A", mode = Mode.Dark), doc.decode("""{"schemaVersion":2,"name":"A","mode":"Dark","count":-1}"""))
    }

    @Test
    fun `field-level fallback starts from the document default`() {
        val seeded = VersionedDocument(Sample.serializer(), default = Sample(name = "seed", count = 7))

        assertEquals(Sample(name = "seed", mode = Mode.Dark, count = 7), seeded.decode("""{"mode":"Dark","count":-1}"""))
    }

    @Test
    fun `repair runs after decoding`() {
        val trimmed = VersionedDocument(Sample.serializer(), Sample(), repair = { it.copy(name = it.name.trim()) })

        assertEquals(Sample(name = "A"), trimmed.decode("""{"name":"  A  "}"""))
    }

    @Test
    fun `a repair that throws counts as nothing usable`() {
        val broken = VersionedDocument(Sample.serializer(), Sample(), repair = { error("bug") })

        assertNull(broken.decodeOrNull("""{"name":"A"}"""))
    }

    @Test
    fun `a lenient list drops only the elements it cannot read`() {
        val bags = VersionedDocument(Bag.serializer(), Bag())

        val bag = bags.decode("""{"items":[{"label":"a"},{"size":2},"junk",{"label":"b","size":"big"},{"label":"c","size":3}],"owner":"me"}""")

        assertEquals(Bag(listOf(Item("a"), Item("c", 3)), owner = "me"), bag)
    }

    @Test
    fun `a lenient list that is not a list falls back alone`() {
        val bags = VersionedDocument(Bag.serializer(), Bag())

        assertEquals(Bag(owner = "me"), bags.decode("""{"items":"none","owner":"me"}"""))
    }

    @Test
    fun `a lenient list is written as a plain list`() {
        val bags = VersionedDocument(Bag.serializer(), Bag())

        assertEquals(
            """{"schemaVersion":1,"items":[{"label":"a","size":1}],"owner":"me"}""",
            bags.encode(Bag(listOf(Item("a")), owner = "me")),
        )
    }
}
