package io.github.twonet.selfish.ui.motion

import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.state.Layer
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

    // Settling (FR-GEST-02)

    @Test
    @Verifies("FR-GEST-02", "FR-GRID-01", "FR-GRID-04")
    fun `the App Grid settles as it always has`() {
        val grid = 0f..1f
        // Opening from Home.
        assertEquals(0f, GestureMath.settleTarget(0f, 0.2f, 0f, minFling, grid))
        assertEquals(1f, GestureMath.settleTarget(0f, 0.3f, 0f, minFling, grid))
        assertEquals(1f, GestureMath.settleTarget(0f, 0.05f, minFling, minFling, grid))
        assertEquals(0f, GestureMath.settleTarget(0f, 0.9f, -minFling, minFling, grid))
        assertEquals(1f, GestureMath.settleTarget(0f, 0f, minFling, minFling, grid), "A fling that hasn't moved yet")
        assertEquals(0f, GestureMath.settleTarget(0f, 0f, -minFling, minFling, grid), "Nothing below Home")
        // Closing from the grid.
        assertEquals(0f, GestureMath.settleTarget(1f, 0.6f, 0f, minFling, grid))
        assertEquals(1f, GestureMath.settleTarget(1f, 0.8f, 0f, minFling, grid))
        assertEquals(0f, GestureMath.settleTarget(1f, 0.95f, -minFling, minFling, grid))
    }

    @Test
    @Verifies("FR-GEST-02", "FR-EVT-01")
    fun `the Events carousel completes, peeks and is flung either way`() {
        val fromHome = -1f..1f
        assertEquals(1f, GestureMath.settleTarget(0f, 0.4f, 0f, minFling, fromHome), "Pushed left")
        assertEquals(-1f, GestureMath.settleTarget(0f, -0.4f, 0f, minFling, fromHome), "Pushed right")
        assertEquals(0f, GestureMath.settleTarget(0f, 0.2f, 0f, minFling, fromHome), "Peeked")
        assertEquals(-1f, GestureMath.settleTarget(0f, -0.05f, -minFling, minFling, fromHome), "Flung right")
        assertEquals(0f, GestureMath.settleTarget(0f, 0.5f, -minFling, minFling, fromHome), "Flung back")

        val fromEvents = 0f..2f
        assertEquals(2f, GestureMath.settleTarget(1f, 1.4f, 0f, minFling, fromEvents), "On to Home on the other side")
        assertEquals(0f, GestureMath.settleTarget(1f, 0.6f, 0f, minFling, fromEvents), "Back to Home")
    }

    // The Events carousel (FR-EVT-01)

    @Test
    @Verifies("FR-EVT-01")
    fun `Events comes in from the side the finger pushes toward, and Home leaves the other way`() {
        // Home in view.
        assertEquals(0f, GestureMath.homeShift(0f))
        assertEquals(1f, GestureMath.eventsShift(0f))
        // Swiping left on Home: Events comes in from the right.
        assertEquals(-0.3f, GestureMath.homeShift(0.3f), 1e-6f)
        assertEquals(0.7f, GestureMath.eventsShift(0.3f), 1e-6f)
        // Swiping right on Home: Events comes in from the left.
        assertEquals(0.3f, GestureMath.homeShift(-0.3f), 1e-6f)
        assertEquals(-0.7f, GestureMath.eventsShift(-0.3f), 1e-6f)
        // Events in view, Home just off the side it was pushed to.
        assertEquals(0f, GestureMath.eventsShift(1f))
        assertEquals(-1f, GestureMath.homeShift(1f))
        assertEquals(0f, GestureMath.eventsShift(-1f))
        assertEquals(1f, GestureMath.homeShift(-1f))
        // Swiping left on Events: Home comes back from the right.
        assertEquals(0.7f, GestureMath.homeShift(1.3f), 1e-6f)
        assertEquals(-0.3f, GestureMath.eventsShift(1.3f), 1e-6f)
    }

    @Test
    @Verifies("FR-EVT-01")
    fun `the carousel goes back near zero by whole turns`() {
        assertEquals(0f, GestureMath.normalize(2f))
        assertEquals(0f, GestureMath.normalize(-2f))
        assertEquals(1f, GestureMath.normalize(1f))
        assertEquals(-1f, GestureMath.normalize(-1f))
        assertEquals(-1f, GestureMath.normalize(3f))
    }

    @Test
    @Verifies("FR-EVT-01", "FR-GEN-03", "FR-GEN-04", "FR-GEST-09")
    fun `Back and Home send Events out the way it came, and a screen reader opens it from the end side`() {
        assertEquals(0f, GestureMath.carouselRest(Layer.Home, 1f, endSide = 1f))
        assertEquals(0f, GestureMath.carouselRest(Layer.Home, -1f, endSide = 1f))
        assertEquals(2f, GestureMath.carouselRest(Layer.Home, 1.6f, endSide = 1f), "Already pushed on toward Home")
        assertEquals(1f, GestureMath.carouselRest(Layer.Events, 0f, endSide = 1f), "From the right")
        assertEquals(-1f, GestureMath.carouselRest(Layer.Events, 0f, endSide = -1f), "From the left in right-to-left layouts")
        assertEquals(-1f, GestureMath.carouselRest(Layer.Events, -0.4f, endSide = 1f), "Already coming in from the left")
        assertEquals(0f, GestureMath.carouselRest(Layer.AppGrid, 0f, endSide = 1f))
    }

    // Who takes a swipe (DESIGN §6.3)

    @Test
    @Verifies("FR-GRID-01", "FR-GRID-04", "FR-EVT-01", "FR-EVT-05", "FR-TOP-01", "FR-TOP-05")
    fun `each resting layer takes only its own swipes`() {
        fun route(from: Layer, axis: Axis, dx: Float = 0f, dy: Float = 0f, scrolls: Scrolls = Scrolls(), claimed: Boolean = false) =
            GestureMath.route(from, axis, dx, dy, scrolls, claimed)

        assertEquals(Track.AppGrid, route(Layer.Home, Axis.Vertical, dy = -20f))
        assertNull(route(Layer.Home, Axis.Vertical, dy = -20f, scrolls = Scrolls(homeForward = true)), "The covers scroll first")
        assertEquals(Track.TopMenu, route(Layer.Home, Axis.Vertical, dy = 20f))
        assertEquals(Track.TopMenu, route(Layer.Home, Axis.Vertical, dy = 20f, scrolls = Scrolls(homeForward = true)), "Covers that could scroll on don't stop a pull down")
        assertNull(route(Layer.Home, Axis.Vertical, dy = 20f, scrolls = Scrolls(homeBackward = true)), "Scrolled covers scroll back first")
        assertEquals(Track.Events, route(Layer.Home, Axis.Horizontal, dx = -20f))
        assertEquals(Track.Events, route(Layer.Home, Axis.Horizontal, dx = 20f))

        assertEquals(Track.AppGrid, route(Layer.AppGrid, Axis.Vertical, dy = 20f))
        assertNull(route(Layer.AppGrid, Axis.Vertical, dy = 20f, scrolls = Scrolls(gridBackward = true)))
        assertNull(route(Layer.AppGrid, Axis.Vertical, dy = -20f))
        assertNull(route(Layer.AppGrid, Axis.Horizontal, dx = 20f))

        assertEquals(Track.Events, route(Layer.Events, Axis.Horizontal, dx = 20f))
        assertEquals(Track.Events, route(Layer.Events, Axis.Horizontal, dx = -20f))
        assertNull(route(Layer.Events, Axis.Horizontal, dx = 20f, claimed = true), "A dismissible notification's")
        assertNull(route(Layer.Events, Axis.Vertical, dy = 20f), "The pulley menu's")
        assertNull(route(Layer.Events, Axis.Vertical, dy = -20f), "The list's")

        assertEquals(Track.TopMenu, route(Layer.TopMenu, Axis.Vertical, dy = -20f))
        assertNull(route(Layer.TopMenu, Axis.Vertical, dy = -20f, scrolls = Scrolls(topMenuForward = true)), "Its content scrolls first")
        assertNull(route(Layer.TopMenu, Axis.Vertical, dy = 20f), "Already open")
        assertNull(route(Layer.TopMenu, Axis.Horizontal, dx = 20f), "The Ambiences row's")
        assertNull(route(Layer.TopMenu, Axis.Horizontal, dx = -20f), "The Ambiences row's")
    }

    @Test
    @Verifies("FR-GEST-01", "FR-TOP-01")
    fun `each track follows the finger in its own direction`() {
        assertEquals(30f, GestureMath.along(Track.AppGrid, dx = 5f, dy = -30f), "Up opens the App Grid")
        assertEquals(30f, GestureMath.along(Track.Events, dx = -30f, dy = 5f), "Left counts up the carousel")
        assertEquals(30f, GestureMath.along(Track.TopMenu, dx = 5f, dy = 30f), "Down pulls the Top Menu")
        assertEquals(-30f, GestureMath.along(Track.TopMenu, dx = 0f, dy = -30f), "Up puts it back")
        for (track in Track.entries) assertEquals(track.grows.axis, track.axis)
    }

    @Test
    @Verifies("FR-TOP-01", "FR-TOP-05", "FR-GEST-02")
    fun `the Top Menu settles like the App Grid, measured in its own height`() {
        assertEquals(1f, GestureMath.settleTarget(0f, 0.4f, 0f, minFling, 0f..1f), "40% opens")
        assertEquals(0f, GestureMath.settleTarget(0f, 0.2f, 0f, minFling, 0f..1f), "20% peeks back")
        assertEquals(0f, GestureMath.settleTarget(1f, 0.6f, 0f, minFling, 0f..1f), "40% back up closes")
        assertEquals(1f, GestureMath.settleTarget(1f, 0.8f, 0f, minFling, 0f..1f), "20% back up stays")
    }
}
