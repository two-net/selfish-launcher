package io.github.twonet.selfish.domain.state

import io.github.twonet.selfish.domain.model.HighlightSetting

// The ambience transitions of the reducer (SRS §4.11, DESIGN §4.4). The
// highlight itself is derived from state (LauncherState.highlight); these
// keep what it is derived from up to date.

/**
 * Asks the system what its home screen wallpaper is, to tell one set
 * elsewhere apart and take its colors (FR-AMB-03). Each read is numbered, so
 * one that was overtaken, for example by the launcher applying an image
 * meanwhile, is ignored.
 */
internal fun readSystemWallpaper(state: LauncherState): Next {
    val number = state.systemWallpaperReads + 1
    return Next(state.copy(systemWallpaperReads = number), listOf(Effect.ReadSystemWallpaper(number)))
}

/** The system reported its wallpaper; only the latest read counts. */
internal fun systemWallpaperRead(state: LauncherState, action: Action.SystemWallpaperRead): Next =
    if (action.number != state.systemWallpaperReads) {
        Next(state)
    } else {
        Next(state.copy(systemWallpaper = SystemWallpaperReport(action.id, action.colors)))
    }

/** Automatic or a preset, saved with the settings (FR-AMB-05). */
internal fun setHighlight(state: LauncherState, setting: HighlightSetting): Next =
    if (setting == state.settings.highlight) Next(state) else Next(state.copy(settings = state.settings.copy(highlight = setting)))
