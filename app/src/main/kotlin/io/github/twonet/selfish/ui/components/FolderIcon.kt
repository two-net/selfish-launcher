package io.github.twonet.selfish.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.twonet.selfish.domain.model.AppEntry
import io.github.twonet.selfish.ui.tokens.Shapes
import io.github.twonet.selfish.ui.tokens.Sizes
import io.github.twonet.selfish.ui.tokens.Spacing

/**
 * A folder's icon: a glass square the size of an app icon (UI-05) that
 * previews the first four of [apps], two by two (FR-GRID-02, FR-FOLD-02).
 * Decorative, like [AppIcon]: the item it belongs to names the folder.
 */
@Composable
fun FolderIcon(apps: List<AppEntry>, modifier: Modifier = Modifier) {
    Glass(modifier = modifier.size(Sizes.Icon), shape = Shapes.Icon, contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Tiny)) {
            apps.take(PREVIEWED).chunked(PER_ROW).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Tiny)) {
                    row.forEach { AppIcon(it, Modifier.size(Sizes.FolderPreviewIcon)) }
                }
            }
        }
    }
}

private const val PREVIEWED = 4
private const val PER_ROW = 2
