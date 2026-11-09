package io.github.twonet.selfish.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.ui.tokens.Shapes
import io.github.twonet.selfish.ui.tokens.Sizes
import io.github.twonet.selfish.ui.tokens.TextRole

/**
 * [app]'s icon at the one uniform size (UI-05), loaded through
 * [LocalAppIcons]. Until it has loaded, or if it can't be, it shows the
 * first letter of the app's name instead. Apps of a work profile carry the
 * system's work badge (FR-GEN-10).
 */
@Composable
fun AppIcon(app: AppEntry, modifier: Modifier = Modifier) {
    val icons = LocalAppIcons.current
    val sizePx = with(LocalDensity.current) { Sizes.Icon.roundToPx() }
    // When the app is updated, the old icon stays until the new one is ready.
    val loaded by produceState(icons?.cached(app, sizePx), icons, app.id, app.version, sizePx) {
        if (icons != null) value = icons.load(app, sizePx)
    }
    val current = loaded
    AppIcon(
        label = app.label,
        modifier = modifier,
        icon = current?.let { remember(it) { BitmapPainter(it.icon) } },
        shaped = current?.shaped ?: true,
        badge = current?.badge?.let { remember(it) { BitmapPainter(it) } },
    )
}

/**
 * An app icon at the one uniform size, [Sizes.Icon] (UI-05). Icons that
 * support a shape ([shaped], such as adaptive icons) are clipped to
 * [Shapes.Icon]; others are drawn as they are, at the same size. Without an
 * [icon] it shows the first letter of [label] on glass. A [badge], such as
 * the work badge, is drawn over the icon without clipping.
 *
 * The icon is decorative: the item it belongs to carries the app's name for
 * screen readers (NFR-A11Y-01).
 */
@Composable
fun AppIcon(
    label: String,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    shaped: Boolean = true,
    badge: Painter? = null,
) {
    Box(modifier.size(Sizes.Icon)) {
        if (icon != null) {
            Image(
                painter = icon,
                contentDescription = null,
                modifier = if (shaped) Modifier.matchParentSize().clip(Shapes.Icon) else Modifier.matchParentSize(),
            )
        } else {
            Glass(
                modifier = Modifier.matchParentSize().clearAndSetSemantics {},
                shape = Shapes.Icon,
                contentAlignment = Alignment.Center,
            ) {
                LauncherText(text = initialOf(label), role = TextRole.Body, maxLines = 1)
            }
        }
        if (badge != null) {
            Image(painter = badge, contentDescription = null, modifier = Modifier.matchParentSize())
        }
    }
}

/** The first character of [label], whole even outside the Basic Multilingual Plane. */
private fun initialOf(label: String): String {
    val trimmed = label.trimStart()
    if (trimmed.isEmpty()) return ""
    return trimmed.substring(0, trimmed.offsetByCodePoints(0, 1)).uppercase()
}
