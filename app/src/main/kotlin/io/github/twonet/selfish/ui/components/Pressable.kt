package io.github.twonet.selfish.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import io.github.twonet.selfish.ui.tokens.Sizes

/**
 * Makes an element respond to taps and long-presses. It is the only way the
 * launcher handles them (`LayerRulesTest`), so every touch target is at least
 * [Sizes.MinTouchTarget] on each side (UI-06), and the pressed glow
 * (FR-ANIM-04, #13) reaches every element through `LocalIndication`.
 */
fun Modifier.pressable(
    onClickLabel: String? = null,
    role: Role? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = this
    .sizeIn(minWidth = Sizes.MinTouchTarget, minHeight = Sizes.MinTouchTarget)
    .combinedClickable(
        onClickLabel = onClickLabel,
        role = role,
        onLongClickLabel = onLongClickLabel,
        onLongClick = onLongClick,
        onClick = onClick,
    )
