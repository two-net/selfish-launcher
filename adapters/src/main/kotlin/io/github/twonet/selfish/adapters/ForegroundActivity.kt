package io.github.twonet.selfish.adapters

import android.app.Activity
import java.lang.ref.WeakReference

/**
 * Holds the launcher's visible Activity, for adapters that need one, such
 * as the default-Home prompt (DESIGN §5.1). The Activity fills it when it is
 * created and clears it when it is destroyed. A weak reference, so a missed
 * [detach] can't leak the Activity.
 *
 * Use it on the main thread.
 */
class ForegroundActivity {
    private var activity: WeakReference<Activity>? = null

    /** The Activity, if one exists and isn't finishing. */
    val current: Activity?
        get() = activity?.get()?.takeUnless { it.isFinishing || it.isDestroyed }

    fun attach(activity: Activity) {
        this.activity = WeakReference(activity)
    }

    /** Clears the holder, unless a newer Activity has attached since. */
    fun detach(activity: Activity) {
        if (this.activity?.get() === activity) this.activity = null
    }
}
