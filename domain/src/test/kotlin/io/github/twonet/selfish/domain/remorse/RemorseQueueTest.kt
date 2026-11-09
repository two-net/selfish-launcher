package io.github.twonet.selfish.domain.remorse

import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.FolderId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

@Verifies("FR-GEST-06", "FR-FOLD-07")
class RemorseQueueTest {
    private val first = Destructive.RemoveFolder(FolderId(1))
    private val second = Destructive.RemoveFolder(FolderId(2))

    @Test
    fun `the timer runs for five seconds`() {
        assertEquals(5.seconds, REMORSE_DELAY)
    }

    @Test
    fun `an action waits, and runs when its own timer is due`() {
        val started = RemorseQueue.start(null, first, 1)

        assertEquals(RemorseQueue.Started(PendingRemorse(1, first), committed = null), started)
        assertEquals(first, RemorseQueue.due(started.pending, 1))
    }

    @Test
    fun `a second action commits the first at once`() {
        val waiting = RemorseQueue.start(null, first, 1).pending

        val started = RemorseQueue.start(waiting, second, 2)

        assertEquals(RemorseQueue.Started(PendingRemorse(2, second), committed = first), started)
        assertNull(RemorseQueue.due(started.pending, 1), "The first timer is stale")
        assertEquals(second, RemorseQueue.due(started.pending, 2))
    }

    @Test
    fun `a cancelled action never runs`() {
        assertNull(RemorseQueue.due(null, 1))
    }
}
