package io.github.twonet.selfish.ui.tokens

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.VectorConverter
import io.github.twonet.selfish.domain.Verifies
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The motion tokens match SRS §4.9, and reduce as FR-ANIM-15 asks (DESIGN
 * §6.3, §6.5). Each spec is measured the way Compose runs it, from 0 to 1.
 */
class MotionTokensTest {
    private val full = LauncherMotion(reduced = false)
    private val reduced = LauncherMotion(reduced = true)

    /** Every transition spec, by name. The remorse countdown isn't one: it shows time left. */
    private fun LauncherMotion.transitions(): Map<String, FiniteAnimationSpec<Float>> = mapOf(
        "settle" to settle(),
        "reflow" to reflow(),
        "fadeIn" to fadeIn(),
        "fadeOut" to fadeOut(),
        "results" to results(),
        "resultsFadeIn" to resultsFadeIn(),
        "resultsFadeOut" to resultsFadeOut(),
        "folder opening" to folder(opening = true),
        "folder closing" to folder(opening = false),
        "pressFade" to pressFade(),
        "homeReturn" to homeReturn(),
        "appearance" to appearance(),
        "highlight" to highlight(),
        "wallpaper" to wallpaper(),
        "hintTrace" to hintTrace(),
        "hintFade" to hintFade(),
    )

    private fun FiniteAnimationSpec<Float>.millis(): Long =
        vectorize(Float.VectorConverter).getDurationNanos(AnimationVector1D(0f), AnimationVector1D(1f), AnimationVector1D(0f)) / 1_000_000

    @Test
    @Verifies("FR-ANIM-15", "NFR-A11Y-05", "AC-09")
    fun `under reduced motion every transition is instant or at most 100 ms`() {
        val tooLong = reduced.transitions()
            .mapValues { (_, spec) -> spec.millis() }
            .filterValues { it > Motion.REDUCED_MILLIS }

        assertEquals(emptyMap(), tooLong, "Reduced transitions in ms")
        assertEquals(100, Motion.REDUCED_MILLIS)
    }

    @Test
    @Verifies("FR-ANIM-15")
    fun `under reduced motion things that move snap, and only fades remain`() {
        for (spec in listOf(reduced.settle<Float>(), reduced.reflow(), reduced.results(), reduced.hintTrace())) {
            assertEquals(0L, spec.millis())
        }
    }

    @Test
    fun `the durations are the SRS tokens`() {
        assertEquals(150, Motion.SHORT_MILLIS)
        assertEquals(250, Motion.MEDIUM_MILLIS)
        assertEquals(400, Motion.LONG_MILLIS)
        assertEquals(800, Motion.AMBIENT_MILLIS)

        val expected = mapOf(
            "reflow" to Motion.MEDIUM_MILLIS,
            "fadeIn" to Motion.MEDIUM_MILLIS,
            "fadeOut" to Motion.MEDIUM_MILLIS,
            "results" to Motion.SHORT_MILLIS,
            "resultsFadeIn" to Motion.SHORT_MILLIS,
            "resultsFadeOut" to Motion.SHORT_MILLIS,
            "folder opening" to Motion.MEDIUM_MILLIS,
            "folder closing" to Motion.MEDIUM_MILLIS,
            "pressFade" to Motion.SHORT_MILLIS,
            "homeReturn" to Motion.MEDIUM_MILLIS,
            "appearance" to Motion.LONG_MILLIS,
            "highlight" to Motion.AMBIENT_MILLIS,
            // The outgoing wallpaper fades in step with the highlight (FR-ANIM-10).
            "wallpaper" to Motion.AMBIENT_MILLIS,
        )
        val actual = full.transitions().filterKeys { it in expected }.mapValues { (_, spec) -> spec.millis().toInt() }

        assertEquals(expected, actual)
    }

    @Test
    fun `releases settle with a critically damped spring`() {
        val spring = assertIs<SpringSpec<Float>>(full.settle<Float>())
        assertEquals(1f, spring.dampingRatio)
    }

    @Test
    fun `arrivals decelerate and departures accelerate`() {
        // Halfway through its time, an element decelerating into place is past
        // halfway, and one accelerating away isn't yet.
        assertTrue(Motion.EnterEasing.transform(0.5f) > 0.5f)
        assertTrue(Motion.ExitEasing.transform(0.5f) < 0.5f)

        for (spec in listOf(full.reflow<Float>(), full.fadeIn(), full.folder(opening = true), full.homeReturn())) {
            assertEquals(Motion.EnterEasing, assertIs<TweenSpec<Float>>(spec).easing)
        }
        for (spec in listOf(full.fadeOut<Float>(), full.folder(opening = false), full.pressFade(), full.resultsFadeOut())) {
            assertEquals(Motion.ExitEasing, assertIs<TweenSpec<Float>>(spec).easing)
        }
    }

    @Test
    @Verifies("FR-ANIM-03")
    fun `Home returns from 96 percent and partly transparent`() {
        assertEquals(0.96f, Motion.HOME_RETURN_SCALE)
        assertTrue(Motion.HOME_RETURN_ALPHA in 0f..1f && Motion.HOME_RETURN_ALPHA < 1f)
    }
}
