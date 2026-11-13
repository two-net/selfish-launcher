package io.github.twonet.selfish.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import io.github.twonet.selfish.R
import io.github.twonet.selfish.domain.state.Action
import io.github.twonet.selfish.domain.state.Layer
import io.github.twonet.selfish.domain.remorse.Destructive
import io.github.twonet.selfish.domain.remorse.REMORSE_DELAY
import io.github.twonet.selfish.domain.state.LauncherState
import io.github.twonet.selfish.domain.state.openFolder
import io.github.twonet.selfish.ui.components.LauncherHaptics
import io.github.twonet.selfish.ui.components.RemorseBanner
import io.github.twonet.selfish.ui.components.onUnhandledTap
import io.github.twonet.selfish.ui.events.EventsScreen
import io.github.twonet.selfish.ui.grid.AppGrid
import io.github.twonet.selfish.ui.home.HomeScreen
import io.github.twonet.selfish.ui.motion.GestureMath
import io.github.twonet.selfish.ui.motion.LayerGestureController
import io.github.twonet.selfish.ui.motion.Scrolls
import io.github.twonet.selfish.ui.motion.WindowBlur
import io.github.twonet.selfish.ui.motion.layerGestures
import io.github.twonet.selfish.ui.tokens.LauncherTheme
import io.github.twonet.selfish.ui.tokens.Spacing
import io.github.twonet.selfish.ui.topmenu.TopMenu
import kotlinx.coroutines.flow.filter

/**
 * Every launcher surface, rendered from [state]; every touch goes back as an
 * action (AR-02). Drawn back to front over the system wallpaper (DESIGN
 * §6.2): the dim and blur, Home, Events, the App Grid with its folders, the
 * Top Menu, then the remorse banner, over everything (FR-GEST-06).
 *
 * Swiping up on Home slides the App Grid up over a wallpaper that dims (and,
 * where supported, blurs) in proportion, while Home moves up out of the
 * way, as one sheet (FR-GRID-01, FR-ANIM-01). Dragging down with the grid at
 * its top brings Home back (FR-GRID-04). Swiping sideways on Home pushes it
 * out and Events in from that side, over the same dim, and swiping sideways
 * on Events, either way, brings Home back (FR-EVT-01). Swiping down on Home
 * pulls the Top Menu down over the same dim, and swiping up on it, or
 * tapping beneath it, puts it back (FR-TOP-01, FR-TOP-05). Swipes that start
 * in [gestureInsets], the system's gesture areas and the status bar, are
 * left to the system, so a pull from the top edge still opens the
 * notification shade (FR-GEST-04); tests pass their own. While a folder is
 * open, swipes are the folder's (FR-FOLD-07).
 */
