package com.brewingbytes.gemmory.app

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import com.brewingbytes.gemmory.ads.PlayerAccess
import com.brewingbytes.gemmory.bundled.TodaysPicks
import com.brewingbytes.gemmory.collections.CollectionBook
import com.brewingbytes.gemmory.completion.CompletionHooks
import com.brewingbytes.gemmory.completion.TimelapseShares
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.copy.Words
import com.brewingbytes.gemmory.photo.FaceFinder
import com.brewingbytes.gemmory.photo.PhotoDecoder
import com.brewingbytes.gemmory.play.CanvasStore
import com.brewingbytes.gemmory.play.Canvases
import com.brewingbytes.gemmory.play.PlayHooks
import com.brewingbytes.gemmory.policy.DayClock
import com.brewingbytes.gemmory.remote.RemoteShelf
import com.brewingbytes.gemmory.settings.AppSettings
import com.brewingbytes.gemmory.share.ProgressShareSession
import com.brewingbytes.gemmory.storage.FileStore
import com.brewingbytes.gemmory.streak.StreakSource
import com.brewingbytes.gemmory.telemetry.Analytics
import com.brewingbytes.gemmory.telemetry.NonFatalReporter
import kotlinx.coroutines.CoroutineScope

/**
 * The shared app's services, built once per process by the platform shell (iOS eng D6: hand-built, no DI library).
 * Screens read it through [LocalAppGraph]; ViewModels get it through [graphViewModel]. Services move in from
 * `GemmoryApp` as the screens that use them move to shared UI (Stage 1c), and the Android shell reads them back from
 * here, so each one exists once.
 */
class AppGraph(val services: PlatformServices) {
    val reporter: NonFatalReporter get() = services.reporter

    val files: FileStore get() = services.files

    val cache: FileStore get() = services.cache

    /**
     * The local date of drops (T36), Today's picture, `day_played` and the gallery's midnight reload: one clock, so
     * they all turn over together.
     */
    val dayClock = DayClock(services::nowMillis, services::timeZone)

    val settings = AppSettings(services.keyValue(AppSettings.FILE))

    /** The words of the language the app shows now. */
    val words: Words get() = services.words()

    val analytics: Analytics get() = services.analytics

    // The gallery's services (T64), read through on each use: see PlatformServices.
    val canvases: Canvases get() = services.canvases

    val store: CanvasStore get() = services.store

    val play: PlayHooks get() = services.play

    val appScope: CoroutineScope get() = services.appScope

    val remote: RemoteShelf get() = services.remote

    val todays: TodaysPicks get() = services.todays

    val collections: CollectionBook get() = services.collections

    val streak: StreakSource get() = services.streak

    val access: PlayerAccess get() = services.access

    val progress: PlayerProgress get() = services.progress

    val photos: PhotoDecoder get() = services.photos

    val faces: FaceFinder get() = services.faces

    val timelapses: TimelapseShares get() = services.timelapses

    val completion: CompletionHooks get() = services.completion

    fun progressSharing(): ProgressShareSession = services.progressSharing()

    /** A pattern drawn as gems for a thumbnail ([PlatformServices.renderGems]). */
    fun renderGems(pattern: Pattern, cellPx: Int, placed: ((Int) -> Boolean)?): ImageBitmap = services.renderGems(pattern, cellPx, placed)
}

/**
 * The [AppGraph] of the running app. Provided at the root by each shell; a composable outside it fails at once instead
 * of running on a second graph.
 */
val LocalAppGraph = staticCompositionLocalOf<AppGraph> { error("No AppGraph: the platform shell provides LocalAppGraph at its root") }
