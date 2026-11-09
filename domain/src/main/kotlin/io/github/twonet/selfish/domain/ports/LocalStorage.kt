package io.github.twonet.selfish.domain.ports

/**
 * Private on-device storage (SRS §8). Documents are small JSON texts;
 * wallpaper images are kept apart, in storage that is never backed up.
 */
interface LocalStorage {
    /** The saved text of [doc], or `null` if it was never written (FR-GEN-07). */
    suspend fun read(doc: DocName): String?

    /** Replaces [doc] atomically: a crash never leaves a half-written file (NFR-REL-03). */
    suspend fun write(doc: DocName, content: String)

    /** Stores one cached wallpaper image (FR-WP-07). */
    suspend fun writeImage(id: ImageId, bytes: ByteArray)

    suspend fun readImage(id: ImageId): ByteArray?

    suspend fun deleteImage(id: ImageId)

    suspend fun imageIds(): Set<ImageId>

    /** Deletes the whole wallpaper cache, for example when Daily is turned off (FR-WP-13). */
    suspend fun clearImages()
}

enum class DocName {
    /** Grid, folders and Home covers. Backed up. */
    Layout,

    /** The values of SRS §4.13. Backed up. */
    Settings,

    /** The wallpaper cache index. Not backed up. */
    WallpaperIndex,
}

/** Names one cached wallpaper image. */
@JvmInline
value class ImageId(val value: String)
