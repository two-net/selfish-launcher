package io.github.twonet.selfish.adapters

import android.app.ActivityOptions
import android.content.Context

/**
 * Options that open an app's window with no window animation, so a launch
 * under reduced motion is instant (FR-ANIM-15). A custom animation with no
 * enter or exit animation means none at all.
 */
internal fun withoutAnimation(context: Context): ActivityOptions = ActivityOptions.makeCustomAnimation(context, 0, 0)
