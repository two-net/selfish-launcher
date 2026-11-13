package io.github.twonet.selfish.adapters.os

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.twonet.selfish.adapters.ForegroundActivity
import io.github.twonet.selfish.domain.Verifies
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * [AndroidOsSettings] against the device's real settings (level A, DESIGN
 * §8.3). The animator scale, dark theme, Battery Saver and Data Saver are
 * changed through the shell, as their switches do, and put back afterwards.
 */
@RunWith(AndroidJUnit4::class)
class AndroidOsSettingsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val settings = AndroidOsSettings(instrumentation.targetContext, ForegroundActivity())
    private var savedScale = ""

    @Before
    fun saveScale() {
        savedScale = shell("settings get global $ANIMATOR_SCALE").trim()
        shell("settings put global $ANIMATOR_SCALE 1")
    }

    @After
    fun restoreScale() {
        if (savedScale.isEmpty() || savedScale == "null") {
            shell("settings delete global $ANIMATOR_SCALE")
        } else {
            shell("settings put global $ANIMATOR_SCALE $savedScale")
        }
    }

    @Test
    @Verifies("FR-ANIM-15", "NFR-A11Y-05")
    fun removingAnimationsIsReportedAndPuttingThemBackIsToo() = runBlocking<Unit> {
        assertFalse(settings.animationsOff.first())

        withTimeout(TIMEOUT) {
            settings.animationsOff.first { off ->
                // Changed once the flow is listening, so the change itself is observed.
                if (!off) shell("settings put global $ANIMATOR_SCALE 0")
                off
            }
        }
        withTimeout(TIMEOUT) {
            settings.animationsOff.first { off ->
                if (off) shell("settings put global $ANIMATOR_SCALE 0.5")
                !off
            }
        }
    }

    @Test
    @Verifies("FR-THM-03")
    fun theDarkThemeIsReportedAndSwitchingItIsToo() = runBlocking<Unit> {
        // "Night mode: yes", "no", "auto" or "custom", put back afterwards.
        val saved = shell("cmd uimode night").substringAfter(":").trim()
        try {
            shell("cmd uimode night no")
            withTimeout(TIMEOUT) { settings.systemDark.first { it == false } }

            withTimeout(TIMEOUT) {
                settings.systemDark.first { dark ->
                    // Changed once the flow is listening, so the change itself is observed.
                    if (dark == false) shell("cmd uimode night yes")
                    dark == true
                }
            }
            withTimeout(TIMEOUT) {
                settings.systemDark.first { dark ->
                    if (dark == true) shell("cmd uimode night no")
                    dark == false
                }
            }
        } finally {
            if (saved.isNotEmpty()) shell("cmd uimode night $saved")
        }
    }

    @Test
    @Verifies("FR-WP-06")
    fun batterySaverIsReported() = runBlocking {
        val saved = shell("settings get global low_power").trim()
        // Battery Saver turns itself off while the device charges.
        shell("cmd battery unplug")
        try {
            shell("cmd power set-mode 1")
            assertTrue(settings.batterySaver())
            shell("cmd power set-mode 0")
            assertFalse(settings.batterySaver())
        } finally {
            shell("cmd power set-mode ${if (saved == "1") 1 else 0}")
            shell("cmd battery reset")
        }
    }

    @Test
    @Verifies("FR-WP-06")
    fun dataSaverIsReported() = runBlocking {
        val saved = shell("cmd netpolicy get restrict-background")
        try {
            shell("cmd netpolicy set restrict-background true")
            assertTrue(settings.networkState().dataSaver)
            shell("cmd netpolicy set restrict-background false")
            assertFalse(settings.networkState().dataSaver)
        } finally {
            shell("cmd netpolicy set restrict-background ${saved.contains("enabled")}")
        }
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .bufferedReader()
            .use { it.readText() }

    private companion object {
        const val ANIMATOR_SCALE = "animator_duration_scale"
        val TIMEOUT = 10.seconds
    }
}
