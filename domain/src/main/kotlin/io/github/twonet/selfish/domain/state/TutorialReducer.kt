package io.github.twonet.selfish.domain.state

// The tutorial's transitions (SRS §4.14, DESIGN §4.1). It starts on a first
// run together with the default-Home offer (`welcome` in Reducer.kt), and
// every step is done with the real gesture on the real launcher: the step is
// reached when its view opens, and the next one shows once Home is back.

/** [state] with the tutorial at its first step (FR-ONB-02). */
internal fun startTutorial(state: LauncherState): LauncherState = state.copy(tutorial = Tutorial(TutorialStep.entries.first()))

/**
 * The tutorial's rules, which hold after every action, so every way into a
 * view and every way back counts (FR-ONB-02):
 * - the step's view opening, by its gesture or its screen-reader action
 *   (FR-GEST-09), reaches the step;
 * - Home coming back after that, by a swipe, Back, Home or a return from an
 *   app, moves on to the next step, or ends the tutorial after the last.
 *
 * Returns [state] itself when nothing changes.
 */
internal fun keepTutorial(state: LauncherState): LauncherState {
    val tutorial = state.tutorial ?: return state
    return when {
        !tutorial.reached && state.layer == tutorial.step.target -> state.copy(tutorial = tutorial.copy(reached = true))
        tutorial.reached && state.layer == Layer.Home -> {
            val next = TutorialStep.entries.getOrNull(tutorial.step.ordinal + 1)
            state.copy(tutorial = next?.let(::Tutorial))
        }
        else -> state
    }
}

/** "Skip" ends the tutorial wherever it is (FR-ONB-02). */
internal fun skipTutorial(state: LauncherState): Next =
    if (state.tutorial == null) Next(state) else Next(state.copy(tutorial = null))

/**
 * "Replay tutorial" (FR-ONB-03): whatever is open closes, Settings included,
 * and the tutorial starts again on Home.
 */
internal fun replayTutorial(state: LauncherState): Next {
    val replayed = startTutorial(state.copy(layer = Layer.Home, overlays = emptyList()))
    return if (replayed == state) Next(state) else Next(replayed)
}
