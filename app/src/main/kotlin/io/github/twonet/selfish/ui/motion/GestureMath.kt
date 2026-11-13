package io.github.twonet.selfish.ui.motion

import io.github.twonet.selfish.domain.state.Layer
import io.github.twonet.selfish.ui.tokens.Motion
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.sign

/** The axis a drag is locked to once it has clearly moved (FR-GEST-04). */
enum class Axis { Horizontal, Vertical }

/** A direction on the screen. */
enum class Direction(val axis: Axis) {
    Up(Axis.Vertical),
    Down(Axis.Vertical),
    Left(Axis.Horizontal),
    Right(Axis.Horizontal),
}

/**
 * One way out of Home, with its own progress (DESIGN §6.2, §6.3). Its
 * values grow as the finger moves toward [grows], so the content always
 * follows the finger.
 */
enum class Track(val grows: Direction) {
    /** Up from Home: 0 is Home, 1 the App Grid fully open (FR-GRID-01). */
    AppGrid(Direction.Up),

    /**
     * Sideways between Home and Events (FR-EVT-01), a carousel of two
     * pages that wraps around, measured in screen widths: Home rests on
     * even numbers and Events on odd ones. A finger moving left counts up,
     * so Events comes in from whichever side the finger pushes toward, and
     * leaves the same way.
     */
    Events(Direction.Left),

    /**
     * Down from Home: 0 is Home, 1 the Top Menu fully pulled down
     * (FR-TOP-01). Measured in the menu's own height, not the screen's.
     */
    TopMenu(Direction.Down),
    ;

    val axis: Axis
        get() = grows.axis
}

/**
 * What can still scroll where a swipe starts, so the content keeps a swipe
 * it would scroll instead of a transition taking it (DESIGN §6.3).
 */
data class Scrolls(
    /** Home's covers can scroll further, so a swipe up scrolls them first. */
    val homeForward: Boolean = false,
    /** Home's covers are scrolled, so a swipe down scrolls them back first. */
    val homeBackward: Boolean = false,
    /** The App Grid isn't at its top, so a swipe down scrolls it (FR-GRID-04). */
    val gridBackward: Boolean = false,
    /** The Top Menu's content can scroll further, as at large font sizes, so a swipe up scrolls it first. */
    val topMenuForward: Boolean = false,
)

/**
 * The pure decisions behind every view transition (DESIGN §6.3), kept apart
 * from pointer handling so they can be tested on the JVM.
 */
object GestureMath {
    /**
     * The axis a drag that has moved by ([dx], [dy]) since touch-down locks
     * to, or `null` while it is still within [slop] of where it started
     * (FR-GEST-04). The dominant axis wins; an exact diagonal counts as
     * vertical.
     */
    fun lockedAxis(dx: Float, dy: Float, slop: Float): Axis? = when {
        dx * dx + dy * dy <= slop * slop -> null
        abs(dy) >= abs(dx) -> Axis.Vertical
        else -> Axis.Horizontal
    }

    /**
     * Whether a released transition completes (FR-GEST-02): it was flung
     * toward its end at [minFling] or faster, or it covered at least
     * [Motion.COMPLETE_FRACTION] of its distance and wasn't flung back.
     * Otherwise it returns to where it started, which is peeking.
     *
     * [covered] is the fraction of the distance covered, from 0 to 1;
     * [velocity] is positive toward the end and negative back toward the
     * start, in the same unit as [minFling].
     */
    fun shouldComplete(covered: Float, velocity: Float, minFling: Float): Boolean = when {
        velocity >= minFling -> true
        velocity <= -minFling -> false
        else -> covered >= Motion.COMPLETE_FRACTION
    }

    /**
     * How far a pulley menu moves for a finger that has moved [drag] pixels
     * (SRS §4.9 "Rubber band"): one to one at first, then with resistance
     * that grows with distance, approaching [limit] without reaching it.
     */
    fun rubberBand(drag: Float, limit: Float): Float = if (drag <= 0f || limit <= 0f) 0f else limit * (1f - exp(-drag / limit))

    /**
     * The pulley menu item that a pull of [pull] pixels reaches (FR-GEST-05):
     * none before [item], then the next of [count] items every [item]
     * further, staying on the last one beyond it. `null` cancels.
     */
    fun pulleyIndex(pull: Float, item: Float, count: Int): Int? = when {
        count <= 0 || item <= 0f || pull < item -> null
        else -> ((pull / item).toInt() - 1).coerceAtMost(count - 1)
    }

    /**
     * How far a pulley menu of [count] items can be pulled at most, its
     * rubber band's limit: one item beyond the last, so every item is
     * within comfortable reach.
     */
    fun pulleyLimit(item: Float, count: Int): Float = item * (count + 2)

