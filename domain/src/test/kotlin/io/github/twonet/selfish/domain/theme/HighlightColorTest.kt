package io.github.twonet.selfish.domain.theme

import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.Appearance
import io.github.twonet.selfish.domain.model.HighlightSetting
import io.github.twonet.selfish.domain.model.Rgb
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Deriving and adjusting the highlight color (DESIGN §4.4). The fixture
 * images are built here, pixel by pixel, as the adapter's 128-pixel samples
 * would arrive.
 */
class HighlightColorTest {
    private val gray = Rgb(0x808080)
    private val blue = Rgb(0x1E88E5)
    private val orange = Rgb(0xFB8C00)
    private val red = Rgb(0xE53935)

    /** An image of 1000 pixels, made of [parts] (a color and how many pixels have it) and gray for the rest. */
    private fun image(vararg parts: Pair<Rgb, Int>): IntArray {
        val pixels = parts.flatMap { (color, count) -> List(count) { color.value } }
        require(pixels.size <= 1000)
        return (pixels + List(1000 - pixels.size) { gray.value }).toIntArray()
    }

    /** Every combination of five levels per channel: grays, darks, lights and saturated colors. */
    private val sweep = (0..124).map { i ->
        val levels = intArrayOf(0x00, 0x40, 0x80, 0xC0, 0xFF)
        Rgb((levels[i / 25] shl 16) or (levels[i / 5 % 5] shl 8) or levels[i % 5])
    }

    private val Rgb.red get() = (value shr 16) and 0xFF
    private val Rgb.green get() = (value shr 8) and 0xFF
    private val Rgb.blue get() = value and 0xFF

    private fun hue(color: Rgb): Double {
        val r = color.red / 255.0
        val g = color.green / 255.0
        val b = color.blue / 255.0
        val max = maxOf(r, g, b)
        val chroma = max - minOf(r, g, b)
        val sector = when (max) {
            r -> ((g - b) / chroma).mod(6.0)
            g -> (b - r) / chroma + 2
            else -> (r - g) / chroma + 4
        }
        return sector * 60
    }

    private fun hueDistance(a: Double, b: Double) = abs(a - b).let { minOf(it, 360 - it) }

    // Deriving (FR-AMB-01)

    @Test
    @Verifies("FR-AMB-01")
    fun `a small vivid region beats a large gray one`() {
        assertEquals(blue, HighlightColor.derive(image(blue to 150)))
    }

    @Test
    @Verifies("FR-AMB-01")
    fun `the most populous of two saturated colors wins`() {
        assertEquals(blue, HighlightColor.derive(image(blue to 300, orange to 150)))
        assertEquals(orange, HighlightColor.derive(image(blue to 150, orange to 300)))
    }

    @Test
    @Verifies("FR-AMB-01")
    fun `saturation counts more than population`() {
        // A muted green (30% saturated) over 40% of the image loses to a vivid
        // red (78%) over 10%: population × saturation², not × saturation.
        val mutedGreen = Rgb(0x589658)

        assertEquals(red, HighlightColor.derive(image(mutedGreen to 400, red to 100)))
    }

    @Test
    @Verifies("FR-AMB-01")
    fun `a red on both sides of 0 degrees counts as one color`() {
        // Each half of the red alone is less prominent than the green, but together they are more.
        val reddish = Rgb(0xE61A2B) // 355°
        val orangeRed = Rgb(0xE62B1A) // 5°
        val green = Rgb(0x1AE61A)

        val derived = assertNotNull(HighlightColor.derive(image(reddish to 250, orangeRed to 250, green to 300)))

        assertTrue(hueDistance(hue(derived), 0.0) < 5, "Red, was $derived")
    }

    @Test
    @Verifies("FR-AMB-01", "FR-AMB-03")
    fun `an image without a prominent saturated color gets the default`() {
        val nearlyBlack = Rgb(0x200000)
        val nearlyWhite = Rgb(0xFFF0F0)
        val grayish = Rgb(0x8A7F7F)

        assertNull(HighlightColor.derive(image()), "gray")
        assertNull(HighlightColor.derive(image(Rgb(0x000000) to 400, Rgb(0xFFFFFF) to 400)), "black and white")
        assertNull(HighlightColor.derive(image(nearlyBlack to 500, nearlyWhite to 400, grayish to 100)), "nearly black, white and gray")
        assertNull(HighlightColor.derive(image(red to 10)), "a speck of red")
        assertNull(HighlightColor.derive(IntArray(0)), "no pixels")
    }

