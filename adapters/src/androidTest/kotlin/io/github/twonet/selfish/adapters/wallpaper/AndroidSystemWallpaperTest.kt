package io.github.twonet.selfish.adapters.wallpaper

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.Bounds
import io.github.twonet.selfish.domain.model.Rgb
import io.github.twonet.selfish.domain.ports.WallpaperScreens
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [AndroidSystemWallpaper] against the platform's decoder and wallpaper
 * service (level A, DESIGN §8.3). Images are made in the test, so there are
 * no binary fixtures.
 *
 * The tests that set a wallpaper change the device's wallpaper for good:
 * they run on an emulator, and on a phone only when asked for with
 * `-e changeWallpaper true`.
 */
@RunWith(AndroidJUnit4::class)
class AndroidSystemWallpaperTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val wallpaper = AndroidSystemWallpaper(context)
    private val manager = WallpaperManager.getInstance(context)

    private fun jpeg(width: Int, height: Int, color: Int = Color.rgb(200, 40, 30)): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    private fun assumeChangingTheWallpaperIsAllowed() {
        val emulator = Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish"
        val asked = InstrumentationRegistry.getArguments().getString("changeWallpaper") == "true"
        assumeTrue("Sets the device's wallpaper: runs on an emulator, or with -e changeWallpaper true", emulator || asked)
    }

    @Test
    @Verifies("FR-WP-14", "FR-WP-04")
    fun aValidImageIsMeasuredAndSampled() = runBlocking {
        val info = assertNotNull(wallpaper.inspect(jpeg(1920, 1080)))

        assertEquals(1920, info.width)
        assertEquals(1080, info.height)
        assertEquals(128, info.sampleWidth)
        assertEquals(72, info.sampleHeight)
        assertEquals(128 * 72, info.samples.size)
        val middle = info.samples[info.samples.size / 2]
        assertTrue(Color.red(middle) > 150 && Color.green(middle) < 90, "sampled color ${Integer.toHexString(middle)}")
        assertTrue(info.samples.all { it ushr 24 == 0 }, "samples are 0xRRGGBB")
    }

    @Test
    @Verifies("FR-WP-14")
    fun truncatedAndNonImageBytesAreRejected() = runBlocking {
        val image = jpeg(1080, 1920)

        assertNull(wallpaper.inspect(image.copyOf(image.size / 2)))
        assertNull(wallpaper.inspect("<html>Not found</html>".encodeToByteArray()))
        assertNull(wallpaper.inspect(ByteArray(0)))
    }

    @Test
    @Verifies("FR-WP-04")
    fun theScreenSizeIsTheWholeDisplayInPortrait() {
        val size = wallpaper.screenSize()
        val real = Point()
        @Suppress("DEPRECATION")
        context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY).getRealSize(real)

        assertTrue(size.height >= size.width)
        assertEquals(setOf(real.x, real.y), setOf(size.width, size.height))
    }

    @Test
    @Verifies("FR-WP-04")
    fun aCropOutsideTheImageIsRefusedBeforeAnythingIsSet() {
        assertFailsWith<IllegalArgumentException> {
            runBlocking { wallpaper.apply(jpeg(1080, 1920), Bounds(0, 0, 1081, 1920), WallpaperScreens.Home) }
        }
    }

    @Test
    @Verifies("FR-WP-01", "FR-WP-04")
    fun applyingSetsTheHomeScreenAndReportsItsId() = runBlocking {
        assumeChangingTheWallpaperIsAllowed()

        val id = wallpaper.apply(jpeg(1920, 1080), Bounds(717, 0, 1203, 1080), WallpaperScreens.Home)

        assertEquals(id.value, manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM))
        assertEquals(id, wallpaper.currentId())
        assertNotEquals(id.value, manager.getWallpaperId(WallpaperManager.FLAG_LOCK), "the lock screen is left alone")
    }

    @Test
    @Verifies("FR-WP-05")
    fun theLockScreenCanBeSetTooOrAlone() = runBlocking {
        assumeChangingTheWallpaperIsAllowed()

        val both = wallpaper.apply(jpeg(1080, 1920), Bounds(108, 0, 972, 1920), WallpaperScreens.HomeAndLock)
        assertEquals(both.value, manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM))
        val lock = manager.getWallpaperId(WallpaperManager.FLAG_LOCK)
        // The lock screen either shows the home screen's wallpaper or has the same one.
        assertTrue(lock < 0 || lock == both.value, "lock id $lock, home id ${both.value}")

        val lockOnly = wallpaper.apply(jpeg(1080, 1920, Color.BLUE), Bounds(108, 0, 972, 1920), WallpaperScreens.Lock)
        assertEquals(lockOnly.value, manager.getWallpaperId(WallpaperManager.FLAG_LOCK))
        assertEquals(both.value, manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM))
    }

    @Test
    @Verifies("FR-AMB-03")
    fun aWallpaperSetElsewhereHasAnotherIdAndTheSystemReportsItsColors() = runBlocking {
        assumeChangingTheWallpaperIsAllowed()
        val ours = wallpaper.apply(jpeg(1080, 1920), Bounds(108, 0, 972, 1920), WallpaperScreens.Home)

        // Another app sets a blue wallpaper.
        val blue = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(30, 60, 220)) }
        manager.setBitmap(blue, null, false, WallpaperManager.FLAG_SYSTEM)
        blue.recycle()

        assertNotEquals(ours, wallpaper.currentId())
        // The system works out a new wallpaper's colors in the background.
        val reported = withTimeoutOrNull(COLORS_TIMEOUT_MS) {
            var colors = wallpaper.systemColors()
            while (!colors.firstOrNull().isBlue()) {
                delay(POLL_MS)
                colors = wallpaper.systemColors()
            }
            colors
        }
        assertNotNull(reported, "The primary color becomes blue, was ${wallpaper.systemColors()}")
    }

    private fun Rgb?.isBlue(): Boolean {
        if (this == null) return false
        val red = (value shr 16) and 0xFF
        val green = (value shr 8) and 0xFF
        val blue = value and 0xFF
        return blue > red + 60 && blue > green + 60
    }

    private companion object {
        const val COLORS_TIMEOUT_MS = 10_000L
        const val POLL_MS = 100L
    }
}
