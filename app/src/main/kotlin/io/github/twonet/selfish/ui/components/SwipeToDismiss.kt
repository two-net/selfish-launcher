package io.github.twonet.selfish.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalViewConfiguration
import io.github.twonet.selfish.ui.motion.Axis
import io.github.twonet.selfish.ui.motion.GestureMath
import io.github.twonet.selfish.ui.motion.SidewaysClaims
import io.github.twonet.selfish.ui.tokens.Motion
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * [content] that a sideways swipe dismisses, such as a notification in
 * Events (FR-EVT-05, DESIGN §6.4). It follows the finger, fading as it goes;
 * released past [Motion.COMPLETE_FRACTION] of its width, or flung, it slides
 * out and [onDismiss] runs; otherwise it springs back.
 *
 * The swipe is recognized like the launcher's own (DESIGN §6.3): it is
 * watched before [content], and taken once it moves sideways past the slop
 * before the long-press timeout. Taps and vertical scrolling still reach
 * [content] and the list. While [enabled], it registers itself in
 * [claims], so the swipe isn't taken as one between views instead.
 */
@Composable
fun SwipeToDismiss(
    enabled: Boolean,
    claims: SidewaysClaims,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val owner = remember { Any() }
    val minFling = LocalViewConfiguration.current.minimumFlingVelocity
    val currentDismiss by rememberUpdatedState(onDismiss)
    DisposableEffect(claims, enabled) {
        onDispose { claims.release(owner) }
    }
    Box(
        modifier
            // Both at rest, before the transform, so the touch is measured
            // in place while the content slides.
            .onGloballyPositioned { if (enabled) claims.claim(owner, it) else claims.release(owner) }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    // The launcher caught a transition with this touch.
                    if (down.isConsumed) return@awaitEachGesture
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    val velocity = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                    var taken = offset.isRunning
                    var moved = Offset.Zero
                    var released = false
                    // Undispatched, so the frame drawn next already shows the move.
                    fun follow(by: Float) {
                        val to = offset.value + by
                        scope.launch(start = CoroutineStart.UNDISPATCHED) { offset.snapTo(to) }
                    }
                    if (taken) {
                        down.consume()
                        follow(0f)
                    }
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                        velocity.addPointerInputChange(change)
                        if (!change.pressed) {
                            if (taken) {
                                change.consume()
                                released = true
                            }
                            break
                        }
                        if (taken) {
                            follow(change.positionChange().x)
                            change.consume()
                            continue
                        }
                        // A swipe moves before the long-press timeout; after
                        // it, the touch belongs to the long-pressed item.
                        if (change.isConsumed || change.uptimeMillis - down.uptimeMillis > viewConfiguration.longPressTimeoutMillis) break
                        moved += change.positionChange()
                        when (GestureMath.lockedAxis(moved.x, moved.y, viewConfiguration.touchSlop)) {
                            null -> Unit
                            Axis.Vertical -> break
                            Axis.Horizontal -> {
                                taken = true
                                follow(moved.x)
                                change.consume()
                            }
                        }
                    }
                    if (!taken) return@awaitEachGesture
                    val at = offset.value
                    val speed = if (released) velocity.calculateVelocity().x else 0f
                    val side = at.sign.takeIf { it != 0f } ?: speed.sign
                    val gone = side != 0f && GestureMath.shouldComplete(abs(at) / width, speed * side, minFling)
                    scope.launch {
                        if (gone) {
                            offset.animateTo(side * width, Motion.settle(), initialVelocity = speed)
                            currentDismiss()
                        } else {
                            offset.animateTo(0f, Motion.settle(), initialVelocity = speed)
                        }
                    }
                }
            }
            .graphicsLayer {
                translationX = offset.value
                alpha = 1f - (abs(offset.value) / size.width.coerceAtLeast(1f)).coerceIn(0f, 1f)
            },
    ) {
        content()
    }
}
