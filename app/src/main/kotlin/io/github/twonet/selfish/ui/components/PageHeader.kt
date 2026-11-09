package io.github.twonet.selfish.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.github.twonet.selfish.ui.tokens.LauncherTheme
import io.github.twonet.selfish.ui.tokens.Spacing

/**
 * The header of a page such as Events, Settings, Wallpaper details or a folder
 * (UI-02): large, aligned to the trailing edge (right in left-to-right
 * languages, mirrored in right-to-left ones) and drawn in the highlight color.
 * Screen readers announce it as a heading.
 */
@Composable
fun PageHeader(title: String, modifier: Modifier = Modifier) {
    BasicText(
        text = title,
        modifier = modifier
            .fillMaxWidth()
            .semantics { heading() }
            .padding(horizontal = Spacing.Gutter, vertical = Spacing.Large),
        style = LauncherTheme.type.header.copy(color = LauncherTheme.highlight, textAlign = TextAlign.End),
    )
}
