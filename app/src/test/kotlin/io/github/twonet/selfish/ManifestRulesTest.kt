package io.github.twonet.selfish

import io.github.twonet.selfish.domain.Verifies
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How the system sees the launcher (DESIGN §5.6): its name and icon, the
 * Home role and the orientation. Runs as a JVM test from the `app` module
 * directory.
 */
class ManifestRulesTest {
    private val manifest: Document = parse(File("src/main/AndroidManifest.xml"))
    private val application = manifest.getElementsByTagName("application").item(0) as Element
    private val mainActivity: Element = manifest.getElementsByTagName("activity").let { nodes ->
        (0 until nodes.length).map { nodes.item(it) as Element }.single { it.android("name") == ".MainActivity" }
    }

    private fun Element.android(name: String): String = getAttributeNS(ANDROID, name)

    private fun Element.children(tag: String): List<Element> =
        getElementsByTagName(tag).let { nodes -> (0 until nodes.length).map { nodes.item(it) as Element } }

    /** Each intent filter of the main activity as its action and categories. */
    private val filters: List<Set<String>> = mainActivity.children("intent-filter").map { filter ->
        (filter.children("action") + filter.children("category")).map { it.android("name") }.toSet()
    }

    @Test
    @Verifies("FR-GEN-01")
    fun `the launcher is named Selfish Launcher`() {
        assertEquals("@string/app_name", application.android("label"))
        val strings = parse(File("src/main/res/values/strings.xml")).documentElement.children("string")
        assertEquals("Selfish Launcher", strings.single { it.getAttribute("name") == "app_name" }.textContent)
        assertEquals("", mainActivity.android("label"), "The activity must not override the name")
    }

    @Test
    @Verifies("FR-GEN-01")
    fun `the launcher has its own adaptive icon, with a monochrome layer`() {
        assertEquals("@mipmap/ic_launcher", application.android("icon"))
        assertEquals("", mainActivity.android("icon"), "The activity must not override the icon")

        val icon = parse(File("src/main/res/mipmap-anydpi/ic_launcher.xml")).documentElement
        assertEquals("adaptive-icon", icon.tagName)
        for (layer in listOf("background", "foreground", "monochrome")) {
            assertEquals(1, icon.children(layer).size, "Missing the $layer layer")
        }
    }

    @Test
    @Verifies("FR-GEN-02")
    fun `the launcher can be chosen as the Home app and opened after install`() {
        assertTrue(setOf(MAIN, HOME, DEFAULT) in filters, "Needs a MAIN/HOME/DEFAULT filter, had $filters")
        assertTrue(setOf(MAIN, LAUNCHER) in filters, "Needs a MAIN/LAUNCHER filter, had $filters")
        assertEquals("true", mainActivity.android("exported"))
    }

    @Test
    @Verifies("FR-GEN-03")
    fun `Home presses reach the one launcher instance`() {
        assertEquals("singleTask", mainActivity.android("launchMode"))
    }

    @Test
    @Verifies("FR-GEN-09")
    fun `the launcher is portrait`() {
        assertEquals("portrait", mainActivity.android("screenOrientation"))
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
        const val MAIN = "android.intent.action.MAIN"
        const val HOME = "android.intent.category.HOME"
        const val DEFAULT = "android.intent.category.DEFAULT"
        const val LAUNCHER = "android.intent.category.LAUNCHER"

        fun parse(file: File): Document {
            assertTrue(file.isFile, "Missing ${file.absolutePath}")
            return DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(file)
        }
    }
}
