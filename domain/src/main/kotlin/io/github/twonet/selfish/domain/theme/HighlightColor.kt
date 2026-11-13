package io.github.twonet.selfish.domain.theme

import io.github.twonet.selfish.domain.model.Appearance
import io.github.twonet.selfish.domain.model.Rgb
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The ambience's highlight color (SRS §4.11, DESIGN §4.4): found in the
 * wallpaper, or in the colors the system reports for it, and adjusted to
 * stay legible on the glass of each appearance.
 */
object HighlightColor {
    /**
     * The darkest backdrop the highlight sits on in Light: white glass at its
     * alpha (`0xAD`) over a pure black wallpaper. `DesignTokensTest` checks
     * that the adjusted highlight reaches 3:1 on every real backdrop token,
     * which keeps this in step with them.
     */
    val LIGHT_WORST_BACKDROP = Rgb(0xADADAD)

    /** The lightest backdrop the highlight sits on in Dark: black glass over a pure white wallpaper. */
    val DARK_WORST_BACKDROP = Rgb(0x525252)

    /**
     * Below this, a pixel counts as gray, however its hue reads. HSL
     * saturation is high for some nearly black or white pixels, which
     * [MIN_LIGHTNESS] and [MAX_LIGHTNESS] leave out instead.
     */
    private const val MIN_SATURATION = 0.2

    private const val MIN_LIGHTNESS = 0.12
    private const val MAX_LIGHTNESS = 0.92

    /**
     * The least score a color needs to stand for the image: as much as 2% of
     * it fully saturated, 8% at half saturation or about 22% at 30%. A speck
     * of color on a gray image isn't prominent.
     */
    private const val MIN_SCORE = 0.02

    private const val HUE_BUCKETS = 36
    private const val LIGHTNESS_BUCKETS = 8

    /**
     * The most prominent saturated color in [samples], `0xRRGGBB` pixels
     * sampled from the image (at most 128 × 128), or `null` when the image
     * has none, which means the default highlight (FR-AMB-01).
     *
     * Pixels that are nearly gray, black or white don't count. The rest are
     * bucketed by hue and lightness, and each bucket is scored by population
     * × saturation² together with its neighbors, so a color that straddles a
     * bucket's edge, such as red across 0°, isn't split in two. The best
     * bucket's mean color, weighted the same way, wins.
     */
    fun derive(samples: IntArray): Rgb? {
        if (samples.isEmpty()) return null
        val buckets = HUE_BUCKETS * LIGHTNESS_BUCKETS
        val weight = DoubleArray(buckets)
        val red = DoubleArray(buckets)
        val green = DoubleArray(buckets)
        val blue = DoubleArray(buckets)
        for (pixel in samples) {
            val hsl = Hsl.of(Rgb(pixel and 0xFFFFFF))
            if (!hsl.colorful) continue
            val hue = (hsl.hue / 360.0 * HUE_BUCKETS).toInt().coerceIn(0, HUE_BUCKETS - 1)
            val band = ((hsl.lightness - MIN_LIGHTNESS) / (MAX_LIGHTNESS - MIN_LIGHTNESS) * LIGHTNESS_BUCKETS)
                .toInt().coerceIn(0, LIGHTNESS_BUCKETS - 1)
            val bucket = hue * LIGHTNESS_BUCKETS + band
            val w = hsl.saturation * hsl.saturation
            weight[bucket] += w
            red[bucket] += w * ((pixel shr 16) and 0xFF)
            green[bucket] += w * ((pixel shr 8) and 0xFF)
            blue[bucket] += w * (pixel and 0xFF)
        }

        /** The buckets around [hue] and [band]: hue wraps around, lightness doesn't. */
        fun window(hue: Int, band: Int): List<Int> = buildList {
            for (dh in -1..1) for (db in -1..1) {
                val b = band + db
                if (b in 0 until LIGHTNESS_BUCKETS) add(Math.floorMod(hue + dh, HUE_BUCKETS) * LIGHTNESS_BUCKETS + b)
            }
        }

        var best: List<Int>? = null
        var bestWeight = 0.0
        for (hue in 0 until HUE_BUCKETS) for (band in 0 until LIGHTNESS_BUCKETS) {
            if (weight[hue * LIGHTNESS_BUCKETS + band] == 0.0) continue
            val around = window(hue, band)
            val total = around.sumOf { weight[it] }
            if (total > bestWeight) {
                bestWeight = total
                best = around
            }
        }
        val chosen = best ?: return null
        if (bestWeight / samples.size < MIN_SCORE) return null
        fun mean(channel: DoubleArray) = (chosen.sumOf { channel[it] } / bestWeight).roundToInt().coerceIn(0, 255)
        return rgb(mean(red), mean(green), mean(blue))
    }

