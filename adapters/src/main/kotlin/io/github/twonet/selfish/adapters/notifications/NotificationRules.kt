package io.github.twonet.selfish.adapters.notifications

import io.github.twonet.selfish.domain.ports.NotificationRef

/**
 * A notification as the system reports it, reduced to what the launcher
 * decides on. Plain Kotlin, so [NotificationRules] runs on the JVM.
 * [toString] leaves out what it says (NFR-PRIV-05).
 */
internal data class Posted(
    val key: String,
    val packageName: String,
    val userSerial: Long,
    /** The system's group key; children of a group share their summary's. */
    val groupKey: String?,
    val groupSummary: Boolean,
    val title: String,
    val text: String,
    val timeMillis: Long,
    val postedAtMillis: Long,
    val clearable: Boolean,
    /** Hidden by the system: its app is suspended, or Do Not Disturb keeps it out of lists. */
    val hidden: Boolean,
) {
    override fun toString(): String = "Posted(key=$key, package=$packageName)"
}

/** Which notifications Events lists and which a dismissal may remove (DESIGN §5.1). */
internal object NotificationRules {
    /**
     * The notifications Events lists from [posted], newest first
     * (FR-EVT-04). Left out are those of [excludedPackage] (the launcher
     * itself), those the system hides, those with neither a title nor
     * text, and a group's summary while the group's own notifications are
     * listed. Ongoing ones are kept; they just can't be dismissed.
     */
    fun shown(posted: List<Posted>, excludedPackage: String?): List<Posted> {
        val visible = posted.filter { it.packageName != excludedPackage && !it.hidden && (it.title.isNotBlank() || it.text.isNotBlank()) }
        val groupsWithChildren = visible.filter { !it.groupSummary }.mapNotNullTo(HashSet()) { it.groupKey }
        return visible
            .filterNot { it.groupSummary && it.groupKey in groupsWithChildren }
            .sortedWith(compareByDescending<Posted> { it.timeMillis }.thenBy { it.key })
    }

    /**
     * The keys of the notifications in [current] to cancel for [refs]: those
     * the user may dismiss that are still as they were when shown. One
     * updated since, such as a new message under the same key, is kept
     * (FR-EVT-05, FR-EVT-06).
     */
    fun toCancel(current: List<Posted>, refs: Set<NotificationRef>): List<String> =
        current.filter { it.clearable && NotificationRef(it.key, it.postedAtMillis) in refs }.map { it.key }
}
