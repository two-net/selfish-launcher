package io.github.twonet.selfish.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** The Activity this context belongs to, if any, for window-level effects (DESIGN §3.1). */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
