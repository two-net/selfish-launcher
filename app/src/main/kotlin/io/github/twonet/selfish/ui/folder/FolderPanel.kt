package io.github.twonet.selfish.ui.folder

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.util.lerp
import io.github.twonet.selfish.R
import io.github.twonet.selfish.domain.model.FolderId
import io.github.twonet.selfish.domain.state.Action
import io.github.twonet.selfish.domain.state.GridCell
import io.github.twonet.selfish.domain.state.LauncherState
import io.github.twonet.selfish.domain.state.MenuTarget
import io.github.twonet.selfish.domain.state.contextMenu
import io.github.twonet.selfish.ui.components.DragHandoff
import io.github.twonet.selfish.ui.components.EditablePageHeader
import io.github.twonet.selfish.ui.components.Glass
import io.github.twonet.selfish.ui.components.PulleyItem
import io.github.twonet.selfish.ui.components.PulleyMenu
import io.github.twonet.selfish.ui.components.ReorderableGrid
import io.github.twonet.selfish.ui.components.onUnhandledTap
import io.github.twonet.selfish.ui.components.pulleyActions
import io.github.twonet.selfish.ui.grid.AppMenu
import io.github.twonet.selfish.ui.grid.AppTile
import io.github.twonet.selfish.ui.grid.appKey
import io.github.twonet.selfish.ui.grid.labelLines
import io.github.twonet.selfish.ui.tokens.LauncherTheme
import io.github.twonet.selfish.ui.tokens.Motion
import io.github.twonet.selfish.ui.tokens.Spacing

/**
 * The [open] folder's panel over the App Grid (SRS §4.4): its name as the
 * page header and its apps in the grid's columns, on glass, over a dim that
 * closes it when tapped (FR-FOLD-03, UI-01, UI-02). Back and Home close it
 * through the store. It grows out of the folder's icon, whose bounds
 * [tileBounds] gives, and shrinks back into it (FR-ANIM-07).
 *
 * - Tapping the name renames the folder (FR-FOLD-04).
 * - Its pulley menu has "Rename" and "Remove folder" (FR-FOLD-07); screen
 *   readers find them as actions on the name (FR-GEST-09).
 * - Its apps launch, open their menu and move as in the grid. Dragged past
 *   the panel's edge, an app leaves the folder: the panel closes and the
 *   App Grid carries on with the drag through [handoff], from its item
 *   nearest the finger, or else just after the folder ([afterFolder])
 *   (FR-FOLD-05, AC-08).
 *
 * While it closes, and for as long as the touch of an app dragged out of it
 * is down, it keeps showing the folder as it was when last open, so the
 * dragged app keeps its touch. Closed by such a drag, it fades out where it
 * is instead of shrinking: the touch is still measured in its coordinates,
 * which shrinking would stretch.
 */
