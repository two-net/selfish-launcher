package io.github.twonet.selfish.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import io.github.twonet.selfish.ui.tokens.LauncherTheme
import io.github.twonet.selfish.ui.tokens.Motion
import io.github.twonet.selfish.ui.tokens.Shapes
import io.github.twonet.selfish.ui.tokens.Sizes
import io.github.twonet.selfish.ui.tokens.Spacing
import kotlin.time.Duration

/**
 * The banner of a destructive action waiting for its remorse timer
 * (FR-GEST-06, FR-ANIM-12, DESIGN §6.4): the action's [label] above a bar
 * in the highlight color (FR-AMB-04) that shrinks evenly over [duration],
 * toward the start edge. Tapping it calls [onCancel]. Screen readers read it
 * as a button, [cancelLabel], and announce it as it appears.
 *
 * It floats over whatever is on screen, such as the App Grid's search
 * field, so its glass lies on the scrim: two translucent layers keep what
 * is beneath from showing through its label, while the wallpaper still
 * does (UI-01).
 *
 * The countdown starts when the banner first shows timer [number]; the
 * reducer is pure and keeps no clock, so the bar's progress is transient UI
 * state (AR-02).
 */
@Composable
fun RemorseBanner(
    label: String,
    cancelLabel: String,
    number: Int,
    duration: Duration,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val remaining = remember(number) { Animatable(1f) }
    LaunchedEffect(remaining) { remaining.animateTo(0f, Motion.countdown(duration)) }
    val highlight = LauncherTheme.highlight
    Glass(
        modifier = modifier
            .fillMaxWidth()
            .background(LauncherTheme.colors.scrim, Shapes.Glass)
            .pressable(role = Role.Button, onClick = onCancel)
            .semantics {
                contentDescription = cancelLabel
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Gutter, vertical = Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            LauncherText(text = label, modifier = Modifier.fillMaxWidth().clearAndSetSemantics {}, textAlign = TextAlign.Center, maxLines = 1)
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(Sizes.RemorseBar)
                    .drawBehind {
                        val width = size.width * remaining.value
                        val left = if (layoutDirection == LayoutDirection.Rtl) size.width - width else 0f
                        drawRect(highlight, topLeft = Offset(left, 0f), size = size.copy(width = width))
                    },
            )
        }
    }
}
