package io.github.twonet.selfish.domain.ports

import io.github.twonet.selfish.domain.model.Bounds
import io.github.twonet.selfish.domain.model.PixelSize
import io.github.twonet.selfish.domain.model.Rgb
import kotlinx.serialization.Serializable

/**
 * The system's home and lock screen wallpaper (SRS §5.2: setting the wallpaper;
 * wallpaper colors reported by the system).
 *
 * Images are passed as their encoded bytes, as downloaded or as cached, so
 * an image is checked before it is ever written (FR-WP-14).
 */
interface SystemWallpaper {
    /**
     * Decodes [bytes] in full, to check they are a complete, valid picture and
     * to sample its pixels, or returns `null` if they aren't (FR-WP-14,
     * FR-AMB-01).
     */
    suspend fun inspect(bytes: ByteArray): ImageInfo?

    /**
     * Sets the image [bytes], cut to [crop] in the image's pixels, as the
     * wallpaper of [screens] (FR-WP-01, FR-WP-04, FR-WP-05). Returns the
     * system's ID for the new wallpaper: the home screen's, or the lock
     * screen's for [WallpaperScreens.Lock].
     */
    suspend fun apply(bytes: ByteArray, crop: Bounds, screens: WallpaperScreens): WallpaperId

    /** The ID of the current home screen wallpaper, to detect one set elsewhere (FR-AMB-03). */
    suspend fun currentId(): WallpaperId?

    /** The primary color the system reports for the current wallpaper, if any (FR-AMB-03). */
    suspend fun systemColors(): Rgb?

    /** The screen's full size in pixels, in portrait, which a wallpaper is cropped to (FR-WP-04). */
    fun screenSize(): PixelSize

    /** Moves the wallpaper horizontally, 0 to 1, for parallax (FR-ANIM-14; optional). */
    fun setParallax(offset: Float)
}

@Serializable
@JvmInline
value class WallpaperId(val value: Int)

/** Which screens a wallpaper is set on (FR-WP-05). */
enum class WallpaperScreens(val home: Boolean, val lock: Boolean) {
    Home(home = true, lock = false),
    HomeAndLock(home = true, lock = true),
    Lock(home = false, lock = true),
}

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
