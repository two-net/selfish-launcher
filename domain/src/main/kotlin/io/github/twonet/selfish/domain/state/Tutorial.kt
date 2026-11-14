package io.github.twonet.selfish.domain.state

/**
 * One step of the first-run tutorial (FR-ONB-02): a way out of Home, taught
 * by doing it. The step is reached when its [target] layer opens, by the
 * gesture or by its screen-reader action (FR-GEST-09).
 */
enum class TutorialStep(val target: Layer) {
    /** Swipe up for the App Grid (FR-GRID-01). */
    Apps(Layer.AppGrid),

    /** Swipe sideways for Events (FR-EVT-01). */
    Events(Layer.Events),

    /** Swipe down for the Top Menu (FR-TOP-01). */
    TopMenu(Layer.TopMenu),
}

/**
 * The tutorial in progress (FR-ONB-02, DESIGN §4.1). It isn't a [Layer]: each
 * step opens the real view it teaches, and the tutorial waits over the
 * layers until the user is back on Home. Not saved, like the layer: a
 * process that dies mid-tutorial doesn't resume it, and Settings can replay
 * it (FR-ONB-03).
 */
data class Tutorial(
    val step: TutorialStep,
    /**
     * Whether the user has done this step's gesture. The next step shows once
     * Home is back; until then, the step's view says how to go back.
     */
    val reached: Boolean = false,
)
