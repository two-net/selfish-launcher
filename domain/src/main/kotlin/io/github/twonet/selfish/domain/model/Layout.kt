package io.github.twonet.selfish.domain.model

import io.github.twonet.selfish.domain.persist.LenientListSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The user's arrangement (SRS §8, DESIGN §4.2): the App Grid and the Home
 * covers, each in the user's order. Saved as `layout.json`, which is backed
 * up; the schema version lives in the document, not here (DESIGN §4.7).
 *
 * Adding a field with a default needs no migration. Renaming, removing or
 * restructuring one needs a migration in `LayoutDoc` and a new frozen sample
 * in the tests (AR-07).
 */
@Serializable
data class Layout(
    /** App Grid items in the user's order (FR-GRID-05). */
    @Serializable(with = LenientListSerializer::class)
    val grid: List<GridItem> = emptyList(),
    /** Home covers in order, at most 9 (FR-HOME-03, FR-HOME-05). */
    @Serializable(with = LenientListSerializer::class)
    val home: List<AppId> = emptyList(),
)

/**
 * One App Grid item. A folder holds only apps, so folders can't nest, and
 * Home holds only apps, so folders can't go there (FR-FOLD-08).
 *
 * The serial names are part of the saved format; never derive them from
 * class names.
 */
@Serializable
sealed interface GridItem {
    @Serializable
    @SerialName("app")
    data class App(val id: AppId) : GridItem

    @Serializable
    @SerialName("folder")
    data class Folder(
        val id: FolderId,
        val name: String = DEFAULT_NAME,
        /** At least two apps once normalized (FR-FOLD-06). */
        @Serializable(with = LenientListSerializer::class)
        val apps: List<AppId>,
    ) : GridItem {
        companion object {
            /** The name a new folder gets (FR-FOLD-01). */
            const val DEFAULT_NAME = "Folder"
        }
    }
}

/** Identifies a folder within one [Layout]; see `LayoutRules.nextFolderId`. */
@Serializable
@JvmInline
value class FolderId(val value: Long)
