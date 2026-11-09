package io.github.twonet.selfish.ui.tokens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Corner radii (UI-08). */
object Radii {
    /** Glass panels, cards and menus. */
    val Glass: Dp = 12.dp

    /** App icons that support a shape (UI-05), about 30% of [Sizes.Icon]. */
    val Icon: Dp = 16.dp
}

/** The shapes built from [Radii]. */
object Shapes {
    val Glass: Shape = RoundedCornerShape(Radii.Glass)

    /** The one rounded shape for app icons (UI-05). */
    val Icon: Shape = RoundedCornerShape(Radii.Icon)
}
