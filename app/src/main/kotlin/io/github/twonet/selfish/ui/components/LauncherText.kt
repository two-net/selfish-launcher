package io.github.twonet.selfish.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.github.twonet.selfish.ui.tokens.Emphasis
import io.github.twonet.selfish.ui.tokens.LauncherTheme
import io.github.twonet.selfish.ui.tokens.TextRole

/**
 * Text in one of the four sizes and two emphasis levels of UI-04. Views draw
 * text only through this or [PageHeader] (`LayerRulesTest`). It takes no
 * color, so body text can never be drawn in the highlight color (FR-AMB-04).
 */
@Composable
fun LauncherText(
    text: String,
    modifier: Modifier = Modifier,
    role: TextRole = TextRole.Body,
    emphasis: Emphasis = Emphasis.Primary,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
) {
    val colors = LauncherTheme.colors
    val color = when (emphasis) {
        Emphasis.Primary -> colors.textPrimary
        Emphasis.Secondary -> colors.textSecondary
    }
    BasicText(
        text = text,
        modifier = modifier,
        style = LauncherTheme.type.style(role).copy(color = color, textAlign = textAlign),
        overflow = TextOverflow.Ellipsis,
        maxLines = maxLines,
    )
}
