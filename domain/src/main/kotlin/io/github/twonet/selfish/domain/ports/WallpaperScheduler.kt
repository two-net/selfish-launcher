package io.github.twonet.selfish.domain.ports

import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

/**
 * Background scheduling with a network condition (SRS §5.2). It carries the
 * launcher's only background job, the wallpaper check (AR-06).
 */
interface WallpaperScheduler {
    /** Schedules the periodic check, on unmetered networks only when [wifiOnly] (FR-WP-02, FR-WP-06). */
    fun schedulePeriodic(wifiOnly: Boolean)

    /** Schedules one extra check after [delay], for retries (FR-WP-11). */
    fun scheduleRetry(delay: Duration, wifiOnly: Boolean)

    /** Cancels every scheduled check (FR-WP-13). */
    fun cancel()

    /** Emits each time the system runs the job. Collect it and call [ScheduledRun.finished] when done. */
    val runs: Flow<ScheduledRun>
}

/** One run of the background job. */
fun interface ScheduledRun {
    /** Tells the system the run is over, so it can release the job's wake lock. */
    fun finished()
}