    @Test
    @Verifies("FR-AMB-01")
    fun `the alpha byte of a sample is ignored`() {
        val samples = image(blue to 150).map { it or (0xFF shl 24) }.toIntArray()

        assertEquals(blue, HighlightColor.derive(samples))
    }

    // Adjusting (FR-AMB-02, NFR-A11Y-03)

    @Test
    @Verifies("FR-AMB-02", "NFR-A11Y-03")
    fun `every adjusted color reaches large-text contrast on the worst glass of its appearance`() {
        val failures = Appearance.entries.flatMap { appearance ->
            val backdrop = HighlightColor.worstBackdrop(appearance)
            sweep.map { it to HighlightColor.adjust(it, appearance) }
                .filter { (_, adjusted) -> Contrast.ratio(adjusted, backdrop) < Contrast.LARGE_TEXT }
                .map { (source, adjusted) -> "$appearance: $source became $adjusted, %.2f:1".format(Contrast.ratio(adjusted, backdrop)) }
        }

        assertEquals(emptyList(), failures)
    }

    @Test
    @Verifies("FR-AMB-02")
    fun `Light only darkens and Dark only lightens, and no further than needed`() {
        for (color in sweep) {
            val light = HighlightColor.adjust(color, Appearance.Light)
            val dark = HighlightColor.adjust(color, Appearance.Dark)

            assertTrue(Contrast.luminance(light) <= Contrast.luminance(color), "Light darkens $color, was $light")
            assertTrue(Contrast.luminance(dark) >= Contrast.luminance(color), "Dark lightens $color, was $dark")
            if (light != color) {
                assertTrue(Contrast.ratio(light, HighlightColor.LIGHT_WORST_BACKDROP) < 3.2, "Light: $color went too far, to $light")
            }
            if (dark != color) {
                assertTrue(Contrast.ratio(dark, HighlightColor.DARK_WORST_BACKDROP) < 3.2, "Dark: $color went too far, to $dark")
            }
        }
    }

    @Test
    @Verifies("FR-AMB-02")
    fun `only the lightness changes, so the hue stays`() {
        for (color in HighlightSetting.entries.mapNotNull { it.color } + listOf(blue, orange, red)) {
            for (appearance in Appearance.entries) {
                val adjusted = HighlightColor.adjust(color, appearance)

                assertTrue(hueDistance(hue(color), hue(adjusted)) < 4, "$appearance: $color became $adjusted")
            }
        }
    }

    @Test
    @Verifies("FR-AMB-02")
    fun `a color that is already legible is kept`() {
        // The default highlight tokens reach 3:1 as they are (DESIGN §6.5).
        assertEquals(Rgb(0x005A70), HighlightColor.adjust(Rgb(0x005A70), Appearance.Light))
        assertEquals(Rgb(0x6CCFF6), HighlightColor.adjust(Rgb(0x6CCFF6), Appearance.Dark))
    }

    @Test
    @Verifies("FR-AMB-02")
    fun `a highlight holds the adjusted color of each appearance`() {
        val highlight = Highlight.of(orange)

        assertEquals(HighlightColor.adjust(orange, Appearance.Light), highlight.on(Appearance.Light))
        assertEquals(HighlightColor.adjust(orange, Appearance.Dark), highlight.on(Appearance.Dark))
        assertTrue(Contrast.luminance(highlight.light) < Contrast.luminance(highlight.dark), "Darker in Light than in Dark")
    }

    // Wallpapers set elsewhere (FR-AMB-03)

    @Test
    @Verifies("FR-AMB-03")
    fun `the first colorful color the system reports is the highlight`() {
        assertEquals(blue, HighlightColor.fromSystem(listOf(gray, blue, red)))
        assertEquals(red, HighlightColor.fromSystem(listOf(red, blue)))
        assertNull(HighlightColor.fromSystem(listOf(gray, Rgb(0x000000), Rgb(0xFFFFFF))))
        assertNull(HighlightColor.fromSystem(emptyList()))
    }

    // Presets (FR-AMB-05)

    @Test
    @Verifies("FR-AMB-05", "FR-AMB-02")
    fun `there are 8 presets besides Automatic, distinct and legible in both appearances`() {
        val presets = HighlightSetting.entries.mapNotNull { it.color }

        assertEquals(8, presets.size)
        assertNull(HighlightSetting.Automatic.color)
        for (appearance in Appearance.entries) {
            val adjusted = presets.map { Highlight.of(it).on(appearance) }
            assertEquals(8, adjusted.toSet().size, "$appearance: $adjusted")
            for (color in adjusted) {
                assertTrue(Contrast.ratio(color, HighlightColor.worstBackdrop(appearance)) >= Contrast.LARGE_TEXT, "$appearance: $color")
            }
        }
    }
}
