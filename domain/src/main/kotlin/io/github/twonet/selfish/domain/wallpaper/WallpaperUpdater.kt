package io.github.twonet.selfish.domain.wallpaper

import io.github.twonet.selfish.domain.model.Market
import io.github.twonet.selfish.domain.model.PixelSize
import io.github.twonet.selfish.domain.model.WallpaperEntry
import io.github.twonet.selfish.domain.model.WallpaperIndex
import io.github.twonet.selfish.domain.model.WallpaperStatus
import io.github.twonet.selfish.domain.ports.FeedEntry
import io.github.twonet.selfish.domain.ports.ImageId
import io.github.twonet.selfish.domain.ports.ImageVariant
import io.github.twonet.selfish.domain.ports.Ports
import io.github.twonet.selfish.domain.ports.WallpaperId
import io.github.twonet.selfish.domain.ports.WallpaperScreens
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

/**
 * Carries out the wallpaper work the store's effects ask for, through the
 * ports (DESIGN §4.3): checking the feed, storing new images, applying one,
 * and keeping the cache tidy. The decisions themselves are
 * [WallpaperPolicy]'s; this class only does the I/O around them.
 *
 * Nothing here throws except for cancellation: a failure is a result, never
 * an error dialog (FR-WP-11).
 */
class WallpaperUpdater(
    private val ports: Ports,
    private val clock: Clock,
    private val locale: () -> Locale,
) {
    fun now(): Instant = clock.instant()

    fun today(): LocalDate = LocalDate.now(clock)

    /**
     * Checks the feed for new images and stores them (FR-WP-01, FR-WP-02):
     *
     * 1. Skipped unless the check is due and no gate is closed (FR-WP-06).
     * 2. The feed for the resolved market (FR-WP-03), with 8 entries on a
     *    first run or after a gap (IF-WP-04).
     * 3. Each entry not already cached, newest first: the portrait version,
     *    else the landscape one (FR-WP-04), checked to be a valid image within
     *    the size limit (FR-WP-14), then stored. The first failure stops it.
     */
    suspend fun check(
        trigger: CheckTrigger,
        market: Market?,
        wifiOnly: Boolean,
        cached: List<WallpaperEntry>,
        status: WallpaperStatus,
    ): CheckResult {
        val today = today()
        try {
            if (!WallpaperPolicy.due(status, trigger, now(), today)) return CheckResult.Skipped
            val os = ports.osSettings
            if (WallpaperPolicy.gated(os.networkState(), os.batterySaver(), wifiOnly)) return CheckResult.Skipped
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The network state couldn't be read: wait, as for a closed gate.
            return CheckResult.Skipped
        }
        val stored = mutableListOf<WallpaperEntry>()
        return try {
            val resolved = MarketResolver.resolve(market, locale())
            val feed = ports.bingFeed.fetchFeed(resolved, WallpaperPolicy.feedCount(cached, today))
                .getOrElse { return CheckResult.Checked(emptyList(), succeeded = false) }
            val known = cached.mapTo(HashSet()) { it.image }
            for (item in WallpaperPolicy.toDownload(feed, known)) {
                stored += download(item) ?: return CheckResult.Checked(stored, succeeded = false)
            }
            CheckResult.Checked(stored, succeeded = true)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            CheckResult.Checked(stored, succeeded = false)
        }
    }

    /** Downloads, checks and stores [item], or returns `null` if neither version could be. */
    private suspend fun download(item: FeedEntry): WallpaperEntry? {
        val image = WallpaperPolicy.identity(item) ?: return null
        for (variant in ImageVariant.entries) {
            val bytes = ports.bingFeed.fetchImage(item, variant).getOrNull() ?: continue
            if (bytes.isEmpty() || bytes.size > WallpaperPolicy.MAX_IMAGE_BYTES) continue
            val info = ports.systemWallpaper.inspect(bytes) ?: continue
            if (info.width <= 0 || info.height <= 0) continue
            val entry = WallpaperPolicy.entryOf(item, image, PixelSize(info.width, info.height), bytes.size) ?: return null
            ports.localStorage.writeImage(image, bytes)
            return entry
        }
        return null
    }

    /**
     * Sets [entry] as the wallpaper of [screens], cropped around its center
     * to the screen's shape (FR-WP-01, FR-WP-04, FR-WP-05). Throws if the
     * image is gone or the system refuses it.
     */
    suspend fun apply(entry: WallpaperEntry, screens: WallpaperScreens): WallpaperId {
        val bytes = ports.localStorage.readImage(entry.image) ?: throw IOException("Image ${entry.image.value} is not cached")
        val crop = Crop.center(entry.size, ports.systemWallpaper.screenSize())
        return ports.systemWallpaper.apply(bytes, crop, screens)
    }

    /**
     * [index] matched with the image files actually stored, at start: entries
     * whose file is missing are dropped, and files no entry names are
     * deleted, such as an image stored just before the process died.
     */
    suspend fun reconcile(index: WallpaperIndex): WallpaperIndex = try {
        val files = ports.localStorage.imageIds()
        val entries = index.entries.filter { it.image in files }
        val named = entries.mapTo(HashSet()) { it.image }
        for (orphan in files - named) ports.localStorage.deleteImage(orphan)
        index.copy(entries = entries, pinned = index.pinned?.takeIf { it in named })
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        index
    }

    suspend fun delete(images: Set<ImageId>) {
        for (image in images) ports.localStorage.deleteImage(image)
    }

    /** Deletes every cached image and the index (FR-WP-13). */
    suspend fun clear() = ports.localStorage.clearWallpaperCache()
}
