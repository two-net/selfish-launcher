package io.github.twonet.selfish.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.Bounds
import io.github.twonet.selfish.domain.state.Action
import io.github.twonet.selfish.domain.state.LauncherState
import io.github.twonet.selfish.ui.components.AppIcon
import io.github.twonet.selfish.ui.components.LauncherText
import io.github.twonet.selfish.ui.components.pressable
import io.github.twonet.selfish.ui.tokens.LauncherTheme
import io.github.twonet.selfish.ui.tokens.Spacing
import kotlin.math.roundToInt

/**
 * The walking skeleton's only surface: a plain list of every app, rendered
 * from [state]; taps go back as actions (AR-02). #6 and #7 replace it with
 * Home and the App Grid.
 *
 * The dim runs edge to edge over the wallpaper, and the list is inset by the
 * system bars, cutouts and rounded corners (UI-07).
 */
@Composable
fun HomeScreen(state: LauncherState, dispatch: (Action) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(LauncherTheme.colors.dim),
        contentPadding = WindowInsets.safeDrawing.asPaddingValues(),
    ) {
        items(state.apps, key = { it.id.toString() }) { app ->
            AppRow(app, onClick = { from -> dispatch(Action.Launch(app.id, from)) })
        }
    }
}

@Composable
private fun AppRow(app: AppEntry, onClick: (Bounds?) -> Unit) {
    var bounds by remember { mutableStateOf<Bounds?>(null) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Gutter),
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                val rect = coordinates.boundsInWindow()
                bounds = Bounds(
                    left = rect.left.roundToInt(),
                    top = rect.top.roundToInt(),
                    right = rect.right.roundToInt(),
                    bottom = rect.bottom.roundToInt(),
                )
            }
            .pressable { onClick(bounds) }
            .padding(horizontal = Spacing.Gutter, vertical = Spacing.Small),
    ) {
        AppIcon(label = app.label)
        LauncherText(text = app.label, maxLines = 1)
    }
}