    /**
     * Where a released transition settles (FR-GEST-02): one step on from
     * [origin], where the drag started, in the direction it moved (or was
     * flung, if it didn't move), when [shouldComplete] says so and that step
     * lies in [range]; otherwise back at [origin], which is peeking.
     * [value] is where the finger left it, in the same unit; [velocity] is
     * positive toward larger values, in the unit of [minFling].
     */
    fun settleTarget(origin: Float, value: Float, velocity: Float, minFling: Float, range: ClosedFloatingPointRange<Float>): Float {
        val direction = (value - origin).sign.takeIf { it != 0f } ?: velocity.sign
        if (direction == 0f) return origin
        val next = origin + direction
        if (next !in range) return origin
        return if (shouldComplete(abs(value - origin), velocity * direction, minFling)) next else origin
    }

    /**
     * Where Home is drawn for the Events carousel at [position] (see
     * [Track.Events]), in screen widths; positive is to the right. Home
     * changes sides only while it is fully out of view.
     */
    fun homeShift(position: Float): Float = 2f * round(position / 2f) - position

    /** Where Events is drawn for the carousel at [position], in screen widths; positive is to the right. */
    fun eventsShift(position: Float): Float = 2f * floor(position / 2f) + 1f - position

    /**
     * [position] moved by whole turns of the carousel to lie in (−2, 2), so
     * it looks the same: Home at 0, and Events at 1 or −1, keeping the side
     * it came in from.
     */
    fun normalize(position: Float): Float = position - 2f * round(position / 2f)

    /**
     * Where the carousel rests for [layer], starting from [near]: the
     * nearest place Home or Events rests (any other layer is Home's). Home
     * goes back the way Events came in. Events opened from Home, with no
     * finger to follow, comes in from [endSide]: 1 for the right, −1 for
     * the left.
     */
    fun carouselRest(layer: Layer, near: Float, endSide: Float): Float {
        val events = layer == Layer.Events
        // The places on either side of near: odd ones for Events, even ones for Home.
        val before = if (events) 2f * floor((near - 1f) / 2f) + 1f else 2f * floor(near / 2f)
        val after = before + 2f
        val toBefore = near - before
        val toAfter = after - near
        return when {
            toBefore < toAfter -> before
            toAfter < toBefore -> after
            events -> near + endSide.sign
            else -> if (abs(before) <= abs(after)) before else after
        }
    }

    /**
     * How far a finger that moved ([dx], [dy]) pixels moves [track], in
     * pixels: positive toward [Track.grows], negative back.
     */
    fun along(track: Track, dx: Float, dy: Float): Float = when (track.grows) {
        Direction.Up -> -dy
        Direction.Down -> dy
        Direction.Left -> -dx
        Direction.Right -> dx
    }

    /**
     * Which transition a swipe from the resting [from] layer moves, once it
     * has locked to [axis] after moving ([dx], [dy]), or `null` if it isn't
     * one, so the elements under the finger keep it (DESIGN §6.3):
     * - on Home, up opens the App Grid and down the Top Menu, unless Home's
     *   covers can still scroll that way ([scrolls]); sideways, either way,
     *   opens Events;
     * - on the App Grid, down closes it while it is at its top;
     * - on Events, sideways returns to Home, unless the swipe started on
     *   something that takes sideways swipes itself ([claimed]), such as a
     *   dismissible notification (FR-EVT-05);
     * - on the Top Menu, up closes it once its content can't scroll any
     *   further (FR-TOP-05); sideways is left to the Ambiences row.
     */
    fun route(from: Layer, axis: Axis, dx: Float, dy: Float, scrolls: Scrolls, claimed: Boolean): Track? =
        when {
            from == Layer.Home && axis == Axis.Vertical && dy < 0f -> Track.AppGrid.takeIf { !scrolls.homeForward }
            from == Layer.Home && axis == Axis.Vertical && dy > 0f -> Track.TopMenu.takeIf { !scrolls.homeBackward }
            from == Layer.Home && axis == Axis.Horizontal -> Track.Events.takeIf { dx != 0f }
            from == Layer.AppGrid && axis == Axis.Vertical -> Track.AppGrid.takeIf { dy > 0f && !scrolls.gridBackward }
            from == Layer.Events && axis == Axis.Horizontal -> Track.Events.takeIf { !claimed }
            from == Layer.TopMenu && axis == Axis.Vertical -> Track.TopMenu.takeIf { dy < 0f && !scrolls.topMenuForward }
            else -> null
        }
}
