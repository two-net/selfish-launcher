package io.github.twonet.selfish.adapters.os

import android.content.Context
import io.github.twonet.selfish.domain.ports.ClockInfo
import io.github.twonet.selfish.domain.ports.NetworkState
import io.github.twonet.selfish.domain.ports.OsSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * [OsSettings] over `Configuration.uiMode`, `Settings.Global`,
 * `ConnectivityManager`, `PowerManager`, the time broadcasts and `RoleManager`
 * (DESIGN §5.1). Stub until #5, #13, #14 and #17: it reports neutral defaults.
 */
@Suppress("unused")
class AndroidOsSettings(private val context: Context) : OsSettings {
    override val systemDark: Flow<Boolean?> = flowOf(null)

    override val animationsOff: Flow<Boolean> = flowOf(false)

    override val clock: Flow<ClockInfo> = emptyFlow()

    override suspend fun networkState(): NetworkState =
        NetworkState(connected = false, metered = true, dataSaver = false)

    override suspend fun batterySaver(): Boolean = false

    override suspend fun isDefaultHome(): Boolean = true

    override fun requestDefaultHome() = Unit
}
