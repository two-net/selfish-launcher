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
}
