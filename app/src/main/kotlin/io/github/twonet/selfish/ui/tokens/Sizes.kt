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

    /**
     * How far a pulley menu is pulled before its first item is chosen, and
     * then from one item to the next (FR-GEST-05). Each item's row is this
     * tall, so the chosen item is the one that has just come fully into view.
     */
    val PulleyItem: Dp = 48.dp

    /** The bar at the top of a page that shows it has a pulley menu (FR-GEST-05): its width, then its thickness. */
    val PulleyIndicatorWidth: Dp = 32.dp
    val PulleyIndicatorThickness: Dp = 4.dp

    /** The thickness of the remorse banner's countdown bar (FR-GEST-06). */
    val RemorseBar: Dp = 4.dp

    /** The thumbnail of the wallpaper on the Events card (FR-EVT-03). */
    val WallpaperThumbnail: Dp = 64.dp

    /** The width of each tile in the Top Menu's Ambiences row (FR-TOP-03). */
    val AmbienceWidth: Dp = 64.dp

    /** Ambience tiles are portrait, like the screen they preview: width divided by height. */
    val AmbienceAspectRatio: Float = 3f / 4f

    /** The outline of a chosen option, such as the active ambience, in the highlight color (FR-TOP-02, FR-TOP-03). */
    val SelectedOutline: Dp = 4.dp

    /** How strongly the wallpaper is blurred behind the open App Grid, where supported (FR-GRID-01). */
    val BlurRadius: Dp = 32.dp
}
