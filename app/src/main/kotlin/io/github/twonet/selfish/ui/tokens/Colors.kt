package io.github.twonet.selfish.ui.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Colors for one appearance (FR-THM-05). Every backdrop is translucent, so
 * the wallpaper always shows through (UI-01). The alphas are as low as the
 * contrast targets allow: `DesignTokensTest` checks every text color on every
 * backdrop over pure white and pure black wallpapers (NFR-A11Y-03).
 */
@Immutable
class LauncherColors(
    /** Body text, labels and icons at primary emphasis (UI-04). */
    val textPrimary: Color,
    /** Text at secondary emphasis; still 4.5:1 on every backdrop (UI-04). */
    val textSecondary: Color,
    /** Panels, cards and menus that float over the wallpaper (UI-01). */
    val glass: Color,
    /**
     * The strongest scrim behind text drawn straight on the wallpaper, such
     * as the clock. Home weakens it over wallpapers that don't need it.
     */
    val scrim: Color,
    /** The App Grid's backdrop at full progress; it follows the gesture (FR-ANIM-01). */
    val dim: Color,
    /**
     * The highlight when the wallpaper gives none (FR-AMB-03). Used only for
     * headers and states, never for body text (FR-AMB-04), so it needs 3:1.
     */
    val highlightDefault: Color,
)

/** Light: dark text on light glass. */
val LightColors = LauncherColors(
    textPrimary = Color(0xFF111111),
    textSecondary = Color(0xFF3A3A3A),
    glass = Color(0xADFFFFFF),
    scrim = Color(0xADFFFFFF),
    dim = Color(0xADFFFFFF),
    highlightDefault = Color(0xFF005A70),
)

/** Dark: light text on dark glass. */
val DarkColors = LauncherColors(
    textPrimary = Color(0xFFF5F5F5),
    textSecondary = Color(0xFFCCCCCC),
    glass = Color(0xAD000000),
    scrim = Color(0xAD000000),
    dim = Color(0xAD000000),
    highlightDefault = Color(0xFF6CCFF6),
)