@Composable
fun LauncherScreen(
    state: LauncherState,
    dispatch: (Action) -> Unit,
    gestureInsets: WindowInsets = WindowInsets.systemGestures.union(WindowInsets.statusBars),
) {
    // Until the saved layout is read, only the wallpaper shows (FR-GEN-08).
    if (!state.restored) return

    val scope = rememberCoroutineScope()
    val controller = remember(scope) { LayerGestureController(scope) }
    val currentDispatch by rememberUpdatedState(dispatch)
    val minFling = LocalViewConfiguration.current.minimumFlingVelocity
    val endSide = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1f else -1f
    SideEffect {
        controller.onSettled = { currentDispatch(Action.LayerSettled(it)) }
        controller.minFlingVelocity = minFling
        controller.endSide = endSide
    }

    // The store changed the layer, for example on a Home press. A press that
    // brings the launcher back from another app arrives before its window
    // has focus, and then Home is simply there, with no closing grid.
    val focused by rememberUpdatedState(LocalWindowInfo.current.isWindowFocused)
    LaunchedEffect(controller, state.layer) { controller.follow(state.layer, animate = focused) }

    val covers = rememberLazyGridState()
    val grid = rememberLazyGridState()
    val events = rememberLazyListState()
    val topMenu = rememberScrollState()
    // Every opening starts at the top (FR-GRID-03), so the grid, Events and
    // the Top Menu go back there once they are out of sight.
    LaunchedEffect(controller, grid) {
        snapshotFlow { controller.gridProgress == 0f }.filter { it }.collect { grid.scrollToItem(0) }
    }
    LaunchedEffect(controller, events) {
        snapshotFlow { controller.eventsProgress == 0f }.filter { it }.collect { events.scrollToItem(0) }
    }
    LaunchedEffect(controller, topMenu) {
        snapshotFlow { controller.topMenuProgress == 0f }.filter { it }.collect { topMenu.scrollTo(0) }
    }
    val closed by remember(controller) { derivedStateOf { controller.gridProgress == 0f } }
    val open by remember(controller) { derivedStateOf { controller.gridProgress == 1f } }
    val eventsClosed by remember(controller) { derivedStateOf { controller.eventsProgress == 0f } }
    val eventsOpen by remember(controller) { derivedStateOf { controller.eventsProgress == 1f } }
    val topMenuClosed by remember(controller) { derivedStateOf { controller.topMenuProgress == 0f } }
    val topMenuOpen by remember(controller) { derivedStateOf { controller.topMenuProgress == 1f } }
    val topMenuProgress = remember(controller) { { controller.topMenuProgress } }
    val topMenuHeight = remember(controller) { { height: Int -> controller.topMenuHeight = height } }
    // The grid, Events and the Top Menu are composed once Home has drawn its
    // first frame, and kept: the launcher shows Home as soon as it can
    // (NFR-PERF-01), and composing them never stalls a swipe that is already
    // under way (NFR-PERF-02).
    var othersNeeded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        othersNeeded = true
    }

    val closeMenu = remember { { currentDispatch(Action.CloseContextMenu) } }
    val folderOpen by rememberUpdatedState(state.openFolder != null)
    val showApps = stringResource(R.string.action_show_apps)
    val showEvents = stringResource(R.string.action_show_events)
    val showTopMenu = stringResource(R.string.action_show_top_menu)
    val showHome = stringResource(R.string.action_show_home)
    val dim = LauncherTheme.colors.dim

    WindowBlur { controller.gridProgress }
    // Every haptic beneath follows the Haptic feedback setting (FR-GEST-08).
    LauncherHaptics(state.settings.haptics) {
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { controller.size = it }
                // Edge to edge, behind every surface (UI-07).
                .drawBehind { drawRect(dim, alpha = maxOf(controller.gridProgress, controller.eventsProgress, controller.topMenuProgress)) }
                .layerGestures(
                    controller = controller,
                    ignored = gestureInsets,
                    scrolls = {
                        Scrolls(
                            homeForward = covers.canScrollForward,
                            homeBackward = covers.canScrollBackward,
                            gridBackward = grid.canScrollBackward,
                            topMenuForward = topMenu.canScrollForward,
                        )
                    },
                    paused = { folderOpen },
                )
                .onUnhandledTap(closeMenu)
                .testTag(LauncherTags.ROOT),
        ) {
            HomeScreen(
                state = state,
                dispatch = dispatch,
                coversState = covers,
                modifier = Modifier
                    .graphicsLayer {
                        translationY = -controller.gridProgress * size.height
                        translationX = GestureMath.homeShift(controller.sideways) * size.width
                    }
                    .then(
                        if (open || eventsOpen || topMenuOpen) {
                            Modifier.clearAndSetSemantics {}
                        } else {
                            // Screen-reader users open the grid, Events and the
                            // Top Menu without swiping (FR-GEST-09).
                            Modifier.semantics {
                                customActions = listOf(
                                    CustomAccessibilityAction(showApps) {
                                        currentDispatch(Action.LayerSettled(Layer.AppGrid))
                                        true
                                    },
                                    CustomAccessibilityAction(showEvents) {
                                        currentDispatch(Action.LayerSettled(Layer.Events))
                                        true
                                    },
                                    CustomAccessibilityAction(showTopMenu) {
                                        currentDispatch(Action.LayerSettled(Layer.TopMenu))
                                        true
                                    },
                                )
                            }
                        },
                    ),
            )
            if (othersNeeded || !eventsClosed) {
                EventsScreen(
                    state = state,
                    dispatch = dispatch,
                    claims = controller.claims,
                    listState = events,
                    modifier = Modifier
                        .graphicsLayer { translationX = GestureMath.eventsShift(controller.sideways) * size.width }
                        .then(
                            if (eventsOpen) {
                                // And return to Home the same way (FR-GEST-09).
                                Modifier.semantics {
                                    customActions = listOf(
                                        CustomAccessibilityAction(showHome) {
                                            currentDispatch(Action.LayerSettled(Layer.Home))
                                            true
                                        },
                                    )
                                }
                            } else {
                                Modifier.clearAndSetSemantics {}
                            },
                        ),
                )
            }
            if (othersNeeded || !closed) {
                AppGrid(
                    state = state,
                    dispatch = dispatch,
                    gridState = grid,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationY = (1f - controller.gridProgress) * size.height }
                        .then(if (closed) Modifier.clearAndSetSemantics {} else Modifier),
                )
            }
            if (othersNeeded || !topMenuClosed) {
                TopMenu(
                    state = state,
                    dispatch = dispatch,
                    progress = topMenuProgress,
                    catchesTouches = !topMenuClosed,
                    onHeight = topMenuHeight,
                    scrollState = topMenu,
                    modifier = if (topMenuOpen) {
                        // And close it the same way (FR-GEST-09).
                        Modifier.semantics {
                            customActions = listOf(
                                CustomAccessibilityAction(showHome) {
                                    currentDispatch(Action.LayerSettled(Layer.Home))
                                    true
                                },
                            )
                        }
                    } else {
                        Modifier.clearAndSetSemantics {}
                    },
                )
            }
            val remorse = state.remorse
            if (remorse != null) {
                val label = when (remorse.action) {
                    is Destructive.RemoveFolder -> stringResource(R.string.remorse_remove_folder)
                    is Destructive.ClearNotifications -> stringResource(R.string.remorse_clear_notifications)
                }
                RemorseBanner(
                    label = label,
                    cancelLabel = stringResource(R.string.remorse_cancel, label),
                    number = remorse.number,
                    duration = REMORSE_DELAY,
                    onCancel = { dispatch(Action.CancelRemorse) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(Spacing.Gutter)
                        .testTag(LauncherTags.REMORSE),
                )
            }
        }
    }
}

/** Test tags for [LauncherScreen]'s instrumented tests. */
internal object LauncherTags {
    const val ROOT = "launcher"
    const val REMORSE = "remorse-banner"
}
