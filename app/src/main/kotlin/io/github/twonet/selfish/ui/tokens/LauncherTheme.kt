package io.github.twonet.selfish.ui.tokens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The one set of design tokens (AR-04, UI-08). Every view reads colors, sizes,
 * type and motion from here; no literal values appear anywhere else.
 */
@Immutable
class LauncherTokens(
    val colors: LauncherColors,
    val type: LauncherType,
)

private val LocalLauncherTokens = staticCompositionLocalOf { LauncherTokens(LightColors, DefaultType) }

/**
 * Provides the tokens for [dark] or light appearance. #17 resolves the
 * appearance in the Domain and passes it in from state.
 */
@Composable
fun LauncherTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    val tokens = LauncherTokens(colors = if (dark) DarkColors else LightColors, type = DefaultType)
    CompositionLocalProvider(LocalLauncherTokens provides tokens, content = content)
}

object LauncherTheme {
    val colors: LauncherColors
        @Composable @ReadOnlyComposable
        get() = LocalLauncherTokens.current.colors

    val type: LauncherType
        @Composable @ReadOnlyComposable
        get() = LocalLauncherTokens.current.type
}
