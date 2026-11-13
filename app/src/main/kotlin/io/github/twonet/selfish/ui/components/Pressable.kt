package io.github.twonet.selfish.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import io.github.twonet.selfish.ui.tokens.Sizes

/**
 * Makes an element respond to taps and long-presses. It is the only way the
 * launcher handles them (`LayerRulesTest`), so every touch target is at least
 * [Sizes.MinTouchTarget] on each side (UI-06), and the pressed glow
 * (FR-ANIM-04, #13) reaches every element through `LocalIndication`.
 *
 * With [liftDrag], a long-press lifts the element instead of calling
 * [onLongClick], and the same touch can then drag it (FR-HOME-06,
 * FR-GRID-06). Screen readers get the lift as the long-click action, labeled
 * [onLongClickLabel].
 *
 * Long-presses wait for the system's long-press timeout and tick
 * (FR-GEST-07): [onLongClick] through the clickable's own long-press
 * haptic, a lift through whoever made the [LiftDrag] (`ReorderableGrid`).
 * Both play through `LocalHapticFeedback`, so both follow the Haptic
 * feedback setting (`LauncherHaptics`, FR-GEST-08).
 */
fun Modifier.pressable(
    onClickLabel: String? = null,
    role: Role? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    liftDrag: LiftDrag? = null,
    onClick: () -> Unit,
): Modifier {
    val clickable = this
        .sizeIn(minWidth = Sizes.MinTouchTarget, minHeight = Sizes.MinTouchTarget)
        .combinedClickable(
            onClickLabel = onClickLabel,
            role = role,
            onLongClickLabel = onLongClickLabel,
            onLongClick = if (liftDrag == null) onLongClick else null,
            onClick = onClick,
        )
    if (liftDrag == null) return clickable
    return clickable
        .semantics {
            // A long-press and release without moving.
            onLongClick(label = onLongClickLabel) {
                liftDrag.onLift()
                liftDrag.onDrop()
                true
            }
        }
        // Inside the clickable, so it sees each event first on the way back
        // up: after a long-press it consumes the moves and the release, and
        // the clickable then neither clicks nor stays pressed.
        .pointerInput(liftDrag) {
            detectDragGesturesAfterLongPress(
                onDragStart = { liftDrag.onLift() },
                onDragEnd = { liftDrag.onDrop() },
                onDragCancel = { liftDrag.onCancel() },
                onDrag = { _, amount -> liftDrag.onDrag(amount) },
            )
        }
}

/**
 * What happens when a [pressable] element is lifted with a long-press and
 * dragged. A long-press and release without moving is a lift followed by a
 * drop. Keep one instance per element, because a new one restarts the
 * gesture.
 */
class LiftDrag(
    /** The long-press lifted the element; the system's long-press timeout has passed. Play the long-press tick here (FR-GEST-07). */
    val onLift: () -> Unit,
    /** The finger moved by this much since the last call, in pixels. */
    val onDrag: (Offset) -> Unit,
    /** The finger was lifted. */
    val onDrop: () -> Unit,
    /** The gesture was taken away, for example by the system. */
    val onCancel: () -> Unit,
)
