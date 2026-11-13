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

        /** Code lines that match any of [patterns], as `path:line: code`. */
        fun matching(patterns: List<Regex>): List<String> = code
            .filter { (_, line) -> patterns.any { it.containsMatchIn(line) } }
            .map { (index, line) -> "$relativePath:${index + 1}: ${line.trim()}" }
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
    @Verifies("AR-04", "UI-08")
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
    @Verifies("UI-06")
    fun `taps are handled only through pressable, which keeps targets at least 48 dp`() {
        val offenders = sources(appRoot)
            .filter { it.relativePath != "ui/components/Pressable.kt" }
            .flatMap { source -> source.matching(CLICK_HANDLERS) }

        assertEquals(emptyList(), offenders, "Use Modifier.pressable from ui/components")
    }

    @Test
    @Verifies("UI-04")
    fun `text is drawn only through the components, in the token type scale`() {
        val outsideComponents = sources(appRoot)
            .filterNot { it.relativePath.startsWith("ui/components/") }
            .flatMap { source -> source.matching(listOf(BASIC_TEXT)) }
        val outsideTokens = sources(appRoot)
            .filterNot { it.relativePath.startsWith("ui/tokens/") }
            .flatMap { source -> source.matching(listOf(TYPE_DEFINITIONS)) }

        assertEquals(emptyList(), outsideComponents, "Use LauncherText or PageHeader")
        assertEquals(emptyList(), outsideTokens, "Type styles are defined only in ui/tokens")
    }

    @Test
    @Verifies("UI-09")
    fun `the launcher makes no sounds`() {
        val offenders = (sources(appRoot) + sources(adaptersRoot))
            .flatMap { source -> source.matching(SOUND_APIS) }

        assertEquals(emptyList(), offenders, "The launcher makes no sounds of its own")
    }

    @Test
    @Verifies("FR-GEST-08")
    fun `haptics play only through LocalHapticFeedback, which the launcher gates by its setting`() {
        val direct = (sources(appRoot) + sources(adaptersRoot))
            .flatMap { source -> source.matching(DIRECT_HAPTICS) }
        val ungated = sources(appRoot)
            .filter { it.relativePath != "ui/components/Haptics.kt" }
            .flatMap { source -> source.matching(listOf(HAPTICS_PROVIDED)) }

        assertEquals(emptyList(), direct, "Play haptics through LocalHapticFeedback, so Haptic feedback Off silences them")
        assertEquals(emptyList(), ungated, "Only LauncherHaptics provides LocalHapticFeedback")
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
            // Corner radii in percent; radii in dp are caught above.
            Regex("""\bRoundedCornerShape\(\s*\d"""),
            // Literal alphas such as `.copy(alpha = 0.5f)` or `.alpha(0.5f)`;
            // computed ones such as `alpha = 1f - progress` are fine.
            Regex("""\balpha\s*=\s*\d*\.?\d+f?\s*([,)};]|$)"""),
            Regex("""\.alpha\(\s*\d*\.?\d+f?\s*\)"""),
        )

        /** Every tap goes through `Modifier.pressable` (UI-06). */
        val CLICK_HANDLERS = listOf(
            Regex("""\.(clickable|combinedClickable|selectable|toggleable|triStateToggleable)\s*[({]"""),
        )

        val BASIC_TEXT = Regex("""\bBasicText\s*\(""")

        val TYPE_DEFINITIONS = Regex("""\b(FontFamily|FontWeight|TextStyle)\b""")

        /** Platform audio APIs (UI-09), as imports or fully qualified names. */
        val SOUND_APIS = listOf(
            Regex(
                """\b(SoundPool|MediaPlayer|AudioManager|AudioTrack|ToneGenerator|Ringtone|RingtoneManager|""" +
                    """AsyncPlayer|JetPlayer|SoundEffectConstants|playSoundEffect)\b""",
            ),
            Regex("""^import\s+android\.media\.\*"""),
        )

        /**
         * Haptics that bypass `LocalHapticFeedback`, and so the Haptic
         * feedback setting (FR-GEST-08): a View's, which takes one of the
         * platform's constants, and the vibrator's. Calls such as
         * `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.LongPress)`
         * are fine.
         */
        val DIRECT_HAPTICS = listOf(
            Regex("""\b(HapticFeedbackConstants|HapticFeedbackConstantsCompat|Vibrator|VibratorManager|VibrationEffect)\b"""),
            Regex("""\bViewCompat\s*\.\s*performHapticFeedback\b"""),
            Regex("""\bperformHapticFeedback\(\s*\d"""),
        )

        val HAPTICS_PROVIDED = Regex("""\bLocalHapticFeedback\s+provides\b""")

        val OPTIONAL_PACKAGES = listOf("notifications", "usage")
    }
}
