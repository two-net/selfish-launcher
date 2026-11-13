package io.github.twonet.selfish.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.github.twonet.selfish.ui.tokens.Emphasis
import io.github.twonet.selfish.ui.tokens.Sizes
import io.github.twonet.selfish.ui.tokens.Spacing
import io.github.twonet.selfish.ui.tokens.TextRole

/**
 * One item of a [ContextMenu]. Without [onClick] it is disabled and shows
 * [explanation] beneath its label, as "Add to Home" does when Home is full
 * (FR-HOME-05).
 */
class ContextMenuItem(
    val label: String,
    val explanation: String? = null,
    val onClick: (() -> Unit)?,
)

/**
 * A context menu: a full-width glass panel of [items]. The list or grid it
 * belongs to inserts it beneath the pressed item's row, pushing later rows
 * down (FR-GRID-06, FR-ANIM-08). When it opens beneath the last visible
 * row, the list or grid scrolls just enough to show it. Taps on the panel
 * that miss an item stay with it, so they don't count as a tap outside
 * ([onUnhandledTap]).
 */
@Composable
fun ContextMenu(items: List<ContextMenuItem>, modifier: Modifier = Modifier) {
    val inView = remember { BringIntoViewRequester() }
    LaunchedEffect(inView) {
        withFrameNanos { } // Once it has been laid out.
        inView.bringIntoView()
    }
    Glass(modifier = modifier.fillMaxWidth().bringIntoViewRequester(inView).keepTaps()) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.Small)) {
            items.forEach { MenuItem(it) }
        }
    }
}

@Composable
private fun MenuItem(item: ContextMenuItem) {
    val onClick = item.onClick
    val action = if (onClick != null) {
        Modifier.pressable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
            .sizeIn(minHeight = Sizes.MinTouchTarget)
            .semantics(mergeDescendants = true) { disabled() }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(action)
            .padding(horizontal = Spacing.Gutter, vertical = Spacing.Medium),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LauncherText(
            text = item.label,
            emphasis = if (onClick != null) Emphasis.Primary else Emphasis.Secondary,
            textAlign = TextAlign.Center,
        )
        if (item.explanation != null) {
            LauncherText(text = item.explanation, role = TextRole.Label, emphasis = Emphasis.Secondary, textAlign = TextAlign.Center)
        }
    }
}

/**
 * Calls [onTap] for a tap that nothing under the pointer handled, such as a
 * tap beside an open context menu, which closes it. It only observes, after
 * every element under the pointer has had its turn: a tap that clicked
 * something, or a touch that scrolled, doesn't count. Pass a stable [onTap],
 * because a new one restarts the gesture.
 */
fun Modifier.onUnhandledTap(onTap: () -> Unit): Modifier = pointerInput(onTap) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        if (waitForUpOrCancellation() != null) onTap()
    }
}

/**
 * Calls [onLongPress] for a long-press that nothing under the pointer
 * handled, such as one on an empty part of Home, which opens the Top Menu
 * (FR-TOP-06). Like [onUnhandledTap], it only observes, after the elements
 * under the pointer: a press one of them took, such as a cover's lift,
 * doesn't count, and neither does a touch that moves past the touch slop or
 * lifts before the system's long-press timeout (FR-GEST-07). Pass a stable
 * [onLongPress], because a new one restarts the gesture.
 */
fun Modifier.onUnhandledLongPress(onLongPress: () -> Unit): Modifier = pointerInput(onLongPress) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        // A pressable element under the finger has taken it.
        if (down.isConsumed) return@awaitEachGesture
        val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed || change.isConsumed) break
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
            }
        }
        // Still down, still in place and still nobody's when the timeout passed.
        if (ended == null) onLongPress()
    }
}

/** Consumes taps that reach this element unhandled, so an [onUnhandledTap] around it ignores them. */
private fun Modifier.keepTaps(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        waitForUpOrCancellation()?.consume()
    }
}
