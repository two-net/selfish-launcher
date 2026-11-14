package io.github.twonet.selfish.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import io.github.twonet.selfish.domain.model.Market
import java.util.Locale

/**
 * [market]'s name in the device's language, such as "English (United
 * States)" for `en-US`, as Wallpaper details and Settings show it (FR-WP-09,
 * FR-SET-03, NFR-L10N-02).
 */
@Composable
fun marketName(market: Market): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(market, locale) { market.displayName(locale) }
}

/** This market's name in [locale], or its code if the platform has no name for it. */
fun Market.displayName(locale: Locale): String = Locale.forLanguageTag(code).getDisplayName(locale).ifBlank { code }
