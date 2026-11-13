package io.github.twonet.selfish.adapters.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * The notification listener the user grants access to (DESIGN §5.1, §5.5).
 * The system creates and binds it; it tells [ListenerNotificationSource]
 * when it connects and disconnects, and that something changed. It never
 * keeps or logs what notifications say (NFR-PRIV-05): the source reads the
 * current ones only while the store follows them.
 */
class LauncherNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        ListenerNotificationSource.connected.value = this
    }

    override fun onListenerDisconnected() = leave()

    override fun onDestroy() {
        leave()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = ListenerNotificationSource.changed()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = ListenerNotificationSource.changed()

    override fun onNotificationRankingUpdate(rankingMap: RankingMap?) = ListenerNotificationSource.changed()

    private fun leave() {
        ListenerNotificationSource.connected.compareAndSet(this, null)
    }
}
