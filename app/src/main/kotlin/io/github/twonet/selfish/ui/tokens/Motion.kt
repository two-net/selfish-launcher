package io.github.twonet.selfish.ui.tokens

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import kotlin.time.Duration

/**
 * The durations and easings of SRS §4.9, in milliseconds, and the gesture
 * thresholds (DESIGN §6.3). The animation specs built from them are in
 * [LauncherMotion], which views read as `LauncherTheme.motion` so that
 * reduced motion reaches every one of them (FR-ANIM-15).
 */
object Motion {
    /** Press feedback, small fades, search results. */
    const val SHORT_MILLIS = 150

    /** Settling after release, folders, context menus, reflow. */
    const val MEDIUM_MILLIS = 250

    /** Appearance changes, large layout changes. */
    const val LONG_MILLIS = 400

    /** Wallpaper and highlight-color crossfades. */
    const val AMBIENT_MILLIS = 800

    /**
     * The longest a transition takes under reduced motion: it is instant, or
     * a fade of at most this long (FR-ANIM-15, NFR-A11Y-05).
     */
    const val REDUCED_MILLIS = 100

    /** Elements arriving or settling decelerate into place (SRS §4.9 "Enter easing"). */
    val EnterEasing: Easing = LinearOutSlowInEasing

    /** Elements leaving accelerate away (SRS §4.9 "Exit easing"). */
    val ExitEasing: Easing = FastOutLinearInEasing

    /** How much a lifted item grows while it is dragged: about 110% (FR-ANIM-06). */
    const val LIFT_SCALE = 1.1f

    /** How small Home is as it starts settling back into place when the launcher returns (FR-ANIM-03). */
    const val HOME_RETURN_SCALE = 0.96f

    /** How opaque Home is as it starts settling back into place: partly transparent (FR-ANIM-03). */
    const val HOME_RETURN_ALPHA = 0.6f

    /**
     * The remorse banner's bar, which shrinks at an even pace over the whole
     * of the timer's [duration] (FR-GEST-06, FR-ANIM-12). Not a transition,
     * but how much time is left, so reduced motion keeps it.
     */
    fun countdown(duration: Duration): FiniteAnimationSpec<Float> =
        tween(durationMillis = duration.inWholeMilliseconds.toInt(), easing = LinearEasing)

    /** How long the chosen pulley menu item flashes before it runs (FR-ANIM-09). */
    const val PULLEY_FLASH_MILLIS = SHORT_MILLIS.toLong()

    /**
     * A released transition completes once it has covered this fraction of
     * its distance; otherwise it returns to where it started (FR-GEST-02).
     */
    const val COMPLETE_FRACTION = 0.30f

    /**
     * Where a drop makes a folder: the central part of an app, as a
     * fraction of its width and height (FR-GRID-08). Around it, the dragged
     * item takes the app's place instead.
     */
    const val DROP_ZONE_FRACTION = 0.5f

    /**
     * How long a dragged item rests at the edge of an app it could also be
     * dropped onto before taking that app's place, so passing over an app on
     * the way to its center doesn't push it away (FR-GRID-07, FR-GRID-08).
     */
    const val REORDER_DWELL_MILLIS = 250L

    /**
     * How many times a gesture hint traces its gesture each time it comes on
     * screen, before it rests (FR-ANIM-13). It never loops forever, so an
     * idle Home draws no frames (NFR-PWR-01).
     */
    const val HINT_REPEATS = 3

    /** The pause before a gesture hint traces its gesture, and between traces (FR-ANIM-13). */
    const val HINT_PAUSE_MILLIS = 1_000L
}

/**
 * Every animation spec the launcher's views use, built from the [Motion]
 * tokens (AR-04, UI-08). When [reduced], transitions are instant ([snap])
 * or fades of at most [Motion.REDUCED_MILLIS] (FR-ANIM-15, NFR-A11Y-05);
 * drags still follow the finger, because they aren't animations.
 */
