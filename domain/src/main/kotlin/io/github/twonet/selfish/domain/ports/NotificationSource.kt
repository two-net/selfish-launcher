package io.github.twonet.selfish.domain.ports

import io.github.twonet.selfish.domain.model.Bounds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Notifications, with access granted by the user (SRS §5.2; optional, AR-09).
 * Contents stay in memory only (NFR-PRIV-05).
 */
interface NotificationSource {
    /**
     * Whether the user has granted notification access (FR-EVT-07,
     * FR-ONB-04). Checked again on every collection, which the store starts
     * each time the launcher comes on screen, and whenever the listener
     * connects or disconnects.
     */
    val granted: Flow<Boolean>

    /**
     * The notifications to show, newest first (FR-EVT-04): the current list
     * on collection, then again after every change. Nothing is emitted while
     * access isn't granted; that arrives through [granted].
     */
    val notifications: Flow<List<NotificationItem>>

    /**
     * Opens the system's screen where the user grants this launcher
     * notification access (FR-EVT-07, FR-ONB-04). Here rather than in
     * [AppCatalog.open], because only this adapter knows its listener, and
     * so it stays removable (AR-09).
     */
    suspend fun requestAccess()

    /**
     * Opens what the notification [key] points to (FR-EVT-04): growing from
     * [from] when given and [animate], and with no window animation when not
     * [animate] (FR-ANIM-15).
     */
    suspend fun open(key: String, from: Bounds?, animate: Boolean)

    /**
     * Dismisses each of [refs] that can be dismissed and hasn't changed
     * since it was shown, so a notification updated or posted while a
     * remorse timer ran is never cleared unseen (FR-EVT-05, FR-EVT-06).
     */
    suspend fun dismiss(refs: Set<NotificationRef>)

    /** Used while the feature is off or access is not granted (DESIGN §5.5). */
    object Disabled : NotificationSource {
        override val granted: Flow<Boolean> = flowOf(false)
        override val notifications: Flow<List<NotificationItem>> = emptyFlow()
        override suspend fun requestAccess() = Unit
        override suspend fun open(key: String, from: Bounds?, animate: Boolean) = Unit
        override suspend fun dismiss(refs: Set<NotificationRef>) = Unit
    }
}

/**
 * One notification as Events shows it (FR-EVT-04). Never saved, logged or
 * sent anywhere (NFR-PRIV-05), so [toString] leaves out what it says.
 */
data class NotificationItem(
    /** The system's key for it, the same across updates. */
    val key: String,
    val packageName: String,
    /** The serial number of the profile that posted it, as in `AppId.userSerial`. */
    val userSerial: Long,
    /** The posting app's name as the system knows it, or empty if unknown. */
    val appLabel: String,
    val title: String,
    val text: String,
    /** The time it shows, in milliseconds since the epoch. */
    val timeMillis: Long,
    /** When this version of it was posted; it changes whenever it is updated. */
    val postedAtMillis: Long,
    /** Whether the user may dismiss it; ongoing ones can't be (FR-EVT-05, FR-EVT-06). */
    val clearable: Boolean,
) {
    /** This notification as it is now, for dismissing exactly what was shown. */
    val ref: NotificationRef get() = NotificationRef(key, postedAtMillis)

    override fun toString(): String = "NotificationItem(key=$key, package=$packageName)"
}

/** A notification as it was when shown: its key and the time that version of it was posted. */
data class NotificationRef(val key: String, val postedAtMillis: Long)
