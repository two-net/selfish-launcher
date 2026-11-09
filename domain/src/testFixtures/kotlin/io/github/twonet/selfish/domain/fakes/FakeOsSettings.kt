package io.github.twonet.selfish.domain.fakes

import io.github.twonet.selfish.domain.ports.ClockInfo
import io.github.twonet.selfish.domain.ports.NetworkState
import io.github.twonet.selfish.domain.ports.OsSettings
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant
import java.time.ZoneOffset

class FakeOsSettings : OsSettings {
    override val systemDark = MutableStateFlow<Boolean?>(false)
    override val animationsOff = MutableStateFlow(false)
    override val clock = MutableStateFlow(ClockInfo(Instant.EPOCH, ZoneOffset.UTC, is24Hour = true))

    var network = NetworkState(connected = true, metered = false, dataSaver = false)
    var batterySaverOn = false
    var defaultHome = true
    var defaultHomeRequests = 0
        private set

    override suspend fun networkState(): NetworkState = network

    override suspend fun batterySaver(): Boolean = batterySaverOn

    override suspend fun isDefaultHome(): Boolean = defaultHome

    override fun requestDefaultHome() {
        defaultHomeRequests++
    }
}
