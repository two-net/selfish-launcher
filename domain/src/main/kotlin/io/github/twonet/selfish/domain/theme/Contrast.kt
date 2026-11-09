package io.github.twonet.selfish.domain.theme

import io.github.twonet.selfish.domain.model.Rgb
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * WCAG 2.1 contrast (NFR-A11Y-03, DESIGN §4.4). Presentation's token tests
 * check the glass against it (DESIGN §6.5), and the highlight color is
 * adjusted with it (FR-AMB-02).
 */
object Contrast {
    /** Body text needs this much contrast (WCAG 2.1 AA). */
    const val BODY_TEXT = 4.5

    /** Large text and UI components need this much contrast (WCAG 2.1 AA). */
    const val LARGE_TEXT = 3.0

    /** The relative luminance of [color], from 0 (black) to 1 (white). */
    fun luminance(color: Rgb): Double =
        0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)

    /** The contrast ratio between [a] and [b], from 1 to 21, in either order. */
    fun ratio(a: Rgb, b: Rgb): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /**
     * [top] drawn with [alpha] over the opaque [bottom]. Blends the
     * gamma-encoded sRGB values, as Android's renderer does.
     */
    fun composite(top: Rgb, alpha: Float, bottom: Rgb): Rgb {
        require(alpha in 0f..1f) { "alpha must be in 0..1, was $alpha" }
        fun blend(t: Int, b: Int) = (alpha * t + (1 - alpha) * b).roundToInt()
        return rgb(blend(top.red, bottom.red), blend(top.green, bottom.green), blend(top.blue, bottom.blue))
    }

    /** One sRGB channel, 0 to 255, in linear light. The threshold is the sRGB standard's. */
    private fun linear(channel: Int): Double {
        val c = channel / 255.0
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun rgb(red: Int, green: Int, blue: Int) = Rgb((red shl 16) or (green shl 8) or blue)

    private val Rgb.red get() = (value shr 16) and 0xFF
    private val Rgb.green get() = (value shr 8) and 0xFF
    private val Rgb.blue get() = value and 0xFF
}
