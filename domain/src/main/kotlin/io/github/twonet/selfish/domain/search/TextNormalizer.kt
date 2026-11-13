package io.github.twonet.selfish.domain.search

import java.text.Normalizer
import java.util.Locale

/**
 * Folds text for matching, so search ignores case and accents (FR-SRCH-02,
 * DESIGN §4.5). App names and the query go through the same folding.
 */
object TextNormalizer {
    private val MARKS = Regex("""\p{Mn}+""")
    private val SPACES = Regex("""[\s\p{Z}]+""")

    /**
     * [text] decomposed (NFD), without its combining marks, lowercased in
     * the root locale, with each run of spaces made one and none at either
     * end. "Café  Noir" becomes "cafe noir". Decomposing first also folds a
     * dotted capital I, whose dot is a combining mark, to a plain "i".
     */
    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(MARKS, "")
            .lowercase(Locale.ROOT)
            .replace(SPACES, " ")
            .trim()

    /**
     * Where each word of [normalized] starts: at 0, and at every letter or
     * digit that follows something else, such as a space or a hyphen.
     * "wi-fi map" starts words at 0, 3 and 6.
     */
    fun wordStarts(normalized: String): IntArray {
        val starts = ArrayList<Int>()
        normalized.forEachIndexed { i, c ->
            if (i == 0 || (c.isLetterOrDigit() && !normalized[i - 1].isLetterOrDigit())) starts += i
        }
        return starts.toIntArray()
    }
}
