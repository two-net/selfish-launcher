package io.github.twonet.selfish.adapters.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Point
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.view.Display
import io.github.twonet.selfish.domain.model.Bounds
import io.github.twonet.selfish.domain.model.PixelSize
import io.github.twonet.selfish.domain.model.Rgb
import io.github.twonet.selfish.domain.ports.ImageInfo
import io.github.twonet.selfish.domain.ports.SystemWallpaper
import io.github.twonet.selfish.domain.ports.WallpaperId
import io.github.twonet.selfish.domain.ports.WallpaperScreens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * [SystemWallpaper] over `WallpaperManager` and `ImageDecoder` (DESIGN §5.1).
 *
 * - **Checking** decodes the whole image, so a truncated or corrupt file is
 *   rejected before it is ever stored (FR-WP-14), and samples it at most
 *   128 pixels across for the highlight color (#16).
 * - **Applying** decodes only the crop rectangle, which already has the
 *   screen's shape, so the system's own crop hints never matter (FR-WP-04,
 *   R-5), and sets it on the home screen, and the lock screen too when asked
 *   (FR-WP-05). Setting the home screen alone leaves the lock screen as it
 *   was.
 *
 * Main-safe: decoding runs on [Dispatchers.Default], the system call on
 * [Dispatchers.IO] (NFR-PERF-06). The system's colors (FR-AMB-03) arrive with
 * #16, and parallax (FR-ANIM-14) is deferred.
 */
class AndroidSystemWallpaper(context: Context) : SystemWallpaper {
    private val appContext = context.applicationContext
    private val manager = WallpaperManager.getInstance(appContext)

    override suspend fun inspect(bytes: ByteArray): ImageInfo? = withContext(Dispatchers.Default) {
        try {
            var size = PixelSize(0, 0)
            val sample = decode(bytes) { info ->
                size = PixelSize(info.size.width, info.size.height)
                val scale = minOf(1f, SAMPLE_MAX.toFloat() / max(size.width, size.height))
                setTargetSize(max(1, (size.width * scale).roundToInt()), max(1, (size.height * scale).roundToInt()))
            }
            try {
                val samples = IntArray(sample.width * sample.height)
                sample.getPixels(samples, 0, sample.width, 0, 0, sample.width, sample.height)
                for (i in samples.indices) samples[i] = samples[i] and 0xFFFFFF
                ImageInfo(size.width, size.height, sample.width, sample.height, samples)
            } finally {
                sample.recycle()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Not an image, a truncated one, or one the platform can't decode.
            null
        }
    }

    override suspend fun apply(bytes: ByteArray, crop: Bounds, screens: WallpaperScreens): WallpaperId {
        val bitmap = withContext(Dispatchers.Default) {
            decode(bytes) { info ->
                val rect = Rect(crop.left, crop.top, crop.right, crop.bottom)
                require(Rect(0, 0, info.size.width, info.size.height).contains(rect)) { "Crop $crop is outside the image" }
                setCrop(rect)
            }
        }
        try {
            val which = (if (screens.home) WallpaperManager.FLAG_SYSTEM else 0) or (if (screens.lock) WallpaperManager.FLAG_LOCK else 0)
            val id = withContext(Dispatchers.IO) { manager.setBitmap(bitmap, null, false, which) }
            if (id <= 0) throw IOException("The system didn't set the wallpaper")
            return WallpaperId(id)
        } finally {
            bitmap.recycle()
        }
    }

    override suspend fun currentId(): WallpaperId? = withContext(Dispatchers.IO) {
        manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM).takeIf { it > 0 }?.let(::WallpaperId)
    }

    override suspend fun systemColors(): Rgb? = null

    /**
     * The default display's full size, with the system bars and cutouts, as
     * the wallpaper covers it, in portrait (FR-GEN-09).
     */
    override fun screenSize(): PixelSize {
        val display = appContext.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val size = Point()
        // WindowMetrics needs a visual context, which the background job doesn't have.
        @Suppress("DEPRECATION")
        display.getRealSize(size)
        return PixelSize(minOf(size.x, size.y), max(size.x, size.y))
    }

    override fun setParallax(offset: Float) = Unit

    /**
     * Decodes [bytes] into a software bitmap, configured by [configure]. A
     * partial image fails instead of being filled in (FR-WP-14).
     */
    private fun decode(bytes: ByteArray, configure: ImageDecoder.(ImageDecoder.ImageInfo) -> Unit): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setOnPartialImageListener { false }
            decoder.configure(info)
        }

    private companion object {
        /** The samples are at most this many pixels across (DESIGN §4.4). */
        const val SAMPLE_MAX = 128
    }
}
