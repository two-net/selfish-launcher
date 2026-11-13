package io.github.twonet.selfish.domain.persist

import io.github.twonet.selfish.domain.model.WallpaperIndex

/**
 * `wallpapers.json`, the wallpaper cache index (SRS §8, DESIGN §4.7). Not
 * backed up. Nothing usable saved means an empty cache: the images are
 * downloaded again.
 *
 * Version 1. To change the format, append a migration here and add a frozen
 * sample of the new version to the tests, keeping the old ones (AR-07).
 */
val WallpaperIndexDoc: VersionedDocument<WallpaperIndex> = VersionedDocument(
    serializer = WallpaperIndex.serializer(),
    default = WallpaperIndex(),
    migrations = emptyList(),
    repair = ::repairIndex,
)

/** Each image once, newest first as saved, and a pin only on a cached image (NFR-REL-03). */
private fun repairIndex(index: WallpaperIndex): WallpaperIndex {
    val entries = index.entries.distinctBy { it.image }
    val pinned = index.pinned?.takeIf { pin -> entries.any { it.image == pin } }
    return if (entries.size == index.entries.size && pinned == index.pinned) index else index.copy(entries = entries, pinned = pinned)
}
