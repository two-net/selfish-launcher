package io.github.twonet.selfish.ui.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * One sans-serif family in four sizes (UI-04): the system font, so nothing is
 * bundled (C-3, NFR-PERF-07), sized in `sp` so it follows the font scale
 * (NFR-A11Y-04). Emphasis is a color, not a size: see [Emphasis].
 */
@Immutable
class LauncherType(
    /** The Home clock. */
    val display: TextStyle,
    /** Page headers (UI-02). Large text, so the highlight color needs only 3:1. */
    val header: TextStyle,
    /** Running text, list items and cover names. */
    val body: TextStyle,
    /** Icon labels, captions and secondary details. */
    val label: TextStyle,
) {
    fun style(role: TextRole): TextStyle = when (role) {
        TextRole.Display -> display
        TextRole.Header -> header
        TextRole.Body -> body
        TextRole.Label -> label
    }
}

/**
 * The system font scale from which App Grid labels may take two lines
 * instead of one (FR-GRID-02, NFR-A11Y-04).
 */
const val LARGE_FONT_SCALE = 1.3f

/** The four sizes of UI-04. */
enum class TextRole { Display, Header, Body, Label }

/** The two emphasis levels of UI-04, drawn in `textPrimary` and `textSecondary`. */
enum class Emphasis { Primary, Secondary }

val DefaultType = LauncherType(
    display = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Light, fontSize = 64.sp, lineHeight = 72.sp),
    header = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Light, fontSize = 32.sp, lineHeight = 40.sp),
    body = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 24.sp),
    label = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
)
