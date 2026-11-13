package io.github.twonet.selfish.ui.components

import android.graphics.BitmapFactory
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.twonet.selfish.domain.ports.ImageId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cached wallpaper images as small bitmaps, such as the thumbnail on the
 * Events card (FR-EVT-03). [read] is the store's query for an image's bytes
 * (DESIGN §3.3); they are decoded here, off the main thread, at the smallest
 * size that still covers the thumbnail.
 */
class WallpaperImages(private val read: suspend (ImageId) -> ByteArray?) {
    /** [image] at least [widthPx] × [heightPx], or `null` if it isn't cached or can't be decoded. */
    suspend fun thumbnail(image: ImageId, widthPx: Int, heightPx: Int): ImageBitmap? {
        val bytes = read(image) ?: return null
        return withContext(Dispatchers.Default) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= widthPx && bounds.outHeight / (sample * 2) >= heightPx) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()?.also { it.prepareToDraw() }
        }
    }
}

/** The launcher's [WallpaperImages]; `null` in previews and tests, where thumbnails show their placeholder. */
val LocalWallpaperImages = staticCompositionLocalOf<WallpaperImages?> { null }
