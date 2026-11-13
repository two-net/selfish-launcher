package io.github.twonet.selfish.domain.fakes

import io.github.twonet.selfish.domain.model.Bounds
import io.github.twonet.selfish.domain.ports.NotificationItem
import io.github.twonet.selfish.domain.ports.NotificationRef
import io.github.twonet.selfish.domain.ports.NotificationSource
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A [NotificationSource] whose access and notifications tests set. Their
 * `subscriptionCount`s show when the store follows them.
 */
class FakeNotificationSource : NotificationSource {
    override val granted = MutableStateFlow(true)
    override val notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    var accessRequests = 0
    val opened = mutableListOf<Opened>()
    val dismissed = mutableListOf<Set<NotificationRef>>()

    override suspend fun requestAccess() {
        accessRequests++
    }

    override suspend fun open(key: String, from: Bounds?, animate: Boolean) {
        opened += Opened(key, from, animate)
    }

    /** Like the system: dismisses those still as they were shown that may be dismissed. */
    override suspend fun dismiss(refs: Set<NotificationRef>) {
        dismissed += refs
        notifications.value = notifications.value.filterNot { it.clearable && it.ref in refs }
    }

    data class Opened(val key: String, val from: Bounds?, val animate: Boolean = true)
}
