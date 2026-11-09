package io.github.twonet.selfish.domain.ports

import io.github.twonet.selfish.domain.model.Bounds
import io.github.twonet.selfish.domain.model.Rgb

/**
 * The system's home and lock screen wallpaper (SRS §5.2: setting the wallpaper;
 * wallpaper colors reported by the system).
 */
interface SystemWallpaper {
    /**
     * Decodes the cached image [image] to check it is a valid picture and to
     * sample its pixels, or returns `null` if it is not a usable image
     * (FR-WP-14, FR-AMB-01).
     */
    suspend fun inspect(image: ImageId): ImageInfo?

    /**
     * Sets [image], cut to [crop], as the home screen wallpaper, and also on the
     * lock screen when [lockScreen] is set (FR-WP-01, FR-WP-04, FR-WP-05).
     * Returns the system's ID for the new wallpaper.
     */
    suspend fun apply(image: ImageId, crop: Bounds, lockScreen: Boolean): WallpaperId

    /** The ID of the current home screen wallpaper, to detect one set elsewhere (FR-AMB-03). */
    suspend fun currentId(): WallpaperId?

    /** The primary color the system reports for the current wallpaper, if any (FR-AMB-03). */
    suspend fun systemColors(): Rgb?

    /** Moves the wallpaper horizontally, 0 to 1, for parallax (FR-ANIM-14; optional). */
    fun setParallax(offset: Float)
}

@JvmInline
value class WallpaperId(val value: Int)

/**
 * A decoded image: its size, and its pixels downsampled to at most
 * 128 × 128 as `0xRRGGBB` values, row by row.
 */
class ImageInfo(
    val width: Int,
    val height: Int,
    val sampleWidth: Int,
    val sampleHeight: Int,
    val samples: IntArray,
)
