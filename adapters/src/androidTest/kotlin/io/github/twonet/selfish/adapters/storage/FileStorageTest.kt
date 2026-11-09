package io.github.twonet.selfish.adapters.storage

import android.content.Context
import android.util.AtomicFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.ports.DocName
import io.github.twonet.selfish.domain.ports.ImageId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Verifies("FR-GEN-07", "NFR-REL-03")
class FileStorageTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val storage = FileStorage(context)

    private val layoutFile = File(context.filesDir, FileStorage.LAYOUT_FILE)
    private val settingsFile = File(context.filesDir, FileStorage.SETTINGS_FILE)
    private val wallpaperDir = File(context.noBackupFilesDir, FileStorage.WALLPAPER_DIR)
    private val imagesDir = File(wallpaperDir, FileStorage.IMAGES_DIR)

    @Before
    @After
    fun clean() {
        for (file in listOf(layoutFile, settingsFile)) AtomicFile(file).delete()
        wallpaperDir.deleteRecursively()
    }

    @Test
    fun nothingWrittenReadsAsNull() = runBlocking {
        for (doc in DocName.entries) assertNull(storage.read(doc), "$doc")
        assertNull(storage.readImage(ImageId("missing")))
        assertEquals(emptySet(), storage.imageIds())
    }

    @Test
    fun documentsRoundTripAndAreReplaced() = runBlocking {
        storage.write(DocName.Settings, """{"schemaVersion":1}""")
        storage.write(DocName.Settings, """{"schemaVersion":1,"gridColumns":5}""")

        assertEquals("""{"schemaVersion":1,"gridColumns":5}""", storage.read(DocName.Settings))
    }

    @Test
    fun layoutAndSettingsAreBackedUpAndTheWallpaperCacheIsNot() = runBlocking {
        for (doc in DocName.entries) storage.write(doc, "{}")
        storage.writeImage(ImageId("20261109_en-US"), byteArrayOf(1, 2, 3))

        assertTrue(layoutFile.isFile)
        assertTrue(settingsFile.isFile)
        assertTrue(File(wallpaperDir, FileStorage.WALLPAPER_INDEX_FILE).isFile)
        assertTrue(File(imagesDir, "20261109_en-US${FileStorage.IMAGE_SUFFIX}").isFile)
        assertFalse(File(context.filesDir, FileStorage.WALLPAPER_INDEX_FILE).exists())
    }

    @Test
    fun aWriteCutShortKeepsTheOldContent() = runBlocking {
        storage.write(DocName.Layout, "old")

        // Start a write the way FileStorage does and stop before finishing,
        // as a crash would.
        val atomic = AtomicFile(layoutFile)
        atomic.startWrite().use { it.write("ne".encodeToByteArray()) }

        assertEquals("old", storage.read(DocName.Layout))
        storage.write(DocName.Layout, "new")
        assertEquals("new", storage.read(DocName.Layout))
    }

    @Test
    fun imagesRoundTripAndOnlyFinishedOnesAreListed() = runBlocking {
        storage.writeImage(ImageId("a"), byteArrayOf(1, 2, 3))
        storage.writeImage(ImageId("b"), byteArrayOf(4))
        File(imagesDir, "c${FileStorage.IMAGE_SUFFIX}.new").writeBytes(byteArrayOf(5))
        File(imagesDir, "d${FileStorage.IMAGE_SUFFIX}.bak").writeBytes(byteArrayOf(6))

        assertContentEquals(byteArrayOf(1, 2, 3), storage.readImage(ImageId("a")))
        assertEquals(setOf(ImageId("a"), ImageId("b")), storage.imageIds())

        storage.deleteImage(ImageId("b"))
        assertEquals(setOf(ImageId("a")), storage.imageIds())
    }

    @Test
    fun clearingTheWallpaperCacheRemovesImagesAndIndexOnly() = runBlocking {
        storage.write(DocName.Layout, "layout")
        storage.write(DocName.WallpaperIndex, "index")
        storage.writeImage(ImageId("a"), byteArrayOf(1))

        storage.clearWallpaperCache()

        assertFalse(wallpaperDir.exists())
        assertNull(storage.read(DocName.WallpaperIndex))
        assertEquals(emptySet(), storage.imageIds())
        assertEquals("layout", storage.read(DocName.Layout))
    }

    @Test
    fun imageIdsThatNameOtherPathsAreRejected() = runBlocking {
        for (id in listOf("../layout", "a/b", "", ".", "..", "a.jpg")) {
            assertFailsWith<IllegalArgumentException>(id) { storage.writeImage(ImageId(id), byteArrayOf(1)) }
        }
        assertFalse(File(context.filesDir, "layout${FileStorage.IMAGE_SUFFIX}").exists())
    }
}
