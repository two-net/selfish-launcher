package io.github.twonet.selfish.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.twonet.selfish.R
import io.github.twonet.selfish.ui.components.Glass
import io.github.twonet.selfish.ui.components.LauncherText
import io.github.twonet.selfish.ui.tokens.Emphasis
import io.github.twonet.selfish.ui.tokens.LauncherTheme
import io.github.twonet.selfish.ui.tokens.Sizes
import io.github.twonet.selfish.ui.tokens.Spacing

/**
 * The App Grid's search field, above its first row (FR-GRID-03). Tapping it
 * shows the keyboard (FR-SRCH-01). Each change of its text goes to
 * [onQuery], which filters the grid, and the keyboard's action key calls
 * [onGo] with the text, to launch the first result (FR-SRCH-03).
 *
 * The store holds the query (AR-02). The field keeps its own editing buffer,
 * with the cursor and the keyboard's composing text, as transient UI state
 * (DESIGN §3.3). Fed back from the store, it could lose keystrokes typed
 * before the store's state arrived. It starts from [query], so it shows what
 * the store has even if the Activity is created again.
 *
 * While the App Grid isn't [open], the field is empty and has no keyboard
 * (FR-SRCH-05). When the launcher stops, for example because an app was
 * launched from the results, the field gives up its focus, so the results
 * come back without the keyboard. That is watched on the lifecycle itself:
 * the store's state isn't collected while the launcher is stopped, so its
 * `homeShown` never reaches the screen as false. Nothing the field does
 * reaches the network (FR-SRCH-06): autocorrection is off, and the query
 * goes only to the store.
 */
@Composable
fun SearchField(
    query: String,
    open: Boolean,
    onQuery: (String) -> Unit,
    onGo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = rememberTextFieldState(initialText = query)
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    val currentQuery by rememberUpdatedState(onQuery)
    val currentGo by rememberUpdatedState(onGo)

    LaunchedEffect(text, open) {
        if (open) {
            // Starts with the text as it is now, in case it was typed while
            // the grid was still settling.
            snapshotFlow { text.text.toString() }.collect { currentQuery(it) }
        } else {
            text.clearText()
            if (focused) focusManager.clearFocus()
            keyboard?.hide()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { if (focused) focusManager.clearFocus() }

    val placeholder = stringResource(R.string.grid_search_placeholder)
    val empty = text.text.isEmpty()
    val colors = LauncherTheme.colors
    BasicTextField(
        state = text,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused },
        textStyle = LauncherTheme.type.body.copy(color = colors.textPrimary),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Go,
        ),
        onKeyboardAction = { currentGo(text.text.toString()) },
        lineLimits = TextFieldLineLimits.SingleLine,
        cursorBrush = SolidColor(LauncherTheme.highlight),
        decorator = { field ->
            Glass(
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = Sizes.MinTouchTarget),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(Modifier.padding(horizontal = Spacing.Gutter)) {
                    // Inside the field, so it is the empty field's label for
                    // screen readers too (NFR-A11Y-01).
                    if (empty) LauncherText(text = placeholder, emphasis = Emphasis.Secondary, maxLines = 1)
                    field()
                }
            }
        },
    )
}
