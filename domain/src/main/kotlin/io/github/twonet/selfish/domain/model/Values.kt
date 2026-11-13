package io.github.twonet.selfish.domain.model

import kotlinx.serialization.Serializable

/** An opaque sRGB color, `0xRRGGBB`. */
@JvmInline
value class Rgb(val value: Int)

/** A Bing market code such as `en-US` (FR-WP-03). */
@Serializable
@JvmInline
value class Market(val code: String)

/** A rectangle in screen pixels, for example the tapped icon a launch grows from. */
data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** A width and height in pixels, such as an image's or the screen's (FR-WP-04). */
@Serializable
data class PixelSize(val width: Int, val height: Int)