    /**
     * The highlight from the colors the system reports for a wallpaper the
     * launcher didn't set, most prominent first: the first one that isn't
     * nearly gray, black or white, or `null` for the default (FR-AMB-03).
     */
    fun fromSystem(colors: List<Rgb>): Rgb? = colors.firstOrNull { Hsl.of(it).colorful }

    /**
     * [color] made legible as the highlight of [appearance] (FR-AMB-02,
     * NFR-A11Y-03): only its lightness changes, darker in Light and lighter
     * in Dark, just far enough to reach large-text contrast (3:1) against the
     * worst-case glass. That is enough because the highlight never colors
     * body text (FR-AMB-04). A color that already reaches it is kept.
     */
    fun adjust(color: Rgb, appearance: Appearance): Rgb {
        val backdrop = worstBackdrop(appearance)
        fun legible(c: Rgb) = Contrast.ratio(c, backdrop) >= Contrast.LARGE_TEXT
        if (legible(color)) return color
        val hsl = Hsl.of(color)
        fun at(lightness: Double) = hsl.copy(lightness = lightness).toRgb()
        // Each channel only grows with lightness, so contrast with the
        // backdrop changes one way along the search: in Light only black's
        // side passes, in Dark only white's.
        var fails = hsl.lightness
        var passes = if (appearance == Appearance.Light) 0.0 else 1.0
        repeat(SEARCH_STEPS) {
            val middle = (fails + passes) / 2
            if (legible(at(middle))) passes = middle else fails = middle
        }
        return at(passes)
    }

    /** The backdrop on which the highlight of [appearance] has the least contrast. */
    fun worstBackdrop(appearance: Appearance): Rgb = when (appearance) {
        Appearance.Light -> LIGHT_WORST_BACKDROP
        Appearance.Dark -> DARK_WORST_BACKDROP
    }

    /** More than enough for lightness to settle within one step of each 8-bit channel. */
    private const val SEARCH_STEPS = 24

    private fun rgb(red: Int, green: Int, blue: Int) = Rgb((red shl 16) or (green shl 8) or blue)

    /** A color as hue (0 until 360), saturation and lightness (0 to 1), as CSS defines HSL. */
    private data class Hsl(val hue: Double, val saturation: Double, val lightness: Double) {
        /** Neither nearly gray nor nearly black or white. */
        val colorful: Boolean
            get() = saturation >= MIN_SATURATION && lightness in MIN_LIGHTNESS..MAX_LIGHTNESS

        fun toRgb(): Rgb {
            val chroma = (1 - abs(2 * lightness - 1)) * saturation
            val h = hue / 60.0
            val x = chroma * (1 - abs(h % 2 - 1))
            val (r, g, b) = when (h.toInt()) {
                0 -> Triple(chroma, x, 0.0)
                1 -> Triple(x, chroma, 0.0)
                2 -> Triple(0.0, chroma, x)
                3 -> Triple(0.0, x, chroma)
                4 -> Triple(x, 0.0, chroma)
                else -> Triple(chroma, 0.0, x)
            }
            val m = lightness - chroma / 2
            fun channel(c: Double) = ((c + m) * 255).roundToInt().coerceIn(0, 255)
            return rgb(channel(r), channel(g), channel(b))
        }

        companion object {
            fun of(color: Rgb): Hsl {
                val r = ((color.value shr 16) and 0xFF) / 255.0
                val g = ((color.value shr 8) and 0xFF) / 255.0
                val b = (color.value and 0xFF) / 255.0
                val max = maxOf(r, g, b)
                val min = minOf(r, g, b)
                val chroma = max - min
                val lightness = (max + min) / 2
                if (chroma == 0.0) return Hsl(0.0, 0.0, lightness)
                val saturation = chroma / (1 - abs(2 * lightness - 1))
                val sector = when (max) {
                    r -> ((g - b) / chroma).mod(6.0)
                    g -> (b - r) / chroma + 2
                    else -> (r - g) / chroma + 4
                }
                return Hsl((sector * 60).mod(360.0), saturation.coerceAtMost(1.0), lightness)
            }
        }
    }
}

/**
 * The highlight color of an ambience for each appearance (FR-AMB-02), already
 * adjusted for contrast. Presentation shows the one for the appearance in use.
 */
data class Highlight(val light: Rgb, val dark: Rgb) {
    fun on(appearance: Appearance): Rgb = when (appearance) {
        Appearance.Light -> light
        Appearance.Dark -> dark
    }

    companion object {
        /** [source], such as a wallpaper's derived color or a preset, adjusted for both appearances. */
        fun of(source: Rgb): Highlight =
            Highlight(HighlightColor.adjust(source, Appearance.Light), HighlightColor.adjust(source, Appearance.Dark))
    }
}
