package io.github.twonet.selfish.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import io.github.twonet.selfish.domain.model.Haptics

/**
 * The one gate for every haptic the launcher plays (FR-GEST-08, DESIGN
 * §6.4). Inside it, `LocalHapticFeedback` plays through the platform's,
 * which calls `View.performHapticFeedback` and so already follows the
 * system's touch-feedback setting, unless the launcher's own Haptic feedback
 * [setting] is Off; then nothing plays.
 *
 * Every haptic goes through `LocalHapticFeedback` (`LayerRulesTest`): the
 * long-press ticks (FR-GEST-07), the pulley menu's ticks (FR-GEST-05), and
 * the ones Compose plays itself, such as a `combinedClickable`'s long-press
 * and a text field's handles, so the setting reaches those too.
 */
@Composable
fun LauncherHaptics(setting: Haptics, content: @Composable () -> Unit) {
    val system = LocalHapticFeedback.current
    val haptics = remember(system, setting) { if (setting == Haptics.Off) Silent else system }
    CompositionLocalProvider(LocalHapticFeedback provides haptics, content = content)
}

/** Haptics with Haptic feedback Off. */
private object Silent : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}
