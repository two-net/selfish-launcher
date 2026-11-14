package io.github.twonet.selfish.domain.state

import io.github.twonet.selfish.domain.model.Animations
import io.github.twonet.selfish.domain.model.Haptics
import io.github.twonet.selfish.domain.model.Settings

// The Settings page's transitions (SRS §4.13, DESIGN §4.1). The wallpaper
// settings live in WallpaperReducer.kt, the highlight color in
// AmbienceReducer.kt, and the appearance, which the Top Menu sets too, in
// Reducer.kt. Every change is saved with the settings by the store's saver
// and takes effect at once: there is no Save button.

/**
 * Opens Settings over Home (FR-TOP-04, FR-WP-09). Whatever was open, such as
 * the Top Menu or Wallpaper details, closes beneath it.
 */
internal fun openSettings(state: LauncherState): Next =
    if (state.layer == Layer.Settings) Next(state) else Next(state.copy(layer = Layer.Settings, overlays = emptyList()))

/** Opens About over Settings, so Back returns to Settings first (FR-SET-17, FR-GEN-04). */
internal fun openAbout(state: LauncherState): Next =
    if (state.layer != Layer.Settings || state.overlays.lastOrNull() == Overlay.About) {
        Next(state)
    } else {
        Next(state.copy(overlays = state.overlays + Overlay.About))
    }

/** "Time and date on Home" (FR-SET-07, FR-HOME-02). */
internal fun setShowClock(state: LauncherState, show: Boolean): Next = state.editSettings { it.copy(showClock = show) }

/** "App Grid columns" (FR-SET-09); a count the grid can't have is ignored. */
internal fun setGridColumns(state: LauncherState, columns: Int): Next =
    if (columns !in Settings.GRID_COLUMNS) Next(state) else state.editSettings { it.copy(gridColumns = columns) }

/** "Haptic feedback" (FR-SET-14, FR-GEST-08). */
internal fun setHaptics(state: LauncherState, haptics: Haptics): Next = state.editSettings { it.copy(haptics = haptics) }

/** "Animations" (FR-SET-15, FR-ANIM-15). */
internal fun setAnimations(state: LauncherState, animations: Animations): Next = state.editSettings { it.copy(animations = animations) }

/**
 * "Notifications in Events" off (FR-SET-12). A "Turn on" still waiting for
 * access is dropped too, so granting it later doesn't turn the feature back
 * on. The notifications themselves go in `keepRules` (NFR-PRIV-05).
 */
internal fun turnOffNotifications(state: LauncherState): Next =
    if (!state.settings.notificationsInEvents && !state.awaitingNotificationAccess) {
        Next(state)
    } else {
        Next(state.copy(settings = state.settings.copy(notificationsInEvents = false), awaitingNotificationAccess = false))
    }

/**
 * Runs "Reset layout" once its remorse timer is over (FR-SET-16). The
 * first-run layout needs the device's default apps, so they are looked up as
 * on a first run, and [Action.DefaultAppsResolved] builds it. Until then the
 * current layout stays, so the grid and Home never flash empty. Settings and
 * the wallpaper are left as they are.
 */
internal fun resetLayout(state: LauncherState): Next =
    if (state.resolvingDefaults) {
        Next(state.copy(resettingLayout = true))
    } else {
        Next(state.copy(resettingLayout = true, resolvingDefaults = true), listOf(Effect.ResolveDefaultApps))
    }

/** [this] with [edit] applied to its settings, keeping the same state when nothing changed. */
private inline fun LauncherState.editSettings(edit: (Settings) -> Settings): Next {
    val settings = edit(this.settings)
    return if (settings == this.settings) Next(this) else Next(copy(settings = settings))
}
