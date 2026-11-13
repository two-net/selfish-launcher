package io.github.twonet.selfish.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import io.github.twonet.selfish.ui.motion.Axis
import io.github.twonet.selfish.ui.motion.GestureMath
import io.github.twonet.selfish.ui.tokens.LauncherMotion
import io.github.twonet.selfish.ui.tokens.LauncherTheme
import io.github.twonet.selfish.ui.tokens.Motion
import io.github.twonet.selfish.ui.tokens.Sizes
import io.github.twonet.selfish.ui.tokens.Spacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** One item of a [PulleyMenu]: its [label], and what choosing it does. */
class PulleyItem(val label: String, val onSelect: () -> Unit)

/**
 * A pulley menu over a page's [content] (FR-GEST-05, FR-ANIM-09, DESIGN
 * §6.4). While [atTop], pulling the page down past its top reveals [items]
 * one at a time above it, the first one nearest the page, stretching with
 * rubber-band resistance. The item that has just come fully into view is
 * highlighted, with a tick whenever that changes, played through
 * `LocalHapticFeedback` so it follows the Haptic feedback setting
 * (FR-GEST-08). Releasing flashes it in the highlight color and chooses it,
 * and releasing before the first item cancels. A short bar at the top shows
 * that the menu is there.
 *
 * Pulls are recognized like the launcher's swipes (DESIGN §6.3): the page is
 * watched before its elements, and a touch that moves down past the slop
 * before the long-press timeout is taken over. Taps, scrolling and
 * long-presses still reach the elements. Screen readers choose the items
 * through [pulleyActions] on the page's header instead (FR-GEST-09).
 *
 * The page grows by the pull, up to the space it has; beyond that, its
 * bottom is pushed out of view.
 */
@Composable
fun PulleyMenu(
    items: List<PulleyItem>,
    atTop: () -> Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val pulley = remember(scope) { Pulley(scope) }
    val haptics = LocalHapticFeedback.current
    val motion = LauncherTheme.motion
    val density = LocalDensity.current
    val header = LauncherTheme.type.header
    // Rows grow with the font scale, so a large label still fits its row.
    val rowPx = with(density) { maxOf(Sizes.PulleyItem.toPx(), header.lineHeight.toPx()) }
    SideEffect {
        pulley.items = items
        pulley.item = rowPx
        pulley.atTop = atTop
        pulley.onTick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) }
        pulley.motion = motion
    }
    val colors = LauncherTheme.colors
    val highlight = LauncherTheme.highlight
    val indicatorSize = with(density) { Size(Sizes.PulleyIndicatorWidth.toPx(), Sizes.PulleyIndicatorThickness.toPx()) }
    val indicatorTop = with(density) { Spacing.Tiny.toPx() }

    Layout(
        contents = listOf(
            {
                items.forEachIndexed { index, item ->
                    val lit = index == pulley.highlighted || index == pulley.chosen
                    BasicText(
                        text = item.label,
                        modifier = Modifier.fillMaxWidth().wrapContentHeight(Alignment.CenterVertically).clearAndSetSemantics {},
                        style = header.copy(color = if (lit) highlight else colors.textPrimary, textAlign = TextAlign.Center),
                        maxLines = 1,
                    )
                }
            },
            content,
        ),
        modifier = modifier
            .pulleyGestures(pulley)
            .clipToBounds()
            .drawWithContent {
                drawContent()
                // Fades out as the menu is pulled into view.
                val shown = 1f - (pulley.pull / rowPx).coerceIn(0f, 1f)
                if (items.isNotEmpty() && shown > 0f) {
                    drawRoundRect(
                        color = colors.textSecondary,
                        topLeft = Offset((size.width - indicatorSize.width) / 2f, indicatorTop),
                        size = indicatorSize,
                        cornerRadius = CornerRadius(indicatorSize.height / 2f),
                        alpha = shown,
                    )
                }
            },
    ) { (rows, page), constraints ->
        val pull = pulley.pull.roundToInt()
        val pages = page.map { it.measure(constraints) }
        val width = pages.maxOfOrNull { it.width } ?: constraints.minWidth
        val row = rowPx.roundToInt()
        val rowPlaceables = rows.map { it.measure(Constraints.fixed(width, row)) }
        val height = ((pages.maxOfOrNull { it.height } ?: 0) + pull).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            pages.forEach { it.place(0, pull) }
            // The first item just above the page, the others above it.
            rowPlaceables.forEachIndexed { index, placeable -> placeable.place(0, pull - (index + 1) * row) }
        }
    }
}

