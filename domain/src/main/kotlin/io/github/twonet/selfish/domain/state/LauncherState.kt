package io.github.twonet.selfish.domain.state

import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.Layout
import io.github.twonet.selfish.domain.model.Settings

/**
 * The single source of truth (AR-02). Presentation renders this and nothing
 * else; feature issues add their fields here (DESIGN §3.3).
 */
data class LauncherState(
    /** Every launchable app, in the device locale's alphabetical order. */
    val apps: List<AppEntry> = emptyList(),
    /**
     * The saved arrangement. `null` until [restored], and after that when
     * nothing usable was saved, which is a first run: then the first-run
     * layout is built from the catalog (FR-HOME-07, FR-ONB-01).
     */
    val layout: Layout? = null,
    val settings: Settings = Settings(),
    /**
     * Whether the saved layout and settings have been read. Until then Home
     * shows only the wallpaper, so it never flashes empty (FR-GEN-08).
     */
    val restored: Boolean = false,
)
