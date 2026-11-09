package io.github.twonet.selfish.adapters.scheduler

import android.content.Context
import io.github.twonet.selfish.domain.ports.ScheduledRun
import io.github.twonet.selfish.domain.ports.WallpaperScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.time.Duration

/**
 * [WallpaperScheduler] over `JobScheduler` (DESIGN §5.4). Its job,
 * [WallpaperJobService], is the launcher's only background job (AR-06).
 *
 * Scheduling is a stub until #14; [runs] already carries the job's runs to
 * the store.
 */
@Suppress("unused")
class JobSchedulerWallpaperScheduler(private val context: Context) : WallpaperScheduler {
    override fun schedulePeriodic(wifiOnly: Boolean) = Unit

    override fun scheduleRetry(delay: Duration, wifiOnly: Boolean) = Unit

    override fun cancel() = Unit

    override val runs: Flow<ScheduledRun> = jobRuns.asSharedFlow()

    internal companion object {
        /**
         * Process-wide, because the system creates [WallpaperJobService] itself.
         * The store subscribes when the process starts, before any job runs.
         */
        val jobRuns = MutableSharedFlow<ScheduledRun>(extraBufferCapacity = 4)
    }
}
