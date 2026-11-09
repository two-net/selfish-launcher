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
 * Back closes the topmost one first (FR-GEN-04). Feature issues add the
 * pulley menu and keyboard.
 */
sealed interface Overlay {
    data class Folder(val id: FolderId) : Overlay

    /**
     * The context menu of [target], open beneath its row (FR-HOME-06,
     * FR-GRID-06). At most one is open at a time.
     */
    data class ContextMenu(val target: MenuTarget) : Overlay
}

/** What a context menu belongs to. The App Grid (#7) adds folders. */
sealed interface MenuTarget {
    val app: AppId

    /** A cover on Home (FR-HOME-05, FR-HOME-06). */
    data class Cover(override val app: AppId) : MenuTarget

    /** An app in the list of all apps (FR-HOME-05, FR-GRID-06). */
    data class App(override val app: AppId) : MenuTarget
}
