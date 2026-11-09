package io.github.twonet.selfish.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import io.github.twonet.selfish.R
import io.github.twonet.selfish.domain.layout.LayoutRules
import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.Bounds
import io.github.twonet.selfish.domain.state.Action
import io.github.twonet.selfish.domain.state.LauncherState
import io.github.twonet.selfish.domain.state.MenuTarget
import io.github.twonet.selfish.domain.state.Overlay
import io.github.twonet.selfish.domain.state.covers
import io.github.twonet.selfish.ui.components.ContextMenu
import io.github.twonet.selfish.ui.components.ContextMenuItem
import io.github.twonet.selfish.ui.components.Cover
import io.github.twonet.selfish.ui.components.Glass
import io.github.twonet.selfish.ui.components.LauncherText
import io.github.twonet.selfish.ui.components.ReorderableGrid
import io.github.twonet.selfish.ui.components.onBoundsInWindow
import io.github.twonet.selfish.ui.components.onUnhandledTap
import io.github.twonet.selfish.ui.components.pressable
import io.github.twonet.selfish.ui.grid.appListStandIn
import io.github.twonet.selfish.ui.tokens.LauncherTheme
import io.github.twonet.selfish.ui.tokens.Spacing
import kotlinx.coroutines.launch

/**
 * Home (SRS §4.2): the wallpaper edge to edge, the time and date, and up to
 * nine covers of favorite apps, rendered from [state]; every touch goes back
 * as an action (AR-02).
 *
 * Until the App Grid arrives (#7), the list of every app sits below Home,
 * so swiping up scrolls to it; Home fills the first screen. Back on the
 * list, or hiding the launcher (for example by launching an app), returns
 * to Home.
 *
 * Content is inset by the system bars, cutouts and rounded corners; only the
 * scrim and the list's dim run edge to edge (UI-07).
 */
@Composable
fun HomeScreen(state: LauncherState, dispatch: (Action) -> Unit) {
    // Until the saved layout is read, only the wallpaper shows (FR-GEN-08).
    if (!state.restored) return

    val list = rememberLazyListState()
    LaunchedEffect(state.homeShown) { if (!state.homeShown) list.scrollToItem(0) }
    // The list stands in for the App Grid layer, so Back closes it the same
    // way, after any context menu (FR-GEN-04).
    val scope = rememberCoroutineScope()
    val onList by remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0 } }
    BackHandler(enabled = onList && state.overlays.isEmpty()) { scope.launch { list.animateScrollToItem(0) } }
    val menu = (state.overlays.lastOrNull { it is Overlay.ContextMenu } as Overlay.ContextMenu?)?.target
    val closeMenu = remember(dispatch) { { dispatch(Action.CloseContextMenu) } }

    LazyColumn(
        state = list,
        modifier = Modifier
            .fillMaxSize()
            .testTag(HomeTags.SCREEN)
            .onUnhandledTap(closeMenu),
    ) {
        item(key = HOME_PAGE_KEY) {
            HomePage(state, menu, dispatch, Modifier.fillParentMaxHeight())
        }
        appListStandIn(state, menu, dispatch)
    }
}

/** Test tags for [HomeScreen]'s instrumented tests. */
internal object HomeTags {
    const val SCREEN = "home"
    const val COVERS = "home-covers"
}

private const val HOME_PAGE_KEY = "home"

@Composable
private fun HomePage(state: LauncherState, menu: MenuTarget?, dispatch: (Action) -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth()) {
        // A scrim behind the status bar and the clock keeps both legible
        // over any wallpaper (FR-HOME-01). It is the worst case for now;
        // #16 weakens it over wallpapers that don't need it.
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(LauncherTheme.colors.scrim, Color.Transparent)))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = Spacing.Gutter)
                .padding(top = Spacing.Large, bottom = Spacing.Huge),
        ) {
            val clock = state.clock
            if (state.settings.showClock && clock != null) HomeClock(clock)
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
        ) {
            val layout = state.layout
            when {
                // A first run, still looking up the default apps.
                layout == null -> Unit
                // Keyed on the layout, not on the covers the catalog has
                // reported, so the hint never flashes while it loads.
                layout.home.isEmpty() -> EmptyHint(Modifier.align(Alignment.Center))
                else -> Covers(state.covers, menu, dispatch)
            }
        }
    }
}

@Composable
private fun Covers(covers: List<AppEntry>, menu: MenuTarget?, dispatch: (Action) -> Unit) {
    val menuApp = (menu as? MenuTarget.Cover)?.app
    ReorderableGrid(
        items = covers,
        key = { it.id.toString() },
        columns = LayoutRules.coverColumns(covers.size),
        onLift = { dispatch(Action.OpenContextMenu(MenuTarget.Cover(it.id))) },
        onDragStart = { dispatch(Action.CloseContextMenu) },
        onMove = { app, to -> dispatch(Action.MoveCover(app.id, to)) },
        modifier = Modifier.fillMaxSize().testTag(HomeTags.COVERS),
        menuFor = covers.firstOrNull { it.id == menuApp },
        menu = { app ->
            ContextMenu(
                listOf(ContextMenuItem(stringResource(R.string.menu_remove_from_home)) { dispatch(Action.RemoveFromHome(app.id)) }),
            )
        },
        contentPadding = PaddingValues(Spacing.Gutter),
    ) { app, liftDrag ->
        var bounds by remember { mutableStateOf<Bounds?>(null) }
        Cover(
            app = app,
            modifier = Modifier
                .onBoundsInWindow { bounds = it }
                .pressable(
                    onLongClickLabel = stringResource(R.string.action_open_menu),
                    liftDrag = liftDrag,
                    onClick = { dispatch(Action.Launch(app.id, bounds)) },
                ),
        )
    }
}

/** With no covers, Home points the way to the apps (FR-HOME-08). */
@Composable
private fun EmptyHint(modifier: Modifier) {
    Glass(modifier = modifier.padding(Spacing.Gutter)) {
        LauncherText(
            text = stringResource(R.string.home_hint_no_covers),
            modifier = Modifier.padding(horizontal = Spacing.Gutter, vertical = Spacing.Medium),
            textAlign = TextAlign.Center,
        )
    }
}
