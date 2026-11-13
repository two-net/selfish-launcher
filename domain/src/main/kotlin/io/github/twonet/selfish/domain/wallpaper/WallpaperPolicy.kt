package io.github.twonet.selfish.domain.wallpaper

import io.github.twonet.selfish.domain.model.Market
import io.github.twonet.selfish.domain.model.PixelSize
import io.github.twonet.selfish.domain.model.WallpaperEntry
import io.github.twonet.selfish.domain.model.WallpaperStatus
import io.github.twonet.selfish.domain.ports.FeedEntry
import io.github.twonet.selfish.domain.ports.ImageId
import io.github.twonet.selfish.domain.ports.NetworkState
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

/** What started a wallpaper check (DESIGN §4.3). */
enum class CheckTrigger {
    /** The launcher came on screen; it checks if the last check is over 3 hours old (FR-WP-02). */
    HomeShown,

    /** The background job, every 3 hours, or a retry (FR-WP-02, FR-WP-11). */
    Scheduled,

    /** The user asked: "Refresh now", turning Daily Bing wallpaper on, or a new market. */
    User,
}

/** How a check ended (DESIGN §4.3). */
sealed interface CheckResult {
    /**
     * Nothing was asked of the network: the check wasn't due, a gate was
     * closed (FR-WP-06), or the check was stopped. It doesn't count.
     */
    data object Skipped : CheckResult

    /**
     * The feed was asked. [stored] lists the new images now cached, newest
     * first. [succeeded] is false if the feed couldn't be read or an image
     * couldn't be stored; what was stored is kept (FR-WP-11).
     */
    data class Checked(val stored: List<WallpaperEntry>, val succeeded: Boolean) : CheckResult
}

/**
 * The wallpaper decisions of DESIGN §4.3, as pure functions over the cache
 * entries, the [WallpaperStatus], the time and the network state
 * (NFR-MNT-01). [WallpaperUpdater] and the reducer apply them.
 */
object WallpaperPolicy {
    /** How old the last check must be before showing Home checks again (FR-WP-02). */
    val CHECK_INTERVAL: Duration = 3.hours

    /** At most this many checks reach the feed each day, retries included (NFR-PWR-01). */
    const val MAX_CHECKS_PER_DAY = 8

    /** The delays before each retry after consecutive failures (FR-WP-11). */
    val RETRY_DELAYS: List<Duration> = listOf(15.minutes, 1.hours, 4.hours)

    /** At most this many retries a day (FR-WP-11). */
    const val MAX_RETRIES_PER_DAY = 3

    /** The newest images kept in the cache (FR-WP-07). */
    const val CACHE_COUNT = 8

    /** The cache stays under this many bytes (NFR-PWR-04). */
    const val CACHE_BUDGET_BYTES = 25_000_000L

    /** An image larger than this is discarded (IF-WP-05, FR-WP-14). */
    const val MAX_IMAGE_BYTES = 5_000_000

    /** Entries asked for on a first run and after a gap (IF-WP-04). */
    const val FULL_FEED = 8

    private val SAFE_ID = Regex("[A-Za-z0-9_-]{1,64}")

    /**
     * Whether a check started by [trigger] should go ahead at [now] (FR-WP-02,
     * NFR-PWR-01). The user's own requests always do; they still count.
     */
    fun due(status: WallpaperStatus, trigger: CheckTrigger, now: Instant, today: LocalDate): Boolean {
        val underCap = status.checksOn(today) < MAX_CHECKS_PER_DAY
        return when (trigger) {
            CheckTrigger.User -> true
            CheckTrigger.Scheduled -> underCap
            CheckTrigger.HomeShown -> {
                val last = status.lastCheck
                // A last check in the future means the clock went back; check again.
                underCap && (last == null || last.isAfter(now) || java.time.Duration.between(last, now) > CHECK_INTERVAL.toJavaDuration())
            }
        }
    }

    /**
     * Whether downloads must wait: offline, Data Saver or Battery Saver on,
     * or a metered network with "Wi-Fi only" set (FR-WP-06). A gated check
     * is skipped and doesn't count.
     */
    fun gated(network: NetworkState, batterySaver: Boolean, wifiOnly: Boolean): Boolean =
        !network.connected || network.dataSaver || batterySaver || (wifiOnly && network.metered)

    /**
     * How many feed entries to ask for: all 8 when nothing is cached or the
     * newest image is more than a day old, to fill the cache (IF-WP-04),
     * otherwise just today's.
     */
    fun feedCount(cached: List<WallpaperEntry>, today: LocalDate): Int {
        val newest = cached.maxOfOrNull { it.date } ?: return FULL_FEED
        return if (newest.plusDays(1).isBefore(today)) FULL_FEED else 1
    }

