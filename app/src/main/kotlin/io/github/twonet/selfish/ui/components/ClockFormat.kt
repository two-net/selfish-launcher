package io.github.twonet.selfish.ui.components

import android.icu.text.DateFormat
import android.icu.util.TimeZone
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone as JavaTimeZone

/**
 * Dates and times in the device locale's own format (NFR-L10N-02), for
 * Home's clock, the Events header and notification times. The patterns
 * come from ICU skeletons, as `DateFormat.getBestDateTimePattern` does, so
 * every locale gets its own order and punctuation.
 */
object ClockFormat {
    /** Hours and minutes on a 24-hour clock. */
    const val TIME_24 = "Hm"

    /** Hours and minutes on a 12-hour clock, with the day period. */
    const val TIME_12 = "hm"

    /** The weekday, month and day, written out: Home's date and the Events header (FR-HOME-02, FR-EVT-02). */
    const val DATE = "EEEEMMMMd"

    /** The month and day, abbreviated: the time of a notification from before today. */
    const val SHORT_DATE = "MMMd"

    /** The time skeleton for the device's 12- or 24-hour setting. */
    fun time(is24Hour: Boolean): String = if (is24Hour) TIME_24 else TIME_12

    /** A formatter for [skeleton] in [locale], showing times in [zone]. */
    fun formatter(skeleton: String, locale: Locale, zone: ZoneId): DateFormat =
        DateFormat.getInstanceForSkeleton(skeleton, locale).apply {
            // Through java.util, which also names fixed offsets in a form ICU knows.
            timeZone = TimeZone.getTimeZone(JavaTimeZone.getTimeZone(zone).id)
        }
}
