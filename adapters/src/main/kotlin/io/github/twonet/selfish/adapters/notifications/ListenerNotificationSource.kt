package io.github.twonet.selfish.adapters.notifications

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import io.github.twonet.selfish.adapters.ForegroundActivity
import io.github.twonet.selfish.adapters.withoutAnimation
import io.github.twonet.selfish.domain.model.Bounds
import io.github.twonet.selfish.domain.ports.NotificationItem
import io.github.twonet.selfish.domain.ports.NotificationRef
import io.github.twonet.selfish.domain.ports.NotificationSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * [NotificationSource] over [LauncherNotificationListener] (DESIGN §5.1,
 * §5.5). Optional (AR-09): nothing outside this package refers to it except
 * the composition root.
 *
 * Notifications are read from the listener when asked for, while the store
 * follows them, and are never kept, written or logged here (NFR-PRIV-05).
 *
 * [excludedPackage] is left out of the list; it is the launcher itself.
 * Tests pass `null` to see their own notifications. [foreground] supplies
 * the Activity that system screens open over, and the view an opened
 * notification's window grows from.
 */
class ListenerNotificationSource internal constructor(
    context: Context,
    private val excludedPackage: String?,
    private val foreground: ForegroundActivity,
) : NotificationSource {
    constructor(context: Context, foreground: ForegroundActivity) :
        this(context, context.applicationContext.packageName, foreground)

    private val appContext = context.applicationContext
    private val notificationManager = appContext.getSystemService(NotificationManager::class.java)
    private val userManager = appContext.getSystemService(UserManager::class.java)
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)
    private val component = ComponentName(appContext, LauncherNotificationListener::class.java)

    /** Apps' names by package and profile; they are looked up once per process. */
    private val labels = ConcurrentHashMap<Pair<String, UserHandle>, String>()

    /**
     * Checked on every collection, and again whenever the listener connects
     * or disconnects. The system has no broadcast for access changes; the
     * store collects this afresh each time the launcher comes on screen,
     * which covers a return from the system's access screen. A disconnect
     * alone isn't taken as a revocation: the system also rebinds listeners,
     * for example after an update.
     */
    override val granted: Flow<Boolean> = connected
        .map { notificationManager.isNotificationListenerAccessGranted(component) }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    /**
     * The current notifications, read again after every change while the
     * listener is connected. While it isn't, nothing is emitted: losing
     * access arrives through [granted].
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override val notifications: Flow<List<NotificationItem>> = connected
        .flatMapLatest { listener -> if (listener == null) emptyFlow() else changes.mapNotNull { snapshot(listener) } }
        .flowOn(Dispatchers.Default)

    /** The detail screen for this listener where there is one (API 30+), or else the list of listeners. */
    override suspend fun requestAccess() = withContext(Dispatchers.Main) {
        val detail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
        } else {
            null
        }
        if (detail == null || !start(detail)) start(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        Unit
    }

    /**
     * Sends the notification's content intent with the launcher's own right
     * to start activities, which it has because it is on screen: the app
     * that posted it is usually in the background, and can't on its own
     * (API 34+). Without an intent, or if it was cancelled, the app is
     * opened instead. As the system's shade does, an auto-cancelling
     * notification is then dismissed.
     */
    override suspend fun open(key: String, from: Bounds?, animate: Boolean) = withContext(Dispatchers.Main) {
        val listener = connected.value ?: return@withContext
        val sbn = active(listener, arrayOf(key))?.firstOrNull() ?: return@withContext
        val notification = sbn.notification
        val bounds = from?.let { Rect(it.left, it.top, it.right, it.bottom) }?.takeUnless { it.isEmpty }
        val options = if (animate) growFrom(bounds) else withoutAnimation(appContext)
        val sent = notification.contentIntent?.let { send(it, options) } ?: false
        if (!sent) launchApp(sbn, bounds, options)
        val flags = notification.flags
        if (flags and Notification.FLAG_AUTO_CANCEL != 0 && flags and Notification.FLAG_FOREGROUND_SERVICE == 0) {
            listener.cancelNotification(key)
        }
    }

    override suspend fun dismiss(refs: Set<NotificationRef>) = withContext(Dispatchers.IO) {
        val listener = connected.value ?: return@withContext
        val current = active(listener, refs.map { it.key }.toTypedArray()) ?: return@withContext
        val keys = NotificationRules.toCancel(current.map { posted(it, null) }, refs)
        if (keys.isNotEmpty()) listener.cancelNotifications(keys.toTypedArray())
    }

    /** The listed notifications now, or `null` if the listener has just lost its connection. */
    private fun snapshot(listener: NotificationListenerService): List<NotificationItem>? {
        val active = active(listener, null) ?: return null
        val ranking = try {
            listener.currentRanking
        } catch (_: SecurityException) {
            null
        }
        val byKey = active.associateBy { it.key }
        return NotificationRules.shown(active.map { posted(it, ranking) }, excludedPackage).map { shown ->
            val sbn = byKey.getValue(shown.key)
            NotificationItem(
                key = shown.key,
                packageName = shown.packageName,
                userSerial = shown.userSerial,
                appLabel = label(sbn.packageName, sbn.user),
                title = shown.title,
                text = shown.text,
                timeMillis = shown.timeMillis,
                postedAtMillis = shown.postedAtMillis,
                clearable = shown.clearable,
            )
        }
    }

    /** The active notifications with [keys], or all of them; `null` once the listener isn't connected. */
    private fun active(listener: NotificationListenerService, keys: Array<String>?): Array<StatusBarNotification>? = try {
        if (keys == null) listener.activeNotifications else listener.getActiveNotifications(keys)
    } catch (_: SecurityException) {
        null
    }

    private fun posted(sbn: StatusBarNotification, ranking: RankingMap?): Posted {
        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.text(Notification.EXTRA_TITLE)
            ?: extras.text(Notification.EXTRA_TITLE_BIG)
            ?: extras.text(Notification.EXTRA_CONVERSATION_TITLE)
        val text = extras.text(Notification.EXTRA_TEXT)
            ?: extras.text(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.lastOrNull()?.toString()
            ?: extras.text(Notification.EXTRA_SUB_TEXT)
        return Posted(
            key = sbn.key,
            packageName = sbn.packageName,
            userSerial = userManager.getSerialNumberForUser(sbn.user),
            groupKey = sbn.groupKey,
            groupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            title = title.orEmpty(),
            text = text.orEmpty(),
            timeMillis = notification.`when`.takeIf { it > 0 } ?: sbn.postTime,
            postedAtMillis = sbn.postTime,
            clearable = sbn.isClearable,
            hidden = ranking?.let { hidden(it, sbn.key) } ?: false,
        )
    }

    /** Hidden by the system: its app is suspended, or Do Not Disturb keeps it out of lists. */
    private fun hidden(ranking: RankingMap, key: String): Boolean {
        val rank = Ranking()
        if (!ranking.getRanking(key, rank)) return false
        return rank.isSuspended ||
            rank.suppressedVisualEffects and NotificationManager.Policy.SUPPRESSED_EFFECT_NOTIFICATION_LIST != 0
    }

    /** The app's name as the system shows it, or empty if it can't be read, for example for a package hidden from the launcher. */
    private fun label(packageName: String, user: UserHandle): String = labels.getOrPut(packageName to user) {
        try {
            launcherApps.getApplicationInfo(packageName, 0, user).loadLabel(appContext.packageManager).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            ""
        }
    }

    private fun send(intent: PendingIntent, animation: ActivityOptions?): Boolean {
        val options = animation ?: ActivityOptions.makeBasic()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        }
        return try {
            intent.send(appContext, 0, null, null, null, null, options.toBundle())
            true
        } catch (_: PendingIntent.CanceledException) {
            false
        }
    }

    private fun launchApp(sbn: StatusBarNotification, bounds: Rect?, options: ActivityOptions?) {
        val activity = launcherApps.getActivityList(sbn.packageName, sbn.user).firstOrNull() ?: return
        launcherApps.startMainActivity(activity.componentName, sbn.user, bounds, options?.toBundle())
    }

    /** Options that grow the opened window from [bounds] (FR-ANIM-02), or `null` without a visible launcher. */
    private fun growFrom(bounds: Rect?): ActivityOptions? {
        if (bounds == null) return null
        val root = foreground.current?.window?.decorView ?: return null
        return ActivityOptions.makeScaleUpAnimation(root, bounds.left, bounds.top, bounds.width(), bounds.height())
    }

    /** Starts a system screen over the launcher, so Back returns to it. */
    private fun start(intent: Intent): Boolean = try {
        val activity = foreground.current
        if (activity != null) activity.startActivity(intent) else appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    internal companion object {
        /** The listener while the system has it connected. */
        val connected = MutableStateFlow<NotificationListenerService?>(null)

        /** Counts changes the listener has seen. Only a count: never what changed. */
        private val changes = MutableStateFlow(0L)

        fun changed() = changes.update { it + 1 }

        private fun Bundle.text(key: String): String? = getCharSequence(key)?.toString()?.takeIf { it.isNotBlank() }
    }
}
