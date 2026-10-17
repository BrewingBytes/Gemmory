package com.brewingbytes.gemmory.play

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.share.ProgressSharing
import com.brewingbytes.gemmory.storage.CanvasRef
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

sealed interface PlayUi {
    data object Loading : PlayUi

    data object Damaged : PlayUi

    class Ready(val session: PlaySession) : PlayUi
}

/** Holds the open canvas across rotation and fold changes. At most one session at a time. */
class PlayViewModel(application: Application) : AndroidViewModel(application) {
    private val store = (application as GemmoryApp).store
    private val streak = (application as GemmoryApp).streak
    private var ref: CanvasRef? = null
    private var opening: Job? = null

    var ui by mutableStateOf<PlayUi>(PlayUi.Loading)
        private set

    /** Share progress (#36); kept here so a rotation keeps its sheet. */
    val sharing = ProgressSharing(application as GemmoryApp)

    /** Share progress tap: the time-lapse of [session]'s log so far. */
    fun shareProgress(session: PlaySession) = sharing.share(session.ref, session.pattern, session.log(), session.title)

    /** First-time hints (design D12); kept here so a rotation doesn't lose the one showing. */
    val hints = CoachHints(application)

    /** Opens [ref] unless it's already open (a recreated screen after rotation calls this again). */
    fun open(ref: CanvasRef) {
        if (ref == this.ref) return
        close()
        this.ref = ref
        opening = viewModelScope.launch {
            val opened = store.open(ref)
            ui = when (opened) {
                is CanvasStore.Opened.Ready -> PlayUi.Ready(store.start(opened))
                is CanvasStore.Opened.Damaged -> PlayUi.Damaged
            }
            // The daily play streak (#33) counts a day when a picture is finished here; reopening a finished one isn't.
            val session = (ui as? PlayUi.Ready)?.session ?: return@launch
            snapshotFlow { session.placedCount == session.cellCount }.drop(1).filter { it }.collect { streak.played() }
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
        sharing.clear()
        hints.reset()
    }

    override fun onCleared() = close()
}
