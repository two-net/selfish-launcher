package io.github.twonet.selfish.ui.grid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.github.twonet.selfish.R
import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.Bounds
import io.github.twonet.selfish.domain.state.Action
import io.github.twonet.selfish.domain.state.GridCell
import io.github.twonet.selfish.domain.state.LauncherState
import io.github.twonet.selfish.domain.state.MenuTarget
import io.github.twonet.selfish.domain.state.contextMenu
import io.github.twonet.selfish.domain.state.gridCells
import io.github.twonet.selfish.domain.state.homeFull
import io.github.twonet.selfish.ui.components.AppIcon
import io.github.twonet.selfish.ui.components.ContextMenu
import io.github.twonet.selfish.ui.components.ContextMenuItem
import io.github.twonet.selfish.ui.components.FolderIcon
import io.github.twonet.selfish.ui.components.Glass
import io.github.twonet.selfish.ui.components.LauncherText
import io.github.twonet.selfish.ui.components.LiftDrag
import io.github.twonet.selfish.ui.components.ReorderableGrid
import io.github.twonet.selfish.ui.components.onBoundsInWindow
import io.github.twonet.selfish.ui.components.pressable
import io.github.twonet.selfish.ui.tokens.Emphasis
import io.github.twonet.selfish.ui.tokens.LARGE_FONT_SCALE
import io.github.twonet.selfish.ui.tokens.Sizes
import io.github.twonet.selfish.ui.tokens.Spacing
import io.github.twonet.selfish.ui.tokens.TextRole

/**
 * The App Grid (SRS §4.3): every app and folder as icons with labels, in
 * the user's order and the chosen number of columns (FR-GRID-02,
 * FR-GRID-05), below a search field (FR-GRID-03). It floats over the dim
 * that [io.github.twonet.selfish.ui.LauncherScreen] draws, so it has no
 * background of its own (UI-01).
 *
 * Long-pressing an app lifts it and opens its menu beneath its row; dragging
 * moves it, and dropping it onto another app makes a folder (FR-GRID-06 to
 * FR-GRID-08). A folder is lifted and moved the same way, without a menu.
 * Opening a folder arrives with #8.
 *
 * [gridState] is hoisted so the layer's gesture can tell whether the grid is
 * at the top (FR-GRID-04) and scroll it back there before it opens
 * (FR-GRID-03).
 */
@Composable
fun AppGrid(state: LauncherState, dispatch: (Action) -> Unit, gridState: LazyGridState, modifier: Modifier = Modifier) {
    val cells = state.gridCells
    val menuApp = (state.contextMenu as? MenuTarget.App)?.app
    val padding = WindowInsets.safeDrawing
        .add(WindowInsets(left = Spacing.Gutter, top = Spacing.Gutter, right = Spacing.Gutter, bottom = Spacing.Gutter))
        .asPaddingValues()
    val labelLines = if (LocalDensity.current.fontScale >= LARGE_FONT_SCALE) LARGE_LABEL_LINES else 1

    ReorderableGrid(
        items = cells,
        key = GridCell::key,
        columns = state.settings.gridColumns,
        onLift = { cell ->
            when (cell) {
                is GridCell.App -> dispatch(Action.OpenContextMenu(MenuTarget.App(cell.entry.id)))
                is GridCell.Folder -> dispatch(Action.CloseContextMenu)
            }
        },
        onDragStart = { dispatch(Action.CloseContextMenu) },
        onMove = { cell, to -> dispatch(Action.MoveGridItem(cell.item, to)) },
        modifier = modifier.testTag(AppGridTags.GRID),
        menuFor = cells.firstOrNull { it is GridCell.App && it.entry.id == menuApp },
        menu = { cell -> if (cell is GridCell.App) AppMenu(cell.entry, state, dispatch) },
        // Folders don't nest, and dropping into a folder arrives with #8.
        canDropOnto = { dragged, target -> dragged is GridCell.App && target is GridCell.App },
        onDropOnto = { dragged, target ->
            if (dragged is GridCell.App && target is GridCell.App) dispatch(Action.DropOnto(dragged.entry.id, target.entry.id))
        },
        header = { SearchField() },
        contentPadding = padding,
        gridState = gridState,
    ) { cell, liftDrag ->
        when (cell) {
            is GridCell.App -> AppTile(cell.entry, labelLines, liftDrag, dispatch)
            is GridCell.Folder -> FolderTile(cell, labelLines, liftDrag)
        }
    }
}

