package io.github.twonet.selfish.domain.persist

import io.github.twonet.selfish.domain.layout.LayoutRules
import io.github.twonet.selfish.domain.model.Layout

/**
 * `layout.json` (SRS §8). Nothing usable saved means a first run: the store
 * reads it with [VersionedDocument.decodeOrNull].
 *
 * Version 1. To change the format, append a migration here and add a frozen
 * sample of the new version to the tests, keeping the old ones (AR-07).
 */
val LayoutDoc: VersionedDocument<Layout> = VersionedDocument(
    serializer = Layout.serializer(),
    default = Layout(),
    migrations = emptyList(),
    repair = LayoutRules::normalize,
)
