package io.github.twonet.selfish.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.clearAndSetSemantics
import io.github.twonet.selfish.ui.tokens.Shapes
import io.github.twonet.selfish.ui.tokens.Sizes
import io.github.twonet.selfish.ui.tokens.TextRole

/**
 * An app icon at the one uniform size, [Sizes.Icon] (UI-05). Icons that
 * support a shape ([shaped], such as adaptive icons) are clipped to
 * [Shapes.Icon]; others are drawn as they are, at the same size. Without an
 * [icon] it shows the first letter of [label] on glass.
 *
 * The icon is decorative: the item it belongs to carries the app's name for
 * screen readers (NFR-A11Y-01). Loading icons arrives with #5 and #7.
 */
@Composable
fun AppIcon(label: String, modifier: Modifier = Modifier, icon: Painter? = null, shaped: Boolean = true) {
    val sized = modifier.size(Sizes.Icon)
    if (icon != null) {
        Image(
            painter = icon,
            contentDescription = null,
            modifier = if (shaped) sized.clip(Shapes.Icon) else sized,
        )
    } else {
        Glass(
            modifier = sized.clearAndSetSemantics {},
            shape = Shapes.Icon,
            contentAlignment = Alignment.Center,
        ) {
            LauncherText(text = initialOf(label), role = TextRole.Body, maxLines = 1)
        }
    }
}

/** The first character of [label], whole even outside the Basic Multilingual Plane. */
private fun initialOf(label: String): String {
    val trimmed = label.trimStart()
    if (trimmed.isEmpty()) return ""
    return trimmed.substring(0, trimmed.offsetByCodePoints(0, 1)).uppercase()
}
