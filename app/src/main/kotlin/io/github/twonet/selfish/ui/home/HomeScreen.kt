package io.github.twonet.selfish.ui.home

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import io.github.twonet.selfish.R
import io.github.twonet.selfish.domain.layout.LayoutRules
import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.Bounds
import io.github.twonet.selfish.domain.state.Action
import io.github.twonet.selfish.domain.state.LauncherState
import io.github.twonet.selfish.domain.state.Layer
import io.github.twonet.selfish.domain.state.MenuTarget
import io.github.twonet.selfish.domain.state.contextMenu
import io.github.twonet.selfish.domain.state.covers
import io.github.twonet.selfish.ui.components.ContextMenu
import io.github.twonet.selfish.ui.components.ContextMenuItem
import io.github.twonet.selfish.ui.components.Cover
import io.github.twonet.selfish.ui.components.GestureHint
import io.github.twonet.selfish.ui.components.Glass
import io.github.twonet.selfish.ui.components.LauncherText
import io.github.twonet.selfish.ui.components.ReorderableGrid
import io.github.twonet.selfish.ui.components.onBoundsInWindow
import io.github.twonet.selfish.ui.components.onUnhandledLongPress
import io.github.twonet.selfish.ui.components.pressable
import io.github.twonet.selfish.ui.tokens.LauncherTheme
import io.github.twonet.selfish.ui.tokens.Sizes
import io.github.twonet.selfish.ui.tokens.Spacing

/**
 * Home (SRS §4.2): the wallpaper edge to edge, the time and date, and up to
 * nine covers of favorite apps, rendered from [state]; every touch goes back
 * as an action (AR-02). Swiping up from here opens the App Grid and swiping
 * down the Top Menu, which [io.github.twonet.selfish.ui.LauncherScreen]
 * handles; it reads [coversState] to let covers that don't fit scroll
 * first. Long-pressing anywhere but a cover opens the Top Menu too
 * (FR-TOP-06), with the long-press tick (FR-GEST-07).
 *
 * Content is inset by the system bars, cutouts and rounded corners; only the
 * scrim runs edge to edge (UI-07).
 */
@Composable
fun HomeScreen(
    state: LauncherState,
    dispatch: (Action) -> Unit,
    modifier: Modifier = Modifier,
    coversState: LazyGridState = rememberLazyGridState(),
) {
    val menu = state.contextMenu
    // Through LocalHapticFeedback, so it follows the Haptic feedback setting (FR-GEST-08).
    val haptics = LocalHapticFeedback.current
    val currentDispatch by rememberUpdatedState(dispatch)
    val openTopMenu = remember(haptics) {
        {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            currentDispatch(Action.LayerSettled(Layer.TopMenu))
        }
    }
    Column(modifier.fillMaxSize().onUnhandledLongPress(openTopMenu).testTag(HomeTags.SCREEN)) {
        // A scrim behind the status bar and the clock keeps both legible
        // over any wallpaper (FR-HOME-01): light in Light, dark in Dark
        // (FR-THM-05). It is the appearance's worst case; weakening it over
        // wallpapers that don't need it is unscheduled (DESIGN §6.5).
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
                // reported, so the hint never flashes while it loads. The
                // tutorial says the same, with its own hint (FR-ONB-02).
                layout.home.isEmpty() -> if (state.tutorial == null) EmptyHint(Modifier.align(Alignment.Center))
                else -> Covers(state.covers, menu, dispatch, coversState)
            }
        }
    }
}

/** Test tags for [HomeScreen]'s instrumented tests. */
internal object HomeTags {
    const val SCREEN = "home"
    const val COVERS = "home-covers"
    const val HINT = "home-gesture-hint"
}

@Composable
private fun Covers(covers: List<AppEntry>, menu: MenuTarget?, dispatch: (Action) -> Unit, gridState: LazyGridState) {
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
        gridState = gridState,
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

/**
 * With no covers, Home points the way to the apps (FR-HOME-08), with a
 * glowing touch point tracing the swipe up above the words (FR-ANIM-13).
 */
@Composable
private fun EmptyHint(modifier: Modifier) {
    Column(modifier.padding(Spacing.Gutter), horizontalAlignment = Alignment.CenterHorizontally) {
        GestureHint(
            from = SWIPE_UP_FROM,
            to = SWIPE_UP_TO,
            modifier = Modifier
                .size(width = Sizes.HintTouchPoint, height = Sizes.HintTravel)
                .testTag(HomeTags.HINT),
        )
        Glass {
            LauncherText(
                text = stringResource(R.string.home_hint_no_covers),
                modifier = Modifier.padding(horizontal = Spacing.Gutter, vertical = Spacing.Medium),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** A swipe up, as fractions of the hint's width and height. */
private val SWIPE_UP_FROM = Offset(0.5f, 1f)
private val SWIPE_UP_TO = Offset(0.5f, 0f)
