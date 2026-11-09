package io.github.twonet.selfish.domain.remorse

import io.github.twonet.selfish.domain.model.FolderId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * How long a destructive action waits before it runs, while its banner
 * offers to cancel it (FR-GEST-06).
 */
val REMORSE_DELAY: Duration = 5.seconds

/**
 * An action that runs only once its remorse timer has run out (FR-GEST-06,
 * DESIGN §4.6). Clearing notifications (#10), sorting A–Z and resetting the
 * layout (#18) join this.
 */
sealed interface Destructive {
    /** "Remove folder": its apps go back into the grid in its place (FR-FOLD-07). */
    data class RemoveFolder(val folder: FolderId) : Destructive
}

/**
 * The one destructive [action] waiting to run. [number] tells its timer
 * apart from those of earlier actions, which were committed or cancelled.
 */
data class PendingRemorse(val number: Int, val action: Destructive)

/**
 * At most one destructive action waits at a time (DESIGN §4.6). Pure, so
 * the reducer decides what runs; the timer itself is an
 * `Effect.DispatchAfter` in the store's scope, which fires even after the
 * user has left the launcher.
 */
object RemorseQueue {
    /** The result of [start]: what now waits, and what must run at once. */
    data class Started(val pending: PendingRemorse, val committed: Destructive?)

    /**
     * Starts [next]'s timer, numbered [number]. A destructive action that
     * was already waiting runs at once, as [Started.committed]: starting a
     * second one commits the first.
     */
    fun start(pending: PendingRemorse?, next: Destructive, number: Int): Started =
        Started(PendingRemorse(number, next), pending?.action)

    /**
     * What runs when the timer numbered [number] is due: the waiting action,
     * if that timer is still its own. A timer for an action that was
     * committed early or cancelled runs nothing.
     */
    fun due(pending: PendingRemorse?, number: Int): Destructive? = pending?.takeIf { it.number == number }?.action
}
