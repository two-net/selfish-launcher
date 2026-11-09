package io.github.twonet.selfish.adapters.wallpaper

import android.content.Context
import io.github.twonet.selfish.domain.model.Bounds
import io.github.twonet.selfish.domain.model.Rgb
import io.github.twonet.selfish.domain.ports.ImageId
import io.github.twonet.selfish.domain.ports.ImageInfo
import io.github.twonet.selfish.domain.ports.SystemWallpaper
import io.github.twonet.selfish.domain.ports.WallpaperId

/**
 * [SystemWallpaper] over `WallpaperManager` and `ImageDecoder` (DESIGN §5.1).
 * Stub until #14 and #16: the system wallpaper is left untouched (FR-WP-12).
 */
@Suppress("unused")
class AndroidSystemWallpaper(private val context: Context) : SystemWallpaper {
    override suspend fun inspect(image: ImageId): ImageInfo? = null

    override suspend fun apply(image: ImageId, crop: Bounds, lockScreen: Boolean): WallpaperId =
        throw UnsupportedOperationException("Applying wallpapers arrives with #14")

    override suspend fun currentId(): WallpaperId? = null

    override suspend fun systemColors(): Rgb? = null

    override fun setParallax(offset: Float) = Unit
}
