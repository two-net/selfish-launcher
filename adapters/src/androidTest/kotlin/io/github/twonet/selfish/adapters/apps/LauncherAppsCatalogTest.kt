package io.github.twonet.selfish.adapters.apps

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.UserManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.twonet.selfish.adapters.ForegroundActivity
import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.AppId
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

/**
 * The app catalog against the real `LauncherApps` (level A, DESIGN §8.1).
 *
 * Changes are simulated by enabling and disabling this test package's own
 * [StubLauncherActivity], which needs no permission and touches no other
 * app, so it is safe on a personal device. Real installs, updates and
 * uninstalls are checked by hand (REVIEW-CHECKLIST.md).
 */
@RunWith(AndroidJUnit4::class)
class LauncherAppsCatalogTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val stub = ComponentName(context, StubLauncherActivity::class.java)
    private val serial = context.getSystemService(UserManager::class.java).getSerialNumberForUser(Process.myUserHandle())
    private val stubId = AppId(stub.packageName, stub.className, serial)

    /** Sees this test package, which a launcher normally leaves out. */
    private val catalog = LauncherAppsCatalog(context, excludedPackage = null)

    @Before
    @After
    fun disableStub() = setStub(enabled = false)

    private fun setStub(enabled: Boolean) {
        val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        // DONT_KILL_APP, because this test runs in that app's process.
        context.packageManager.setComponentEnabledSetting(stub, state, PackageManager.DONT_KILL_APP)
    }

    private fun List<AppEntry>.stub(): AppEntry? = firstOrNull { it.id == stubId }

    /** Collects [this] until an emission matches [predicate]. */
    private suspend fun <T> Flow<T>.awaitFirst(predicate: (T) -> Boolean): T =
        withTimeout(TIMEOUT) { first(predicate) }

    /** Receives until an emission matches [predicate], within [TIMEOUT]. */
    private suspend fun ReceiveChannel<List<AppEntry>>.receiveUntil(predicate: (List<AppEntry>) -> Boolean): List<AppEntry> =
        withTimeout(TIMEOUT) {
            var apps = receive()
            while (!predicate(apps)) apps = receive()
            apps
        }

    @Test
    @Verifies("FR-GEN-06")
    fun enablingAndDisablingAnAppShowsWhileTheListIsCollected() = runBlocking {
        val updates = catalog.apps().produceIn(this)
        try {
            assertNull(withTimeout(TIMEOUT) { updates.receive() }.stub(), "A disabled app is listed")

            setStub(enabled = true)
            val shown = assertNotNull(updates.receiveUntil { it.stub() != null }.stub())
            assertEquals("Selfish stub", shown.label)
            assertTrue(shown.version > 0, "A changed package has a new version, so its icon is reloaded")

            setStub(enabled = false)
            assertNull(updates.receiveUntil { it.stub() == null }.stub())
        } finally {
            updates.cancel()
        }
    }

    @Test
    @Verifies("FR-GEN-06")
    fun theLauncherLeavesItselfOut() = runBlocking {
        setStub(enabled = true)
        assertNotNull(catalog.apps().awaitFirst { it.stub() != null }.stub())

        val apps = withTimeout(TIMEOUT) { LauncherAppsCatalog(context, ForegroundActivity()).apps().first() }

        assertTrue(apps.none { it.id.packageName == context.packageName })
    }

    /**
     * R-4 (DESIGN §9): with only the `<queries>` block of DESIGN §5.6, the
     * catalog sees every launchable app the system knows about.
     */
    @Test
    @Verifies("FR-GEN-06")
    fun everyLaunchableAppIsListed() = runBlocking {
        val expected = shell("cmd package query-activities --components --user current -a android.intent.action.MAIN -c android.intent.category.LAUNCHER")
            .lines()
            .mapNotNull { ComponentName.unflattenFromString(it.trim()) }
            .filter { it.packageName != context.packageName }
            .map { AppId(it.packageName, it.className, serial) }
            .toSet()
        if (expected.isEmpty()) fail("The shell listed no launchable apps; check the command's output format")

        val listed = withTimeout(TIMEOUT) { catalog.apps().first() }.map { it.id }.toSet()

        assertEquals(emptySet(), expected - listed, "Launchable apps missing from the catalog")
    }

    @Test
    @Verifies("UI-05")
    fun iconsAreRenderedAtTheRequestedSize() = runBlocking {
        setStub(enabled = true)
        catalog.apps().awaitFirst { it.stub() != null }

        val icon = assertNotNull(catalog.icon(stubId, ICON_SIZE))

        assertEquals(ICON_SIZE, icon.width)
        assertEquals(ICON_SIZE, icon.height)
        assertTrue(icon.argb.any { it != 0 }, "The icon is blank")
        assertNull(icon.badge, "Apps of the main profile have no badge")
    }

    /**
     * The default apps match what the system itself resolves for each
     * intent, which also shows the `<queries>` block is enough to see them,
     * and each one is a launchable app (FR-HOME-07).
     */
    @Test
    @Verifies("FR-HOME-07")
    fun defaultAppsAreTheSystemsDefaultsAndLaunchable() = runBlocking {
        val launchable = withTimeout(TIMEOUT) { catalog.apps().first() }.map { it.id }

        val defaults = catalog.defaultApps()

        val expected = mapOf(
            "phone" to systemDefault("-a android.intent.action.DIAL"),
            "messaging" to systemDefault("-a android.intent.action.SENDTO -d smsto:"),
            "browser" to systemDefault("-a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d https://"),
            "camera" to systemDefault("-a android.media.action.STILL_IMAGE_CAMERA"),
        ).mapValues { (_, pkg) -> pkg?.takeIf { p -> launchable.any { it.packageName == p } } }
        val actual = mapOf(
            "phone" to defaults.phone,
            "messaging" to defaults.messaging,
            "browser" to defaults.browser,
            "camera" to defaults.camera,
        )

        assertEquals(expected, actual.mapValues { it.value?.packageName })
        actual.values.filterNotNull().forEach { assertTrue(it in launchable, "$it is not a launchable app") }
    }

    /** The package the system starts for an intent without asking, or `null` if it would ask or has nothing. */
    private fun systemDefault(intent: String): String? =
        shell("cmd package resolve-activity --brief --user current $intent")
            .lines()
            .lastOrNull { it.isNotBlank() }
            ?.let { ComponentName.unflattenFromString(it.trim()) }
            ?.packageName
            ?.takeUnless { it == "android" }

    @Test
    fun anAppThatIsGoneHasNoIcon() = runBlocking {
        assertNull(catalog.icon(AppId("io.github.twonet.selfish.missing", "Main", serial), ICON_SIZE))
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .bufferedReader()
            .use { it.readText() }

    private companion object {
        val TIMEOUT = 10.seconds
        const val ICON_SIZE = 96
    }
}
