package io.github.twonet.selfish.domain.search

import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.domain.model.AppId
import io.github.twonet.selfish.domain.model.FolderId
import io.github.twonet.selfish.domain.model.GridItem
import io.github.twonet.selfish.domain.model.Layout
import io.github.twonet.selfish.domain.state.LauncherState
import java.text.Collator
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/** Search ranking (FR-SRCH-02, DESIGN §4.5), on the JVM, in a fixed locale. */
class SearchRankerTest {
    private fun app(label: String) = AppEntry(AppId("pkg.${label.lowercase()}", "Main", 0), label)

    private fun ranker(vararg labels: String) = SearchRanker(labels.map(::app), Collator.getInstance(Locale.ENGLISH))

    private fun SearchRanker.labels(query: String) = rank(query).map { it.label }

    @Test
    @Verifies("FR-SRCH-02")
    fun `case doesn't matter`() {
        val ranker = ranker("Maps", "Clock")

        assertEquals(listOf("Maps"), ranker.labels("MAPS"))
        assertEquals(listOf("Maps"), ranker.labels("mAp"))
    }

    @Test
    @Verifies("FR-SRCH-02")
    fun `accents don't matter, in the name or the query`() {
        val ranker = ranker("Café", "Ölreich", "İstanbul Metro", "Notes")

        assertEquals(listOf("Café"), ranker.labels("cafe"))
        assertEquals(listOf("Café"), ranker.labels("CAFÉ"))
        assertEquals(listOf("Ölreich"), ranker.labels("olr"))
        assertEquals(listOf("İstanbul Metro"), ranker.labels("istanbul"))
        assertEquals(listOf("Notes"), ranker.labels("nötes"))
    }

    @Test
    @Verifies("FR-SRCH-02")
    fun `names and words that start with the query come before other matches`() {
        val ranker = ranker("Bitmap Studio", "Maps", "Google Maps", "Clock")

        assertEquals(listOf("Google Maps", "Maps", "Bitmap Studio"), ranker.labels("ma"))
    }

    @Test
    @Verifies("FR-SRCH-02")
    fun `words are split at spaces and punctuation`() {
        val ranker = ranker("Wi-Fi Analyzer", "Domino's", "Profile")

        assertEquals(listOf("Wi-Fi Analyzer", "Profile"), ranker.labels("fi"))
        assertEquals(listOf("Domino's"), ranker.labels("s"))
    }

    @Test
    @Verifies("FR-SRCH-02")
    fun `a query of several words matches from any word start`() {
        val ranker = ranker("Google Play Store", "Play Games", "Store Locator")

        assertEquals(listOf("Google Play Store"), ranker.labels("play st"))
        assertEquals(listOf("Google Play Store"), ranker.labels("  Play   Store "))
    }

    @Test
    @Verifies("FR-SRCH-02")
    fun `ties are alphabetical in the locale, whatever the catalog order`() {
        val ranker = ranker("notes", "Navigation", "Écran", "Nova", "eBay")

        assertEquals(listOf("Navigation", "notes", "Nova", "Écran"), ranker.labels("n"))
        assertEquals(listOf("eBay", "Écran", "notes"), ranker.labels("e"))
    }

    @Test
    @Verifies("FR-SRCH-02")
    fun `apps that don't match are left out, and a blank query matches nothing`() {
        val ranker = ranker("Maps", "Clock")

        assertEquals(emptyList(), ranker.labels("zzz"))
        assertEquals(emptyList(), ranker.labels(""))
        assertEquals(emptyList(), ranker.labels("   "))
    }

    @Test
    @Verifies("FR-SRCH-02")
    fun `apps inside folders are found, since the ranker takes the whole catalog`() {
        val maps = app("Maps")
        val clock = app("Clock")
        val state = LauncherState(
            apps = listOf(clock, maps),
            layout = Layout(grid = listOf(GridItem.Folder(FolderId(1), "Tools", listOf(clock.id, maps.id))), home = emptyList()),
        )

        assertEquals(listOf(maps), SearchRanker(state.apps).rank("map"))
    }

    @Test
    @Verifies("FR-SRCH-06")
    fun `ranking needs nothing but the catalog and the query`() {
        // No port, clock or dispatcher goes in: search can't reach the network.
        val ranker = SearchRanker(listOf(app("Maps")))

        assertEquals(listOf("Maps"), ranker.labels("maps"))
    }

    @Test
    fun `normalizing folds case, accents and spaces, and finds word starts`() {
        assertEquals("cafe noir", TextNormalizer.normalize("  Café  Noir "))
        assertContentEquals(intArrayOf(0, 3, 6), TextNormalizer.wordStarts("wi-fi map"))
        assertContentEquals(intArrayOf(0, 1), TextNormalizer.wordStarts("+plus"))
        assertContentEquals(intArrayOf(), TextNormalizer.wordStarts(""))
    }
}
