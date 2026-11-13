package io.github.twonet.selfish.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.semantics
import io.github.twonet.selfish.domain.ports.ClockInfo
import io.github.twonet.selfish.ui.components.ClockFormat
import io.github.twonet.selfish.ui.components.LauncherText
import io.github.twonet.selfish.ui.tokens.Emphasis
import io.github.twonet.selfish.ui.tokens.TextRole
import java.util.Date

/**
 * Home's time and date in large type (FR-HOME-02), in the device locale's
 * own format and its 12- or 24-hour setting (NFR-L10N-02). The patterns come
 * from ICU skeletons, as `DateFormat.getBestDateTimePattern` does, so every
 * locale gets its own order and punctuation.
 */
@Composable
fun HomeClock(clock: ClockInfo, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val time = remember(locale, clock.zone, clock.is24Hour) {
        ClockFormat.formatter(ClockFormat.time(clock.is24Hour), locale, clock.zone)
    }
    val date = remember(locale, clock.zone) { ClockFormat.formatter(ClockFormat.DATE, locale, clock.zone) }
    val now = Date.from(clock.now)
    Column(modifier.semantics(mergeDescendants = true) {}) {
        LauncherText(text = time.format(now), role = TextRole.Display, maxLines = 1)
        LauncherText(text = date.format(now), role = TextRole.Body, emphasis = Emphasis.Secondary, maxLines = 1)
    }
}

