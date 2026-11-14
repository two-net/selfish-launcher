package io.github.twonet.selfish.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.twonet.selfish.ui.tokens.LauncherTheme
import io.github.twonet.selfish.ui.tokens.Motion
import io.github.twonet.selfish.ui.tokens.Sizes
import kotlinx.coroutines.delay

/**
 * A glowing touch point that traces a gesture (FR-ANIM-13), for the
 * tutorial (FR-ONB-02) and for hints in context, such as empty Home's
 * "Swipe up".
 * It moves from [from] to [to], each a fraction of its own width and
 * height, as a finger would.
 *
 * Each time the launcher comes on screen it fades in, traces the gesture and
 * fades out [Motion.HINT_REPEATS] times, with a pause before each, and then
 * rests, so an idle Home draws no frames. Under reduced motion it doesn't
 * trace: the point stays still where the gesture starts (FR-ANIM-15).
 *
 * Decorative: the text beside it says what to do, so screen readers skip
 * it (NFR-A11Y-01).
 */
@Composable
fun GestureHint(from: Offset, to: Offset, modifier: Modifier = Modifier) {
    val motion = LauncherTheme.motion
    val highlight = LauncherTheme.highlight
    val along = remember { Animatable(0f) }
    val shown = remember { Animatable(0f) }
    // Counts the times the launcher came on screen; each one plays again.
    var starts by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { starts++ }
    LaunchedEffect(starts, motion) {
        along.snapTo(0f)
        if (motion.reduced) {
            shown.snapTo(1f)
            return@LaunchedEffect
        }
        shown.snapTo(0f)
        if (starts == 0) return@LaunchedEffect
        repeat(Motion.HINT_REPEATS) {
            delay(Motion.HINT_PAUSE_MILLIS)
            along.snapTo(0f)
            shown.animateTo(1f, motion.hintFade())
            along.animateTo(1f, motion.hintTrace())
            shown.animateTo(0f, motion.hintFade())
        }
    }
    Spacer(
        modifier.drawBehind {
            val strength = shown.value
            if (strength == 0f) return@drawBehind
            val radius = Sizes.HintTouchPoint.toPx() / 2f
            // Inset by the point's size, so it stays inside at both ends.
            val at = lerp(from, to, along.value)
            val center = Offset(radius + at.x * (size.width - 2 * radius), radius + at.y * (size.height - 2 * radius))
            drawCircle(
                brush = Brush.radialGradient(listOf(highlight, Color.Transparent), center, radius),
                radius = radius,
                center = center,
                alpha = strength,
            )
        },
    )
}
