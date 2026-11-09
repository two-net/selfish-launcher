package io.github.twonet.selfish.domain.fakes

import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.AppId
import io.github.twonet.selfish.domain.model.Bounds
import io.github.twonet.selfish.domain.ports.AppCatalog
import io.github.twonet.selfish.domain.ports.AppIconImage
import io.github.twonet.selfish.domain.ports.AppShortcut
import io.github.twonet.selfish.domain.ports.DefaultApps
import io.github.twonet.selfish.domain.ports.SystemScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAppCatalog(apps: List<AppEntry> = emptyList()) : AppCatalog {
    /** Set this to simulate installs, removals and updates. */
    val installed = MutableStateFlow(apps)
    var shortcutsByApp: Map<AppId, List<AppShortcut>> = emptyMap()
    var defaults: DefaultApps = DefaultApps()
    val icons = mutableMapOf<AppId, AppIconImage>()

    val launched = mutableListOf<Pair<AppId, Bounds?>>()
    val launchedShortcuts = mutableListOf<Pair<AppId, String>>()
    val openedAppInfo = mutableListOf<AppId>()
    val uninstallRequests = mutableListOf<AppId>()
    val openedScreens = mutableListOf<SystemScreen>()

    override fun apps(): Flow<List<AppEntry>> = installed

    override suspend fun icon(app: AppId, sizePx: Int): AppIconImage? = icons[app]

    override suspend fun launch(app: AppId, from: Bounds?) {
        launched += app to from
    }

    override suspend fun openAppInfo(app: AppId) {
        openedAppInfo += app
    }

    override suspend fun requestUninstall(app: AppId) {
        uninstallRequests += app
    }

    override suspend fun shortcuts(app: AppId): List<AppShortcut> = shortcutsByApp[app].orEmpty()

    override suspend fun launchShortcut(app: AppId, shortcutId: String, from: Bounds?) {
        launchedShortcuts += app to shortcutId
    }

    override suspend fun defaultApps(): DefaultApps = defaults

    override suspend fun open(screen: SystemScreen) {
        openedScreens += screen
    }
}
