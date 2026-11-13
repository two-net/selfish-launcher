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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import io.github.twonet.selfish.domain.state.Layer
import io.github.twonet.selfish.ui.tokens.Motion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The one gesture-and-motion component (AR-04, DESIGN §6.3). It owns every
 * transition out of Home, one [Track] each: up to the App Grid and sideways
 * to Events. Each follows the finger 1:1 (FR-GEST-01), and on release
 * completes or peeks back (FR-GEST-02). One moves at a time.
 *
 * The progress is transient UI state (AR-02). Only where a release ends up
 * reaches the store, through [onSettled]; when the store changes the layer
 * itself, for example on a Home press, [follow] moves there. The Top Menu
 * axis joins with #11, as one more track.
 */
@Stable
class LayerGestureController(private val scope: CoroutineScope) {
    private val grid = Animatable(0f)
    private val carousel = Animatable(0f)

    /** Called when a release settles on a different layer than the one it started from. */
    var onSettled: (Layer) -> Unit = {}

    /** The size of the screen, in pixels: each track's whole transition is its height or width. */
    var size: IntSize = IntSize(1, 1)

    /** The platform's minimum fling velocity, in pixels per second. */
    var minFlingVelocity: Float = 0f

    /**
     * The side Events comes in from when it is opened without a finger, for
     * example by a screen reader: 1 for the right, −1 for the left. The end
     * side of the layout direction.
     */
    var endSide: Float = 1f

    /**
     * What on Events takes sideways swipes itself, such as a dismissible
     * notification (FR-EVT-05). Swipes that start there aren't the
     * carousel's.
     */
    val claims = SidewaysClaims()

    /** The App Grid's progress: 0 on Home, 1 when it is fully open. Read it in a draw or layout phase. */
    val gridProgress: Float
        get() = grid.value

    /**
     * Where the Events carousel is (see [Track.Events]). Draw Home and Events
     * with [GestureMath.homeShift] and [GestureMath.eventsShift]. Read it in
     * a draw or layout phase.
     */
    val sideways: Float
        get() = carousel.value

    /** How much of Events is in view, from 0 to 1. Read it in a draw or layout phase. */
    val eventsProgress: Float
        get() = (1f - abs(GestureMath.eventsShift(carousel.value))).coerceIn(0f, 1f)

    /**
     * Where the last release, or the store, sent each track. A new drag
     * measures from here, so catching a transition mid-flight and releasing
     * it soon after carries on to where it was going.
     */
    private var gridGoal = 0f
    private var carouselGoal = 0f

    /** The track a finger is moving, if any, and where the finger has taken it. */
    private var dragging: Track? = null
    private var dragValue = 0f

    /** Whether a finger is moving a transition. */
    val isDragging: Boolean
        get() = dragging != null

    /** The track that is settling, so a touch catches it (FR-GEST-03). */
    val settling: Track?
        get() = when {
            grid.isRunning -> Track.AppGrid
            carousel.isRunning -> Track.Events
            else -> null
        }

    /**
     * The layer fully in view with nothing moving, or `null` while a
     * transition is under way. Only a resting layer takes new swipes.
     */
    val resting: Layer?
        get() {
            if (isDragging || settling != null) return null
            return when {
                grid.value == 1f && gridGoal == 1f -> Layer.AppGrid
                grid.value != 0f -> null
                GestureMath.homeShift(carousel.value) == 0f -> Layer.Home
                GestureMath.eventsShift(carousel.value) == 0f -> Layer.Events
                else -> null
            }
        }

    /** Starts moving [track] with a finger, stopping it where it is if it was settling (FR-GEST-03). */
    fun grab(track: Track) {
        dragging = track
        dragValue = animatable(track).value
        snap(track, dragValue)
    }

    /**
     * Moves the grabbed track by [distance] pixels toward larger values; for
     * both tracks, that is the finger moving up or left.
     */
    fun dragBy(distance: Float) {
        val track = dragging ?: return
        dragValue = (dragValue + distance / travel(track)).coerceIn(range(track))
        snap(track, dragValue)
    }

    /**
     * The finger left the screen at [velocity] pixels per second toward
     * larger values: complete the transition or return to where it started
     * (FR-GEST-02), then settle with the release spring.
     */
    fun release(velocity: Float) {
        val track = dragging ?: return
        dragging = null
        val origin = goal(track)
        val next = GestureMath.settleTarget(origin, dragValue, velocity, minFlingVelocity, range(track))
        setGoal(track, next)
        val layer = layerAt(track, next)
        if (layer != layerAt(track, origin)) onSettled(layer)
        scope.launch {
            animatable(track).animateTo(next, Motion.settle(), initialVelocity = velocity / travel(track))
            settled(track)
        }
    }

    /**
     * Moves to the store's [layer], for example after a Home or Back press,
     * unless the transitions are already heading there. With [animate]
     * false it jumps, for a change made while the launcher wasn't in front.
     * Layers no track leads to yet are left to their own issues.
     */
    fun follow(layer: Layer, animate: Boolean) {
        if (layer != Layer.Home && layer != Layer.AppGrid && layer != Layer.Events) return
        val gridTarget = if (layer == Layer.AppGrid) 1f else 0f
        val carouselTarget = GestureMath.carouselRest(layer, carousel.value, endSide)
        moveTo(Track.AppGrid, gridTarget, animate)
        moveTo(Track.Events, carouselTarget, animate)
    }

    private fun moveTo(track: Track, target: Float, animate: Boolean) {
        if (goal(track) == target && dragging != track) return
        setGoal(track, target)
        if (dragging == track) dragging = null
        scope.launch {
            if (animate) animatable(track).animateTo(target, Motion.settle()) else animatable(track).snapTo(target)
            settled(track)
        }
    }

