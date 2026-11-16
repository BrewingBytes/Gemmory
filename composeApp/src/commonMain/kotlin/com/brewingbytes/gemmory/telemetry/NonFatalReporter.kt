package com.brewingbytes.gemmory.telemetry

/**
 * Hook for errors the app recovers from but wants to hear about (design D17, #12). Release builds send them to
 * Crashlytics, debug builds and unit tests log them. Reports carry kinds, codes and reasons only: never photo data,
 * titles, user ids or paths to user content (AGENTS.md); `CrashlyticsReporter` also scrubs paths and URIs. Common since
 * iOS Stage 1c (T64), so shared code reports through the [com.brewingbytes.gemmory.app.AppGraph].
 */
fun interface NonFatalReporter {
    fun report(error: Throwable)

    /** A custom key that rides along with later reports and crashes, such as the last encode's wall time. */
    fun setKey(key: String, value: String) = Unit

    companion object {
        /** The platform log: Logcat on Android, the unified log on iOS. */
        val Logcat: NonFatalReporter = object : NonFatalReporter {
            override fun report(error: Throwable) = logNonFatal(error)

            override fun setKey(key: String, value: String) = logKey(key, value)
        }

        /**
         * Release builds send to Crashlytics; debug builds only with `-Pgemmory.firebaseInDebug=true` (the forced-crash
         * check), and otherwise log.
         */
        fun forBuild(sendsToFirebase: Boolean, crashlytics: () -> NonFatalReporter): NonFatalReporter =
            if (sendsToFirebase) crashlytics() else Logcat
    }
}

/** Writes [error] to the platform log as a warning ([NonFatalReporter.Logcat]). */
internal expect fun logNonFatal(error: Throwable)

/** Writes a custom key to the platform log ([NonFatalReporter.Logcat]). */
internal expect fun logKey(key: String, value: String)
