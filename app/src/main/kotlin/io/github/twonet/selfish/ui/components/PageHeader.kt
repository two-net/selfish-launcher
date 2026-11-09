package io.github.twonet.selfish.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import io.github.twonet.selfish.ui.tokens.LauncherTheme
import io.github.twonet.selfish.ui.tokens.Spacing

/**
 * The header of a page such as Events, Settings, Wallpaper details or a folder
 * (UI-02): large, aligned to the trailing edge (right in left-to-right
 * languages, mirrored in right-to-left ones) and drawn in the highlight color.
 * Screen readers announce it as a heading, with [actions], such as its
 * page's pulley menu items (DESIGN §6.7).
 */
@Composable
fun PageHeader(title: String, modifier: Modifier = Modifier, actions: List<CustomAccessibilityAction> = emptyList()) {
    BasicText(
        text = title,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                heading()
                if (actions.isNotEmpty()) customActions = actions
            }
            .padding(horizontal = Spacing.Gutter, vertical = Spacing.Large),
        style = LauncherTheme.type.header.copy(color = LauncherTheme.highlight, textAlign = TextAlign.End),
    )
}

/**
 * A [PageHeader] the user can edit, such as a folder's name (FR-FOLD-04).
 * Tapping it, labeled [editLabel] for screen readers, asks to start
 * [editing]; while editing, it is a one-line field in the same style, with
 * all of [title] selected and the keyboard shown.
 *
 * What was typed is kept, through [onRename], when the keyboard's Done key
 * is pressed, when the field loses focus and when it leaves the screen, for
 * example as its page closes. Editing is transient UI state (AR-02), so it
 * ends with [onEditingChange] `false`.
 */
@Composable
fun EditablePageHeader(
    title: String,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onRename: (String) -> Unit,
    editLabel: String,
    modifier: Modifier = Modifier,
    actions: List<CustomAccessibilityAction> = emptyList(),
) {
    if (!editing) {
        PageHeader(
            title = title,
            modifier = modifier.pressable(onClickLabel = editLabel) { onEditingChange(true) },
            actions = actions,
        )
        return
    }
    var text by remember { mutableStateOf(TextFieldValue(title, TextRange(0, title.length))) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    val currentRename by rememberUpdatedState(onRename)
    val currentEditingChange by rememberUpdatedState(onEditingChange)
    // Keeps what was typed once, however editing ends.
    var done by remember { mutableStateOf(false) }
    val finish = {
        if (!done) {
            done = true
            currentRename(text.text)
            currentEditingChange(false)
        }
    }
    LaunchedEffect(focus) {
        focus.requestFocus()
        keyboard?.show()
    }
    DisposableEffect(Unit) { onDispose { if (!done) currentRename(text.text) } }
    val highlight = LauncherTheme.highlight
    BasicTextField(
        value = text,
        onValueChange = { text = it },
        modifier = modifier
            .fillMaxWidth()
            .semantics { heading() }
            .padding(horizontal = Spacing.Gutter, vertical = Spacing.Large)
            .focusRequester(focus)
            .onFocusChanged {
                if (focused && !it.isFocused) finish()
                focused = it.isFocused
            },
        textStyle = LauncherTheme.type.header.copy(color = highlight, textAlign = TextAlign.End),
        singleLine = true,
        cursorBrush = SolidColor(highlight),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            keyboard?.hide()
            finish()
        }),
    )
}
