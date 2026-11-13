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
 * bytes say their size and color, and any other bytes are "not an image".
 */
class FakeSystemWallpaper : SystemWallpaper {
    var current: WallpaperId? = null
    var lockCurrent: WallpaperId? = null
    /** What [systemColors] reports, most prominent first. */
    var colors: List<Rgb> = emptyList()
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
        val image = parse(bytes) ?: return null
        val pixel = image.color?.value ?: 0
        return ImageInfo(image.size.width, image.size.height, SAMPLE_SIDE, SAMPLE_SIDE, IntArray(SAMPLE_SIDE * SAMPLE_SIDE) { pixel })
    }

    override suspend fun apply(bytes: ByteArray, crop: Bounds, screens: WallpaperScreens): WallpaperId {
        check(!failApply) { "The system refused the wallpaper" }
        val image = requireNotNull(parse(bytes)) { "Not an image" }
        applied += Applied(image.label, crop, screens)
        val id = WallpaperId(nextId++)
        if (screens.home) current = id
        if (screens.lock) lockCurrent = id
        return id
    }

    override suspend fun currentId(): WallpaperId? = current

    override suspend fun systemColors(): List<Rgb> = colors

    override fun screenSize(): PixelSize = screen

    override fun setParallax(offset: Float) {
        parallaxOffset = offset
    }

    companion object {
        private const val MAGIC = "FAKEIMG"

        /** The samples [inspect] returns are this many pixels across. */
        private const val SAMPLE_SIDE = 4

        /**
         * The bytes of a fake image named [label] of [width] × [height]
         * pixels, all of [color] (black without one), padded to [bytes] if
         * that is longer.
         */
        fun image(label: String, width: Int = 1080, height: Int = 1920, bytes: Int = 0, color: Rgb? = null): ByteArray {
            val header = "$MAGIC:$label:${width}x$height:${color?.value?.toString(16).orEmpty()}:".encodeToByteArray()
            return if (header.size >= bytes) header else header + ByteArray(bytes - header.size)
        }

        private class Parsed(val label: String, val size: PixelSize, val color: Rgb?)

        private fun parse(bytes: ByteArray): Parsed? {
            val text = bytes.decodeToString().substringBefore('\u0000')
            val parts = text.split(':')
            if (parts.size < 3 || parts[0] != MAGIC) return null
            val dims = parts[2].split('x').mapNotNull { it.toIntOrNull() }
            if (dims.size != 2 || dims.any { it <= 0 }) return null
            val color = parts.getOrNull(3)?.toIntOrNull(16)?.let(::Rgb)
            return Parsed(parts[1], PixelSize(dims[0], dims[1]), color)
        }
    }
}
