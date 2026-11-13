package io.github.twonet.selfish.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import io.github.twonet.selfish.ui.tokens.Spacing
import io.github.twonet.selfish.ui.tokens.TextRole

/**
 * A short action in a card, such as "Learn more", as text, at least the
 * minimum touch size (UI-06). Used by Events and Wallpaper details.
 */
@Composable
internal fun TextButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.pressable(role = Role.Button, onClick = onClick).padding(horizontal = Spacing.Small),
        contentAlignment = Alignment.CenterStart,
    ) {
        LauncherText(label, role = TextRole.Label, maxLines = 1)
    }
}
