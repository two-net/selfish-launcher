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

    /** The icons a folder's tile previews, up to four in a square (FR-GRID-02, FR-FOLD-02). */
    val FolderPreviewIcon: Dp = 24.dp

    /**
     * How close to the top or bottom of the App Grid a dragged item must be
     * for the grid to scroll (FR-GRID-07).
     */
    val AutoScrollEdge: Dp = 64.dp

    /** How far the App Grid scrolls in a second with a dragged item at its very edge (FR-GRID-07). */
    val AutoScrollSpeed: Dp = 960.dp

    /** How strongly the wallpaper is blurred behind the open App Grid, where supported (FR-GRID-01). */
    val BlurRadius: Dp = 32.dp
}
