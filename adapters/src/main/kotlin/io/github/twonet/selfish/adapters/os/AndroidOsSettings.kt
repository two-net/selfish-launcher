package io.github.twonet.selfish.adapters.os

import android.app.role.RoleManager
import android.content.Context
import io.github.twonet.selfish.adapters.ForegroundActivity
import io.github.twonet.selfish.domain.ports.ClockInfo
import io.github.twonet.selfish.domain.ports.NetworkState
import io.github.twonet.selfish.domain.ports.OsSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/**
 * [OsSettings] over `Configuration.uiMode`, `Settings.Global`,
 * `ConnectivityManager`, `PowerManager`, the time broadcasts and `RoleManager`
 * (DESIGN §5.1). The Home role is real (#5); the rest reports neutral defaults
 * until #13, #14 and #17.
 */
class AndroidOsSettings(
    context: Context,
    private val foreground: ForegroundActivity,
) : OsSettings {
    private val roleManager = context.applicationContext.getSystemService(RoleManager::class.java)

    override val systemDark: Flow<Boolean?> = flowOf(null)

    override val animationsOff: Flow<Boolean> = flowOf(false)

    override val clock: Flow<ClockInfo> = emptyFlow()

    override suspend fun networkState(): NetworkState =
        NetworkState(connected = false, metered = true, dataSaver = false)

    override suspend fun batterySaver(): Boolean = false

    /** True also when the device has no Home role, because then there is nothing to offer. */
    override suspend fun isDefaultHome(): Boolean = withContext(Dispatchers.IO) {
        !roleManager.isRoleAvailable(RoleManager.ROLE_HOME) || roleManager.isRoleHeld(RoleManager.ROLE_HOME)
    }

    /**
     * Shows the system's "Set as default Home app?" prompt over the launcher
     * (FR-GEN-02). It must be started for a result: the prompt reads the
     * caller from `getCallingPackage()`, which is null otherwise. The result
     * isn't needed, so nobody waits for it. Without a visible Activity this
     * does nothing.
     */
    override fun requestDefaultHome() {
        val activity = foreground.current ?: return
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) return
        activity.startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME), REQUEST_HOME_ROLE)
    }

    private companion object {
        const val REQUEST_HOME_ROLE = 0x5E1F
    }
}
