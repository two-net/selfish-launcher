package io.github.twonet.selfish.ui.motion

import io.github.twonet.selfish.ui.tokens.Motion
import kotlin.math.exp
import kotlin.math.abs

/** The axis a drag is locked to once it has clearly moved (FR-GEST-04). */
enum class Axis { Horizontal, Vertical }

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
}
