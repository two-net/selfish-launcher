package io.github.twonet.selfish.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
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
import io.github.twonet.selfish.ui.tokens.LauncherTheme
import io.github.twonet.selfish.ui.tokens.Spacing
import kotlin.math.roundToInt

/**
 * The walking skeleton's only surface: a plain list of every app, rendered
 * from [state]; taps go back as actions (AR-02). #6 and #7 replace it with
 * Home and the App Grid.
 */
@Composable
fun HomeScreen(state: LauncherState, dispatch: (Action) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(LauncherTheme.colors.scrim),
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
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.MinTouchTarget)
            .onGloballyPositioned { coordinates ->
                val rect = coordinates.boundsInWindow()
                bounds = Bounds(
                    left = rect.left.roundToInt(),
                    top = rect.top.roundToInt(),
                    right = rect.right.roundToInt(),
                    bottom = rect.bottom.roundToInt(),
                )
            }
            .clickable { onClick(bounds) }
            .padding(horizontal = Spacing.Gutter),
    ) {
        BasicText(
            text = app.label,
            style = LauncherTheme.type.body.copy(color = LauncherTheme.colors.textPrimary),
        )
    }
}
