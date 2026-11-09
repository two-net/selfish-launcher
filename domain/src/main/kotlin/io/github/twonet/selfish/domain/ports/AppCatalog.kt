package io.github.twonet.selfish.domain.ports

import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.AppId
import io.github.twonet.selfish.domain.model.Bounds
import kotlinx.coroutines.flow.Flow

/**
 * The installed, launchable apps and the actions on them (SRS §5.2: list of
 * launchable apps with change events; launching apps; app info and uninstall;
 * apps' published shortcuts).
 */
interface AppCatalog {
    /**
     * The enabled, launchable apps of every profile, excluding the launcher
     * itself. Emits the current list on collection, then again after every
     * install, removal, update or enable/disable (FR-GEN-06).
     */
    fun apps(): Flow<List<AppEntry>>

    /** Starts [app], growing its window from [from] when given (FR-GEN-05, FR-ANIM-02). */
    suspend fun launch(app: AppId, from: Bounds?)

    /** Opens the system's app-info screen for [app] (FR-GRID-06). */
    suspend fun openAppInfo(app: AppId)

    /** Opens the system's uninstall confirmation for [app] (FR-GRID-06). */
    suspend fun requestUninstall(app: AppId)

    /** The shortcuts [app] publishes, or none when unavailable (FR-HOME-09, FR-GRID-10; optional). */
    suspend fun shortcuts(app: AppId): List<AppShortcut>

    /** Starts one of [app]'s published shortcuts (FR-HOME-09, FR-GRID-10). */
    suspend fun launchShortcut(app: AppId, shortcutId: String, from: Bounds?)

    /** The device's default phone, messaging, browser and camera apps, for first run (FR-HOME-07). */
    suspend fun defaultApps(): DefaultApps

    /** Opens a screen owned by the system or another app (FR-EVT-03, FR-TOP-04, FR-ONB-04). */
    suspend fun open(screen: SystemScreen)
}

data class AppShortcut(val id: String, val label: String)

data class DefaultApps(
    val phone: AppId? = null,
    val messaging: AppId? = null,
    val browser: AppId? = null,
    val camera: AppId? = null,
)

sealed interface SystemScreen {
    /** An `https` link, such as a wallpaper's "Learn more" page. */
    data class Link(val url: String) : SystemScreen
    data object SystemSettings : SystemScreen
    data object NotificationAccess : SystemScreen
    data object UsageAccess : SystemScreen
}
