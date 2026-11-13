package io.github.twonet.selfish.domain.model

import io.github.twonet.selfish.domain.ports.ImageId

/**
 * A cached image of the day, as Events and the Top Menu identify it
 * (FR-EVT-03, FR-TOP-03, FR-WP-10): its cached [image], [title], [copyright]
 * text and "Learn more" [link]. #14 fills these in, and adds the date,
 * market and highlight color that Wallpaper details shows (FR-WP-09).
 */
data class WallpaperEntry(
    val image: ImageId,
    val title: String,
    val copyright: String,
    /** An `https` page about the image, if the feed gave one. */
    val link: String?,
)
