package io.github.twonet.selfish.domain.ports

import io.github.twonet.selfish.domain.model.AppId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Notifications, with access granted by the user (SRS §5.2; optional, AR-09).
 * Contents stay in memory only (NFR-PRIV-05).
 */
interface NotificationSource {
    /** Whether the user has granted notification access (FR-EVT-07, FR-ONB-04). */
    val granted: Flow<Boolean>

    /** The current notifications, newest first (FR-EVT-04, FR-GRID-11). */
    val notifications: Flow<List<NotificationItem>>

    /** Dismisses one notification (FR-EVT-05). */
    suspend fun dismiss(key: String)

    /** Dismisses every clearable notification (FR-EVT-06). */
    suspend fun dismissAll()

    /** Opens the notification's target (FR-EVT-04). */
    suspend fun open(key: String)

    /** Used while the feature is off or access is not granted (DESIGN §5.5). */
    object Disabled : NotificationSource {
        override val granted: Flow<Boolean> = flowOf(false)
        override val notifications: Flow<List<NotificationItem>> = emptyFlow()
        override suspend fun dismiss(key: String) = Unit
        override suspend fun dismissAll() = Unit
        override suspend fun open(key: String) = Unit
    }
}

data class NotificationItem(
    val key: String,
    val app: AppId?,
    val title: String,
    val text: String,
    val postedAtMillis: Long,
    val clearable: Boolean,
)
