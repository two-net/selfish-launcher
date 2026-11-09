package io.github.twonet.selfish.domain.persist

import io.github.twonet.selfish.domain.model.Settings

/**
 * `settings.json` (SRS §8). Nothing usable saved means the SRS defaults.
 *
 * Version 1. To change the format, append a migration here and add a frozen
 * sample of the new version to the tests, keeping the old ones (AR-07).
 */
val SettingsDoc: VersionedDocument<Settings> = VersionedDocument(
    serializer = Settings.serializer(),
    default = Settings(),
    migrations = emptyList(),
)
