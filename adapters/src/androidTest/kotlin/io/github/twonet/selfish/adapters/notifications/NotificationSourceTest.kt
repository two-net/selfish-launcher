package io.github.twonet.selfish.adapters.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.UserManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.twonet.selfish.adapters.ForegroundActivity
import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.ports.NotificationItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * The notification source against the real notification listener (level
 * A, DESIGN §8.1). The test package grants itself listener access through
 * `cmd notification`, and takes it back afterwards. It looks only at the
 * notifications it posts itself, so it is safe on a personal device: others
 * are read in memory by the source, as in the launcher, and never asserted
 * on or printed (NFR-PRIV-05).
 */
@RunWith(AndroidJUnit4::class)
class NotificationSourceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val own = context.packageName
    private val listener = ComponentName(context, LauncherNotificationListener::class.java)
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val serial = context.getSystemService(UserManager::class.java).getSerialNumberForUser(Process.myUserHandle())

    /** Sees this test package's notifications, which a launcher leaves out of its own. */
    private val source = ListenerNotificationSource(context, excludedPackage = null, ForegroundActivity())

    @Before
    fun grantAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(own, Manifest.permission.POST_NOTIFICATIONS)
        }
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Tests", NotificationManager.IMPORTANCE_LOW))
        manager.cancelAll()
        allow(true)
        runBlocking { withTimeout(TIMEOUT) { ListenerNotificationSource.connected.first { it != null } } }
    }

    @After
    fun revokeAccess() {
        manager.cancelAll()
        allow(false)
    }

    private fun allow(allowed: Boolean) {
        shell("cmd notification ${if (allowed) "allow" else "disallow"}_listener ${listener.flattenToString()}")
    }

    private fun post(
        id: Int,
        title: String = "Title $id",
        text: String = "Text $id",
        time: Long = System.currentTimeMillis(),
        ongoing: Boolean = false,
        group: String? = null,
        summary: Boolean = false,
        extra: Notification.Builder.() -> Unit = {},
    ) {
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setWhen(time)
            .setOngoing(ongoing)
            .apply {
                if (group != null) setGroup(group)
                setGroupSummary(summary)
                extra()
            }
            .build()
        manager.notify(id, notification)
    }

    /** This package's notifications only. */
    private val ownNotifications: Flow<List<NotificationItem>> =
        source.notifications.map { list -> list.filter { it.packageName == own } }

    private suspend fun awaitOwn(predicate: (List<NotificationItem>) -> Boolean): List<NotificationItem> =
        withTimeout(TIMEOUT) { ownNotifications.first(predicate) }

    @Test
    @Verifies("FR-EVT-07", "FR-ONB-04")
    fun accessFollowsTheSystemSetting() = runBlocking {
        assertTrue(withTimeout(TIMEOUT) { source.granted.first() })

        allow(false)

        withTimeout(TIMEOUT) { source.granted.first { !it } }
        Unit
    }

    @Test
    @Verifies("FR-EVT-04")
    fun postedNotificationsAreListedNewestFirstAndCancelledOnesLeave() = runBlocking {
        val now = System.currentTimeMillis()
        post(1, title = "Older", text = "First", time = now - 60_000)
        post(2, title = "Newer", text = "Second", time = now)

        val listed = awaitOwn { it.size == 2 }

        assertEquals(listOf("Newer", "Older"), listed.map { it.title })
        assertEquals(listOf("Second", "First"), listed.map { it.text })
        assertTrue(listed.all { it.userSerial == serial && it.clearable })

        manager.cancel(2)

        assertEquals(listOf("Older"), awaitOwn { it.size == 1 }.map { it.title })
    }

    @Test
    @Verifies("FR-EVT-04")
    fun summariesWithTheirNotificationsAndEmptyOnesAreLeftOut() = runBlocking {
        post(1, title = "Summary", text = "2 new", group = GROUP, summary = true)
        post(2, title = "One", group = GROUP)
        post(3, title = "", text = "")
        post(4, title = "Last")

        val listed = awaitOwn { list -> list.any { it.title == "Last" } && list.any { it.title == "One" } }

        assertEquals(setOf("One", "Last"), listed.map { it.title }.toSet())
    }

    @Test
    @Verifies("FR-EVT-04")
    fun theLaunchersOwnNotificationsAreLeftOut() = runBlocking {
        val launcher = ListenerNotificationSource(context, excludedPackage = own, ForegroundActivity())
        post(1)
        awaitOwn { it.size == 1 }

        val listed = withTimeout(TIMEOUT) { launcher.notifications.first() }

        assertTrue(listed.none { it.packageName == own })
    }

    @Test
    @Verifies("FR-EVT-05", "FR-EVT-06")
    fun ongoingNotificationsAreListedButNeverDismissed() = runBlocking {
        post(1, title = "Ongoing", ongoing = true)
        val ongoing = awaitOwn { it.size == 1 }.single()
        assertEquals(false, ongoing.clearable)

        source.dismiss(setOf(ongoing.ref))
        post(2, title = "Marker")

        assertEquals(setOf("Ongoing", "Marker"), awaitOwn { it.size == 2 }.map { it.title }.toSet())
    }

    @Test
    @Verifies("FR-EVT-05", "FR-EVT-06")
    fun dismissingRemovesOnlyWhatWasShown() = runBlocking {
        post(1, title = "Seen")
        post(2, title = "Updated")
        val shown = awaitOwn { it.size == 2 }
        // An update after it was shown: a new message under the same key.
        post(2, title = "Updated again")
        awaitOwn { list -> list.any { it.title == "Updated again" } }

        source.dismiss(shown.mapTo(HashSet()) { it.ref })

        assertEquals(listOf("Updated again"), awaitOwn { it.size == 1 }.map { it.title })
    }

    @Test
    @Verifies("FR-EVT-04")
    fun openingSendsTheContentIntentAndAutoCancels() = runBlocking {
        val received = CompletableDeferred<Unit>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                received.complete(Unit)
            }
        }
        register(receiver)
        try {
            val intent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_OPENED).setPackage(own),
                PendingIntent.FLAG_IMMUTABLE,
            )
            post(1) {
                setContentIntent(intent)
                setAutoCancel(true)
            }
            val item = awaitOwn { it.size == 1 }.single()

            source.open(item.key, null)

            assertNotNull(withTimeoutOrNull(TIMEOUT) { received.await() }, "The content intent was sent")
            assertNotNull(withTimeoutOrNull(TIMEOUT) { ownNotifications.first { it.isEmpty() } }, "Opening it cancelled it")
            Unit
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    /**
     * Registers [receiver] for the test's own content intent. Before API 33
     * a receiver can't be registered as not exported without AndroidX Core;
     * another app sending this action could at worst end that wait early.
     */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun register(receiver: BroadcastReceiver) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, IntentFilter(ACTION_OPENED), Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, IntentFilter(ACTION_OPENED))
        }
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .bufferedReader()
            .use { it.readText() }

    private companion object {
        val TIMEOUT = 10.seconds
        const val CHANNEL = "selfish-tests"
        const val GROUP = "selfish-test-group"
        const val ACTION_OPENED = "io.github.twonet.selfish.adapters.test.OPENED"
    }
}
