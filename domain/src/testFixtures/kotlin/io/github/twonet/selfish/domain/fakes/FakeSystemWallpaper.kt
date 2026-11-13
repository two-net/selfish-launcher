package io.github.twonet.selfish.domain.fakes

import io.github.twonet.selfish.domain.model.Bounds
import io.github.twonet.selfish.domain.model.PixelSize
import io.github.twonet.selfish.domain.model.Rgb
import io.github.twonet.selfish.domain.ports.ImageInfo
import io.github.twonet.selfish.domain.ports.SystemWallpaper
import io.github.twonet.selfish.domain.ports.WallpaperId
import io.github.twonet.selfish.domain.ports.WallpaperScreens

/**
 * A system wallpaper that only knows the fake images of [image]: their
 * bytes say their size, and any other bytes are "not an image".
 */
class FakeSystemWallpaper : SystemWallpaper {
    var current: WallpaperId? = null
    var lockCurrent: WallpaperId? = null
    var colors: Rgb? = null
    var parallaxOffset: Float = 0f

    /** What [screenSize] reports. */
    var screen = PixelSize(1080, 2400)

    /** Whether [apply] fails, as when the system refuses the image. */
    var failApply = false

    /** One call to [apply]: the image's [label] (see [image]), the crop and the screens. */
    data class Applied(val label: String, val crop: Bounds, val screens: WallpaperScreens)

    val applied = mutableListOf<Applied>()
    private var nextId = 1

    override suspend fun inspect(bytes: ByteArray): ImageInfo? {
        val (_, size) = parse(bytes) ?: return null
        return ImageInfo(size.width, size.height, 1, 1, intArrayOf(0))
    }

    override suspend fun apply(bytes: ByteArray, crop: Bounds, screens: WallpaperScreens): WallpaperId {
        check(!failApply) { "The system refused the wallpaper" }
        val (label, _) = requireNotNull(parse(bytes)) { "Not an image" }
        applied += Applied(label, crop, screens)
        val id = WallpaperId(nextId++)
        if (screens.home) current = id
        if (screens.lock) lockCurrent = id
        return id
    }

    override suspend fun currentId(): WallpaperId? = current

    override suspend fun systemColors(): Rgb? = colors

    override fun screenSize(): PixelSize = screen

    override fun setParallax(offset: Float) {
        parallaxOffset = offset
    }

    companion object {
        private const val MAGIC = "FAKEIMG"

        /**
         * The bytes of a fake image named [label] of [width] × [height]
         * pixels, padded to [bytes] if that is longer.
         */
        fun image(label: String, width: Int = 1080, height: Int = 1920, bytes: Int = 0): ByteArray {
            val header = "$MAGIC:$label:${width}x$height:".encodeToByteArray()
            return if (header.size >= bytes) header else header + ByteArray(bytes - header.size)
        }

        private fun parse(bytes: ByteArray): Pair<String, PixelSize>? {
            val text = bytes.decodeToString().substringBefore('\u0000')
            val parts = text.split(':')
            if (parts.size < 3 || parts[0] != MAGIC) return null
            val dims = parts[2].split('x').mapNotNull { it.toIntOrNull() }
            if (dims.size != 2 || dims.any { it <= 0 }) return null
            return parts[1] to PixelSize(dims[0], dims[1])
        }
    }
}
