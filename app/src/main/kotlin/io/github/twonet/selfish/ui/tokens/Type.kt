package io.github.twonet.selfish.ui.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/** Text styles in the system font, sized in `sp` (UI-04). #4 adds the full scale. */
@Immutable
class LauncherType(
    val body: TextStyle,
)

val DefaultType = LauncherType(
    body = TextStyle(fontFamily = FontFamily.Default, fontSize = 18.sp),
)
