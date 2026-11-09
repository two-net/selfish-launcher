package io.github.twonet.selfish.ui.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Colors for one appearance. Placeholders until #4 sets the glass and contrast-tested values. */
@Immutable
class LauncherColors(
    val textPrimary: Color,
    /** Laid over the wallpaper so text stays legible. */
    val scrim: Color,
)

val LightColors = LauncherColors(
    textPrimary = Color(0xFF111111),
    scrim = Color(0xB3FFFFFF),
)

val DarkColors = LauncherColors(
    textPrimary = Color(0xFFF5F5F5),
    scrim = Color(0xB3000000),
)
