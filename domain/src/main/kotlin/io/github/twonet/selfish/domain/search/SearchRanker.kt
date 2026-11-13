package io.github.twonet.selfish.domain.search

import io.github.twonet.selfish.domain.model.AppEntry
import java.text.Collator

/**
 * Ranks [apps] against what the user types in the App Grid's search field
 * (FR-SRCH-02, DESIGN §4.5). Give it the whole catalog, so apps inside
 * folders are found too.
 *
 * The names are folded by [TextNormalizer] and split into words once, here,
 * so each keystroke only compares strings (NFR-PERF-04). Build one per
 * catalog change. It is a pure function of the catalog and the query and
 * reaches no port, so search never touches the network (FR-SRCH-06).
 */
class SearchRanker(apps: List<AppEntry>, collator: Collator = Collator.getInstance()) {
    // Alphabetical, so each rank keeps that order and ties need no sorting.
    private val names: List<Name> = apps.sortedWith(compareBy(collator) { it.label }).map { Name(it) }

    /**
     * The apps that match [query], best first:
     * 1. those whose name, or any word in it, starts with the query;
     * 2. those with the query anywhere else in the name.
     *
     * Ties are alphabetical. Apps that don't match are left out, and a blank
     * query matches nothing.
     */
    fun rank(query: String): List<AppEntry> {
        val folded = TextNormalizer.normalize(query)
        if (folded.isEmpty()) return emptyList()
        val starting = ArrayList<AppEntry>()
        val containing = ArrayList<AppEntry>()
        for (name in names) {
            when {
                name.starts.any { name.text.startsWith(folded, it) } -> starting += name.app
                folded in name.text -> containing += name.app
            }
        }
        return starting + containing
    }

    private class Name(val app: AppEntry) {
        val text = TextNormalizer.normalize(app.label)
        val starts = TextNormalizer.wordStarts(text)
    }
}
