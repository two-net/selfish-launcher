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
import kotlin.time.Duration.Companion.seconds

/**
 * [AndroidOsSettings] against the device's real settings (level A, DESIGN
 * §8.3). The animator scale is changed through the shell, as the "Remove
 * animations" switch does, and put back afterwards.
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

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .bufferedReader()
            .use { it.readText() }

    private companion object {
        const val ANIMATOR_SCALE = "animator_duration_scale"
        val TIMEOUT = 10.seconds
    }
}
