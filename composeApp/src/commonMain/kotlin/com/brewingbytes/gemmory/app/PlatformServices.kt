package com.brewingbytes.gemmory.app

import com.brewingbytes.gemmory.storage.FileStore
import com.brewingbytes.gemmory.storage.KeyValueStore
import com.brewingbytes.gemmory.telemetry.NonFatalReporter
import kotlinx.datetime.TimeZone

/**
 * What each platform shell hands the shared app (iOS eng D6): the storage seams, the crash reporter and the clock.
 * Android's is built by `GemmoryApp` over SharedPreferences, `filesDir` and Crashlytics; iOS's by
 * `MainViewController(services)` in Stage 2. A service shared code needs is added here as a constructor-checked member,
 * so a shell that doesn't provide it fails to compile rather than at first use.
 */
interface PlatformServices {
    /** The key-value file [name] of [com.brewingbytes.gemmory.storage.StorageLayout.Prefs], opened once per name. */
    fun keyValue(name: String): KeyValueStore

    /** The app's files folder. */
    val files: FileStore

    val reporter: NonFatalReporter

    /** Wall time in epoch milliseconds (connected tests move it on Android). */
    fun nowMillis(): Long

    /** The phone's time zone now, read at each use so a zone change follows at once. */
    fun timeZone(): TimeZone = TimeZone.currentSystemDefault()
}
