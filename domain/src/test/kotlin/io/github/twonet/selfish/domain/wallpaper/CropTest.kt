package io.github.twonet.selfish.domain.wallpaper

import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.Bounds
import io.github.twonet.selfish.domain.model.PixelSize
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Fitting an image to the screen (FR-WP-04, DESIGN §4.3). */
@Verifies("FR-WP-04")
class CropTest {
    private val phone = PixelSize(1080, 2400)

    @Test
    fun `a landscape image is cut to the screen's shape around its center`() {
        val crop = Crop.center(PixelSize(1920, 1080), phone)

        assertEquals(Bounds(717, 0, 1203, 1080), crop)
    }

    @Test
    fun `a portrait image wider than the screen's shape loses its sides`() {
        val crop = Crop.center(PixelSize(1080, 1920), phone)

        assertEquals(Bounds(108, 0, 972, 1920), crop)
    }

    @Test
    fun `an image narrower than the screen's shape loses its top and bottom`() {
        val crop = Crop.center(PixelSize(1080, 2700), phone)

        assertEquals(Bounds(0, 150, 1080, 2550), crop)
    }

    @Test
    fun `an image of the screen's shape is kept whole, whatever its size`() {
        assertEquals(Bounds(0, 0, 540, 1200), Crop.center(PixelSize(540, 1200), phone))
        assertEquals(Bounds(0, 0, 2160, 4800), Crop.center(PixelSize(2160, 4800), phone))
    }

    @Test
    fun `the crop always has the screen's aspect ratio, so nothing is stretched`() {
        val images = listOf(PixelSize(1920, 1080), PixelSize(1080, 1920), PixelSize(3840, 2160), PixelSize(777, 1333), PixelSize(1, 1))
        val screens = listOf(phone, PixelSize(1440, 3200), PixelSize(720, 1280), PixelSize(1080, 1920))
        for (image in images) for (screen in screens) {
            val crop = Crop.center(image, screen)
            val width = crop.right - crop.left
            val height = crop.bottom - crop.top
            assertTrue(crop.left >= 0 && crop.top >= 0 && crop.right <= image.width && crop.bottom <= image.height, "$crop inside $image")
            assertTrue(width == image.width || height == image.height, "$crop is as large as it can be in $image")
            // The screen's shape, give or take rounding to a whole pixel of width.
            val expected = width.toDouble() * screen.height / screen.width
            assertTrue(abs(height - expected) <= maxOf(1.0, screen.height.toDouble() / screen.width), "$crop has the shape of $screen")
            // Centered, to the pixel.
            assertTrue(abs(crop.left - (image.width - crop.right)) <= 1 && abs(crop.top - (image.height - crop.bottom)) <= 1, "$crop centered in $image")
        }
    }
}
