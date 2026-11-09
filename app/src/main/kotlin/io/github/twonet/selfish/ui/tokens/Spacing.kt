package io.github.twonet.selfish.ui.tokens

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Sizes on a 4 dp scale (UI-06). #4 completes the set. */
object Spacing {
    /** The base step of the spacing scale. */
    val Step: Dp = 4.dp

    /** Side margin of full-width content. */
    val Gutter: Dp = Step * 4

    /** The minimum size of anything that can be touched (UI-06). */
    val MinTouchTarget: Dp = 48.dp
}
