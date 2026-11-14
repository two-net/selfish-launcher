package io.github.twonet.selfish.ui.settings

import io.github.twonet.selfish.domain.Verifies
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * About credits every open-source component the launcher ships (FR-SET-17):
 * each module in `gradle/runtime-dependencies.txt`, the reviewed list of the
 * release runtime classpath (AR-08, DESIGN §7.1), belongs to a component in
 * [Licenses], and no component is left over from one that was removed. Runs
 * as a JVM test from the `app` module directory.
 */
class LicensesRulesTest {
    private val shipped: List<String> = File("../gradle/runtime-dependencies.txt").also {
        assertTrue(it.isFile, "Missing ${it.absolutePath}")
    }.readLines()
        .map { it.substringBefore('#').trim() }
        .filter { it.isNotEmpty() }

    private fun OpenSourceComponent.covers(module: String) = modules.any { module.startsWith(it) }

    @Test
    @Verifies("FR-SET-17", "AR-08")
    fun `About credits every component on the release runtime classpath`() {
        val uncredited = shipped.filter { module -> Licenses.COMPONENTS.none { it.covers(module) } }

        assertEquals(emptyList(), uncredited, "Add them to Licenses.COMPONENTS, with their license")
    }

    @Test
    @Verifies("FR-SET-17")
    fun `About credits no component that isn't shipped`() {
        val unused = Licenses.COMPONENTS.filter { component -> shipped.none { component.covers(it) } }

        assertEquals(emptyList(), unused.map { it.modules }, "Remove them from Licenses.COMPONENTS")
    }
}
