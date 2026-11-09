package io.github.twonet.selfish.ui.tokens

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween

/** The durations of SRS §4.9, in milliseconds. #12 and #13 add easings and springs. */
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
}
