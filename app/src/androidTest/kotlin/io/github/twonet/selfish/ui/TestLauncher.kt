package io.github.twonet.selfish.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.Density
import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.AppId
import io.github.twonet.selfish.domain.model.GridItem
import io.github.twonet.selfish.domain.model.Layout
import io.github.twonet.selfish.domain.state.Action
import io.github.twonet.selfish.domain.state.Layer
import io.github.twonet.selfish.domain.state.LauncherState
import io.github.twonet.selfish.domain.state.reduce
import io.github.twonet.selfish.ui.tokens.LauncherTheme

/**
 * The launcher as the user sees it, for instrumented tests (level I, DESIGN
 * §8.3). Actions go through the real reducer, as in the store, but not
 * through the ports: a launch or an uninstall is only recorded in
 * [dispatched].
 */
class TestLauncher(private val rule: ComposeContentTestRule) {
    val dispatched = mutableListOf<Action>()
    var state by mutableStateOf(LauncherState())

    /**
     * Shows [state]. The system gesture areas are [gestureInsets], none by
     * default, so tests don't depend on the device's navigation mode. With a
     * [fontScale], text is sized as at that system font scale.
     */
    fun show(state: LauncherState, gestureInsets: WindowInsets = NO_INSETS, fontScale: Float? = null) {
        this.state = state
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale ?: density.fontScale)) {
                LauncherTheme { LauncherScreen(this.state, ::dispatch, gestureInsets) }
            }
        }
        rule.waitForIdle()
    }

    fun dispatch(action: Action) {
        dispatched += action
        state = reduce(state, action).state
    }

    companion object {
        val NO_INSETS = WindowInsets(0, 0, 0, 0)

        fun app(label: String, removable: Boolean = true) = AppEntry(AppId("pkg.${label.lowercase()}", "Main", 0), label, removable = removable)

        /** A restored launcher on [layer] with [apps] in the grid in that order, and [home] as covers. */
        fun launcher(apps: List<AppEntry>, home: List<AppEntry> = emptyList(), layer: Layer = Layer.Home) = LauncherState(
            apps = apps,
            layout = Layout(grid = apps.map { GridItem.App(it.id) }, home = home.map { it.id }),
            restored = true,
            homeShown = true,
            layer = layer,
        )
    }
}
