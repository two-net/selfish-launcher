package io.github.twonet.selfish.domain.events

import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.ports.NotificationItem

/**
 * The notifications of one app in one profile, as Events lists them
 * (FR-EVT-04): the app's [label], its catalog entry [app] for the icon when
 * it can be launched, and its [items], newest first.
 */
data class NotificationGroup(
    val packageName: String,
    val userSerial: Long,
    val label: String,
    val app: AppEntry?,
    val items: List<NotificationItem>,
)

/** Groups notifications by the app that posted them (FR-EVT-04, DESIGN §4.1). */
object NotificationGroups {
    /**
     * [items] grouped by app and profile. The group with the newest
     * notification comes first, and each group lists its notifications
     * newest first.
     *
     * Each group is named by the app's name as the system knows it, or
     * else by its catalog entry, or else by its package. The icon is that of
     * the catalog entry from [apps] for the same package and profile: the one
     * whose label matches, when a package has several, or else the first.
     */
    fun of(items: List<NotificationItem>, apps: List<AppEntry>): List<NotificationGroup> {
        if (items.isEmpty()) return emptyList()
        val entries = apps.groupBy { it.id.packageName to it.id.userSerial }
        return items
            .sortedWith(NEWEST_FIRST)
            .groupBy { it.packageName to it.userSerial }
            .map { (owner, group) ->
                val systemLabel = group.first().appLabel
                val candidates = entries[owner].orEmpty()
                val app = candidates.firstOrNull { it.label == systemLabel } ?: candidates.firstOrNull()
                NotificationGroup(
                    packageName = owner.first,
                    userSerial = owner.second,
                    label = systemLabel.ifBlank { app?.label ?: owner.first },
                    app = app,
                    items = group,
                )
            }
    }

    /** Newest first; equal times keep a stable order by key. */
    private val NEWEST_FIRST = compareByDescending<NotificationItem> { it.timeMillis }.thenBy { it.key }
}
