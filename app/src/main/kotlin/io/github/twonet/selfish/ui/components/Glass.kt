package io.github.twonet.selfish.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import io.github.twonet.selfish.ui.tokens.LauncherTheme
import io.github.twonet.selfish.ui.tokens.Shapes

/**
 * A panel of translucent glass over the wallpaper (UI-01): cards, menus and
 * folder panels sit on this, never on an opaque background. Its color is
 * contrast-tested for both appearances (NFR-A11Y-03).
 */
@Composable
fun Glass(
    modifier: Modifier = Modifier,
    shape: Shape = Shapes.Glass,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.clip(shape).background(LauncherTheme.colors.glass, shape),
        contentAlignment = contentAlignment,
        content = content,
    )
}
