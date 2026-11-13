package io.github.twonet.selfish.domain.state

import io.github.twonet.selfish.domain.model.AppId
import io.github.twonet.selfish.domain.model.FolderId

/**
 * The launcher surface the user is on (SRS §3.2, DESIGN §4.1). Home is at
 * the bottom; every other layer sits over it. Feature issues add the layers
 * they build, such as Settings pages and the tutorial; [reduce]'s Back and
 * Home rules don't depend on which layers exist.
 */
sealed interface Layer {
    data object Home : Layer

    data object AppGrid : Layer

    data object Events : Layer

    data object TopMenu : Layer
}

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
