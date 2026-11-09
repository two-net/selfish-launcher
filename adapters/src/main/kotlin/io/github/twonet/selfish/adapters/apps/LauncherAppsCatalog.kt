package io.github.twonet.selfish.adapters.apps

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.os.UserHandle
import android.os.UserManager
import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.AppId
import io.github.twonet.selfish.domain.model.Bounds
import io.github.twonet.selfish.domain.ports.AppCatalog
import io.github.twonet.selfish.domain.ports.AppShortcut
import io.github.twonet.selfish.domain.ports.DefaultApps
import io.github.twonet.selfish.domain.ports.SystemScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** [AppCatalog] over `LauncherApps` and `UserManager` (DESIGN §5.1). */
class LauncherAppsCatalog(context: Context) : AppCatalog {
    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)
    private val userManager = appContext.getSystemService(UserManager::class.java)

    override fun apps(): Flow<List<AppEntry>> =
        packageChanges()
            .conflate()
            .map { query() }
            .flowOn(Dispatchers.IO)

    /** Emits once at once, then once per package event in any profile. */
    private fun packageChanges(): Flow<Unit> = callbackFlow {
        val callback = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String, user: UserHandle) {
                trySend(Unit)
            }

            override fun onPackageRemoved(packageName: String, user: UserHandle) {
                trySend(Unit)
            }

            override fun onPackageChanged(packageName: String, user: UserHandle) {
                trySend(Unit)
            }

            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
                trySend(Unit)
            }

            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
                trySend(Unit)
            }
        }
        trySend(Unit)
        launcherApps.registerCallback(callback, android.os.Handler(android.os.Looper.getMainLooper()))
        awaitClose { launcherApps.unregisterCallback(callback) }
    }

    private fun query(): List<AppEntry> =
        userManager.userProfiles.flatMap { user ->
            val serial = userManager.getSerialNumberForUser(user)
            launcherApps.getActivityList(null, user)
                .filter { it.componentName.packageName != appContext.packageName }
                .map { info ->
                    AppEntry(
                        id = AppId(info.componentName.packageName, info.componentName.className, serial),
                        label = info.label.toString(),
                    )
                }
        }

    override suspend fun launch(app: AppId, from: Bounds?) {
        val user = userManager.getUserForSerialNumber(app.userSerial) ?: return
        val component = ComponentName(app.packageName, app.activityName)
        val bounds = from?.let { Rect(it.left, it.top, it.right, it.bottom) }
        // The grow-from-icon animation of FR-ANIM-02 is added with #13.
        withContext(Dispatchers.Main) {
            launcherApps.startMainActivity(component, user, bounds, null)
        }
    }

    // The members below are filled in by #5 and #7.

    override suspend fun openAppInfo(app: AppId) = Unit

    override suspend fun requestUninstall(app: AppId) = Unit

    override suspend fun shortcuts(app: AppId): List<AppShortcut> = emptyList()

    override suspend fun launchShortcut(app: AppId, shortcutId: String, from: Bounds?) = Unit

    override suspend fun defaultApps(): DefaultApps = DefaultApps()

    override suspend fun open(screen: SystemScreen) = Unit
}
