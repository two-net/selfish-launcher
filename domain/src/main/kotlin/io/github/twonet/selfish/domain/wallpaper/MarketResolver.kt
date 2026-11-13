package io.github.twonet.selfish.domain.wallpaper

import io.github.twonet.selfish.domain.model.Market
import java.util.Locale

/**
 * Picks the Bing market for the image of the day (FR-WP-03, DESIGN §4.3).
 * A market the user chose wins. Automatic maps the device's language and
 * region to the closest supported market, falling back to `en-US` (Q-5).
 */
object MarketResolver {
    /** Used when nothing closer is supported (FR-WP-03, Q-5). */
    val FALLBACK = Market("en-US")

    /** The market codes Bing documents for its search APIs, which the image feed accepts. */
    val SUPPORTED: List<Market> = listOf(
        "da-DK", "de-AT", "de-CH", "de-DE", "en-AU", "en-CA", "en-GB", "en-ID", "en-IN", "en-MY",
        "en-NZ", "en-PH", "en-US", "en-ZA", "es-AR", "es-CL", "es-ES", "es-MX", "es-US", "fi-FI",
        "fr-BE", "fr-CA", "fr-CH", "fr-FR", "it-IT", "ja-JP", "ko-KR", "nl-BE", "nl-NL", "no-NO",
        "pl-PL", "pt-BR", "ru-RU", "sv-SE", "tr-TR", "zh-CN", "zh-HK", "zh-TW",
    ).map(::Market)

    /** The market for a language when its region isn't supported. */
    private val LANGUAGE_DEFAULTS: Map<String, Market> = mapOf(
        "da" to "da-DK", "de" to "de-DE", "en" to "en-US", "es" to "es-ES", "fi" to "fi-FI",
        "fr" to "fr-FR", "it" to "it-IT", "ja" to "ja-JP", "ko" to "ko-KR", "nl" to "nl-NL",
        "no" to "no-NO", "pl" to "pl-PL", "pt" to "pt-BR", "ru" to "ru-RU", "sv" to "sv-SE",
        "tr" to "tr-TR", "zh" to "zh-CN",
    ).mapValues { Market(it.value) }

    private val byCode: Map<String, Market> = SUPPORTED.associateBy { it.code.lowercase(Locale.ROOT) }

    /** The market to ask for: [setting] if the user chose one, else the closest to [locale]. */
    fun resolve(setting: Market?, locale: Locale): Market {
        if (setting != null) return setting
        val language = when (val tag = locale.language.lowercase(Locale.ROOT)) {
            // Bokmål and Nynorsk both read Bing's Norwegian market.
            "nb", "nn" -> "no"
            else -> tag
        }
        byCode["$language-${locale.country.lowercase(Locale.ROOT)}"]?.let { return it }
        // Traditional Chinese without a supported region reads Taiwan's market.
        if (language == "zh" && locale.script == "Hant") return Market("zh-TW")
        return LANGUAGE_DEFAULTS[language] ?: FALLBACK
    }
}
