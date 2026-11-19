package com.brewingbytes.gemmory.app

import androidx.compose.ui.graphics.ImageBitmap
import com.brewingbytes.gemmory.ads.PlayerAccess
import com.brewingbytes.gemmory.bundled.TodaysPicks
import com.brewingbytes.gemmory.collections.CollectionBook
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.copy.Words
import com.brewingbytes.gemmory.photo.FaceFinder
import com.brewingbytes.gemmory.photo.PhotoDecoder
import com.brewingbytes.gemmory.play.Canvases
import com.brewingbytes.gemmory.remote.RemoteShelf
import com.brewingbytes.gemmory.render.GemRenderer
import com.brewingbytes.gemmory.share.ProgressShareSession
import com.brewingbytes.gemmory.storage.FileStore
import com.brewingbytes.gemmory.storage.KeyValueStore
import com.brewingbytes.gemmory.streak.StreakSource
import com.brewingbytes.gemmory.telemetry.Analytics
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

    /** The app's cache folder: never backed up, and the system may clear it (the photo flow's draft lives here). */
    val cache: FileStore

    val reporter: NonFatalReporter

    /** Wall time in epoch milliseconds (connected tests move it on Android). */
    fun nowMillis(): Long

    /** The phone's time zone now, read at each use so a zone change follows at once. */
    fun timeZone(): TimeZone = TimeZone.currentSystemDefault()

    /** The words of the language the app shows now (a language switch follows at the next read). */
    fun words(): Words

    val analytics: Analytics

    /*
     * Services that live in the Android shell until their implementation moves to common code (iOS Stage 1c, T64). Each
     * is a common interface, so shared screens and ViewModels already read them from the graph; Android hands over the
     * instance in use at each read (connected tests replace some), iOS builds its own in Stage 2.
     */

    val canvases: Canvases

    val remote: RemoteShelf

    val todays: TodaysPicks

    val collections: CollectionBook

    val streak: StreakSource

    val access: PlayerAccess

    val progress: PlayerProgress

    /** Decode, crop and JPEG for the photo flow. */
    val photos: PhotoDecoder

    /** The photo flow's face check. */
    val faces: FaceFinder

    /** A new Share progress session for one screen's ViewModel. */
    fun progressSharing(): ProgressShareSession

    /**
     * A pattern drawn as gems for a thumbnail: the shared [GemRenderer]. Android draws through its own entry point so
     * the renderer hotfix switch (R10-8) covers the gallery too.
     */
    fun renderGems(pattern: Pattern, cellPx: Int, placed: ((Int) -> Boolean)?): ImageBitmap = GemRenderer.render(pattern, cellPx, placed)
}
