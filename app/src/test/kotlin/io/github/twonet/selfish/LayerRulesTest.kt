package io.github.twonet.selfish

import io.github.twonet.selfish.domain.Verifies
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Source rules the compiler can't enforce (DESIGN §3.1, §5.5, §6.5). Runs as
 * a JVM test from the `app` module directory.
 */
class LayerRulesTest {
    private val appRoot = File("src/main/kotlin/io/github/twonet/selfish")
    private val adaptersRoot = File("../adapters/src/main/kotlin/io/github/twonet/selfish/adapters")

    private class Source(val file: File, val relativePath: String) {
        val lines = file.readLines()
        val imports = lines.mapNotNull { IMPORT.find(it)?.groupValues?.get(1) }

        /** Code lines, without comment-only lines. */
        val code = lines.withIndex().filterNot { (_, line) ->
            line.trimStart().let { it.startsWith("//") || it.startsWith("*") || it.startsWith("/*") }
        }
    }

    private fun sources(root: File): List<Source> {
        assertTrue(root.isDirectory, "Missing ${root.absolutePath}")
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { Source(it, it.relativeTo(root).invariantSeparatorsPath) }
            .toList()
            .also { assertTrue(it.isNotEmpty(), "No sources under ${root.absolutePath}") }
    }

    @Test
    @Verifies("AR-01")
    fun `only the composition root sees the adapters`() {
        val offenders = sources(appRoot)
            .filter { it.relativePath != "AppGraph.kt" }
            .filter { source -> source.imports.any { it.startsWith("io.github.twonet.selfish.adapters") } }
            .map { it.relativePath }

        assertEquals(emptyList(), offenders, "Only AppGraph.kt may import :adapters")
    }

    @Test
    @Verifies("AR-01")
    fun `views never talk to the OS or the network`() {
        val offenders = sources(appRoot)
            .filter { it.relativePath.startsWith("ui/") }
            .flatMap { source ->
                source.imports
                    .filter { import -> OS_AND_NETWORK.any { import == it || import.startsWith("$it.") } }
                    .map { "${source.relativePath}: $it" }
            }

        assertEquals(emptyList(), offenders, "Views must go through the Domain")
    }

    @Test
    @Verifies("AR-04")
    fun `colors, sizes and durations are defined only in the tokens`() {
        val offenders = sources(appRoot)
            .filterNot { it.relativePath.startsWith("ui/tokens/") }
            .flatMap { source ->
                source.code
                    .filter { (_, line) -> LITERALS.any { it.containsMatchIn(line) } }
                    .map { (index, line) -> "${source.relativePath}:${index + 1}: ${line.trim()}" }
            }

        assertEquals(emptyList(), offenders, "Use ui/tokens instead of literal values")
    }

    @Test
    @Verifies("AR-09")
    fun `optional adapters are referenced by no other adapter`() {
        val offenders = sources(adaptersRoot).flatMap { source ->
            OPTIONAL_PACKAGES
                .filterNot { source.relativePath.startsWith("$it/") }
                .filter { pkg -> source.imports.any { it.startsWith("io.github.twonet.selfish.adapters.$pkg") } }
                .map { "${source.relativePath} imports adapters.$it" }
        }

        assertEquals(emptyList(), offenders, "Optional features must stay removable")
    }

    private companion object {
        val IMPORT = Regex("""^import\s+([\w.]+)""")

        /** OS-service and network packages that belong in :adapters (DESIGN §3.1). */
        val OS_AND_NETWORK = listOf(
            "android.app.WallpaperManager",
            "android.app.job",
            "android.app.usage",
            "android.content.pm",
            "android.net",
            "android.provider.Settings",
            "android.service.notification",
            "java.net",
            "javax.net",
        )

        val LITERALS = listOf(
            Regex("""\bColor\(\s*0x"""),
            Regex("""\b\d+(\.\d+)?f?\.(dp|sp)\b"""),
            Regex("""\b(tween|delay|snap)\(\s*(durationMillis\s*=\s*)?\d"""),
            Regex("""\b(durationMillis|delayMillis)\s*=\s*\d"""),
            Regex("""\b\d+\.(milliseconds|seconds)\b"""),
        )

        val OPTIONAL_PACKAGES = listOf("notifications", "usage")
    }
}
