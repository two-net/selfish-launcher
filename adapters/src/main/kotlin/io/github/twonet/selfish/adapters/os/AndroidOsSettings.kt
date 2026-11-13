package io.github.twonet.selfish.adapters.os

import android.app.role.RoleManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import io.github.twonet.selfish.adapters.ForegroundActivity
import io.github.twonet.selfish.domain.ports.ClockInfo
import io.github.twonet.selfish.domain.ports.NetworkState
import io.github.twonet.selfish.domain.ports.OsSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/**
 * [OsSettings] over `Configuration.uiMode`, `Settings.Global`,
 * `ConnectivityManager`, `PowerManager`, the time broadcasts and `RoleManager`
 * (DESIGN §5.1). The Home role (#5), the clock (#6) and the animator scale
 * (#13) are real; the rest reports neutral defaults until #14 and #17.
 */
class AndroidOsSettings(
    context: Context,
    private val foreground: ForegroundActivity,
) : OsSettings {
    private val appContext = context.applicationContext
    private val roleManager = appContext.getSystemService(RoleManager::class.java)

    override val systemDark: Flow<Boolean?> = flowOf(null)

    /**
     * Whether the system's animator duration scale is 0, the "remove
     * animations" setting (FR-ANIM-15): the value now, then after every
     * change of the setting. The observer is registered only while the flow
     * is collected.
     */
    override val animationsOff: Flow<Boolean> = animatorScaleChanges().distinctUntilChanged().conflate()

    private fun animatorScaleChanges(): Flow<Boolean> = callbackFlow {
        val resolver = appContext.contentResolver
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                trySend(animatorScaleIsZero())
            }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        // After registering, so a change in between isn't missed.
        trySend(animatorScaleIsZero())
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    private fun animatorScaleIsZero(): Boolean =
        Settings.Global.getFloat(appContext.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    /**
     * The time now, then again on every minute and whenever the time, the
     * time zone or the 12/24-hour setting changes (FR-HOME-02, NFR-L10N-02);
     * toggling 12/24 hours sends `ACTION_TIME_CHANGED`. The receiver is
     * registered only while the flow is collected, which the store does only
     * while the launcher is shown.
     */
    override val clock: Flow<ClockInfo> = clockChanges().conflate()

    private fun clockChanges(): Flow<ClockInfo> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(now())
            }
        }
        // All protected system broadcasts, which reach a not-exported
        // receiver too.
        val filter = IntentFilter(Intent.ACTION_TIME_TICK)
        filter.addAction(Intent.ACTION_TIME_CHANGED)
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            appContext.registerReceiver(receiver, filter)
        }
        // After registering, so a change in between isn't missed.
        trySend(now())
        awaitClose { appContext.unregisterReceiver(receiver) }
    }

    private fun now() = ClockInfo(Instant.now(), ZoneId.systemDefault(), DateFormat.is24HourFormat(appContext))

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
