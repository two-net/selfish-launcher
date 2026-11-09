package io.github.twonet.selfish.domain.fakes

import io.github.twonet.selfish.domain.model.Bounds
import io.github.twonet.selfish.domain.model.Rgb
import io.github.twonet.selfish.domain.ports.ImageId
import io.github.twonet.selfish.domain.ports.ImageInfo
import io.github.twonet.selfish.domain.ports.SystemWallpaper
import io.github.twonet.selfish.domain.ports.WallpaperId

class FakeSystemWallpaper : SystemWallpaper {
    /** What [inspect] returns per image; a missing entry means "not a valid image". */
    val decodable = mutableMapOf<ImageId, ImageInfo>()
    var current: WallpaperId? = null
    var colors: Rgb? = null
    var parallaxOffset: Float = 0f

    data class Applied(val image: ImageId, val crop: Bounds, val lockScreen: Boolean)

    val applied = mutableListOf<Applied>()
    private var nextId = 1

    override suspend fun inspect(image: ImageId): ImageInfo? = decodable[image]

    override suspend fun apply(image: ImageId, crop: Bounds, lockScreen: Boolean): WallpaperId {
        applied += Applied(image, crop, lockScreen)
        return WallpaperId(nextId++).also { current = it }
    }

    override suspend fun currentId(): WallpaperId? = current

    override suspend fun systemColors(): Rgb? = colors

    override fun setParallax(offset: Float) {
        parallaxOffset = offset
    }
}
