package io.github.twonet.selfish.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import io.github.twonet.selfish.R
import io.github.twonet.selfish.domain.state.Action
import io.github.twonet.selfish.domain.state.Layer
import io.github.twonet.selfish.domain.state.LauncherState
import io.github.twonet.selfish.ui.components.onUnhandledTap
import io.github.twonet.selfish.ui.grid.AppGrid
import io.github.twonet.selfish.ui.home.HomeScreen
import io.github.twonet.selfish.ui.motion.LayerGestureController
import io.github.twonet.selfish.ui.motion.WindowBlur
import io.github.twonet.selfish.ui.motion.layerGestures
import io.github.twonet.selfish.ui.tokens.LauncherTheme
import kotlinx.coroutines.flow.filter

/**
 * Every launcher surface, rendered from [state]; every touch goes back as an
 * action (AR-02). Drawn back to front over the system wallpaper (DESIGN
 * §6.2): the dim and blur, Home, then the App Grid.
 *
 * Swiping up on Home slides the App Grid up over a wallpaper that dims (and,
 * where supported, blurs) in proportion, while Home moves up out of the
 * way, as one sheet (FR-GRID-01, FR-ANIM-01). Dragging down with the grid at
 * its top brings Home back (FR-GRID-04). Swipes that start in
 * [gestureInsets], the system's gesture areas, are left to the system
 * (FR-GEST-04); tests pass their own.
 */
@Composable
fun LauncherScreen(
    state: LauncherState,
    dispatch: (Action) -> Unit,
    gestureInsets: WindowInsets = WindowInsets.systemGestures,
) {
    // Until the saved layout is read, only the wallpaper shows (FR-GEN-08).
    if (!state.restored) return

    val scope = rememberCoroutineScope()
    val controller = remember(scope) { LayerGestureController(scope) }
    val currentDispatch by rememberUpdatedState(dispatch)
    val minFling = LocalViewConfiguration.current.minimumFlingVelocity
    SideEffect {
        controller.onSettled = { currentDispatch(Action.LayerSettled(it)) }
        controller.minFlingVelocity = minFling
    }

    // The store changed the layer, for example on a Home press. A press that
    // brings the launcher back from another app arrives before its window
    // has focus, and then Home is simply there, with no closing grid.
    val focused by rememberUpdatedState(LocalWindowInfo.current.isWindowFocused)
    LaunchedEffect(controller, state.layer) { controller.follow(state.layer, animate = focused) }

    val covers = rememberLazyGridState()
    val grid = rememberLazyGridState()
    // Every opening starts at the top (FR-GRID-03), so the grid goes back
    // there once it is out of sight.
    LaunchedEffect(controller, grid) {
        snapshotFlow { controller.value == 0f }.filter { it }.collect { grid.scrollToItem(0) }
    }
    val closed by remember(controller) { derivedStateOf { controller.value == 0f } }
    val open by remember(controller) { derivedStateOf { controller.value == 1f } }
    // The grid is composed once Home has drawn its first frame, and kept: the
    // launcher shows Home as soon as it can (NFR-PERF-01), and composing the
    // grid never stalls a swipe that is already under way (NFR-PERF-02).
    var gridNeeded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        gridNeeded = true
    }

    val closeMenu = remember { { currentDispatch(Action.CloseContextMenu) } }
    val showApps = stringResource(R.string.action_show_apps)
    val dim = LauncherTheme.colors.dim

    WindowBlur { controller.value }
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { controller.travel = it.height.toFloat() }
            // Edge to edge, behind both surfaces (UI-07).
            .drawBehind { drawRect(dim, alpha = controller.value) }
            .layerGestures(
                controller = controller,
                ignored = gestureInsets,
                homeScrolls = { covers.canScrollForward },
                gridScrolls = { grid.canScrollBackward },
            )
            .onUnhandledTap(closeMenu)
            .testTag(LauncherTags.ROOT),
    ) {
        HomeScreen(
            state = state,
            dispatch = dispatch,
            coversState = covers,
            modifier = Modifier
                .graphicsLayer { translationY = -controller.value * size.height }
                .then(
                    if (open) {
                        Modifier.clearAndSetSemantics {}
                    } else {
                        // Screen-reader users open the grid without swiping (FR-GEST-09).
                        Modifier.semantics {
                            customActions = listOf(
                                CustomAccessibilityAction(showApps) {
                                    currentDispatch(Action.LayerSettled(Layer.AppGrid))
                                    true
                                },
                            )
                        }
                    },
                ),
        )
        if (gridNeeded || !closed) {
            AppGrid(
                state = state,
                dispatch = dispatch,
                gridState = grid,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = (1f - controller.value) * size.height }
                    .then(if (closed) Modifier.clearAndSetSemantics {} else Modifier),
            )
        }
    }
}

/** Test tags for [LauncherScreen]'s instrumented tests. */
internal object LauncherTags {
    const val ROOT = "launcher"
}
