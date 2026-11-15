package com.brewingbytes.gemmory.app

import androidx.compose.runtime.staticCompositionLocalOf
import com.brewingbytes.gemmory.policy.DayClock
import com.brewingbytes.gemmory.settings.AppSettings
import com.brewingbytes.gemmory.storage.FileStore
import com.brewingbytes.gemmory.telemetry.NonFatalReporter

/**
 * The shared app's services, built once per process by the platform shell (iOS eng D6: hand-built, no DI library).
 * Screens read it through [LocalAppGraph]; ViewModels get it through [graphViewModel]. Services move in from
 * `GemmoryApp` as the screens that use them move to shared UI (Stage 1c), and the Android shell reads them back from
 * here, so each one exists once.
 */
class AppGraph(val services: PlatformServices) {
    val reporter: NonFatalReporter get() = services.reporter

    val files: FileStore get() = services.files

    /**
     * The local date of drops (T36), Today's picture, `day_played` and the gallery's midnight reload: one clock, so
     * they all turn over together.
     */
    val dayClock = DayClock(services::nowMillis, services::timeZone)

    val settings = AppSettings(services.keyValue(AppSettings.FILE))
}

/**
 * The [AppGraph] of the running app. Provided at the root by each shell; a composable outside it fails at once instead
 * of running on a second graph.
 */
val LocalAppGraph = staticCompositionLocalOf<AppGraph> { error("No AppGraph: the platform shell provides LocalAppGraph at its root") }
