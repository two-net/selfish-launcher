package io.github.twonet.selfish.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.AppId
import io.github.twonet.selfish.domain.model.GridItem
import io.github.twonet.selfish.domain.model.Layout
import io.github.twonet.selfish.domain.state.Action
import io.github.twonet.selfish.domain.state.Layer
import io.github.twonet.selfish.domain.state.LauncherState
import io.github.twonet.selfish.domain.state.reduce
import io.github.twonet.selfish.domain.state.reducedMotion
import io.github.twonet.selfish.ui.motion.GestureMath
import io.github.twonet.selfish.ui.tokens.DefaultType
import io.github.twonet.selfish.ui.tokens.LauncherTheme
import io.github.twonet.selfish.ui.tokens.Sizes
import kotlin.math.ln
import java.time.LocalDate
import io.github.twonet.selfish.domain.ports.ImageId
import io.github.twonet.selfish.domain.model.WallpaperEntry
import io.github.twonet.selfish.domain.model.PixelSize
import io.github.twonet.selfish.domain.model.Market

/**
 * The launcher as the user sees it, for instrumented tests (level I, DESIGN
 * §8.3). Actions go through the real reducer, as in the store, but not
 * through the ports: a launch or an uninstall is only recorded in
 * [dispatched]. Haptics don't reach the device either: they are recorded in
 * [haptics].
 */
class TestLauncher(private val rule: ComposeContentTestRule) {
    val dispatched = mutableListOf<Action>()
    var state by mutableStateOf(LauncherState())

    /** Every haptic that got past the launcher's Haptic feedback setting, in order (FR-GEST-07, FR-GEST-08). */
    val haptics = mutableListOf<HapticFeedbackType>()

    private val platformHaptics = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            haptics += hapticFeedbackType
        }
    }

    /**
     * Shows [state]. The system gesture areas are [gestureInsets], none by
     * default, so tests don't depend on the device's navigation mode. With a
     * [fontScale], text is sized as at that system font scale.
     */
    fun show(state: LauncherState, gestureInsets: WindowInsets = NO_INSETS, fontScale: Float? = null) {
        this.state = state
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale ?: density.fontScale),
                LocalHapticFeedback provides platformHaptics,
            ) {
                LauncherTheme(reducedMotion = this.state.reducedMotion) { LauncherScreen(this.state, ::dispatch, gestureInsets) }
            }
        }
        rule.waitForIdle()
    }

    fun dispatch(action: Action) {
        dispatched += action
        state = reduce(state, action).state
    }

    /**
     * How far a finger moves to pull a pulley menu of [items] down by [rows]
     * of its rows (FR-GEST-05): item `i` is highlighted from `i + 1` rows to
     * `i + 2`. It undoes the rubber band, so it lands where the menu's own
     * math puts it.
     */
    fun pullDistance(rows: Float, items: Int): Float {
        val item = with(rule.density) { maxOf(Sizes.PulleyItem.toPx(), DefaultType.header.lineHeight.toPx()) }
        val limit = GestureMath.pulleyLimit(item, items)
        return -limit * ln(1f - item * rows / limit)
    }

    /**
     * Pulls the page under [node] slowly down to the middle of item [toItem]
     * (from 0) of its pulley menu of [items], and lets go without speed
     * (FR-GEST-05).
     */
    fun pull(node: SemanticsNodeInteraction, toItem: Int, items: Int) {
        val distance = pullDistance(rows = toItem + 1.5f, items = items)
        node.performTouchInput {
            down(center)
            repeat(PULL_STEPS) { moveBy(Offset(0f, distance / PULL_STEPS)) }
            moveBy(Offset.Zero, delayMillis = PULL_HOLD_MILLIS)
            up()
        }
    }

    companion object {
        val NO_INSETS = WindowInsets(0, 0, 0, 0)

        private const val PULL_STEPS = 10
        private const val PULL_HOLD_MILLIS = 300L

        fun app(label: String, removable: Boolean = true) = AppEntry(AppId("pkg.${label.lowercase()}", "Main", 0), label, removable = removable)

        /** A cached image of the day, as #14 stores them (FR-WP-07). */
        fun wallpaper(
            id: String,
            title: String,
            copyright: String,
            link: String? = null,
            date: LocalDate = LocalDate.of(2026, 11, 13),
        ) = WallpaperEntry(ImageId(id), title, copyright, link, date, Market("en-US"), PixelSize(1080, 1920), bytes = 400_000)

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
