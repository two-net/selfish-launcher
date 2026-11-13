package io.github.twonet.selfish.adapters.scheduler

import android.app.job.JobScheduler
import android.content.ComponentName
import android.net.NetworkCapabilities
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.twonet.selfish.domain.Verifies
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * [JobSchedulerWallpaperScheduler] against the device's `JobScheduler`
 * (level A, DESIGN §8.3). Jobs belong to the test's own app, so the
 * launcher's job, if it is installed, is untouched.
 */
@RunWith(AndroidJUnit4::class)
@Verifies("AR-06")
class JobSchedulerWallpaperSchedulerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scheduler = JobSchedulerWallpaperScheduler(context)
    private val jobs = context.getSystemService(JobScheduler::class.java)
    private val service = ComponentName(context, WallpaperJobService::class.java)

    @Before
    @After
    fun cancelAll() = scheduler.cancel()

    private fun unmeteredOnly(id: Int): Boolean =
        assertNotNull(assertNotNull(jobs.getPendingJob(id)).requiredNetwork).hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)

    @Test
    @Verifies("FR-WP-02", "NFR-PWR-01")
    fun thePeriodicCheckRunsEveryThreeHoursWithANetworkAndSurvivesReboots() {
        scheduler.schedulePeriodic(wifiOnly = false)

        val job = assertNotNull(jobs.getPendingJob(JobSchedulerWallpaperScheduler.PERIODIC_JOB_ID))
        assertEquals(service, job.service)
        assertTrue(job.isPeriodic)
        assertEquals(3.hours.inWholeMilliseconds, job.intervalMillis)
        assertTrue(job.isPersisted)
        assertNotNull(job.requiredNetwork)
        assertFalse(unmeteredOnly(JobSchedulerWallpaperScheduler.PERIODIC_JOB_ID))
    }

    @Test
    @Verifies("FR-WP-06")
    fun wifiOnlyWaitsForAnUnmeteredNetwork() {
        scheduler.schedulePeriodic(wifiOnly = true)
        assertTrue(unmeteredOnly(JobSchedulerWallpaperScheduler.PERIODIC_JOB_ID))

        // Asking again with the same condition keeps the job; a new one replaces it.
        scheduler.schedulePeriodic(wifiOnly = true)
        assertTrue(unmeteredOnly(JobSchedulerWallpaperScheduler.PERIODIC_JOB_ID))
        scheduler.schedulePeriodic(wifiOnly = false)
        assertFalse(unmeteredOnly(JobSchedulerWallpaperScheduler.PERIODIC_JOB_ID))
        assertEquals(1, jobs.allPendingJobs.size)
    }

    @Test
    @Verifies("FR-WP-11")
    fun aRetryIsOneCheckAfterItsDelay() {
        scheduler.scheduleRetry(15.minutes, wifiOnly = true)

        val retry = assertNotNull(jobs.getPendingJob(JobSchedulerWallpaperScheduler.RETRY_JOB_ID))
        assertFalse(retry.isPeriodic)
        assertEquals(15.minutes.inWholeMilliseconds, retry.minLatencyMillis)
        assertTrue(unmeteredOnly(JobSchedulerWallpaperScheduler.RETRY_JOB_ID))
    }

    @Test
    @Verifies("FR-WP-13")
    fun cancellingRemovesEveryCheck() {
        scheduler.schedulePeriodic(wifiOnly = false)
        scheduler.scheduleRetry(1.hours, wifiOnly = false)

        scheduler.cancel()

        assertNull(jobs.getPendingJob(JobSchedulerWallpaperScheduler.PERIODIC_JOB_ID))
        assertNull(jobs.getPendingJob(JobSchedulerWallpaperScheduler.RETRY_JOB_ID))
    }
}
