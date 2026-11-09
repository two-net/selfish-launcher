package io.github.twonet.selfish

import io.github.twonet.selfish.adapters.storage.FileStorage
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Backup and device transfer carry the layout and settings and nothing else;
 * the wallpaper cache can be downloaded again (SRS §8, DESIGN §5.3). Runs as
 * a JVM test from the `app` module directory.
 */
class BackupRulesTest {
    private val xmlDir = File("src/main/res/xml")

    /** The `include` and `exclude` rules of each section, as `"domain:path"`. */
    private fun rules(fileName: String, sections: List<String>): Map<String, List<String>> {
        val file = File(xmlDir, fileName)
        assertTrue(file.isFile, "Missing ${file.absolutePath}")
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
        val parents = sections.associateWith { name ->
            if (name == root.tagName) root else root.getElementsByTagName(name).item(0) as Element
        }
        return parents.mapValues { (_, parent) ->
            val children = parent.childNodes
            (0 until children.length).mapNotNull { children.item(it) as? Element }
                .map { "${it.tagName} ${it.getAttribute("domain")}:${it.getAttribute("path")}" }
        }
    }

    private val expected = FileStorage.BACKED_UP_FILES.map { "include file:$it" }.sorted()

    @Test
    fun `API 31 and later back up and transfer exactly the layout and settings`() {
        val sections = rules("data_extraction_rules.xml", listOf("cloud-backup", "device-transfer"))

        for ((section, found) in sections) assertEquals(expected, found.sorted(), section)
    }

    @Test
    fun `API 29 and 30 back up exactly the layout and settings`() {
        val sections = rules("backup_rules.xml", listOf("full-backup-content"))

        assertEquals(expected, sections.getValue("full-backup-content").sorted())
    }
}
