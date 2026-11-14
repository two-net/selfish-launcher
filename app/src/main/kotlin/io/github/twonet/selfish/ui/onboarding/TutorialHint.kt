package io.github.twonet.selfish.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import io.github.twonet.selfish.R
import io.github.twonet.selfish.domain.state.Tutorial
import io.github.twonet.selfish.domain.state.TutorialStep
import io.github.twonet.selfish.ui.components.GestureHint
import io.github.twonet.selfish.ui.components.Glass
import io.github.twonet.selfish.ui.components.LauncherText
import io.github.twonet.selfish.ui.components.TextButton
import io.github.twonet.selfish.ui.tokens.Emphasis
import io.github.twonet.selfish.ui.tokens.LauncherTheme
import io.github.twonet.selfish.ui.tokens.Shapes
import io.github.twonet.selfish.ui.tokens.Sizes
import io.github.twonet.selfish.ui.tokens.Spacing
import io.github.twonet.selfish.ui.tokens.TextRole

/**
 * The first-run tutorial's card (FR-ONB-02, DESIGN §6.4), drawn over every
 * view at the bottom of the screen, above the remorse banner. [shown] is
 * `LauncherState.tutorialShown`: on Home, the gesture to do next, and on the
 * view it opened, how to go back. Each comes with a glowing touch point
 * tracing that gesture (FR-ANIM-13), and "Skip tutorial" ends it.
 *
 * Only "Skip tutorial" takes touches. Swipes that start on the card still
 * open the views, because the root watches every touch before the elements
 * under it (DESIGN §6.3), so the card never stands in the way of the gesture
 * it teaches. Screen-reader users hear each step as it comes, and do it with
 * Home's actions, which reach the step the same way (FR-GEST-09).
 */
@Composable
internal fun TutorialHint(shown: Tutorial?, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    val motion = LauncherTheme.motion
    AnimatedContent(
        targetState = shown,
        modifier = modifier.fillMaxWidth(),
        transitionSpec = { ContentTransform(fadeIn(motion.fadeIn()), fadeOut(motion.fadeOut()), sizeTransform = null) },
        contentAlignment = Alignment.BottomCenter,
        label = "tutorial",
    ) { tutorial ->
        if (tutorial != null) Card(tutorial, onSkip)
    }
}

@Composable
private fun Card(tutorial: Tutorial, onSkip: () -> Unit) {
    val gesture = Gesture.of(tutorial)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // Keyed, so the point traces each new gesture from its start.
        key(tutorial) {
            GestureHint(
                from = gesture.from,
                to = gesture.to,
                modifier = Modifier
                    .padding(bottom = Spacing.Small)
                    .size(width = gesture.width, height = gesture.height)
                    .testTag(TutorialTags.HINT),
            )
        }
        Glass(
            Modifier
                .fillMaxWidth()
                // On the scrim, like the remorse banner, so what is beneath
                // doesn't show through the words.
                .background(LauncherTheme.colors.scrim, Shapes.Glass)
                .testTag(TutorialTags.CARD),
        ) {
            Column(Modifier.fillMaxWidth().padding(start = Spacing.Gutter, end = Spacing.Small, top = Spacing.Medium)) {
                LauncherText(
                    text = stringResource(textOf(tutorial)),
                    modifier = Modifier
                        .padding(end = Spacing.Small)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LauncherText(
                        text = stringResource(R.string.tutorial_progress, tutorial.step.ordinal + 1, TutorialStep.entries.size),
                        modifier = Modifier.weight(1f),
                        role = TextRole.Label,
                        emphasis = Emphasis.Secondary,
                    )
                    TextButton(stringResource(R.string.tutorial_skip), Modifier.testTag(TutorialTags.SKIP), onSkip)
                }
            }
        }
    }
}

/** What the card says: the step's gesture, or once it is done, how to go back. */
private fun textOf(tutorial: Tutorial): Int = when (tutorial.step) {
    TutorialStep.Apps -> if (tutorial.reached) R.string.tutorial_back_apps else R.string.tutorial_step_apps
    TutorialStep.Events -> if (tutorial.reached) R.string.tutorial_back_events else R.string.tutorial_step_events
    TutorialStep.TopMenu -> if (tutorial.reached) R.string.tutorial_back_top_menu else R.string.tutorial_step_top_menu
}

/** The gesture the touch point traces, in a box of [width] by [height], as fractions of it. */
private class Gesture(val from: Offset, val to: Offset, val width: Dp, val height: Dp) {
    companion object {
        private val up = Gesture(Offset(0.5f, 1f), Offset(0.5f, 0f), Sizes.HintTouchPoint, Sizes.HintTravel)
        private val down = Gesture(Offset(0.5f, 0f), Offset(0.5f, 1f), Sizes.HintTouchPoint, Sizes.HintTravel)
        private val left = Gesture(Offset(1f, 0.5f), Offset(0f, 0.5f), Sizes.HintTravel, Sizes.HintTouchPoint)
        private val right = Gesture(Offset(0f, 0.5f), Offset(1f, 0.5f), Sizes.HintTravel, Sizes.HintTouchPoint)

        /** The step's gesture on Home, or the one that goes back once it is done. */
        fun of(tutorial: Tutorial): Gesture = when (tutorial.step) {
            TutorialStep.Apps -> if (tutorial.reached) down else up
            // Either way works; going back, it returns the way it came.
            TutorialStep.Events -> if (tutorial.reached) right else left
            TutorialStep.TopMenu -> if (tutorial.reached) up else down
        }
    }
}

/** Test tags for [TutorialHint]'s instrumented tests. */
internal object TutorialTags {
    const val CARD = "tutorial-card"
    const val HINT = "tutorial-gesture-hint"
    const val SKIP = "tutorial-skip"
}
