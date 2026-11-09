package io.github.twonet.selfish.adapters.scheduler

import android.app.job.JobParameters
import android.app.job.JobService

/**
 * The launcher's only background job, the wallpaper check (AR-06). It hands
 * each run to the store through [JobSchedulerWallpaperScheduler.runs]; the
 * store calls `finished()` when the check is done.
 */
class WallpaperJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean =
        JobSchedulerWallpaperScheduler.jobRuns.tryEmit { jobFinished(params, false) }

    /** Interrupted runs are retried with the job's back-off policy. */
    override fun onStopJob(params: JobParameters): Boolean = true
}
