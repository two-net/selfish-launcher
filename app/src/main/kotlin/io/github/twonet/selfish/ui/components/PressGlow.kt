package io.github.twonet.selfish.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import io.github.twonet.selfish.ui.tokens.LocalLauncherTokens
import io.github.twonet.selfish.ui.tokens.PRESS_GLOW_ALPHA
import io.github.twonet.selfish.ui.tokens.Radii
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * A glow in the highlight color where a pressed element was touched, from
 * the first frame of the touch, fading out over the Short duration once the
 * finger lifts or something else takes the touch, such as a scroll or a
 * swipe between views (FR-ANIM-04). [pressable] adds it to every element.
 *
 * It watches the touch itself instead of being a Compose `Indication`,
 * because a clickable inside something scrollable, as every cover and app
 * is, reports its press only after the tap timeout, about 100 ms later.
 */
internal fun Modifier.pressGlow(): Modifier = this then PressGlowElement

private data object PressGlowElement : ModifierNodeElement<PressGlowNode>() {
    override fun create() = PressGlowNode()

    override fun update(node: PressGlowNode) = Unit

    override fun InspectorInfo.inspectableProperties() {
        name = "pressGlow"
    }
}

private class PressGlowNode : DelegatingNode(), DrawModifierNode, CompositionLocalConsumerModifierNode {
    /** How strong the glow is, from 0 to 1. */
    private val glow = Animatable(0f)

    /** Where the finger went down, in this element's coordinates. */
    private var center = Offset.Zero

    init {
        delegate(
            SuspendingPointerInputModifierNode {
                awaitEachGesture {
                    // Before the elements inside see it, and without taking it.
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    // The launcher caught a moving view with this touch.
                    if (down.isConsumed) return@awaitEachGesture
                    center = down.position
                    // Undispatched, so the frame drawn next already glows.
                    coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) { glow.snapTo(1f) }
                    try {
                        while (true) {
                            val change = awaitPointerEvent(PointerEventPass.Final).changes.firstOrNull { it.id == down.id } ?: break
                            // Lifted, or taken by a scroll, a swipe or a drag.
                            if (!change.pressed || change.isConsumed && change.position != change.previousPosition) break
                        }
                    } finally {
                        fadeOut()
                    }
                }
            },
        )
    }

    private fun fadeOut() {
        if (!isAttached) return
        val spec = currentValueOf(LocalLauncherTokens).motion.pressFade<Float>()
        coroutineScope.launch { glow.animateTo(0f, spec) }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val strength = glow.value
        if (strength == 0f || size.minDimension <= 0f) return
        val highlight = currentValueOf(LocalLauncherTokens).highlight
        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(highlight.copy(alpha = PRESS_GLOW_ALPHA * strength), Color.Transparent),
                center = center,
                radius = size.maxDimension,
            ),
            cornerRadius = CornerRadius(Radii.Glass.toPx()),
        )
    }

    /** Reused for another item, as lazy grids do: it starts without a glow. */
    override fun onReset() {
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) { glow.snapTo(0f) }
    }
}
