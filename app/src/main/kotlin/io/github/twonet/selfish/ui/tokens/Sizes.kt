package io.github.twonet.selfish.ui.tokens

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Fixed sizes of things, as opposed to the gaps between them ([Spacing]). */
object Sizes {
    /** The one size every app icon is shown at, on Home and in the App Grid (UI-05). */
    val Icon: Dp = 56.dp

    /** The minimum size of anything that can be touched (UI-06). */
    val MinTouchTarget: Dp = 48.dp

    /** Home's covers are portrait cards (FR-HOME-03): width divided by height. */
    val CoverAspectRatio: Float = 3f / 4f

    /** The shadow a lifted item casts while it is dragged (FR-ANIM-06). */
    val LiftElevation: Dp = 8.dp
}
