package io.github.twonet.selfish.domain.fakes

import io.github.twonet.selfish.domain.ports.ScheduledRun
import io.github.twonet.selfish.domain.ports.WallpaperScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.time.Duration

class FakeWallpaperScheduler : WallpaperScheduler {
    var periodic: Boolean = false
        private set
    var wifiOnly: Boolean = false
        private set
    val retries = mutableListOf<Duration>()

    private val runFlow = MutableSharedFlow<ScheduledRun>(extraBufferCapacity = 8)
    override val runs: Flow<ScheduledRun> = runFlow

    /** Simulates the system running the job. */
    fun trigger(run: ScheduledRun) {
        check(runFlow.tryEmit(run)) { "Run buffer full" }
    }

    override fun schedulePeriodic(wifiOnly: Boolean) {
        periodic = true
        this.wifiOnly = wifiOnly
    }

    override fun scheduleRetry(delay: Duration, wifiOnly: Boolean) {
        retries += delay
        this.wifiOnly = wifiOnly
    }

    override fun cancel() {
        periodic = false
        retries.clear()
    }
}
