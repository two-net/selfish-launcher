package io.github.twonet.selfish.ui.tokens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.twonet.selfish.domain.theme.Highlight

/**
 * The one set of design tokens (AR-04, UI-08). Every view reads colors, sizes,
 * type and motion from here; no literal values appear anywhere else.
 */
@Immutable
data class LauncherTokens(
    val colors: LauncherColors,
    val type: LauncherType,
    /** The ambience's highlight color (SRS §4.11), for headers and states only (FR-AMB-04). */
    val highlight: Color,
    /** The animation specs, reduced or not (FR-ANIM-15). */
    val motion: LauncherMotion,
)

/**
 * Not static: while colors crossfade, only the views that read them
 * recompose, not everything beneath the theme (FR-ANIM-11).
 */
internal val LocalLauncherTokens = compositionLocalOf {
    LauncherTokens(LightColors, DefaultType, LightColors.highlightDefault, LauncherMotion())
}

/**
 * Provides the tokens for [dark] or light appearance, the ambience's
 * [highlight], which falls back to the appearance's default, and motion,
 * [reducedMotion] or not. The Domain resolves the appearance and the
 * highlight (DESIGN §4.4), and they are passed in from state.
 *
 * Nothing is reloaded when they change (FR-THM-03): every color crossfades to
 * the new appearance over the Long duration (FR-ANIM-11), and a new
 * highlight moves there over the Ambient duration (FR-ANIM-10). With no
 * highlight, the appearance's default crossfades with the other colors.
 *
 * The highlight has a color for each appearance (FR-AMB-02). Each one
 * follows the ambience on its own, and the appearance crossfades from one to
 * the other with every other color, so switching appearance moves the
 * highlight over the Long duration too.
 */
@Composable
fun LauncherTheme(
    dark: Boolean = false,
    highlight: Highlight? = null,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val motion = remember(reducedMotion) { LauncherMotion(reducedMotion) }
    val target = if (dark) DarkColors else LightColors
    val colors = target.crossfaded(motion)
    val light = highlight?.color(dark = false).shownOr(LightColors.highlightDefault, motion, "lightHighlight")
    val darkHighlight = highlight?.color(dark = true).shownOr(DarkColors.highlightDefault, motion, "darkHighlight")
    val darkness = animateFloatAsState(if (dark) 1f else 0f, motion.appearance(), label = "appearance").value
    val shownHighlight = when (darkness) {
        0f -> light
        1f -> darkHighlight
        else -> lerp(light, darkHighlight, darkness)
    }
    val tokens = LauncherTokens(colors = colors, type = DefaultType, highlight = shownHighlight, motion = motion)
    CompositionLocalProvider(LocalLauncherTokens provides tokens, content = content)
}

/**
 * This highlight as it is now, on its way to a new ambience over the Ambient
 * duration (FR-ANIM-10); or, with none, to the appearance's [default] over
 * the Long duration, with the other colors.
 */
@Composable
private fun Color?.shownOr(default: Color, motion: LauncherMotion, label: String): Color =
    animateColorAsState(
        targetValue = this ?: default,
        animationSpec = if (this == null) motion.appearance() else motion.highlight(),
        label = label,
    ).value

/** These colors as they are now, on their way to [target] (FR-ANIM-11). */
@Composable
private fun LauncherColors.crossfaded(motion: LauncherMotion): LauncherColors {
    val spec = motion.appearance<Color>()

    @Composable
    fun Color.shown(label: String): Color = animateColorAsState(this, spec, label = label).value

    return LauncherColors(
        textPrimary = textPrimary.shown("textPrimary"),
        textSecondary = textSecondary.shown("textSecondary"),
        glass = glass.shown("glass"),
        scrim = scrim.shown("scrim"),
        dim = dim.shown("dim"),
        highlightDefault = highlightDefault.shown("highlightDefault"),
    )
}

object LauncherTheme {
    val colors: LauncherColors
        @Composable @ReadOnlyComposable
        get() = LocalLauncherTokens.current.colors

    val type: LauncherType
        @Composable @ReadOnlyComposable
        get() = LocalLauncherTokens.current.type

    val highlight: Color
        @Composable @ReadOnlyComposable
        get() = LocalLauncherTokens.current.highlight

    val motion: LauncherMotion
        @Composable @ReadOnlyComposable
        get() = LocalLauncherTokens.current.motion
}
