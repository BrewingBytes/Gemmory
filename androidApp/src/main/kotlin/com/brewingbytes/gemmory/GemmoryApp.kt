package com.brewingbytes.gemmory

import android.app.Application
import com.brewingbytes.gemmory.play.CanvasStore
import com.brewingbytes.gemmory.settings.AppSettings
import com.brewingbytes.gemmory.timelapse.EncodeManager
import com.brewingbytes.gemmory.timelapse.MediaCodecEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class GemmoryApp : Application() {
    /** App-scoped, so a canvas's last write finishes even after its screen and ViewModel are gone. */
    lateinit var store: CanvasStore
        private set

    lateinit var settings: AppSettings
        private set

    /** App-scoped, so an encode keeps going while the process lives, whatever screen is showing (design D8). */
    lateinit var encodes: EncodeManager
        private set

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        encodes = EncodeManager(
            cacheDir = cacheDir,
            scope = scope,
            backend = { request, partialOf -> MediaCodecEncoder(this, request, partialOf) },
            canvasExists = { ref -> store.repository.dirOf(ref).isDirectory },
        )
        store = CanvasStore(
            filesDir = filesDir,
            scope = scope,
            writer = Dispatchers.IO.limitedParallelism(1),
            bundledGrid = { picture -> assets.open(picture.asset).use { it.readBytes() } },
            encodes = encodes,
        )
    }
}
