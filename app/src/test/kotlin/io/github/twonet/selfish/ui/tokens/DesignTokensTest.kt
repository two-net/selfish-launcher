package io.github.twonet.selfish.ui.tokens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.Rgb
import io.github.twonet.selfish.domain.theme.Contrast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The token values meet the SRS (DESIGN §6.5). Colors are checked over the two
 * worst-case wallpapers, pure white and pure black, so they hold over any
 * wallpaper. The adaptive scrims of #6 only ever get weaker than [LauncherColors.scrim]
 * over wallpapers that don't need it.
 */
class DesignTokensTest {
    private val appearances = mapOf("Light" to LightColors, "Dark" to DarkColors)
    private val wallpapers = mapOf("white" to Rgb(0xFFFFFF), "black" to Rgb(0x000000))

    private fun LauncherColors.backdrops() = mapOf("glass" to glass, "scrim" to scrim, "dim" to dim)

    private val Color.rgb get() = Rgb(toArgb() and 0xFFFFFF)

    /** Each backdrop of each appearance, as it looks over each worst-case wallpaper. */
    private class Case(val name: String, val colors: LauncherColors, val background: Rgb)

    private val worstCases = appearances.flatMap { (appearance, colors) ->
        wallpapers.flatMap { (wallpaper, color) ->
            colors.backdrops().map { (backdrop, fill) ->
                Case("$appearance $backdrop over $wallpaper", colors, Contrast.composite(fill.rgb, fill.alpha, color))
            }
        }
    }

    @Test
    @Verifies("NFR-A11Y-03", "FR-THM-05")
    fun `text at both emphasis levels reaches body-text contrast on every backdrop`() {
        val failures = worstCases.flatMap { case ->
            listOf("textPrimary" to case.colors.textPrimary, "textSecondary" to case.colors.textSecondary)
                .map { (name, text) -> Triple(name, text, Contrast.ratio(text.rgb, case.background)) }
                .filter { (_, _, ratio) -> ratio < Contrast.BODY_TEXT }
                .map { (name, _, ratio) -> "${case.name}: $name is %.2f:1".format(ratio) }
        }

        assertEquals(emptyList(), failures, "Body text needs ${Contrast.BODY_TEXT}:1")
    }

    @Test
    @Verifies("NFR-A11Y-03")
    fun `the default highlight reaches large-text contrast on every backdrop`() {
        val failures = worstCases
            .map { it to Contrast.ratio(it.colors.highlightDefault.rgb, it.background) }
            .filter { (_, ratio) -> ratio < Contrast.LARGE_TEXT }
            .map { (case, ratio) -> "${case.name}: %.2f:1".format(ratio) }

        assertEquals(emptyList(), failures, "The highlight needs ${Contrast.LARGE_TEXT}:1")
    }

    @Test
    @Verifies("NFR-A11Y-03", "UI-02")
    fun `the highlight is only used at large-text sizes`() {
        // 3:1 is enough for the highlight because it colors headers and
        // states, never body text (FR-AMB-04). WCAG counts 24 px as large.
        for ((name, style) in listOf("header" to DefaultType.header, "display" to DefaultType.display)) {
            assertTrue(style.fontSize.isSp, "$name is in sp")
            assertTrue(style.fontSize.value >= 24f, "$name is large text, was ${style.fontSize}")
        }
    }

    @Test
    @Verifies("UI-04")
    fun `text uses one family in sp and opaque colors`() {
        for ((appearance, colors) in appearances) {
            for (color in listOf(colors.textPrimary, colors.textSecondary, colors.highlightDefault)) {
                assertEquals(1f, color.alpha, "$appearance text and highlight are opaque, so the contrast above is what shows")
            }
        }
        for (role in TextRole.entries) {
            val style = DefaultType.style(role)
            assertEquals(FontFamily.Default, style.fontFamily, "$role uses the one system family")
            assertTrue(style.fontSize.isSp, "$role follows the font scale (NFR-A11Y-04)")
        }
    }

    @Test
    @Verifies("UI-01")
    fun `every backdrop is translucent`() {
        val opaqueOrInvisible = appearances.flatMap { (appearance, colors) ->
            colors.backdrops()
                .filter { (_, fill) -> fill.alpha <= 0f || fill.alpha >= 1f }
                .map { (name, fill) -> "$appearance $name has alpha ${fill.alpha}" }
        }

        assertEquals(emptyList(), opaqueOrInvisible, "The wallpaper must always show through")
    }

    @Test
    @Verifies("FR-THM-05")
    fun `Light has dark text on light glass and Dark the reverse`() {
        val light = Contrast.luminance(LightColors.textPrimary.rgb) < Contrast.luminance(LightColors.glass.rgb)
        val dark = Contrast.luminance(DarkColors.textPrimary.rgb) > Contrast.luminance(DarkColors.glass.rgb)

        assertTrue(light, "Light: text darker than glass")
        assertTrue(dark, "Dark: text lighter than glass")
    }

    @Test
    @Verifies("UI-06", "UI-05")
    fun `spacing and sizes sit on the 4 dp scale and touch targets are at least 48 dp`() {
        assertEquals(4.dp, Spacing.Step)
        val offScale = (Spacing.scale + listOf(Sizes.Icon, Sizes.MinTouchTarget, Sizes.LiftElevation))
            .filter { it.value <= 0f || it.value % Spacing.Step.value != 0f }

        assertEquals(emptyList(), offScale, "Not a multiple of ${Spacing.Step}")
        assertTrue(Sizes.MinTouchTarget >= 48.dp, "Touch targets are at least 48 dp, was ${Sizes.MinTouchTarget}")
    }
}
