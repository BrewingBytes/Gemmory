package com.brewingbytes.gemmory

import android.app.Application
import com.brewingbytes.gemmory.play.CanvasStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class GemmoryApp : Application() {
    /** App-scoped, so a canvas's last write finishes even after its screen and ViewModel are gone. */
    lateinit var store: CanvasStore
        private set

    override fun onCreate() {
        super.onCreate()
        store = CanvasStore(
            filesDir = filesDir,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            writer = Dispatchers.IO.limitedParallelism(1),
        )
    }
}
