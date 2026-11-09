package io.github.twonet.selfish.ui.tokens

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Gaps and padding on a 4 dp scale (UI-06). Every step is a multiple of [Step]. */
object Spacing {
    /** The base step of the spacing scale. */
    val Step: Dp = 4.dp

    val Tiny: Dp = Step

    val Small: Dp = Step * 2

    val Medium: Dp = Step * 3

    /** Side margin of full-width content. */
    val Gutter: Dp = Step * 4

    val Large: Dp = Step * 6

    val Huge: Dp = Step * 8

    /** Every named step, smallest first. */
    val scale: List<Dp> = listOf(Tiny, Small, Medium, Gutter, Large, Huge)
}
