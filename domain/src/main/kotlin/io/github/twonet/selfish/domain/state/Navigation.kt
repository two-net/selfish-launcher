package io.github.twonet.selfish.domain.state

import io.github.twonet.selfish.domain.model.AppId
import io.github.twonet.selfish.domain.model.FolderId

/**
 * The launcher surface the user is on (SRS §3.2, DESIGN §4.1). Home is at
 * the bottom; every other layer sits over it. Feature issues add the layers
 * they build, such as Settings pages; [reduce]'s Back and Home rules don't
 * depend on which layers exist. The tutorial isn't a layer: it waits over
 * the real ones ([Tutorial]).
 */
sealed interface Layer {
    data object Home : Layer

    data object AppGrid : Layer

    data object Events : Layer

    /**
     * The menu pulled down over Home (SRS §4.7): appearance, ambiences and
     * shortcuts. Reached like the App Grid and Events, by a released swipe
     * down or a long-press on empty Home (FR-TOP-01, FR-TOP-06).
     */
    data object TopMenu : Layer

    /**
     * The page about the current wallpaper (FR-WP-09), opened from Events
     * (FR-EVT-03, FR-EVT-06) and the Top Menu (FR-TOP-04).
     */
    data object WallpaperDetails : Layer

    /**
     * The launcher's Settings page (SRS §4.13), opened from the Top Menu
     * (FR-TOP-04) and from Wallpaper details (FR-WP-09). About opens over it
     * as [Overlay.About], so Back returns to it first.
     */
    data object Settings : Layer
}

/**
 * Whether this layer is a page that opens over Home rather than a view a
 * swipe leads to: Wallpaper details and Settings. Whatever it was opened
 * from goes back beneath it, and it takes every touch until Back or Home
 * closes it (DESIGN §6.2).
 */
val Layer.isPage: Boolean
    get() = this == Layer.WallpaperDetails || this == Layer.Settings

/**
 * Something open over a [Layer], such as a folder. Overlays stack; System
 * Back closes the topmost one first (FR-GEN-04). The on-screen keyboard is
 * not one: the system's input method takes Back to hide itself before the
 * launcher sees it, so the keyboard stays with the field that shows it, as
 * transient UI state (DESIGN §4.1).
 */
sealed interface Overlay {
    /**
     * The panel of the folder [id], open over the App Grid (FR-FOLD-03). At
     * most one is open, and it closes when the folder no longer exists.
     */
    data class Folder(val id: FolderId) : Overlay

    /**
     * The context menu of [target], open beneath its row (FR-HOME-06,
     * FR-GRID-06). At most one is open at a time.
     */
    data class ContextMenu(val target: MenuTarget) : Overlay

    /**
     * Settings' About page (FR-SET-17), open over [Layer.Settings], so Back
     * returns to Settings before Home.
     */
    data object About : Overlay

    /**
     * The one-screen explanation of [feature] and the access it needs, open
     * over [Layer.Settings] when the user turns the feature on without that
     * access (FR-ONB-04). Its "Continue" asks for access; Back closes it and
     * leaves the feature off.
     */
    data class AccessExplanation(val feature: AccessFeature) : Overlay
}

/**
 * A feature that needs special access granted by the user in the system's
 * settings, so it asks for it only when turned on, after a one-screen
 * explanation (FR-ONB-04, NFR-PRIV-04). Notification dots (FR-GRID-11) and
 * recently used covers (FR-HOME-10) join this list when they are built.
 */
enum class AccessFeature {
    /** "Notifications in Events", which needs notification access (FR-SET-12, FR-EVT-04). */
    NotificationsInEvents,
}

/**
 * What a context menu belongs to. Only apps have one, in the App Grid or in
 * a folder's panel: a folder is lifted and moved without a menu, and its own
 * actions live in its panel's pulley menu (FR-FOLD-07).
 */
sealed interface MenuTarget {
    val app: AppId

    /** A cover on Home (FR-HOME-05, FR-HOME-06). */
    data class Cover(override val app: AppId) : MenuTarget

    /** An app in the App Grid, or in an open folder (FR-HOME-05, FR-GRID-06). */
    data class App(override val app: AppId) : MenuTarget
}
