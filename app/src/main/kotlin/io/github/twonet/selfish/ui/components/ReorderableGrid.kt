package io.github.twonet.selfish.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import io.github.twonet.selfish.ui.tokens.Motion
import io.github.twonet.selfish.ui.tokens.Shapes
import io.github.twonet.selfish.ui.tokens.Sizes
import io.github.twonet.selfish.ui.tokens.Spacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A grid whose items are lifted with a long-press and dragged to a new
 * place while the others make room (FR-HOME-06, FR-GRID-05, FR-ANIM-05,
 * FR-ANIM-06), with the lifted item's context menu inserted beneath its row
 * (FR-GRID-06, FR-ANIM-08). Owned code, because the rules are the
 * launcher's own (DESIGN §6.4, §7.3).
 *
 * Each item gets a [LiftDrag] to pass to [pressable]. A long-press calls
 * [onLift], which opens the item's menu; the item stays lifted while
 * [menuFor] is that item. Once the finger moves past the touch slop,
 * [onDragStart] is called, which closes the menu. The order shown while
 * dragging is transient UI state (AR-02): only the drop reaches the store,
 * through [onMove] with the item's new index.
 *
 * Holding a dragged item near the top or bottom scrolls the grid
 * (FR-GRID-07). Where [canDropOnto] allows it, the center of another item
 * is a drop target, highlighted while the dragged item is over it, and
 * dropping there calls [onDropOnto] instead of moving (FR-GRID-08). Around
 * that center, the dragged item takes the other's place only after resting
 * for [Motion.REORDER_DWELL_MILLIS], so passing over it on the way doesn't
 * push it away.
 *
 * [header], if given, is a full-width first row that never moves, such as
 * the App Grid's search field. [key] must be unique and stable.
 */
