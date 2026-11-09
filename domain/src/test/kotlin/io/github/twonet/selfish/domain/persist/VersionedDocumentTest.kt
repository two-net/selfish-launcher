package io.github.twonet.selfish.domain.persist

import io.github.twonet.selfish.domain.Verifies
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Verifies("AR-07", "NFR-REL-03")
class VersionedDocumentTest {
    @Serializable
    enum class Mode { Light, Dark, System }

    /** Version 2 of a sample document. Version 1 called `name` `title`. */
    @Serializable
    data class Sample(val name: String = "default", val mode: Mode = Mode.System, val count: Int = 0)

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
}
