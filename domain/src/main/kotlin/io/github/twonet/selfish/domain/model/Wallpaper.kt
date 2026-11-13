@file:UseSerializers(InstantSerializer::class, LocalDateSerializer::class)

package io.github.twonet.selfish.domain.model

import io.github.twonet.selfish.domain.persist.InstantSerializer
import io.github.twonet.selfish.domain.persist.LenientListSerializer
import io.github.twonet.selfish.domain.persist.LocalDateSerializer
import io.github.twonet.selfish.domain.ports.ImageId
import io.github.twonet.selfish.domain.ports.WallpaperId
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant
import java.time.LocalDate

/**
 * A cached image of the day (FR-WP-07, SRS §8), as Events, the Top Menu and
 * Wallpaper details identify it (FR-EVT-03, FR-TOP-03, FR-WP-09, FR-WP-10).
 * Saved in the cache index; #16 adds the highlight color derived from it.
 */
@Serializable
data class WallpaperEntry(
    /** The cached image, named by the image's identity (IF-WP-01, DESIGN §4.3). */
    val image: ImageId,
    val title: String,
    val copyright: String,
    /** An `https` page about the image, if the feed gave one. */
    val link: String?,
    /** The day Bing published the image for [market]. */
    val date: LocalDate,
    val market: Market,
    /** The image's size, so it can be cropped to the screen without decoding it again (FR-WP-04). */
    val size: PixelSize,
    /** The image file's size, for the cache budget (NFR-PWR-04). */
    val bytes: Int,
) {
    init {
        require(size.width > 0 && size.height > 0) { "size must be positive, was $size" }
        require(bytes > 0) { "bytes must be positive, was $bytes" }
    }
}

/** The image the launcher last set as the system's home screen wallpaper, and the ID the system gave it. */
@Serializable
data class AppliedWallpaper(
    val image: ImageId,
    /** Tells a wallpaper set elsewhere since then apart (FR-AMB-03, #16). */
    val id: WallpaperId,
)

/**
 * What the wallpaper checks remember between runs (DESIGN §4.3): when the
 * last ones ran, how many ran today, and what was applied. Saved in the cache
 * index, so it survives the process and is never backed up (SRS §8).
 */
data class WallpaperStatus(
    /** The last check that reached the feed, successful or not (FR-WP-02). */
    val lastCheck: Instant? = null,
    /** The last check that read the feed and stored every image it chose (FR-WP-09). */
    val lastSuccess: Instant? = null,
    /** The day [checksToday] and [retriesToday] count, in the device's time zone. */
    val day: LocalDate? = null,
    /** Checks that reached the feed on [day] (NFR-PWR-01). */
    val checksToday: Int = 0,
    /** Retries scheduled on [day] (FR-WP-11). */
    val retriesToday: Int = 0,
    /** Failed checks since the last success, which pick the next retry's delay (FR-WP-11). */
    val failures: Int = 0,
    val applied: AppliedWallpaper? = null,
) {
    init {
        require(checksToday >= 0 && retriesToday >= 0 && failures >= 0) { "counts must not be negative" }
    }

    /** Checks that reached the feed on [today]. */
    fun checksOn(today: LocalDate): Int = if (day == today) checksToday else 0

    /** Retries scheduled on [today]. */
    fun retriesOn(today: LocalDate): Int = if (day == today) retriesToday else 0
}

/**
 * The wallpaper cache index, `wallpapers.json` (SRS §8, DESIGN §4.7): the
 * cached images, newest first, the pinned one and the [status] of the
 * checks. Kept beside the images, never backed up, and deleted with them
 * when Daily Bing wallpaper is turned off (FR-WP-13).
 *
 * The status is saved field by field, at the top level, so one unreadable
 * value falls back alone (NFR-REL-03, DESIGN §4.7).
 *
 * Adding a field with a default needs no migration. Renaming, removing or
 * restructuring one needs a migration in `WallpaperIndexDoc` and a new frozen
 * sample in the tests (AR-07).
 */
@Serializable
data class WallpaperIndex(
    @Serializable(with = LenientListSerializer::class)
    val entries: List<WallpaperEntry> = emptyList(),
    /** The image chosen as an ambience, or `null` for Daily (FR-WP-08). */
    val pinned: ImageId? = null,
    val lastCheck: Instant? = null,
    val lastSuccess: Instant? = null,
    val day: LocalDate? = null,
    val checksToday: Int = 0,
    val retriesToday: Int = 0,
    val failures: Int = 0,
    val applied: AppliedWallpaper? = null,
) {
    constructor(entries: List<WallpaperEntry>, pinned: ImageId? = null, status: WallpaperStatus) : this(
        entries = entries,
        pinned = pinned,
        lastCheck = status.lastCheck,
        lastSuccess = status.lastSuccess,
        day = status.day,
        checksToday = status.checksToday,
        retriesToday = status.retriesToday,
        failures = status.failures,
        applied = status.applied,
    )

    init {
        require(checksToday >= 0 && retriesToday >= 0 && failures >= 0) { "counts must not be negative" }
    }

    val status: WallpaperStatus
        get() = WallpaperStatus(lastCheck, lastSuccess, day, checksToday, retriesToday, failures, applied)
}
