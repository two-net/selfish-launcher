package io.github.twonet.selfish.ui.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.twonet.selfish.R
import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.Bounds
import io.github.twonet.selfish.domain.state.Action
import io.github.twonet.selfish.domain.state.LauncherState
import io.github.twonet.selfish.domain.state.MenuTarget
import io.github.twonet.selfish.domain.state.homeFull
import io.github.twonet.selfish.ui.components.AppIcon
import io.github.twonet.selfish.ui.components.ContextMenu
import io.github.twonet.selfish.ui.components.ContextMenuItem
import io.github.twonet.selfish.ui.components.LauncherText
import io.github.twonet.selfish.ui.components.onBoundsInWindow
import io.github.twonet.selfish.ui.components.pressable
import io.github.twonet.selfish.ui.tokens.LauncherTheme
import io.github.twonet.selfish.ui.tokens.Motion
import io.github.twonet.selfish.ui.tokens.Spacing

/**
 * A stand-in for the App Grid until #7 replaces it: every app as a row on
 * the dim backdrop, below Home, so swiping up on Home scrolls to them. Apps
 * that aren't covers stay reachable, and a long-press opens a context menu
 * beneath the row with "Add to Home" or "Remove from Home" (FR-HOME-05).
 */
fun LazyListScope.appListStandIn(state: LauncherState, menu: MenuTarget?, dispatch: (Action) -> Unit) {
    val menuApp = (menu as? MenuTarget.App)?.app
    val rows = buildList {
        state.apps.forEach { app ->
            add(ListEntry.App(app))
            if (app.id == menuApp) add(ListEntry.Menu(app))
        }
    }
    items(rows, key = { it.key }, contentType = { if (it is ListEntry.Menu) CONTENT_MENU else CONTENT_APP }) { row ->
        val rowModifier = Modifier.animateItem(fadeInSpec = Motion.reflow(), placementSpec = Motion.reflow(), fadeOutSpec = Motion.reflow())
        when (row) {
            is ListEntry.App -> AppRow(row.app, dispatch, rowModifier)
            is ListEntry.Menu -> AppMenu(row.app, state, dispatch, rowModifier)
        }
    }
    // The dim runs on behind the navigation bar, and the last row clears it (UI-07).
    item(key = END_KEY) {
        Spacer(Modifier.fillMaxWidth().background(LauncherTheme.colors.dim).windowInsetsBottomHeight(WindowInsets.safeDrawing))
    }
}

private const val END_KEY = "end"
private const val CONTENT_APP = "app"
private const val CONTENT_MENU = "menu"

private sealed interface ListEntry {
    val key: String

    class App(val app: AppEntry) : ListEntry {
        override val key = app.id.toString()
    }

    class Menu(val app: AppEntry) : ListEntry {
        override val key = "menu:${app.id}"
    }
}

@Composable
private fun AppRow(app: AppEntry, dispatch: (Action) -> Unit, modifier: Modifier) {
    var bounds by remember { mutableStateOf<Bounds?>(null) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Gutter),
        modifier = modifier
            .fillMaxWidth()
            .background(LauncherTheme.colors.dim)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .onBoundsInWindow { bounds = it }
            .pressable(
                onLongClickLabel = stringResource(R.string.action_open_menu),
                onLongClick = { dispatch(Action.OpenContextMenu(MenuTarget.App(app.id))) },
                onClick = { dispatch(Action.Launch(app.id, bounds)) },
            )
            .padding(horizontal = Spacing.Gutter, vertical = Spacing.Small),
    ) {
        AppIcon(app)
        LauncherText(text = app.label, maxLines = 1)
    }
}

@Composable
private fun AppMenu(app: AppEntry, state: LauncherState, dispatch: (Action) -> Unit, modifier: Modifier) {
    val onHome = state.layout?.home?.contains(app.id) == true
    val item = when {
        onHome -> ContextMenuItem(stringResource(R.string.menu_remove_from_home)) { dispatch(Action.RemoveFromHome(app.id)) }
        state.homeFull -> ContextMenuItem(stringResource(R.string.menu_add_to_home), stringResource(R.string.menu_home_full), onClick = null)
        else -> ContextMenuItem(stringResource(R.string.menu_add_to_home)) { dispatch(Action.AddToHome(app.id)) }
    }
    Box(
        modifier
            .fillMaxWidth()
            .background(LauncherTheme.colors.dim)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = Spacing.Gutter, vertical = Spacing.Small),
    ) {
        ContextMenu(listOf(item))
    }
}
