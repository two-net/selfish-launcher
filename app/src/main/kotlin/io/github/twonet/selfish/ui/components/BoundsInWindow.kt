package io.github.twonet.selfish.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import io.github.twonet.selfish.domain.model.Bounds
import kotlin.math.roundToInt

/**
 * Reports the element's bounds in the window whenever it moves, for example
 * the cover or icon a launched app grows from (FR-ANIM-02). The launcher's
 * window fills the screen, so these are also screen coordinates.
 */
fun Modifier.onBoundsInWindow(onBounds: (Bounds) -> Unit): Modifier = onGloballyPositioned { coordinates ->
    val rect = coordinates.boundsInWindow()
    onBounds(
        Bounds(
            left = rect.left.roundToInt(),
            top = rect.top.roundToInt(),
            right = rect.right.roundToInt(),
            bottom = rect.bottom.roundToInt(),
        ),
    )
}
