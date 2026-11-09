package io.github.twonet.selfish.domain.fakes

import io.github.twonet.selfish.domain.ports.NotificationItem
import io.github.twonet.selfish.domain.ports.NotificationSource
import kotlinx.coroutines.flow.MutableStateFlow

class FakeNotificationSource : NotificationSource {
    override val granted = MutableStateFlow(true)
    override val notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    val opened = mutableListOf<String>()

    override suspend fun dismiss(key: String) {
        notifications.value = notifications.value.filterNot { it.key == key }
    }

    override suspend fun dismissAll() {
        notifications.value = notifications.value.filterNot { it.clearable }
    }

    override suspend fun open(key: String) {
        opened += key
    }
}
