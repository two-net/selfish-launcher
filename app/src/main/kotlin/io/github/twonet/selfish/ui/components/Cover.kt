package io.github.twonet.selfish.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.ui.tokens.Sizes
import io.github.twonet.selfish.ui.tokens.Spacing
import io.github.twonet.selfish.ui.tokens.TextRole

/**
 * A Home cover (FR-HOME-03): a portrait card of translucent glass with
 * [app]'s icon and name. The caller makes it [pressable], which also merges
 * it into one element that screen readers read as the app's name
 * (NFR-A11Y-01).
 */
@Composable
fun Cover(app: AppEntry, modifier: Modifier = Modifier) {
    Glass(
        modifier = modifier.fillMaxWidth().aspectRatio(Sizes.CoverAspectRatio),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.Small),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            AppIcon(app)
            LauncherText(text = app.label, role = TextRole.Body, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}
