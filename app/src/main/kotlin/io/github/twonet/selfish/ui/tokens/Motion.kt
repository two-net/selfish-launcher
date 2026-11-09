package io.github.twonet.selfish.ui.tokens

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
}
