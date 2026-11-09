package io.github.twonet.selfish.adapters.notifications

import android.content.Context
import io.github.twonet.selfish.domain.ports.NotificationItem
import io.github.twonet.selfish.domain.ports.NotificationSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * [NotificationSource] over a `NotificationListenerService` (DESIGN §5.1).
 * Optional (AR-09): nothing outside this package refers to it except the
 * composition root. Stub until #10, which also declares the listener service.
 */
@Suppress("unused")
class ListenerNotificationSource(private val context: Context) : NotificationSource {
    override val granted: Flow<Boolean> = flowOf(false)

    override val notifications: Flow<List<NotificationItem>> = emptyFlow()

    override suspend fun dismiss(key: String) = Unit

    override suspend fun dismissAll() = Unit

    override suspend fun open(key: String) = Unit
}