    /** The day [entry] was published, or `null` if the feed gave no valid date. */
    fun date(entry: FeedEntry): LocalDate? = try {
        LocalDate.parse(entry.startDate, DateTimeFormatter.BASIC_ISO_DATE)
    } catch (_: DateTimeParseException) {
        null
    }

    /**
     * [entry]'s identity (IF-WP-01): its hash, or else its start date and
     * market, as long as it is a safe file name; `null` otherwise.
     */
    fun identity(entry: FeedEntry): ImageId? {
        val hash = entry.hash?.takeIf { SAFE_ID.matches(it) }
        val id = hash ?: "${entry.startDate}_${entry.market}".takeIf { SAFE_ID.matches(it) }
        return id?.let(::ImageId)
    }

    /**
     * The feed entries to download: those with a valid identity and date
     * that aren't cached, newest first (FR-WP-02). Downloading only what
     * differs from the cache means the current image is never fetched again.
     */
    fun toDownload(feed: List<FeedEntry>, cached: Set<ImageId>): List<FeedEntry> =
        feed.filter { identity(it) != null && date(it) != null }
            .distinctBy { identity(it) }
            .filter { identity(it) !in cached }
            .sortedByDescending { date(it) }

    /**
     * The cache entry for [feed], stored as [image] (IF-WP-06): without a
     * title the copyright text is used, and only an `https` link is kept.
     */
    fun entryOf(feed: FeedEntry, image: ImageId, size: PixelSize, bytes: Int): WallpaperEntry? {
        val date = date(feed) ?: return null
        val copyright = feed.copyright?.trim().orEmpty()
        val title = feed.title?.trim()?.takeIf { it.isNotEmpty() } ?: copyright
        val link = feed.link?.trim()?.takeIf { it.startsWith("https://") }
        return WallpaperEntry(image, title, copyright, link, date, Market(feed.market), size, bytes)
    }

    /** The status after a check that reached the feed, and the delay of the retry to schedule, if any. */
    data class AfterCheck(val status: WallpaperStatus, val retry: Duration?)

    /**
     * Records a check that reached the feed at [now] (FR-WP-09, FR-WP-11,
     * NFR-PWR-01). The daily counts start over on a new [today]. A success
     * restarts the retry delays; a failure schedules the next retry, at most
     * [MAX_RETRIES_PER_DAY] a day.
     */
    fun afterCheck(status: WallpaperStatus, succeeded: Boolean, now: Instant, today: LocalDate): AfterCheck {
        val counted = status.copy(
            lastCheck = now,
            day = today,
            checksToday = status.checksOn(today) + 1,
            retriesToday = status.retriesOn(today),
        )
        if (succeeded) return AfterCheck(counted.copy(lastSuccess = now, failures = 0), retry = null)
        val failed = counted.copy(failures = counted.failures + 1)
        if (failed.retriesToday >= MAX_RETRIES_PER_DAY) return AfterCheck(failed, retry = null)
        val delay = RETRY_DELAYS[(failed.failures - 1).coerceIn(RETRY_DELAYS.indices)]
        return AfterCheck(failed.copy(retriesToday = failed.retriesToday + 1), delay)
    }

    /**
     * [stored] merged into [cached], newest first; on the same day, the
     * newly stored image comes first.
     */
    fun merge(cached: List<WallpaperEntry>, stored: List<WallpaperEntry>): List<WallpaperEntry> =
        (stored + cached).distinctBy { it.image }.sortedByDescending { it.date }

    /**
     * Splits [entries] (newest first) into those kept and those evicted
     * (FR-WP-07, NFR-PWR-04, NFR-REL-02): the [CACHE_COUNT] newest and the
     * [protected] ones (pinned, applied, in use) are kept, then the oldest
     * unprotected ones go while the cache is over [CACHE_BUDGET_BYTES].
     */
    fun evict(entries: List<WallpaperEntry>, protected: Set<ImageId>): Pair<List<WallpaperEntry>, List<WallpaperEntry>> {
        val kept = entries.filterIndexed { i, entry -> i < CACHE_COUNT || entry.image in protected }.toMutableList()
        var total = kept.sumOf { it.bytes.toLong() }
        for (entry in kept.asReversed().toList()) {
            if (total < CACHE_BUDGET_BYTES) break
            if (entry.image in protected) continue
            kept.remove(entry)
            total -= entry.bytes
        }
        val keptIds = kept.mapTo(HashSet()) { it.image }
        return kept to entries.filter { it.image !in keptIds }
    }

    /** The image to use: the [pinned] one, or else the newest (FR-WP-01, FR-WP-08). */
    fun inUse(entries: List<WallpaperEntry>, pinned: ImageId?): WallpaperEntry? =
        entries.firstOrNull { it.image == pinned } ?: entries.firstOrNull()
}
