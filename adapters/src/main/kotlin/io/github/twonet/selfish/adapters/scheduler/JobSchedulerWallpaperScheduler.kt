package io.github.twonet.selfish.adapters.scheduler

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.PersistableBundle
import io.github.twonet.selfish.domain.ports.ScheduledRun
import io.github.twonet.selfish.domain.ports.WallpaperScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * [WallpaperScheduler] over `JobScheduler` (DESIGN §5.4). Its job,
 * [WallpaperJobService], is the launcher's only background job (AR-06):
 *
 * - one periodic job, every 3 hours, on any network or on unmetered ones
 *   only, kept across reboots (FR-WP-02, FR-WP-06);
 * - one one-off job for a retry after a failure (FR-WP-11).
 *
 * Both only run with a network, so a device that is offline does nothing.
 * Data Saver and Battery Saver have no job constraint; the check looks at
 * them when it runs.
 */
class JobSchedulerWallpaperScheduler(context: Context) : WallpaperScheduler {
    private val appContext = context.applicationContext
    private val jobs = appContext.getSystemService(JobScheduler::class.java)
    private val service = ComponentName(appContext, WallpaperJobService::class.java)

    /**
     * Schedules the periodic job, unless the same one is already pending:
     * scheduling it again would restart its period, and the launcher asks on
     * every start.
     */
    override fun schedulePeriodic(wifiOnly: Boolean) {
        val pending = jobs.getPendingJob(PERIODIC_JOB_ID)
        if (pending != null && pending.isPeriodic && pending.intervalMillis == PERIOD.inWholeMilliseconds && pending.extras.matches(wifiOnly)) return
        jobs.schedule(
            JobInfo.Builder(PERIODIC_JOB_ID, service)
                .setPeriodic(PERIOD.inWholeMilliseconds)
                .configure(wifiOnly)
                .build(),
        )
    }

    /** Schedules one more check after [delay], replacing a retry already waiting. */
    override fun scheduleRetry(delay: Duration, wifiOnly: Boolean) {
        jobs.schedule(
            JobInfo.Builder(RETRY_JOB_ID, service)
                .setMinimumLatency(delay.inWholeMilliseconds)
                .configure(wifiOnly)
                .build(),
        )
    }

    override fun cancel() {
        jobs.cancel(PERIODIC_JOB_ID)
        jobs.cancel(RETRY_JOB_ID)
    }

    override val runs: Flow<ScheduledRun> = jobRuns.asSharedFlow()

    /** The network condition, persistence across reboots, and the extras that record them. */
    private fun JobInfo.Builder.configure(wifiOnly: Boolean): JobInfo.Builder =
        setRequiredNetworkType(if (wifiOnly) JobInfo.NETWORK_TYPE_UNMETERED else JobInfo.NETWORK_TYPE_ANY)
            .setEstimatedNetworkBytes(ESTIMATED_DOWNLOAD_BYTES, ESTIMATED_UPLOAD_BYTES)
            .setPersisted(true)
            .setExtras(
                PersistableBundle().apply {
                    putInt(EXTRA_VERSION, JOB_VERSION)
                    putBoolean(EXTRA_WIFI_ONLY, wifiOnly)
                },
            )

    private fun PersistableBundle.matches(wifiOnly: Boolean): Boolean =
        getInt(EXTRA_VERSION) == JOB_VERSION && getBoolean(EXTRA_WIFI_ONLY) == wifiOnly

    internal companion object {
        /**
         * Process-wide, because the system creates [WallpaperJobService] itself.
         * The store subscribes when the process starts, before any job runs.
         */
        val jobRuns = MutableSharedFlow<ScheduledRun>(extraBufferCapacity = 4)

        const val PERIODIC_JOB_ID = 0x5E1F_0001
        const val RETRY_JOB_ID = 0x5E1F_0002

        /** At most every 3 hours (FR-WP-02, NFR-PWR-01). */
        val PERIOD: Duration = 3.hours

        /** A feed request and about one image (DESIGN §5.4), a hint for the system. */
        const val ESTIMATED_DOWNLOAD_BYTES = 1_000_000L
        const val ESTIMATED_UPLOAD_BYTES = 2_000L

        /** Raised when the job's settings change, so a job scheduled by an older version is replaced. */
        private const val JOB_VERSION = 1
        private const val EXTRA_VERSION = "version"
        private const val EXTRA_WIFI_ONLY = "wifiOnly"
    }
}
