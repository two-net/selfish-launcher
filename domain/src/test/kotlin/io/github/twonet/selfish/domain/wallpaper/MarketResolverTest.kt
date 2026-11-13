package io.github.twonet.selfish.domain.wallpaper

import io.github.twonet.selfish.domain.Verifies
import io.github.twonet.selfish.domain.model.Market
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The market for the image of the day (FR-WP-03, DESIGN §4.3). */
@Verifies("FR-WP-03")
class MarketResolverTest {
    private fun automatic(tag: String) = MarketResolver.resolve(null, Locale.forLanguageTag(tag))

    @Test
    fun `a market the user chose wins over the device's language`() {
        assertEquals(Market("ja-JP"), MarketResolver.resolve(Market("ja-JP"), Locale.forLanguageTag("de-DE")))
    }

    @Test
    fun `Automatic uses the device's language and region when Bing supports them`() {
        assertEquals(Market("en-GB"), automatic("en-GB"))
        assertEquals(Market("fr-CA"), automatic("fr-CA"))
        assertEquals(Market("de-CH"), automatic("de-CH"))
        assertEquals(Market("zh-TW"), automatic("zh-Hant-TW"))
        assertEquals(Market("pt-BR"), automatic("pt-BR"))
    }

    @Test
    fun `Automatic uses the language's own market for an unsupported region`() {
        assertEquals(Market("de-DE"), automatic("de-LU"))
        assertEquals(Market("fr-FR"), automatic("fr-SN"))
        assertEquals(Market("es-ES"), automatic("es-CO"))
        assertEquals(Market("pt-BR"), automatic("pt-PT"))
        assertEquals(Market("en-US"), automatic("en"))
        assertEquals(Market("zh-TW"), automatic("zh-Hant-MO"))
        assertEquals(Market("zh-CN"), automatic("zh-SG"))
    }

    @Test
    fun `Norwegian in either written form reads the Norwegian market`() {
        assertEquals(Market("no-NO"), automatic("nb-NO"))
        assertEquals(Market("no-NO"), automatic("nn-NO"))
    }

    @Test
    fun `anything else falls back to en-US`() {
        assertEquals(MarketResolver.FALLBACK, Market("en-US"))
        assertEquals(Market("en-US"), automatic("is-IS"))
        assertEquals(Market("en-US"), automatic("ar-EG"))
        assertEquals(Market("en-US"), MarketResolver.resolve(null, Locale.ROOT))
    }

    @Test
    fun `every market Automatic can pick is a supported one`() {
        val tags = Locale.getAvailableLocales().map { it.toLanguageTag() }
        for (tag in tags) {
            assertTrue(automatic(tag) in MarketResolver.SUPPORTED, "for $tag")
        }
    }
}
