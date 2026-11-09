package io.github.twonet.selfish.ui.tokens

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import kotlin.time.Duration

/**
 * The durations of SRS §4.9, in milliseconds, the release spring and the
 * gesture thresholds (DESIGN §6.3). #13 adds the easings.
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

    /** How much a lifted item grows while it is dragged: about 110% (FR-ANIM-06). */
    const val LIFT_SCALE = 1.1f

    /**
     * Items making room, menus opening beneath their row, and dropped items
     * settling into place (FR-ANIM-05, FR-ANIM-06, FR-ANIM-08). #13 adds the
     * enter and exit easings.
     */
    fun <T> reflow(): FiniteAnimationSpec<T> = tween(MEDIUM_MILLIS)

    /**
     * A folder expanding from its tile into its panel and collapsing back
     * into it, and the dim behind it (FR-FOLD-03, FR-ANIM-07). #13 adds the
     * enter and exit easings.
     */
    fun <T> folder(): FiniteAnimationSpec<T> = tween(MEDIUM_MILLIS)

    /**
     * The remorse banner's bar, which shrinks at an even pace over the whole
     * of the timer's [duration] (FR-GEST-06, FR-ANIM-12).
     */
    fun countdown(duration: Duration): FiniteAnimationSpec<Float> =
        tween(durationMillis = duration.inWholeMilliseconds.toInt(), easing = LinearEasing)

    /** How long the chosen pulley menu item flashes before it runs (FR-ANIM-09). */
    const val PULLEY_FLASH_MILLIS = SHORT_MILLIS.toLong()

    /**
     * After a drag is released: critically damped, so it never visibly
     * overshoots (SRS §4.9 "Release"). Seed it with the release velocity.
     */
    fun <T> settle(): SpringSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

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
}
