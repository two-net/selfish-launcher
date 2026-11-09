package io.github.twonet.selfish

import io.github.twonet.selfish.domain.Verifies
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The window theme behind every surface (DESIGN §6.1): the wallpaper shows
 * through, there is no action bar, and views make no sounds. Runs as a JVM
 * test from the `app` module directory.
 */
class ThemeRulesTest {
    private val theme: Element = run {
        val file = File("src/main/res/values/themes.xml")
        assertTrue(file.isFile, "Missing ${file.absolutePath}")
        val styles = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("style")
        (0 until styles.length).map { styles.item(it) as Element }.single { it.getAttribute("name") == THEME }
    }

    private val items: Map<String, String> = theme.getElementsByTagName("item").let { nodes ->
        (0 until nodes.length).map { nodes.item(it) as Element }.associate { it.getAttribute("name") to it.textContent.trim() }
    }

    @Test
    @Verifies("UI-01", "UI-03", "UI-09")
    fun `the manifest gives every activity the launcher theme`() {
        val manifest = File("src/main/AndroidManifest.xml")
        assertTrue(manifest.isFile, "Missing ${manifest.absolutePath}")
        val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(manifest)
        val application = document.getElementsByTagName("application").item(0) as Element
        val activities = document.getElementsByTagName("activity")

        assertEquals("@style/$THEME", application.getAttributeNS(ANDROID, "theme"))
        for (i in 0 until activities.length) {
            val activity = activities.item(i) as Element
            assertEquals("", activity.getAttributeNS(ANDROID, "theme"), "${activity.getAttributeNS(ANDROID, "name")} overrides the theme")
        }
    }

    @Test
    @Verifies("UI-01")
    fun `the window is transparent over the wallpaper`() {
        assertEquals("true", items["android:windowShowWallpaper"])
        assertEquals("@android:color/transparent", items["android:windowBackground"])
    }

    @Test
    @Verifies("UI-03")
    fun `the window has no action bar`() {
        assertTrue(theme.getAttribute("parent").endsWith(".NoActionBar"), "Parent was ${theme.getAttribute("parent")}")
        assertTrue(items["android:windowActionBar"] in setOf(null, "false"))
    }

    @Test
    @Verifies("UI-09")
    fun `views play no sound effects`() {
        assertEquals("false", items["android:soundEffectsEnabled"])
    }

    private companion object {
        const val THEME = "Theme.Selfish"
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }
}
