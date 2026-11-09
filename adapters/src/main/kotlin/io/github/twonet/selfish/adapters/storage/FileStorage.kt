package io.github.twonet.selfish.adapters.storage

import android.content.Context
import android.util.AtomicFile
import io.github.twonet.selfish.domain.ports.DocName
import io.github.twonet.selfish.domain.ports.ImageId
import io.github.twonet.selfish.domain.ports.LocalStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

/**
 * [LocalStorage] over files private to the app (DESIGN §5.3, NFR-PRIV-03).
 *
 * - Layout and settings live in `filesDir`, which backup and device transfer
 *   include. The backup rules in `:app` must list exactly [BACKED_UP_FILES].
 * - The wallpaper cache, images and index, lives in
 *   `noBackupFilesDir/wallpapers/`. It is never backed up and, unlike
 *   `cacheDir`, never cleared by the system (SRS §8, NFR-REL-02).
 * - Every file is replaced through [AtomicFile], so a crash never leaves a
 *   half-written file where a good one was (NFR-REL-03). Before API 30 a
 *   *first* write cut short can leave a partial file; readers already treat
 *   that like any corrupt file.
 *
 * Main-safe: all I/O runs on [Dispatchers.IO]. One lock covers reads and
 * writes, because before API 30 reading an [AtomicFile] during a write
 * restores its backup and the write is lost.
 */
class FileStorage(context: Context) : LocalStorage {
    private val context = context.applicationContext
    private val lock = Mutex()

    override suspend fun read(doc: DocName): String? = io { readFully(docFile(doc))?.decodeToString() }

    override suspend fun write(doc: DocName, content: String) = io { replace(docFile(doc), content.encodeToByteArray()) }

    override suspend fun writeImage(id: ImageId, bytes: ByteArray) = io { replace(imageFile(id), bytes) }

    override suspend fun readImage(id: ImageId): ByteArray? = io { readFully(imageFile(id)) }

    override suspend fun deleteImage(id: ImageId) = io { AtomicFile(imageFile(id)).delete() }

    override suspend fun imageIds(): Set<ImageId> = io {
        // Only finished images: AtomicFile's temporary and backup files have other suffixes.
        imagesDir().listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(IMAGE_SUFFIX) }
            .map { ImageId(it.name.removeSuffix(IMAGE_SUFFIX)) }
            .toSet()
    }

    override suspend fun clearWallpaperCache() {
        io { wallpaperDir().deleteRecursively() }
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { lock.withLock { block() } }

    private fun docFile(doc: DocName): File = when (doc) {
        DocName.Layout -> File(context.filesDir, LAYOUT_FILE)
        DocName.Settings -> File(context.filesDir, SETTINGS_FILE)
        DocName.WallpaperIndex -> File(wallpaperDir(), WALLPAPER_INDEX_FILE)
    }

    private fun wallpaperDir() = File(context.noBackupFilesDir, WALLPAPER_DIR)

    private fun imagesDir() = File(wallpaperDir(), IMAGES_DIR)

    private fun imageFile(id: ImageId): File {
        // Image IDs derive from feed data; never let one name another path.
        require(SAFE_ID.matches(id.value)) { "Unsafe image ID: ${id.value}" }
        return File(imagesDir(), id.value + IMAGE_SUFFIX)
    }

    private fun readFully(file: File): ByteArray? = try {
        AtomicFile(file).readFully()
    } catch (_: FileNotFoundException) {
        null
    }

    private fun replace(file: File, bytes: ByteArray) {
        file.parentFile?.mkdirs()
        val atomic = AtomicFile(file)
        val out = atomic.startWrite()
        try {
            out.write(bytes)
            atomic.finishWrite(out)
        } catch (e: Throwable) {
            atomic.failWrite(out)
            throw e
        }
    }

    companion object {
        const val LAYOUT_FILE = "layout.json"
        const val SETTINGS_FILE = "settings.json"

        /** The files backup and device transfer include, all in `filesDir` (SRS §8). */
        val BACKED_UP_FILES: Set<String> = setOf(LAYOUT_FILE, SETTINGS_FILE)

        /** Under `noBackupFilesDir`. */
        const val WALLPAPER_DIR = "wallpapers"
        const val WALLPAPER_INDEX_FILE = "wallpapers.json"
        const val IMAGES_DIR = "images"
        const val IMAGE_SUFFIX = ".jpg"

        private val SAFE_ID = Regex("[A-Za-z0-9_-]+")
    }
}