@Composable
fun <T> ReorderableGrid(
    items: List<T>,
    key: (T) -> String,
    columns: Int,
    onLift: (T) -> Unit,
    onDragStart: () -> Unit,
    onMove: (item: T, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    menuFor: T? = null,
    menu: @Composable (T) -> Unit = {},
    canDropOnto: (dragged: T, target: T) -> Boolean = { _, _ -> false },
    onDropOnto: (dragged: T, target: T) -> Unit = { _, _ -> },
    header: (@Composable () -> Unit)? = null,
    liftShape: Shape = Shapes.Glass,
    contentPadding: PaddingValues = PaddingValues(),
    gridState: LazyGridState = rememberLazyGridState(),
    itemContent: @Composable (item: T, liftDrag: LiftDrag) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val drag = remember(gridState) { GridDrag(gridState, scope) }
    val keys = items.map(key)
    val slop = LocalViewConfiguration.current.touchSlop
    val density = LocalDensity.current
    SideEffect {
        drag.keys = keys
        drag.slop = slop
        drag.edge = with(density) { Sizes.AutoScrollEdge.toPx() }
        drag.speed = with(density) { Sizes.AutoScrollSpeed.toPx() }
        val byKey = { k: String -> items.firstOrNull { key(it) == k } }
        drag.onLift = { k -> byKey(k)?.let(onLift) }
        drag.onDragStart = onDragStart
        drag.onMove = { k, to -> byKey(k)?.let { onMove(it, to) } }
        drag.canDropOnto = { a, b ->
            val dragged = byKey(a)
            val target = byKey(b)
            dragged != null && target != null && canDropOnto(dragged, target)
        }
        drag.onDropOnto = { a, b ->
            val dragged = byKey(a)
            val target = byKey(b)
            if (dragged != null && target != null) onDropOnto(dragged, target)
        }
    }
    // The store has taken the drop, or the items changed: show them as they are.
    LaunchedEffect(keys) { if (drag.dragged == null) drag.order = null }

    val shown = drag.shown(items, key)
    val menuKey = menuFor?.let(key)
    val entries = buildList {
        val menuAt = if (menuFor == null) -1 else shown.indexOfFirst { key(it) == menuKey }
        // After the last item of the menu's row. The header has a row of its
        // own, so it doesn't shift the items' rows.
        val menuAfter = if (menuAt < 0) -1 else minOf((menuAt / columns + 1) * columns, shown.size) - 1
        shown.forEachIndexed { index, item ->
            add(GridEntry.Item(item, key(item)))
            if (index == menuAfter && menuFor != null) add(GridEntry.Menu(menuFor, MENU_KEY_PREFIX + menuKey))
        }
    }
    val elevation = with(density) { Sizes.LiftElevation.toPx() }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier,
        state = gridState,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        if (header != null) {
            item(key = HEADER_KEY, span = { GridItemSpan(maxLineSpan) }, contentType = CONTENT_HEADER) { header() }
        }
        items(
            items = entries,
            key = { it.key },
            span = { if (it is GridEntry.Menu) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
            contentType = { if (it is GridEntry.Menu) CONTENT_MENU else CONTENT_ITEM },
        ) { entry ->
            when (entry) {
                is GridEntry.Menu -> Box(
                    Modifier.animateItem(fadeInSpec = Motion.reflow(), placementSpec = Motion.reflow(), fadeOutSpec = Motion.reflow()),
                ) { menu(entry.item) }

                is GridEntry.Item -> {
                    val itemKey = entry.key
                    val moving = itemKey == drag.dragged || itemKey == drag.settling
                    val lifted = itemKey == drag.dragged || itemKey == menuKey || itemKey == drag.dropTarget
                    val scale by animateFloatAsState(if (lifted) Motion.LIFT_SCALE else 1f, Motion.reflow(), label = "lift")
                    if (itemKey == drag.dragged) {
                        // Stays composed while auto-scrolling carries its slot out of view.
                        val pinnable = LocalPinnableContainer.current
                        DisposableEffect(pinnable) {
                            val handle = pinnable?.pin()
                            onDispose { handle?.release() }
                        }
                    }
                    Box(
                        Modifier
                            .zIndex(if (moving) 1f else 0f)
                            // The dragged item follows the finger, not its slot.
                            .animateItem(
                                fadeInSpec = Motion.reflow(),
                                placementSpec = if (moving) null else Motion.reflow(),
                                fadeOutSpec = Motion.reflow(),
                            )
                            .graphicsLayer {
                                val offset = drag.offsetOf(itemKey)
                                translationX = offset.x
                                translationY = offset.y
                                scaleX = scale
                                scaleY = scale
                                shadowElevation = elevation * ((scale - 1f) / (Motion.LIFT_SCALE - 1f)).coerceIn(0f, 1f)
                                shape = liftShape
                            },
                    ) { itemContent(entry.item, drag.liftDrag(itemKey)) }
                }
            }
        }
    }
}

private sealed interface GridEntry<T> {
    val key: String

    class Item<T>(val item: T, override val key: String) : GridEntry<T>

    class Menu<T>(val item: T, override val key: String) : GridEntry<T>
}

private const val HEADER_KEY = "header"
private const val MENU_KEY_PREFIX = "menu:"
private const val CONTENT_HEADER = "header"
private const val CONTENT_ITEM = "item"
private const val CONTENT_MENU = "menu"
private const val NANOS_PER_SECOND = 1_000_000_000f

/** The drag in progress, if any, and the order it has made so far. */
@Stable
private class GridDrag(private val gridState: LazyGridState, private val scope: CoroutineScope) {
    /** The key of the item being dragged. */
    var dragged by mutableStateOf<String?>(null)
        private set

    /** The key of a dropped item still settling into its slot, or into the item it was dropped onto. */
    var settling by mutableStateOf<String?>(null)
        private set

    /** The key of the item the dragged one would be dropped onto (FR-GRID-08). */
    var dropTarget by mutableStateOf<String?>(null)
        private set

    /** The order while dragging, and after a drop until the store's order arrives. */
    var order by mutableStateOf<List<String>?>(null)

    /** How far the finger has moved since the lift. */
    private var total by mutableStateOf(Offset.Zero)

    /** Where the dragged item's slot was when it was lifted, and its size. */
    private var start = Offset.Zero
    private var size = Size.Zero

    /** Where the dragged item's slot was last seen, for frames where it is out of view. */
    private var lastSlot = Offset.Zero
    private var moved = false
    private val settle = Animatable(Offset.Zero, Offset.VectorConverter)

    /** Where a dropped item is until [settle] has taken over, so it doesn't jump for a frame. */
    private var settleFrom by mutableStateOf<Offset?>(null)
    private val liftDrags = HashMap<String, LiftDrag>()
    private var autoScroll: Job? = null
    private var dwell: Job? = null
    private var dwellTarget: String? = null

    // Updated after every composition.
    var keys: List<String> = emptyList()
    var slop = 0f
    var edge = 0f
    var speed = 0f
    var onLift: (String) -> Unit = {}
    var onDragStart: () -> Unit = {}
    var onMove: (String, Int) -> Unit = { _, _ -> }
    var canDropOnto: (String, String) -> Boolean = { _, _ -> false }
    var onDropOnto: (String, String) -> Unit = { _, _ -> }

    /** [items] in the transient order, if there is one. Items it doesn't know yet go last. */
    fun <T> shown(items: List<T>, key: (T) -> String): List<T> {
        val order = order ?: return items
        val byKey = items.associateBy(key)
        val ordered = order.mapNotNull { byKey[it] }
        if (ordered.size == items.size) return ordered
        val inOrder = order.toHashSet()
        return ordered + items.filter { key(it) !in inOrder }
    }

    fun liftDrag(key: String): LiftDrag = liftDrags.getOrPut(key) {
        LiftDrag(
            onLift = { lift(key) },
            onDrag = { drag(key, it) },
            onDrop = { drop(key, commit = true) },
            onCancel = { drop(key, commit = false) },
        )
    }

    /** How far [key]'s item is drawn from its slot. */
    fun offsetOf(key: String): Offset = when (key) {
        dragged -> start + total - slotOffset(key)
        settling -> settleFrom ?: settle.value
        else -> Offset.Zero
    }

    private fun slot(key: String): LazyGridItemInfo? = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }

    private fun slotOffset(key: String): Offset {
        val offset = slot(key)?.offset?.toOffset() ?: return if (key == dragged) lastSlot else start
        if (key == dragged) lastSlot = offset
        return offset
    }

    private fun lift(key: String) {
        val slot = slot(key)
        if (slot != null && dragged == null) {
            dragged = key
            start = slot.offset.toOffset()
            lastSlot = start
            size = slot.size.toSize()
            total = Offset.Zero
            moved = false
            order = keys
        }
        onLift(key)
    }

    private fun drag(key: String, by: Offset) {
        if (dragged != key) return
        total += by
        if (!moved && total.getDistance() > slop) {
            moved = true
            onDragStart()
        }
        if (moved) {
            if (autoScrollSpeed() == 0f) hover(key) else follow(key)
            startAutoScroll(key)
        }
    }

    /** The dragged item's center, in the same coordinates as the slots. */
    private fun center(): Offset = start + total + Offset(size.width / 2f, size.height / 2f)

    /** The item under the dragged item's center, other than itself. */
    private fun under(key: String): LazyGridItemInfo? {
        val current = order ?: return null
        val center = center()
        return gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
            info.key != key && info.key in current && info.bounds().contains(center)
        }
    }

    /**
     * Reacts to the item under the dragged one: its center is a drop target
     * where dropping is allowed; elsewhere the dragged item takes its place,
     * at once or, where dropping is allowed, after a rest.
     */
    private fun hover(key: String) {
        val target = under(key)?.let { info -> info to info.key as String }
        when {
            target == null -> {
                dropTarget = null
                stopDwell()
            }
            !canDropOnto(key, target.second) -> {
                dropTarget = null
                stopDwell()
                moveTo(key, target.second)
            }
            target.first.dropZone().contains(center()) -> {
                dropTarget = target.second
                stopDwell()
            }
            else -> {
                dropTarget = null
                startDwell(key, target.second)
            }
        }
    }

    /** While auto-scrolling, the dragged item's slot follows the finger at once, so it stays in view. */
    private fun follow(key: String) {
        dropTarget = null
        stopDwell()
        under(key)?.let { moveTo(key, it.key as String) }
    }

    private fun startDwell(key: String, target: String) {
        if (dwellTarget == target && dwell?.isActive == true) return
        dwell?.cancel()
        dwellTarget = target
        dwell = scope.launch {
            delay(Motion.REORDER_DWELL_MILLIS)
            dwellTarget = null
            if (dragged == key) moveTo(key, target)
        }
    }

    private fun stopDwell() {
        dwell?.cancel()
        dwell = null
        dwellTarget = null
    }

    /** Moves [key] to [target]'s place in the transient order. */
    private fun moveTo(key: String, target: String) {
        val current = order ?: return
        val from = current.indexOf(key)
        val to = current.indexOf(target)
        if (from < 0 || to < 0 || from == to) return
        order = current.toMutableList().apply {
            removeAt(from)
            add(to, key)
        }
    }

    /**
     * How fast to scroll for the dragged item's position, in pixels per
     * second: negative near the top, positive near the bottom, faster the
     * deeper into the edge, and 0 elsewhere (FR-GRID-07).
     */
    private fun autoScrollSpeed(): Float {
        if (edge <= 0f) return 0f
        val info = gridState.layoutInfo
        // Slots are measured from the end of the top padding.
        val top = 0f
        val bottom = (info.viewportEndOffset - info.afterContentPadding).toFloat()
        val y = center().y
        return when {
            y < top + edge -> -speed * ((top + edge - y) / edge).coerceAtMost(1f)
            y > bottom - edge -> speed * ((y - (bottom - edge)) / edge).coerceAtMost(1f)
            else -> 0f
        }
    }

    private fun startAutoScroll(key: String) {
        if (autoScroll?.isActive == true || autoScrollSpeed() == 0f) return
        autoScroll = scope.launch {
            var last = withFrameNanos { it }
            while (dragged == key) {
                val now = withFrameNanos { it }
                val speed = autoScrollSpeed()
                if (speed == 0f) break
                gridState.scrollBy(speed * (now - last) / NANOS_PER_SECOND)
                last = now
                follow(key)
            }
            if (dragged == key) hover(key)
        }
    }

    private fun drop(key: String, commit: Boolean) {
        if (dragged != key) return
        autoScroll?.cancel()
        stopDwell()
        val from = keys.indexOf(key)
        val to = order?.indexOf(key) ?: -1
        val wasMoved = moved
        val target = dropTarget
        val visual = offsetOf(key)
        // Into the target, measured from the dropped item's own slot.
        val into = target?.let { slot(it) }?.let { it.offset.toOffset() - slotOffset(key) }
        dragged = null
        dropTarget = null
        moved = false
        total = Offset.Zero
        settleFrom = visual
        settling = key
        if (commit && wasMoved && target != null && into != null) {
            scope.launch {
                settle.snapTo(visual)
                settleFrom = null
                settle.animateTo(into, Motion.reflow())
                // The store took the drop and the item is gone; if it is
                // still here, the drop was refused, so it goes back.
                if (key in keys) settle.animateTo(Offset.Zero, Motion.reflow())
                if (settling == key) settling = null
            }
            // The transient order stays until the store's arrives.
            onDropOnto(key, target)
            return
        }
        scope.launch {
            settle.snapTo(visual)
            settleFrom = null
            settle.animateTo(Offset.Zero, Motion.reflow())
            if (settling == key) settling = null
        }
        // The transient order stays until the store's order arrives, so the
        // drop doesn't flicker back for a frame.
        if (commit && wasMoved && to >= 0 && to != from) onMove(key, to) else order = null
    }
}

private fun LazyGridItemInfo.bounds(): Rect = Rect(offset.toOffset(), size.toSize())

/** The central part of an item, where a drop goes onto it rather than beside it (FR-GRID-08). */
private fun LazyGridItemInfo.dropZone(): Rect {
    val bounds = bounds()
    val inset = (1f - Motion.DROP_ZONE_FRACTION) / 2f
    return Rect(
        left = bounds.left + bounds.width * inset,
        top = bounds.top + bounds.height * inset,
        right = bounds.right - bounds.width * inset,
        bottom = bounds.bottom - bounds.height * inset,
    )
}
