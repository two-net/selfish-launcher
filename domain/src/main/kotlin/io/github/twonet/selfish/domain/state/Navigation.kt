package io.github.twonet.selfish.domain.state

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
 * context menu, pulley menu and keyboard.
 */
sealed interface Overlay {
    data class Folder(val id: FolderId) : Overlay
}
