package com.brewingbytes.gemmory.telemetry

import android.util.Log

/**
 * Hook for errors the app recovers from but wants to hear about (design D17, #12). Release builds send them to
 * Crashlytics, debug builds and unit tests log them. Reports carry kinds, codes and reasons only: never photo data,
 * titles, user ids or paths to user content (AGENTS.md); [CrashlyticsReporter] also scrubs paths and URIs.
 */
fun interface NonFatalReporter {
    fun report(error: Throwable)

    /** A custom key that rides along with later reports and crashes, such as the last encode's wall time. */
    fun setKey(key: String, value: String) = Unit

    companion object {
        val Logcat = object : NonFatalReporter {
            override fun report(error: Throwable) {
                Log.w("Gemmory", "non-fatal", error)
            }

            override fun setKey(key: String, value: String) {
                Log.i("Gemmory", "key $key=$value")
            }
        }

        /**
         * Release builds send to Crashlytics; debug builds only with `-Pgemmory.firebaseInDebug=true` (the forced-crash
         * check), and otherwise log.
         */
        fun forBuild(sendsToFirebase: Boolean, crashlytics: () -> NonFatalReporter): NonFatalReporter =
            if (sendsToFirebase) crashlytics() else Logcat
    }
}
