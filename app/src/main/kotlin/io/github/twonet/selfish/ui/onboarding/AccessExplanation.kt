package io.github.twonet.selfish.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.twonet.selfish.R
import io.github.twonet.selfish.domain.state.AccessFeature
import io.github.twonet.selfish.ui.components.LauncherText
import io.github.twonet.selfish.ui.components.PageHeader
import io.github.twonet.selfish.ui.components.TextButton
import io.github.twonet.selfish.ui.tokens.Emphasis
import io.github.twonet.selfish.ui.tokens.Spacing

/**
 * The one-screen explanation shown before the launcher asks for special
 * access (FR-ONB-04, NFR-PRIV-04): what [feature] does, the access it needs
 * and what is done with what it gives, and how to undo it. "Continue" opens
 * the system's screen for that access; "Not now", like Back, leaves the
 * feature off. Settings draws it in its own frame, like About, when a switch
 * turns on a feature whose access isn't granted.
 *
 * Notification dots (FR-GRID-11) and recently used covers (FR-HOME-10) add
 * their own words here when they are built.
 */
@Composable
internal fun AccessExplanation(feature: AccessFeature, onContinue: () -> Unit, onNotNow: () -> Unit, modifier: Modifier = Modifier) {
    val words = Words.of(feature)
    Column(
        modifier = modifier
            .fillMaxSize()
            // Outside the scroll, so nothing scrolls under the status bar or a cutout (UI-07).
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
            .padding(start = Spacing.Gutter, end = Spacing.Gutter, bottom = Spacing.Gutter)
            .testTag(AccessTags.PAGE),
        verticalArrangement = Arrangement.spacedBy(Spacing.Large),
    ) {
        PageHeader(stringResource(words.title), Modifier.testTag(AccessTags.HEADER))
        LauncherText(stringResource(words.body))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            for (line in words.details) LauncherText(stringResource(line), emphasis = Emphasis.Secondary)
        }
        Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            TextButton(stringResource(R.string.access_not_now), Modifier.testTag(AccessTags.NOT_NOW), onNotNow)
            TextButton(stringResource(R.string.access_continue), Modifier.testTag(AccessTags.CONTINUE), onContinue)
        }
    }
}

/** The words of one feature's explanation: its name, what it does and needs, and the rest. */
private class Words(val title: Int, val body: Int, val details: List<Int>) {
    companion object {
        fun of(feature: AccessFeature): Words = when (feature) {
            AccessFeature.NotificationsInEvents -> Words(
                title = R.string.settings_notifications_in_events,
                body = R.string.access_notifications_body,
                details = listOf(R.string.access_notifications_privacy, R.string.access_notifications_revoke),
            )
        }
    }
}

/** Test tags for [AccessExplanation]'s instrumented tests. */
internal object AccessTags {
    const val PAGE = "access-explanation"
    const val HEADER = "access-explanation-header"
    const val CONTINUE = "access-continue"
    const val NOT_NOW = "access-not-now"
}
