package io.github.twonet.selfish.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Status-bar and navigation-bar icons that stay legible over the surface
 * beneath them (FR-THM-08, FR-HOME-01, DESIGN §6.1). Every surface shares
 * the appearance's glass, scrim and dim, so the icons are dark in Light and
 * light in [dark].
 *
 * The bars themselves stay transparent. With 3-button navigation, the system
 * puts its own scrim behind the buttons and picks it by the same flag: light
 * behind dark buttons, dark behind light ones. With gesture navigation there
 * is none, and the wallpaper runs to the edge (UI-07).
 *
 * The bars' appearance is part of the UI toolkit, so it belongs here rather
 * than in an adapter (DESIGN §3.1).
 */
@Composable
fun SystemBars(dark: Boolean) {
    val view = LocalView.current
    val window = view.context.findActivity()?.window ?: return
    SideEffect {
        WindowCompat.getInsetsController(window, view).run {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}
