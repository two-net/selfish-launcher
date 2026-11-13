package io.github.twonet.selfish.domain.wallpaper

import io.github.twonet.selfish.domain.model.Bounds
import io.github.twonet.selfish.domain.model.PixelSize

/** Fits an image to the screen's shape (FR-WP-04, DESIGN §4.3). */
object Crop {
    /**
     * The largest rectangle of [image] with [screen]'s aspect ratio, centered,
     * in the image's pixels. The image is only ever cut, never stretched: the
     * system scales the rectangle uniformly to the screen.
     */
    fun center(image: PixelSize, screen: PixelSize): Bounds {
        require(image.width > 0 && image.height > 0) { "image must not be empty, was $image" }
        require(screen.width > 0 && screen.height > 0) { "screen must not be empty, was $screen" }
        val imageIsWider = image.width.toLong() * screen.height > screen.width.toLong() * image.height
        return if (imageIsWider) {
            val width = rounded(image.height.toLong() * screen.width, screen.height.toLong()).coerceIn(1, image.width)
            val left = (image.width - width) / 2
            Bounds(left, 0, left + width, image.height)
        } else {
            val height = rounded(image.width.toLong() * screen.height, screen.width.toLong()).coerceIn(1, image.height)
            val top = (image.height - height) / 2
            Bounds(0, top, image.width, top + height)
        }
    }

    private fun rounded(numerator: Long, denominator: Long): Int = ((numerator + denominator / 2) / denominator).toInt()
}
