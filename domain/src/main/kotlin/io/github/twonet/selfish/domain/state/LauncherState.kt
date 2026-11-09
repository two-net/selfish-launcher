package io.github.twonet.selfish.domain.state

import io.github.twonet.selfish.domain.model.AppEntry

/**
 * The single source of truth (AR-02). Presentation renders this and nothing
 * else; feature issues add their fields here (DESIGN §3.3).
 */
data class LauncherState(
    /** Every launchable app, in the device locale's alphabetical order. */
    val apps: List<AppEntry> = emptyList(),
)
