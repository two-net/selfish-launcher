package io.github.twonet.selfish.domain.theme

import io.github.twonet.selfish.domain.model.Appearance
import io.github.twonet.selfish.domain.model.AppearanceMode

/**
 * The appearance the launcher is drawn in (FR-THM-01, DESIGN §4.4). An
 * explicit Light or Dark wins over the device (FR-THM-06). System follows
 * the device's dark-theme setting, [systemDark] (FR-THM-03), and looks like
 * Light where the device has none (`null`, FR-THM-04).
 */
fun resolveAppearance(mode: AppearanceMode, systemDark: Boolean?): Appearance = when (mode) {
    AppearanceMode.Light -> Appearance.Light
    AppearanceMode.Dark -> Appearance.Dark
    AppearanceMode.System -> if (systemDark == true) Appearance.Dark else Appearance.Light
}