@Immutable
data class LauncherMotion(val reduced: Boolean = false) {
    /**
     * After a drag is released, or when a view moves on its own, such as on a
     * Back press: critically damped, so it never visibly overshoots (SRS §4.9
     * "Release"). Seed it with the release velocity.
     */
    fun <T> settle(): FiniteAnimationSpec<T> =
        if (reduced) snap() else spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    /**
     * Items making room, menus opening beneath their row, lifted items
     * growing, and dropped items settling into place (FR-ANIM-05,
     * FR-ANIM-06, FR-ANIM-08).
     */
    fun <T> reflow(): FiniteAnimationSpec<T> = move(Motion.MEDIUM_MILLIS)

    /** Items and menus appearing (FR-ANIM-05, FR-ANIM-08). */
    fun <T> fadeIn(): FiniteAnimationSpec<T> = fade(Motion.MEDIUM_MILLIS, Motion.EnterEasing)

    /** Items and menus leaving (FR-ANIM-05, FR-ANIM-08). */
    fun <T> fadeOut(): FiniteAnimationSpec<T> = fade(Motion.MEDIUM_MILLIS, Motion.ExitEasing)

    /**
     * Search results moving into place as the query changes (FR-SRCH-01),
     * quicker than [reflow] so they keep up with typing.
     */
    fun <T> results(): FiniteAnimationSpec<T> = move(Motion.SHORT_MILLIS)

    /** Search results appearing. */
    fun <T> resultsFadeIn(): FiniteAnimationSpec<T> = fade(Motion.SHORT_MILLIS, Motion.EnterEasing)

    /** Search results leaving. */
    fun <T> resultsFadeOut(): FiniteAnimationSpec<T> = fade(Motion.SHORT_MILLIS, Motion.ExitEasing)

    /**
     * A folder expanding from its tile into its panel when [opening], and
     * collapsing back into it otherwise, with the dim behind it (FR-FOLD-03,
     * FR-ANIM-07). When [reduced], the panel only fades.
     */
    fun <T> folder(opening: Boolean): FiniteAnimationSpec<T> =
        fade(Motion.MEDIUM_MILLIS, if (opening) Motion.EnterEasing else Motion.ExitEasing)

    /** A pressed item's glow fading out after release (FR-ANIM-04). */
    fun <T> pressFade(): FiniteAnimationSpec<T> = fade(Motion.SHORT_MILLIS, Motion.ExitEasing)

    /**
     * Home settling back into place as the launcher returns from an app
     * (FR-ANIM-03). When [reduced], it only fades in.
     */
    fun <T> homeReturn(): FiniteAnimationSpec<T> = fade(Motion.MEDIUM_MILLIS, Motion.EnterEasing)

    /** Every launcher color crossfading to a new appearance (FR-ANIM-11). */
    fun <T> appearance(): FiniteAnimationSpec<T> = fade(Motion.LONG_MILLIS, Motion.EnterEasing)

    /** The highlight color moving to the new wallpaper's (FR-ANIM-10). */
    fun <T> highlight(): FiniteAnimationSpec<T> = fade(Motion.AMBIENT_MILLIS, Motion.EnterEasing)

    /**
     * The outgoing wallpaper fading away to reveal the new one, in step with
     * the highlight color (FR-ANIM-10, DESIGN §6.6).
     */
    fun <T> wallpaper(): FiniteAnimationSpec<T> = fade(Motion.AMBIENT_MILLIS, Motion.EnterEasing)

    /** A gesture hint's touch point tracing its gesture (FR-ANIM-13). When [reduced], it doesn't move. */
    fun <T> hintTrace(): FiniteAnimationSpec<T> = move(Motion.LONG_MILLIS)

    /** A gesture hint's touch point appearing before it traces its gesture and leaving after (FR-ANIM-13). */
    fun <T> hintFade(): FiniteAnimationSpec<T> = fade(Motion.SHORT_MILLIS, Motion.EnterEasing)

    /** Something moving into place: instant when [reduced]. */
    private fun <T> move(millis: Int): FiniteAnimationSpec<T> =
        if (reduced) snap() else tween(durationMillis = millis, easing = Motion.EnterEasing)

    /** Something fading or changing color: at most [Motion.REDUCED_MILLIS] when [reduced]. */
    private fun <T> fade(millis: Int, easing: Easing): FiniteAnimationSpec<T> =
        tween(durationMillis = if (reduced) minOf(millis, Motion.REDUCED_MILLIS) else millis, easing = easing)
}
