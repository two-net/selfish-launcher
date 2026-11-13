package io.github.twonet.selfish.ui.motion

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot

/**
 * The places on screen whose sideways swipes are their own, such as a
 * dismissible notification in Events (FR-EVT-05, DESIGN §6.3).
 * [layerGestures] watches every touch before the elements under it, so it
 * asks here where a touch started before it takes a sideways swipe.
 *
 * Each place is kept as its layout coordinates and measured when asked,
 * so a list that has scrolled or a page that has moved is never out of
 * date. Use it on the main thread.
 */
class SidewaysClaims {
    private val places = HashMap<Any, LayoutCoordinates>()

    /** [owner] takes sideways swipes that start within [coordinates], as laid out now and later. */
    fun claim(owner: Any, coordinates: LayoutCoordinates) {
        places[owner] = coordinates
    }

    /** [owner] no longer takes sideways swipes. */
    fun release(owner: Any) {
        places.remove(owner)
    }

    /** Whether a touch at [position], in the root's coordinates, starts on a place that takes sideways swipes. */
    fun contains(position: Offset): Boolean =
        places.values.any { it.isAttached && it.boundsInRoot().contains(position) }
}