@Composable
internal fun FolderPanel(
    open: GridCell.Folder?,
    state: LauncherState,
    dispatch: (Action) -> Unit,
    handoff: DragHandoff,
    tileBounds: (FolderId) -> Rect?,
    afterFolder: (FolderId) -> Int,
) {
    val last = remember { LastFolder() }
    if (open != null && !handoff.holding) {
        last.folder = open
        last.draggedOut = false
    } else if (handoff.holding) {
        last.draggedOut = true
    }
    val progress = remember { Animatable(0f) }
    val isOpen = open != null
    LaunchedEffect(isOpen) { progress.animateTo(if (isOpen) 1f else 0f, Motion.folder()) }
    val closed by remember { derivedStateOf { progress.value == 0f } }
    val folder = last.folder
    if (folder == null || (!isOpen && !handoff.holding && closed)) return

    val id = folder.folder.id
    // Where it grows from: the folder's icon when it was opened, if it was seen.
    val tile = remember(id) { tileBounds(id) }
    val panel = remember { PanelBounds() }
    // Its top, once it has opened: it then grows downward, so an app's menu
    // or a pull never moves the app under the finger.
    val anchor = remember(id) { PanelAnchor() }
    val gridState = rememberLazyGridState()
    var editing by remember(id) { mutableStateOf(false) }
    val currentDispatch by rememberUpdatedState(dispatch)
    val close = remember { { currentDispatch(Action.CloseFolder) } }
    val menuApp = (state.contextMenu as? MenuTarget.App)?.app
    val dim = LauncherTheme.colors.dim
    val labelLines = labelLines()

    val items = listOf(
        PulleyItem(stringResource(R.string.menu_rename)) { editing = true },
        PulleyItem(stringResource(R.string.menu_remove_folder)) { dispatch(Action.RemoveFolder(id)) },
    )

    // Once closing, it is gone for screen readers.
    Box(Modifier.fillMaxSize().then(if (isOpen) Modifier else Modifier.clearAndSetSemantics {})) {
        // Edge to edge (UI-07). It also keeps touches from the grid beneath.
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(dim, alpha = progress.value) }
                .onUnhandledTap(close)
                .testTag(FolderTags.DIM),
        )
        Glass(
            modifier = Modifier
                .anchored(anchor)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(Spacing.Gutter)
                .fillMaxWidth()
                // Before the transform, so these are the bounds at rest.
                .onGloballyPositioned { panel.bounds = it.boundsInWindow() }
                .graphicsLayer {
                    val p = progress.value
                    val to = panel.bounds
                    val from = tile ?: Rect(to.center, Size.Zero)
                    if (to.width > 0f && to.height > 0f && !last.draggedOut) {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = lerp(from.width / to.width, 1f, p)
                        scaleY = lerp(from.height / to.height, 1f, p)
                        translationX = lerp(from.left - to.left, 0f, p)
                        translationY = lerp(from.top - to.top, 0f, p)
                    }
                    alpha = p
                }
                .testTag(FolderTags.PANEL),
        ) {
            PulleyMenu(
                items = items,
                atTop = { !gridState.canScrollBackward },
            ) {
                Column(Modifier.fillMaxWidth()) {
                    EditablePageHeader(
                        title = folder.folder.name,
                        editing = editing,
                        onEditingChange = { editing = it },
                        onRename = { dispatch(Action.RenameFolder(id, it)) },
                        editLabel = stringResource(R.string.menu_rename),
                        actions = pulleyActions(items),
                    )
                    ReorderableGrid(
                        items = folder.apps,
                        key = { appKey(it.id) },
                        columns = state.settings.gridColumns,
                        onLift = { dispatch(Action.OpenContextMenu(MenuTarget.App(it.id))) },
                        onDragStart = { dispatch(Action.CloseContextMenu) },
                        onMove = { app, to -> dispatch(Action.MoveInFolder(app.id, to)) },
                        modifier = Modifier.testTag(FolderTags.GRID),
                        menuFor = folder.apps.firstOrNull { it.id == menuApp },
                        menu = { AppMenu(it, state, dispatch) },
                        contentPadding = PaddingValues(start = Spacing.Gutter, end = Spacing.Gutter, bottom = Spacing.Gutter),
                        gridState = gridState,
                        handoff = handoff,
                        onHandOff = { app, at ->
                            dispatch(Action.TakeOutOfFolder(app.id, handoff.indexNear(at) ?: afterFolder(id)))
                        },
                        handOffOutside = { panel.bounds },
                    ) { app, liftDrag -> AppTile(app, labelLines, liftDrag, dispatch) }
                }
            }
        }
    }
}

/** Test tags for [FolderPanel]'s instrumented tests. */
internal object FolderTags {
    const val PANEL = "folder-panel"
    const val GRID = "folder-grid"
    const val DIM = "folder-dim"
}

/**
 * The folder last shown open, kept while the panel closes, and whether it
 * was closed by an app dragged out of it. Not state: it never needs to
 * redraw anything by itself.
 */
private class LastFolder {
    var folder: GridCell.Folder? = null
    var draggedOut = false
}

/** The panel's bounds in the window at rest, from its last layout. */
private class PanelBounds {
    var bounds: Rect = Rect.Zero
}

/** Where the panel's top was placed when it opened, in pixels from the top of its space. */
private class PanelAnchor {
    var top: Int? = null
}

/**
 * Centers the panel vertically when it first opens, and then keeps its top
 * there, so it grows downward as an app's menu opens in it or it is pulled
 * (FR-GRID-06, FR-GEST-05); it moves up only as far as it must to stay on
 * screen.
 */
private fun Modifier.anchored(anchor: PanelAnchor): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0))
    val space = constraints.maxHeight
    val top = anchor.top ?: ((space - placeable.height) / 2).coerceAtLeast(0).also { anchor.top = it }
    layout(constraints.maxWidth, space) {
        placeable.place(0, minOf(top, space - placeable.height).coerceAtLeast(0))
    }
}
