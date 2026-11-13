package io.github.twonet.selfish.domain.state

import io.github.twonet.selfish.domain.model.AppId
import io.github.twonet.selfish.domain.model.Bounds
import io.github.twonet.selfish.domain.ports.NotificationRef
import io.github.twonet.selfish.domain.ports.ScheduledRun
import io.github.twonet.selfish.domain.ports.SystemScreen
import kotlin.time.Duration

/** Work the reducer asks for, carried out through the ports by [LauncherStore]. */
sealed interface Effect {
    data class LaunchApp(val app: AppId, val from: Bounds?) : Effect

    data class FinishRun(val run: ScheduledRun) : Effect

    /** Opens the system's app-info screen for [app] (FR-GRID-06). */
    data class OpenAppInfo(val app: AppId) : Effect

    /** Opens the system's uninstall confirmation for [app] (FR-GRID-06). */
    data class RequestUninstall(val app: AppId) : Effect

    /** Shows the system's default-Home prompt, unless the launcher already is the default (FR-GEN-02). */
    data object OfferDefaultHome : Effect

    /**
     * Looks up the device's default apps and dispatches
     * [Action.DefaultAppsResolved], with none if the lookup fails, so a first
     * run always gets its layout (FR-HOME-07).
     */
    data object ResolveDefaultApps : Effect

    /**
     * Dispatches [action] after [delay], in the store's scope, so it fires
     * even after the user leaves the launcher. Tests run it in virtual time.
     */
    data class DispatchAfter(val delay: Duration, val action: Action) : Effect

    /** Opens a screen owned by the system or another app, such as a web page (FR-EVT-03). */
    data class OpenScreen(val screen: SystemScreen) : Effect

    /** Opens the system's notification access screen (FR-EVT-07, FR-ONB-04). */
    data object RequestNotificationAccess : Effect

    /** Opens what the notification [key] points to (FR-EVT-04). */
    data class OpenNotification(val key: String, val from: Bounds?) : Effect

    /** Dismisses [refs], as they were when shown (FR-EVT-05, FR-EVT-06). */
    data class DismissNotifications(val refs: Set<NotificationRef>) : Effect
}
