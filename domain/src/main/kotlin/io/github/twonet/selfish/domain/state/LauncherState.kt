package io.github.twonet.selfish.domain.state

import io.github.twonet.selfish.domain.layout.LayoutRules
import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.Layout
import io.github.twonet.selfish.domain.model.Settings
import io.github.twonet.selfish.domain.ports.ClockInfo

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
    /**
     * Whether nothing usable was saved when this process started. Unlike
     * `layout == null`, it stays true after the first-run layout is built.
     */
    val firstRun: Boolean = false,
    /**
     * The layer on screen. Not saved: after the process is killed, the
     * launcher opens on Home (FR-GEN-08, DESIGN §3.4).
     */
    val layer: Layer = Layer.Home,
    /** What is open over [layer], bottom to top (FR-GEN-04). */
    val overlays: List<Overlay> = emptyList(),
    /** Whether the launcher is on screen: between its `onStart` and `onStop`. */
    val homeShown: Boolean = false,
    /** Whether this process has already offered the default Home role (FR-GEN-02). */
    val defaultHomeOffered: Boolean = false,
    /**
     * Whether an app is launching. Further launches are ignored until the
     * launcher is hidden or [LAUNCH_TIMEOUT] passes (FR-GEN-05).
     */
    val launching: Boolean = false,
    /** How many launches have started, so a stale timeout can be told apart. */
    val launches: Int = 0,
    /**
     * Whether the default apps are being looked up for the first-run layout,
     * so the lookup starts only once (FR-HOME-07).
     */
    val resolvingDefaults: Boolean = false,
    /**
     * The time for Home's clock (FR-HOME-02). Updated only while the launcher
     * is shown, so the clock doesn't wake it in the background (NFR-PWR-02).
     */
    val clock: ClockInfo? = null,
)

/**
 * Home's covers, in the user's order (FR-HOME-03). A cover whose app the
 * catalog hasn't reported yet is left out until it does.
 */
val LauncherState.covers: List<AppEntry>
    get() {
        val home = layout?.home.orEmpty()
        if (home.isEmpty()) return emptyList()
        val byId = apps.associateBy { it.id }
        return home.mapNotNull { byId[it] }
    }

/** Whether Home has no room for another cover, so "Add to Home" explains instead (FR-HOME-05). */
val LauncherState.homeFull: Boolean
    get() = (layout?.home?.size ?: 0) >= LayoutRules.MAX_COVERS
