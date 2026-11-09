package io.github.twonet.selfish.systemtest

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import io.github.twonet.selfish.domain.Verifies
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The launcher as the system sees it (level I, DESIGN §8.3). Runs in its own
 * process (self-instrumenting), so it can kill the launcher's.
 *
 * Safe to run on a personal device: each test puts the device's Home app
 * back as it was. The first-run test clears the launcher's own data.
 */
@RunWith(AndroidJUnit4::class)
class SystemIntegrationTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    /** The Home app before the test, or `null` if there was none. */
    private var originalHome: String? = null

    @Before
    fun rememberHome() {
        originalHome = homeRoleHolder()
    }

    @After
    fun restoreHome() {
        val home = originalHome
        if (home != null) setHomeApp(home) else shell("cmd role remove-role-holder $HOME_ROLE $LAUNCHER")
        device.pressHome()
    }

    @Test
    @Verifies("FR-GEN-02")
    fun aFirstLaunchWhileAnotherAppIsHomeOffersToBecomeTheDefault() {
        val other = otherHomeApp()
        assumeTrue("The device has no other Home app to hand the role to", other != null)
        setHomeApp(other!!)
        shell("pm clear $LAUNCHER") // A first run: nothing saved.

        shell("am start -W -n $LAUNCHER/.MainActivity")

        val prompt = device.wait(Until.findObject(By.pkg(PERMISSION_CONTROLLER)), TIMEOUT_MS)
        assertNotNull(prompt, "The system's default-Home prompt didn't appear")
        device.pressBack() // Decline; the role is put back in restoreHome().
    }

    @Test
    @Verifies("FR-GEN-03")
    fun homeReturnsToTheLauncherFromAnotherAppAndDoesNothingOnHome() {
        showLauncherAsHome()

        shell("am start -W -a android.settings.SETTINGS")
        assertTrue(device.wait(Until.gone(By.pkg(LAUNCHER).depth(0)), TIMEOUT_MS), "Settings didn't open")
        device.pressHome()
        assertLauncherInFront()

        val pid = pidOfLauncher()
        device.pressHome()
        device.waitForIdle()
        assertLauncherInFront()
        assertEquals(pid, pidOfLauncher(), "Home on Home must not restart the launcher")
    }

    @Test
    @Verifies("FR-GEN-04")
    fun backOnHomeDoesNothing() {
        showLauncherAsHome()
        val pid = pidOfLauncher()

        device.pressBack()
        device.waitForIdle()

        assertLauncherInFront()
        assertEquals(pid, pidOfLauncher())
    }

    @Test
    @Verifies("FR-GEN-08")
    fun homeReopensTheLauncherAfterTheSystemEndsItsProcess() {
        showLauncherAsHome()
        val before = pidOfLauncher()
        shell("am start -W -a android.settings.SETTINGS")
        assertTrue(device.wait(Until.gone(By.pkg(LAUNCHER).depth(0)), TIMEOUT_MS), "Settings didn't open")

        shell("am kill $LAUNCHER")
        assertTrue(waitFor { pidOfLauncher() != before }, "The launcher's process wasn't ended")

        device.pressHome()

        assertLauncherInFront()
        assertNotEquals("", pidOfLauncher())
        assertNotEquals(before, pidOfLauncher())
    }

    private fun showLauncherAsHome() {
        setHomeApp(LAUNCHER)
        device.pressHome()
        assertLauncherInFront()
    }

    private fun assertLauncherInFront() {
        assertTrue(device.wait(Until.hasObject(By.pkg(LAUNCHER).depth(0)), TIMEOUT_MS), "The launcher isn't in front")
    }

    private fun homeRoleHolder(): String? =
        shell("cmd role get-role-holders $HOME_ROLE").split(';', '\n').map { it.trim() }.firstOrNull { it.isNotEmpty() }

    /** Makes [packageName] the Home app. The role is exclusive, so this replaces the current one. */
    private fun setHomeApp(packageName: String) {
        shell("cmd role add-role-holder $HOME_ROLE $packageName")
        assertTrue(waitFor { homeRoleHolder() == packageName }, "Couldn't make $packageName the Home app")
    }

    /** Another app that can be Home, other than the system's fallback. */
    private fun otherHomeApp(): String? =
        shell("cmd package query-activities --components -a android.intent.action.MAIN -c android.intent.category.HOME")
            .lines()
            .map { it.trim().substringBefore('/') }
            .firstOrNull { it.isNotEmpty() && it != LAUNCHER && it != "com.android.settings" && '.' in it }

    private fun pidOfLauncher(): String = shell("pidof $LAUNCHER").trim()

    private fun waitFor(condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(POLL_MS)
        }
        return condition()
    }

    private fun shell(command: String): String = device.executeShellCommand(command)

    private companion object {
        const val LAUNCHER = "io.github.twonet.selfish"
        const val HOME_ROLE = "android.app.role.HOME"
        const val TIMEOUT_MS = 10_000L
        const val POLL_MS = 200L

        /** The system's role prompt; its package differs between Android builds. */
        val PERMISSION_CONTROLLER: Pattern = Pattern.compile(".*permissioncontroller.*")
    }
}
