package io.github.twonet.selfish.ui.tokens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The one set of design tokens (AR-04, UI-08). Every view reads colors, sizes,
 * type and motion from here; no literal values appear anywhere else.
 */
@Immutable
class LauncherTokens(
    val colors: LauncherColors,
    val type: LauncherType,
    /** The ambience's highlight color (SRS §4.11), for headers and states only (FR-AMB-04). */
    val highlight: Color,
)

private val LocalLauncherTokens = staticCompositionLocalOf {
    LauncherTokens(LightColors, DefaultType, LightColors.highlightDefault)
}

/**
 * Provides the tokens for [dark] or light appearance and the current
 * [highlight], which falls back to the appearance's default. #16 and #17
 * resolve both in the Domain and pass them in from state.
 */
@Composable
fun LauncherTheme(dark: Boolean = false, highlight: Color? = null, content: @Composable () -> Unit) {
    val colors = if (dark) DarkColors else LightColors
    val tokens = LauncherTokens(colors = colors, type = DefaultType, highlight = highlight ?: colors.highlightDefault)
    CompositionLocalProvider(LocalLauncherTokens provides tokens, content = content)
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
}