/**
 * The [PulleyMenu] [items] as custom accessibility actions, for the page's
 * header (FR-GEST-09, DESIGN §6.7).
 */
fun pulleyActions(items: List<PulleyItem>): List<CustomAccessibilityAction> = items.map { item ->
    CustomAccessibilityAction(item.label) {
        item.onSelect()
        true
    }
}

/** A pulley menu's pull: transient UI state (AR-02), owned by the page. */
@Stable
private class Pulley(private val scope: CoroutineScope) {
    /** How far the finger has pulled, before the rubber band. */
    private val drag = Animatable(0f)

    /** The item at the current depth, highlighted while pulling. */
    var highlighted by mutableStateOf<Int?>(null)
        private set

    /** The item just chosen, flashing while the menu closes. */
    var chosen by mutableStateOf<Int?>(null)
        private set

    // Updated after every composition.
    var items: List<PulleyItem> = emptyList()
    var item = 1f
    var atTop: () -> Boolean = { false }
    var onTick: () -> Unit = {}
    var motion = LauncherMotion()

    private val limit: Float get() = GestureMath.pulleyLimit(item, items.size)

    /** How far the page has moved down, in pixels. Read it in a layout or draw phase. */
    val pull: Float get() = GestureMath.rubberBand(drag.value, limit)

    fun canPull(): Boolean = items.isNotEmpty() && chosen == null && atTop()

    /** Moves the pull by [distance] pixels, down being positive. */
    fun dragBy(distance: Float) {
        val to = (drag.value + distance).coerceAtLeast(0f)
        // Undispatched, so the frame drawn next already shows the move.
        scope.launch(start = CoroutineStart.UNDISPATCHED) { drag.snapTo(to) }
        val index = GestureMath.pulleyIndex(GestureMath.rubberBand(to, limit), item, items.size)
        if (index != highlighted) {
            highlighted = index
            if (index != null) onTick()
        }
    }

    /** The finger was lifted: choose the highlighted item, if any, once it has flashed. */
    fun release() {
        val index = highlighted
        val choice = index?.let { items.getOrNull(it) }
        end()
        if (index == null || choice == null) return
        chosen = index
        scope.launch {
            delay(Motion.PULLEY_FLASH_MILLIS)
            chosen = null
            choice.onSelect()
        }
    }

    /** The touch was taken away: close without choosing. */
    fun cancel() = end()

    private fun end() {
        highlighted = null
        scope.launch { drag.animateTo(0f, motion.settle()) }
    }
}

/** Takes over a touch that pulls the page down from its top (DESIGN §6.3). */
private fun Modifier.pulleyGestures(pulley: Pulley): Modifier = pointerInput(pulley) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!pulley.canPull()) return@awaitEachGesture
        var taken = false
        var moved = Offset.Zero
        var released = false
        while (true) {
            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                if (taken) {
                    change.consume()
                    released = true
                }
                break
            }
            if (taken) {
                pulley.dragBy(change.positionChange().y)
                change.consume()
                continue
            }
            // A pull moves before the long-press timeout; after it, the touch
            // belongs to the long-pressed item.
            if (change.isConsumed || change.uptimeMillis - down.uptimeMillis > viewConfiguration.longPressTimeoutMillis) break
            moved += change.positionChange()
            when (GestureMath.lockedAxis(moved.x, moved.y, viewConfiguration.touchSlop)) {
                null -> Unit
                Axis.Horizontal -> break
                Axis.Vertical -> {
                    if (moved.y <= 0f) break
                    taken = true
                    pulley.dragBy(moved.y)
                    change.consume()
                }
            }
        }
        when {
            released -> pulley.release()
            // A touch the system took away still has to close the menu.
            taken -> pulley.cancel()
        }
    }
}
