package io.github.twonet.selfish.domain.theme

import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.Appearance
import io.github.twonet.selfish.domain.model.AppearanceMode
import kotlin.test.Test
import kotlin.test.assertEquals

/** Resolving the appearance mode against the device's dark-theme setting (DESIGN §4.4). */
class ThemeResolverTest {
    private val deviceSettings = listOf(true, false, null)

    @Test
    @Verifies("FR-THM-01", "FR-THM-03")
    fun `System follows the device's dark theme`() {
        assertEquals(Appearance.Dark, resolveAppearance(AppearanceMode.System, systemDark = true))
        assertEquals(Appearance.Light, resolveAppearance(AppearanceMode.System, systemDark = false))
    }

    @Test
    @Verifies("FR-THM-04")
    fun `System looks like Light on a device with no dark-theme setting`() {
        assertEquals(Appearance.Light, resolveAppearance(AppearanceMode.System, systemDark = null))
    }

    @Test
    @Verifies("FR-THM-01", "FR-THM-06")
    fun `an explicit Light or Dark wins over whatever the device says`() {
        for (systemDark in deviceSettings) {
            assertEquals(Appearance.Light, resolveAppearance(AppearanceMode.Light, systemDark), "Light, device dark = $systemDark")
            assertEquals(Appearance.Dark, resolveAppearance(AppearanceMode.Dark, systemDark), "Dark, device dark = $systemDark")
        }
    }
}
