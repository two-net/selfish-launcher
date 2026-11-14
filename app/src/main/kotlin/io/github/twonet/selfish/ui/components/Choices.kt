package io.github.twonet.selfish.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import io.github.twonet.selfish.ui.tokens.Emphasis
import io.github.twonet.selfish.ui.tokens.LauncherTheme
import io.github.twonet.selfish.ui.tokens.PRESS_GLOW_ALPHA
import io.github.twonet.selfish.ui.tokens.Shapes
import io.github.twonet.selfish.ui.tokens.Sizes
import io.github.twonet.selfish.ui.tokens.Spacing
import io.github.twonet.selfish.ui.tokens.TextRole

// The controls the Top Menu and the Settings page share (SRS §4.7, §4.13):
// labeled parts, a row of options, a switch, and a row that opens or does
// something. Every one is drawn on the page's backdrop, as Sailfish OS
// draws its controls, with the chosen or active state in the highlight
// color (FR-AMB-04).

/**
 * A labeled part of a menu or page. The label is a heading, so screen
 * readers can jump between parts.
 */
@Composable
fun Section(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        LauncherText(label, role = TextRole.Label, emphasis = Emphasis.Secondary, modifier = Modifier.semantics { heading() })
        content()
    }
}

/** One option of a [ChoiceRow]: what choosing it sets, and its [label]. */
class Choice<T>(val value: T, val label: String)

/**
 * A few options side by side, such as System, Light and Dark (FR-TOP-02,
 * FR-SET-01), as radio buttons. The [selected] one is outlined in the
 * highlight color and read as selected; tapping another calls [onSelect]. A
 * label can wrap to a second line at large font sizes (NFR-A11Y-04).
 */
@Composable
fun <T> ChoiceRow(choices: List<Choice<T>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        for (choice in choices) {
            val chosen = choice.value == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(Shapes.Glass)
                    .selectedOutline(chosen, LauncherTheme.highlight)
                    .pressable(role = Role.RadioButton) { onSelect(choice.value) }
                    .semantics { this.selected = chosen },
                contentAlignment = Alignment.Center,
            ) {
                LauncherText(
                    text = choice.label,
                    emphasis = if (chosen) Emphasis.Primary else Emphasis.Secondary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier.padding(Spacing.Small),
                )
            }
        }
    }
}

/**
 * A setting that is on or off, such as "Daily Bing wallpaper" (SRS §4.13),
 * in the manner of Sailfish OS's text switches: its [label], an optional
 * [description] beneath it, and an indicator at the end that glows in the
 * highlight color while [checked] (FR-AMB-04). The whole row is the switch,
 * read as one with its state; tapping it calls [onCheckedChange] with the
 * other state.
 */
@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(Shapes.Glass)
            .pressable(role = Role.Switch) { onCheckedChange(!checked) }
            .semantics { toggleableState = ToggleableState(checked) }
            .padding(horizontal = Spacing.Small, vertical = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Column(Modifier.weight(1f)) {
            LauncherText(label)
            if (description != null) LauncherText(description, role = TextRole.Label, emphasis = Emphasis.Secondary)
        }
        SwitchIndicator(checked)
    }
}

/**
 * A switch's state: a ring while off, and a dot in the highlight color with
 * a soft glow around it while on, fading between the two. Decorative: the
 * row reads the state.
 */
@Composable
private fun SwitchIndicator(checked: Boolean) {
    val motion = LauncherTheme.motion
    val on by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = if (checked) motion.fadeIn() else motion.fadeOut(),
        label = "switch",
    )
    val highlight = LauncherTheme.highlight
    val ring = LauncherTheme.colors.textSecondary
    Box(
        Modifier
            .size(Sizes.SwitchIndicator)
            .drawBehind {
                val radius = size.minDimension / 2f
                val stroke = Sizes.SwitchRing.toPx()
                drawCircle(ring, radius = radius - stroke / 2f, style = Stroke(stroke), alpha = 1f - on)
                // The same soft glow as a press (FR-ANIM-04), kept within the row's padding.
                val glow = radius * GLOW_REACH
                drawCircle(
                    brush = Brush.radialGradient(listOf(highlight.copy(alpha = PRESS_GLOW_ALPHA), Color.Transparent), center, glow),
                    radius = glow,
                    alpha = on,
                )
                drawCircle(highlight, radius = radius, alpha = on)
            },
    )
}

/** How far a switch's glow reaches beyond its dot, as a multiple of the dot's radius. */
private const val GLOW_REACH = 1.5f

/**
 * A full-width row that opens or does something, such as a Top Menu
 * shortcut (FR-TOP-04) or "Reset layout" (FR-SET-16), with an optional
 * [description] beneath its [label].
 */
@Composable
fun ActionRow(label: String, modifier: Modifier = Modifier, description: String? = null, onClick: () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(Shapes.Glass)
            .pressable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.Small, vertical = Spacing.Medium),
    ) {
        LauncherText(label)
        if (description != null) LauncherText(description, role = TextRole.Label, emphasis = Emphasis.Secondary)
    }
}

/** The outline of a chosen option, in the [highlight] color (FR-AMB-04), drawn over its content. */
fun Modifier.selectedOutline(selected: Boolean, highlight: Color): Modifier =
    if (selected) border(Sizes.SelectedOutline, highlight, Shapes.Glass) else this

/**
 * Takes every touch that reaches it, so nothing beneath can be pressed. A
 * page such as Wallpaper details or Settings puts it under its content.
 */
fun Modifier.takeEveryTouch(): Modifier = pointerInput(Unit) {
    awaitEachGesture { awaitFirstDown().consume() }
}