/** Test tags for the App Grid's instrumented tests. */
internal object AppGridTags {
    const val GRID = "app-grid"
    const val SEARCH = "app-grid-search"
}

/** Labels take one line, or this many at large font scales (FR-GRID-02, NFR-A11Y-04). */
private const val LARGE_LABEL_LINES = 2

private val GridCell.key: String
    get() = when (this) {
        is GridCell.App -> "app:${entry.id}"
        is GridCell.Folder -> "folder:${folder.id.value}"
    }

@Composable
private fun AppTile(app: AppEntry, labelLines: Int, liftDrag: LiftDrag, dispatch: (Action) -> Unit) {
    var bounds by remember { mutableStateOf<Bounds?>(null) }
    Tile(
        label = app.label,
        labelLines = labelLines,
        modifier = Modifier.pressable(
            onLongClickLabel = stringResource(R.string.action_open_menu),
            liftDrag = liftDrag,
            // The app's window grows from its icon (FR-ANIM-02).
            onClick = { dispatch(Action.Launch(app.id, bounds)) },
        ),
    ) { AppIcon(app, Modifier.onBoundsInWindow { bounds = it }) }
}

/**
 * A folder's tile: its preview and name, read by screen readers as
 * "Folder, ⟨name⟩, ⟨n⟩ apps" (NFR-A11Y-01). Tapping it opens it once #8
 * builds the panel.
 */
@Composable
private fun FolderTile(cell: GridCell.Folder, labelLines: Int, liftDrag: LiftDrag) {
    val description = pluralStringResource(R.plurals.folder_description, cell.apps.size, cell.folder.name, cell.apps.size)
    Tile(
        label = cell.folder.name,
        labelLines = labelLines,
        modifier = Modifier
            .pressable(onLongClickLabel = stringResource(R.string.action_open_menu), liftDrag = liftDrag, onClick = {})
            .semantics { contentDescription = description },
        labelModifier = Modifier.clearAndSetSemantics {},
    ) { FolderIcon(cell.apps) }
}

/** An icon above its label, centered, as every App Grid item is laid out (FR-GRID-02). */
@Composable
private fun Tile(
    label: String,
    labelLines: Int,
    modifier: Modifier,
    labelModifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.Small),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.Tiny),
    ) {
        icon()
        LauncherText(text = label, modifier = labelModifier, role = TextRole.Label, textAlign = TextAlign.Center, maxLines = labelLines)
    }
}

/**
 * The app's context menu beneath its row (FR-GRID-06): "Add to Home", which
 * explains when Home is full, or "Remove from Home" (FR-HOME-05); "App info";
 * and "Uninstall", except for system apps, which can't be.
 */
@Composable
private fun AppMenu(app: AppEntry, state: LauncherState, dispatch: (Action) -> Unit) {
    val onHome = state.layout?.home?.contains(app.id) == true
    val home = when {
        onHome -> ContextMenuItem(stringResource(R.string.menu_remove_from_home)) { dispatch(Action.RemoveFromHome(app.id)) }
        state.homeFull -> ContextMenuItem(stringResource(R.string.menu_add_to_home), stringResource(R.string.menu_home_full), onClick = null)
        else -> ContextMenuItem(stringResource(R.string.menu_add_to_home)) { dispatch(Action.AddToHome(app.id)) }
    }
    val info = ContextMenuItem(stringResource(R.string.menu_app_info)) { dispatch(Action.OpenAppInfo(app.id)) }
    val uninstall = if (app.removable) ContextMenuItem(stringResource(R.string.menu_uninstall)) { dispatch(Action.Uninstall(app.id)) } else null
    ContextMenu(listOfNotNull(home, info, uninstall))
}

/**
 * Where the search field goes, above the first row (FR-GRID-03). Search
 * itself arrives with #9, which replaces this; until then it is shown, and
 * read by screen readers, as disabled.
 */
@Composable
private fun SearchField() {
    Glass(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = Sizes.MinTouchTarget)
            .semantics(mergeDescendants = true) { disabled() }
            .testTag(AppGridTags.SEARCH),
        contentAlignment = Alignment.CenterStart,
    ) {
        LauncherText(
            text = stringResource(R.string.grid_search_placeholder),
            modifier = Modifier.padding(horizontal = Spacing.Gutter),
            emphasis = Emphasis.Secondary,
            maxLines = 1,
        )
    }
}
