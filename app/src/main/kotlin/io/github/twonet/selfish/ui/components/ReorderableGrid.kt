package io.github.twonet.selfish.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.launch

/**
 * A grid whose items are lifted with a long-press and dragged to a new
 * place while the others make room (FR-HOME-06, FR-ANIM-05, FR-ANIM-06),
 * with the lifted item's context menu inserted beneath its row
 * (FR-ANIM-08). Owned code, because the rules are the launcher's own
 * (DESIGN §6.4, §7.3).
 *
 * Each item gets a [LiftDrag] to pass to [pressable]. A long-press calls
 * [onLift], which opens the item's menu; the item stays lifted while
 * [menuFor] is that item. Once the finger moves past the touch slop,
 * [onDragStart] is called, which closes the menu. The order shown while
 * dragging is transient UI state (AR-02): only the drop reaches the store,
 * through [onMove] with the item's new index.
 *
 * [key] must be unique and stable. The App Grid (#7) adds scrolling at the
 * edges while dragging and dropping onto an app to make a folder.
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
    liftShape: Shape = Shapes.Glass,
    contentPadding: PaddingValues = PaddingValues(),
    gridState: LazyGridState = rememberLazyGridState(),
    itemContent: @Composable (item: T, liftDrag: LiftDrag) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val drag = remember(gridState) { GridDrag(gridState, scope) }
    val keys = items.map(key)
    val slop = LocalViewConfiguration.current.touchSlop
    SideEffect {
        drag.keys = keys
        drag.slop = slop
        drag.onLift = { k -> items.firstOrNull { key(it) == k }?.let(onLift) }
        drag.onDragStart = onDragStart
        drag.onMove = { k, to -> items.firstOrNull { key(it) == k }?.let { onMove(it, to) } }
    }
    // The store has taken the drop, or the items changed: show them as they are.
    LaunchedEffect(keys) { if (drag.dragged == null) drag.order = null }

    val shown = drag.shown(items, key)
    val menuKey = menuFor?.let(key)
    val entries = buildList {
        val menuAt = if (menuFor == null) -1 else shown.indexOfFirst { key(it) == menuKey }
        // After the last item of the menu's row.
        val menuAfter = if (menuAt < 0) -1 else minOf((menuAt / columns + 1) * columns, shown.size) - 1
        shown.forEachIndexed { index, item ->
            add(GridEntry.Item(item, key(item)))
            if (index == menuAfter && menuFor != null) add(GridEntry.Menu(menuFor, MENU_KEY_PREFIX + menuKey))
        }
    }
    val elevation = with(LocalDensity.current) { Sizes.LiftElevation.toPx() }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier,
        state = gridState,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
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
                    val lifted = itemKey == drag.dragged || itemKey == menuKey
                    val scale by animateFloatAsState(if (lifted) Motion.LIFT_SCALE else 1f, Motion.reflow(), label = "lift")
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

private const val MENU_KEY_PREFIX = "menu:"
private const val CONTENT_ITEM = "item"
private const val CONTENT_MENU = "menu"

/** The drag in progress, if any, and the order it has made so far. */
@Stable
private class GridDrag(private val gridState: LazyGridState, private val scope: CoroutineScope) {
    /** The key of the item being dragged. */
    var dragged by mutableStateOf<String?>(null)
        private set

    /** The key of a dropped item still settling into its slot. */
    var settling by mutableStateOf<String?>(null)
        private set

    /** The order while dragging, and after a drop until the store's order arrives. */
    var order by mutableStateOf<List<String>?>(null)

    /** How far the finger has moved since the lift. */
    private var total by mutableStateOf(Offset.Zero)

    /** Where the dragged item's slot was when it was lifted. */
    private var start = Offset.Zero
    private var moved = false
    private val settle = Animatable(Offset.Zero, Offset.VectorConverter)

    /** Where a dropped item is until [settle] has taken over, so it doesn't jump for a frame. */
    private var settleFrom by mutableStateOf<Offset?>(null)
    private val liftDrags = HashMap<String, LiftDrag>()

    // Updated after every composition.
    var keys: List<String> = emptyList()
    var slop = 0f
    var onLift: (String) -> Unit = {}
    var onDragStart: () -> Unit = {}
    var onMove: (String, Int) -> Unit = { _, _ -> }

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
        dragged -> start + total - (slot(key)?.offset?.toOffset() ?: start)
        settling -> settleFrom ?: settle.value
        else -> Offset.Zero
    }

    private fun slot(key: String): LazyGridItemInfo? = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }

    private fun lift(key: String) {
        val slot = slot(key)
        if (slot != null && dragged == null) {
            dragged = key
            start = slot.offset.toOffset()
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
        if (moved) reorder(key)
    }

    /** Moves the dragged item to the slot of the item under its center, if any. */
    private fun reorder(key: String) {
        val current = order ?: return
        val self = slot(key) ?: return
        val center = start + total + Offset(self.size.width / 2f, self.size.height / 2f)
        val target = gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
            info.key != key && info.key in current && Rect(info.offset.toOffset(), info.size.toSize()).contains(center)
        } ?: return
        val from = current.indexOf(key)
        val to = current.indexOf(target.key)
        if (from < 0 || to < 0 || from == to) return
        order = current.toMutableList().apply {
            removeAt(from)
            add(to, key)
        }
    }

    private fun drop(key: String, commit: Boolean) {
        if (dragged != key) return
        val from = keys.indexOf(key)
        val to = order?.indexOf(key) ?: -1
        val wasMoved = moved
        val visual = offsetOf(key)
        dragged = null
        moved = false
        total = Offset.Zero
        settleFrom = visual
        settling = key
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
