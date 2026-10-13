package com.brewingbytes.gemmory.telemetry

import android.util.Log

/**
 * Hook for errors the app recovers from but wants to hear about. Crashlytics plugs in here (#12). Reports carry
 * ids and reasons only, never photo data (AGENTS.md).
 */
fun interface NonFatalReporter {
    fun report(error: Throwable)

    companion object {
        /** Until Crashlytics lands: logcat only. */
        val Logcat = NonFatalReporter { Log.w("Gemmory", "non-fatal", it) }
    }
}
