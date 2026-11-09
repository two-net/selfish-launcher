package io.github.twonet.selfish.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import io.github.twonet.selfish.domain.state.Layer
import io.github.twonet.selfish.ui.tokens.Motion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The one gesture-and-motion component (AR-04, DESIGN §6.3). It owns the
 * transition between Home and the App Grid as a single progress from 0 (Home)
 * to 1 (the App Grid open), which follows the finger 1:1 (FR-GEST-01), and
 * decides on release whether to complete or peek back (FR-GEST-02).
 *
 * The progress is transient UI state (AR-02). Only where a release ends up
 * reaches the store, through [onSettled]; when the store changes the layer
 * itself, for example on a Home press, [follow] moves there. The Events and
 * Top Menu axes join with #10 and #11.
 *
 * Positive drag distances and velocities point toward the App Grid, which is
 * up on screen.
 */
@Stable
class LayerGestureController(private val scope: CoroutineScope) {
    private val progress = Animatable(0f)

    /** Called when a release settles on a different layer than the one it started from. */
    var onSettled: (Layer) -> Unit = {}

    /** The distance of a whole transition, in pixels: the height of the screen. */
    var travel: Float = 1f
        set(value) {
            field = value.coerceAtLeast(1f)
        }

    /** The platform's minimum fling velocity, in pixels per second. */
    var minFlingVelocity: Float = 0f

    /** The App Grid's progress: 0 on Home, 1 when it is fully open. Read it in a draw or layout phase. */
    val value: Float
        get() = progress.value

    /**
     * Where the last release, or the store, sent the transition. A new drag
     * measures its distance from here, so catching a transition mid-flight
     * and releasing it soon after carries on to where it was going.
     */
    private var target: Layer = Layer.Home
    private var dragValue = 0f

    /** Whether a finger is moving the transition. */
    var isDragging: Boolean = false
        private set

    /** Whether the transition is settling, so a touch catches it (FR-GEST-03). */
    val isSettling: Boolean
        get() = progress.isRunning

    /** Whether Home is fully in view, with nothing moving. */
    val isAtHome: Boolean
        get() = !isDragging && target != Layer.AppGrid && progress.value == 0f

    /** Whether the App Grid is fully in view, with nothing moving. */
    val isOpen: Boolean
        get() = !isDragging && target == Layer.AppGrid && progress.value == 1f

    /**
     * Moves the transition by [distance] pixels, starting a drag if none is
     * in progress; touching a settling transition stops it where it is
     * (FR-GEST-03).
     */
    fun dragBy(distance: Float) {
        if (!isDragging) {
            isDragging = true
            dragValue = progress.value
        }
        dragValue = (dragValue + distance / travel).coerceIn(0f, 1f)
        val to = dragValue
        // Undispatched, so the frame drawn next already shows the move.
        scope.launch(start = CoroutineStart.UNDISPATCHED) { progress.snapTo(to) }
    }

    /**
     * The finger left the screen at [velocity] pixels per second: complete
     * the transition or return to where it started (FR-GEST-02), then
     * settle with the release spring.
     */
    fun release(velocity: Float) {
        if (!isDragging) return
        isDragging = false
        val origin = target
        val start = valueOf(origin)
        val towardOther = if (start == 0f) velocity else -velocity
        val next = if (GestureMath.shouldComplete(abs(dragValue - start), towardOther, minFlingVelocity)) other(origin) else origin
        target = next
        if (next != origin) onSettled(next)
        scope.launch { progress.animateTo(valueOf(next), Motion.settle(), initialVelocity = velocity / travel) }
    }

    /**
     * Moves to the store's [layer], for example after a Home or Back press,
     * unless a release is already heading there. With [animate] false it
     * jumps, for a change made while the launcher wasn't in front.
     */
    fun follow(layer: Layer, animate: Boolean) {
        val goal = if (layer == Layer.AppGrid) Layer.AppGrid else Layer.Home
        if (goal == target) return
        target = goal
        isDragging = false
        scope.launch {
            if (animate) progress.animateTo(valueOf(goal), Motion.settle()) else progress.snapTo(valueOf(goal))
        }
    }

    private fun valueOf(layer: Layer): Float = if (layer == Layer.AppGrid) 1f else 0f

    private fun other(layer: Layer): Layer = if (layer == Layer.AppGrid) Layer.Home else Layer.AppGrid
}

/**
 * Turns swipes into transitions (DESIGN §6.3). Put it on the launcher's
 * root, which never moves, so distances and the release velocity are
 * measured against the screen even while the surfaces slide.
 *
 * It watches every touch before the elements under it do, and takes the
 * touch over once it is clearly a swipe the transition owns:
 * - up on Home, unless Home's covers can still scroll that way
 *   ([homeScrolls]), which opens the App Grid (FR-GRID-01);
 * - down on the App Grid while it is at its top, so [gridScrolls] is false,
 *   which closes it (FR-GRID-04).
 *
 * Until then the elements under the finger get the touch as usual: taps,
 * scrolling, and long-presses, which lift an item and keep the touch for
 * dragging it. Touches that start inside [ignored], the system gesture
 * areas, are left alone (FR-GEST-04). Touching a settling transition
 * catches it, and the same touch drags it either way (FR-GEST-03).
 */
fun Modifier.layerGestures(
    controller: LayerGestureController,
    ignored: WindowInsets,
    homeScrolls: () -> Boolean,
    gridScrolls: () -> Boolean,
): Modifier = pointerInput(controller, ignored) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val catching = controller.isSettling
        if (!catching) {
            val resting = controller.isAtHome || controller.isOpen
            if (!resting || ignored.contains(down.position, this, size.width, size.height)) return@awaitEachGesture
        }
        val velocity = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
        var taken = catching
        if (catching) {
            down.consume()
            controller.dragBy(0f)
        }
        var moved = Offset.Zero
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
            velocity.addPointerInputChange(change)
            if (!change.pressed) {
                if (taken) {
                    change.consume()
                    controller.release(-velocity.calculateVelocity().y)
                }
                break
            }
            if (taken) {
                controller.dragBy(-change.positionChange().y)
                change.consume()
                continue
            }
            // A swipe moves before the long-press timeout; after it, the
            // touch belongs to the long-pressed item.
            if (change.isConsumed || change.uptimeMillis - down.uptimeMillis > viewConfiguration.longPressTimeoutMillis) break
            moved += change.positionChange()
            when (GestureMath.lockedAxis(moved.x, moved.y, viewConfiguration.touchSlop)) {
                null -> Unit
                // Sideways is Events (#10).
                Axis.Horizontal -> break
                Axis.Vertical -> {
                    // Down on Home is the Top Menu (#11).
                    val ours = if (controller.isAtHome) moved.y < 0f && !homeScrolls() else moved.y > 0f && !gridScrolls()
                    if (!ours) break
                    taken = true
                    controller.dragBy(-moved.y)
                    change.consume()
                }
            }
        }
        // A touch the system took away still has to settle somewhere.
        if (taken && controller.isDragging) controller.release(0f)
    }
}

/** Whether [position] lies in one of these insets along the edges of a [width] × [height] area. */
private fun WindowInsets.contains(position: Offset, density: Density, width: Int, height: Int): Boolean =
    position.x < getLeft(density, LayoutDirection.Ltr) ||
        position.x >= width - getRight(density, LayoutDirection.Ltr) ||
        position.y < getTop(density) ||
        position.y >= height - getBottom(density)
