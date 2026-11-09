package io.github.twonet.selfish.domain.model

import kotlinx.serialization.Serializable

/**
 * Identifies one launchable entry: package, launch activity and user profile
 * (SRS §8). Stable across reboots and app updates, so it is what layouts store.
 */
@Serializable
data class AppId(
    val packageName: String,
    val activityName: String,
    /** The profile's serial number, which unlike a user handle survives reboots. */
    val userSerial: Long,
)

/** A launchable app as the catalog reports it. */
data class AppEntry(
    val id: AppId,
    val label: String,
    /**
     * Changes whenever the app's package is updated or changed, so its icon
     * is loaded again (FR-GEN-06). Only meaningful within one process.
     */
    val version: Long = 0,
)