    /**
     * After a settle that wasn't interrupted: the carousel goes back near 0
     * by whole turns, which looks the same, so its numbers stay small.
     */
    private suspend fun settled(track: Track) {
        if (track != Track.Events) return
        val normal = GestureMath.normalize(carouselGoal)
        if (normal != carouselGoal) {
            carouselGoal = normal
            carousel.snapTo(normal)
        }
    }

    /** Undispatched, so the frame drawn next already shows the move. */
    private fun snap(track: Track, to: Float) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { animatable(track).snapTo(to) }
    }

    private fun animatable(track: Track): Animatable<Float, *> = when (track) {
        Track.AppGrid -> grid
        Track.Events -> carousel
    }

    private fun goal(track: Track): Float = when (track) {
        Track.AppGrid -> gridGoal
        Track.Events -> carouselGoal
    }

    private fun setGoal(track: Track, value: Float) = when (track) {
        Track.AppGrid -> gridGoal = value
        Track.Events -> carouselGoal = value
    }

    private fun travel(track: Track): Float = when (track.axis) {
        Axis.Vertical -> size.height
        Axis.Horizontal -> size.width
    }.toFloat().coerceAtLeast(1f)

    /** How far a drag may take [track]: the App Grid between closed and open, the carousel one page either way. */
    private fun range(track: Track): ClosedFloatingPointRange<Float> = when (track) {
        Track.AppGrid -> 0f..1f
        Track.Events -> (carouselGoal - 1f)..(carouselGoal + 1f)
    }

    private fun layerAt(track: Track, value: Float): Layer = when (track) {
        Track.AppGrid -> if (value == 1f) Layer.AppGrid else Layer.Home
        Track.Events -> if (abs(value % 2f) == 1f) Layer.Events else Layer.Home
    }
}

/** The part of [offset] that moves this track toward larger values: up or left. */
private fun Track.along(offset: Offset): Float = when (axis) {
    Axis.Vertical -> -offset.y
    Axis.Horizontal -> -offset.x
}

private fun Track.along(velocity: Velocity): Float = when (axis) {
    Axis.Vertical -> -velocity.y
    Axis.Horizontal -> -velocity.x
}

/**
 * Turns swipes into transitions (DESIGN §6.3). Put it on the launcher's
 * root, which never moves, so distances and the release velocity are
 * measured against the screen even while the surfaces slide.
 *
 * It watches every touch before the elements under it do, and takes the
 * touch over once it is clearly a swipe a transition owns, as
 * [GestureMath.route] decides:
 * - up on Home, unless Home's covers can still scroll that way
 *   ([homeScrolls]), which opens the App Grid (FR-GRID-01);
 * - down on the App Grid while it is at its top, so [gridScrolls] is false,
 *   which closes it (FR-GRID-04);
 * - sideways on Home or Events, which moves between them (FR-EVT-01),
 *   unless it starts on something in the controller's [SidewaysClaims],
 *   such as a dismissible notification (FR-EVT-05).
 *
 * Until then the elements under the finger get the touch as usual: taps,
 * scrolling, pulley menus, and long-presses, which lift an item and keep
 * the touch for dragging it. Touches that start inside [ignored], the
 * system gesture areas, are left alone (FR-GEST-04). Touching a settling
 * transition catches it, and the same touch drags it either way
 * (FR-GEST-03).
 *
 * While [paused], for example with a folder open over the App Grid, resting
 * layers take no touches at all, so a pull on the folder goes to its pulley
 * menu (FR-FOLD-07).
 */
fun Modifier.layerGestures(
    controller: LayerGestureController,
    ignored: WindowInsets,
    homeScrolls: () -> Boolean,
    gridScrolls: () -> Boolean,
    paused: () -> Boolean = { false },
): Modifier = pointerInput(controller, ignored) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var track = controller.settling
        val from: Layer?
        if (track != null) {
            from = null
            down.consume()
            controller.grab(track)
        } else {
            from = controller.resting
            if (from == null || paused() || ignored.contains(down.position, this, size.width, size.height)) return@awaitEachGesture
        }
        val claimed = from == Layer.Events && controller.claims.contains(down.position)
        val velocity = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
        var moved = Offset.Zero
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
            velocity.addPointerInputChange(change)
            val taken = track
            if (!change.pressed) {
                if (taken != null) {
                    change.consume()
                    controller.release(taken.along(velocity.calculateVelocity()))
                }
                break
            }
            if (taken != null) {
                controller.dragBy(taken.along(change.positionChange()))
                change.consume()
                continue
            }
            // A swipe moves before the long-press timeout; after it, the
            // touch belongs to the long-pressed item.
            if (change.isConsumed || change.uptimeMillis - down.uptimeMillis > viewConfiguration.longPressTimeoutMillis) break
            moved += change.positionChange()
            val axis = GestureMath.lockedAxis(moved.x, moved.y, viewConfiguration.touchSlop) ?: continue
            val owned = GestureMath.route(checkNotNull(from), axis, moved.x, moved.y, homeScrolls(), gridScrolls(), claimed) ?: break
            track = owned
            controller.grab(owned)
            controller.dragBy(owned.along(moved))
            change.consume()
        }
        // A touch the system took away still has to settle somewhere.
        if (track != null && controller.isDragging) controller.release(0f)
    }
}

/** Whether [position] lies in one of these insets along the edges of a [width] × [height] area. */
private fun WindowInsets.contains(position: Offset, density: Density, width: Int, height: Int): Boolean =
    position.x < getLeft(density, LayoutDirection.Ltr) ||
        position.x >= width - getRight(density, LayoutDirection.Ltr) ||
        position.y < getTop(density) ||
        position.y >= height - getBottom(density)
