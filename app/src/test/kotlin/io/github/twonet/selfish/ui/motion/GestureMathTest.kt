package io.github.twonet.selfish.ui.motion

import io.github.twonet.selfish.domain.Verifies
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GestureMathTest {
    private val minFling = 100f

    @Test
    @Verifies("FR-GEST-02", "FR-GRID-04")
    fun `a slow release completes from 30 percent and peeks back below it`() {
        assertFalse(GestureMath.shouldComplete(covered = 0.2f, velocity = 0f, minFling = minFling))
        assertFalse(GestureMath.shouldComplete(covered = 0.29f, velocity = minFling / 2, minFling = minFling))
        assertTrue(GestureMath.shouldComplete(covered = 0.3f, velocity = 0f, minFling = minFling))
        assertTrue(GestureMath.shouldComplete(covered = 0.9f, velocity = -minFling / 2, minFling = minFling))
    }

    @Test
    @Verifies("FR-GEST-02", "FR-GRID-01")
    fun `a fling decides whatever the distance`() {
        assertTrue(GestureMath.shouldComplete(covered = 0.05f, velocity = minFling, minFling = minFling))
        assertFalse(GestureMath.shouldComplete(covered = 0.95f, velocity = -minFling, minFling = minFling), "Flung back, it returns")
    }

    @Test
    @Verifies("FR-GEST-04")
    fun `a drag locks to its dominant axis once it passes the slop`() {
        val slop = 8f

        assertNull(GestureMath.lockedAxis(dx = 5f, dy = 6f, slop = slop))
        assertEquals(Axis.Vertical, GestureMath.lockedAxis(dx = 3f, dy = -12f, slop = slop))
        assertEquals(Axis.Horizontal, GestureMath.lockedAxis(dx = -12f, dy = 3f, slop = slop))
        assertEquals(Axis.Vertical, GestureMath.lockedAxis(dx = 9f, dy = 9f, slop = slop), "A diagonal is vertical")
    }

    @Test
    @Verifies("FR-GEST-05")
    fun `a pulley menu stretches with growing resistance and never past its limit`() {
        val limit = 200f

        assertEquals(0f, GestureMath.rubberBand(-10f, limit))
        assertEquals(0f, GestureMath.rubberBand(0f, limit))
        val steps = listOf(20f, 100f, 200f, 400f, 2_000f).map { GestureMath.rubberBand(it, limit) }
        assertTrue(steps.zipWithNext().all { (a, b) -> b > a }, "Further is always further: $steps")
        assertTrue(steps.first() > 18f, "Almost one to one at first: ${steps.first()}")
        assertTrue(steps.last() < limit, "Never past the limit: ${steps.last()}")
        val gains = listOf(0f, 100f, 200f, 300f).map { GestureMath.rubberBand(it + 100f, limit) - GestureMath.rubberBand(it, limit) }
        assertTrue(gains.zipWithNext().all { (a, b) -> b < a }, "Resistance grows: $gains")
    }

    @Test
    @Verifies("FR-GEST-05")
    fun `the pull depth picks one item at a time, none before the first, and stays on the last`() {
        val item = 48f

        assertNull(GestureMath.pulleyIndex(pull = 47f, item = item, count = 2), "Before the first item, a release cancels")
        assertEquals(0, GestureMath.pulleyIndex(pull = 48f, item = item, count = 2))
        assertEquals(0, GestureMath.pulleyIndex(pull = 95f, item = item, count = 2))
        assertEquals(1, GestureMath.pulleyIndex(pull = 96f, item = item, count = 2))
        assertEquals(1, GestureMath.pulleyIndex(pull = 500f, item = item, count = 2))
        assertNull(GestureMath.pulleyIndex(pull = 500f, item = item, count = 0))
    }

    @Test
    @Verifies("FR-GEST-05")
    fun `every item of a pulley menu can be reached within its limit`() {
        val item = 48f
        (1..4).forEach { count ->
            val limit = GestureMath.pulleyLimit(item, count)
            assertEquals(count - 1, GestureMath.pulleyIndex(GestureMath.rubberBand(10_000f, limit), item, count))
        }
    }
}
