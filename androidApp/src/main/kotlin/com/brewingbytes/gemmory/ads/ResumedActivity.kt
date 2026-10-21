package com.brewingbytes.gemmory.ads

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * The Activity on screen, for interstitial loads (T34: Google's Unity guide wants an Activity context). Held weakly
 * and dropped on pause and destroy, so no Activity outlives its screen. [onResumed] runs on every resume.
 */
class ResumedActivity : Application.ActivityLifecycleCallbacks {
    private var ref: WeakReference<Activity>? = null
    var onResumed: (() -> Unit)? = null

    fun current(): Activity? = ref?.get()?.takeUnless { it.isFinishing || it.isDestroyed }

    override fun onActivityResumed(activity: Activity) {
        ref = WeakReference(activity)
        onResumed?.invoke()
    }

    override fun onActivityPaused(activity: Activity) = clear(activity)

    override fun onActivityDestroyed(activity: Activity) = clear(activity)

    private fun clear(activity: Activity) {
        if (ref?.get() === activity) ref = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
