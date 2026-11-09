package io.github.twonet.selfish.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The stored values of SRS §4.13, with the SRS defaults (DESIGN §4.1). Saved
 * as `settings.json`, which is backed up (SRS §8). Sort A–Z, Reset layout and
 * About (FR-SET-10, FR-SET-16, FR-SET-17) are actions, not values. Highlight
 * color (FR-SET-06) arrives with its presets.
 *
 * Adding a field with a default needs no migration. Renaming, removing or
 * restructuring one needs a migration in `SettingsDoc` and a new frozen
 * sample in the tests (AR-07). Enum serial names are part of the saved format.
 */
@Serializable
data class Settings(
    /** FR-SET-01, FR-THM-02. */
    val appearance: AppearanceMode = AppearanceMode.System,
    /** FR-SET-02. */
    val dailyWallpaper: Boolean = true,
    /** FR-SET-03. `null` is Automatic. */
    val market: Market? = null,
    /** FR-SET-04. */
    val lockScreen: Boolean = false,
    /** FR-SET-05. */
    val downloadOn: DownloadOn = DownloadOn.AnyNetwork,
    /** FR-SET-07: time and date on Home. */
    val showClock: Boolean = true,
    /** FR-SET-08. */
    val coversShow: CoversShow = CoversShow.Favorites,
    /** FR-SET-09: one of [GRID_COLUMNS]. */
    val gridColumns: Int = 4,
    /** FR-SET-11. */
    val swipeDownOpens: SwipeDownOpens = SwipeDownOpens.TopMenu,
    /** FR-SET-12. */
    val notificationsInEvents: Boolean = false,
    /** FR-SET-13. */
    val notificationDots: Boolean = false,
    /** FR-SET-14. */
    val haptics: Haptics = Haptics.FollowSystem,
    /** FR-SET-15. */
    val animations: Animations = Animations.FollowSystem,
) {
    init {
        // Also runs while decoding, so an out-of-range saved value is rejected
        // and that one setting falls back to its default (NFR-REL-03).
        require(gridColumns in GRID_COLUMNS) { "gridColumns must be in $GRID_COLUMNS, was $gridColumns" }
    }

    companion object {
        val GRID_COLUMNS = 3..5
    }
}

/** The chosen appearance mode (FR-THM-01); resolving it to Light or Dark is the theme's job. */
@Serializable
enum class AppearanceMode {
    @SerialName("system") System,
    @SerialName("light") Light,
    @SerialName("dark") Dark,
}

@Serializable
enum class DownloadOn {
    @SerialName("any-network") AnyNetwork,
    @SerialName("wifi-only") WifiOnly,
}

@Serializable
enum class CoversShow {
    @SerialName("favorites") Favorites,
    @SerialName("recently-used") RecentlyUsed,
}

@Serializable
enum class SwipeDownOpens {
    @SerialName("top-menu") TopMenu,
    @SerialName("notification-shade") NotificationShade,
}

@Serializable
enum class Haptics {
    @SerialName("follow-system") FollowSystem,
    @SerialName("off") Off,
}

@Serializable
enum class Animations {
    @SerialName("follow-system") FollowSystem,
    @SerialName("reduced") Reduced,
}
