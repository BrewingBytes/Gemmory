package com.brewingbytes.gemmory.play

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.storage.CanvasRef
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed interface PlayUi {
    data object Loading : PlayUi

    data object Damaged : PlayUi

    class Ready(val session: PlaySession) : PlayUi
}

/** Holds the open canvas across rotation and fold changes. At most one session at a time. */
class PlayViewModel(application: Application) : AndroidViewModel(application) {
    private val store = (application as GemmoryApp).store
    private var ref: CanvasRef? = null
    private var opening: Job? = null

    var ui by mutableStateOf<PlayUi>(PlayUi.Loading)
        private set

    /** Opens [ref] unless it's already open (a recreated screen after rotation calls this again). */
    fun open(ref: CanvasRef) {
        if (ref == this.ref) return
        close()
        this.ref = ref
        opening = viewModelScope.launch {
            ui = when (val opened = store.open(ref)) {
                is CanvasStore.Opened.Ready -> PlayUi.Ready(store.start(opened))
                is CanvasStore.Opened.Damaged -> PlayUi.Damaged
            }
        }
    }

    fun onStart() {
        (ui as? PlayUi.Ready)?.session?.onStart()
    }

    fun onStop() {
        (ui as? PlayUi.Ready)?.session?.onStop()
    }

    fun close() {
        opening?.cancel()
        opening = null
        (ui as? PlayUi.Ready)?.session?.close()
        ui = PlayUi.Loading
        ref = null
    }

    override fun onCleared() = close()
}
