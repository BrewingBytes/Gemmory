package com.brewingbytes.gemmory.telemetry

import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Non-fatals and custom keys to Crashlytics (design D17: always on, under legitimate interest, no user ids). Every
 * report is scrubbed of paths and URIs first; no user id is ever set and no log lines are attached.
 */
class CrashlyticsReporter(private val crashlytics: FirebaseCrashlytics) : NonFatalReporter {
    override fun report(error: Throwable) = crashlytics.recordException(Redaction.sanitize(error))

    override fun setKey(key: String, value: String) = crashlytics.setCustomKey(key, value)

    companion object {
        /**
         * Crashes go through [Redaction] too: Crashlytics' own handler, installed when Firebase starts, gets the
         * scrubbed copy. Call once, after Firebase has started (Application.onCreate).
         */
        fun scrubCrashes() {
            val next = Thread.getDefaultUncaughtExceptionHandler() ?: return
            Thread.setDefaultUncaughtExceptionHandler { thread, error -> next.uncaughtException(thread, Redaction.sanitize(error)) }
        }
    }
}
