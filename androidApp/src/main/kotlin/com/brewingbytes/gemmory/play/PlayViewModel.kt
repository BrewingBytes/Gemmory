package com.brewingbytes.gemmory.play

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brewingbytes.gemmory.GemmoryApp
import com.brewingbytes.gemmory.policy.Item
import com.brewingbytes.gemmory.share.ProgressSharing
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent
import com.brewingbytes.gemmory.telemetry.CanvasEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface PlayUi {
    data object Loading : PlayUi

    data object Damaged : PlayUi

    class Ready(val session: PlaySession) : PlayUi
}

/** Holds the open canvas across rotation and fold changes. At most one session at a time. */
class PlayViewModel(application: Application) : AndroidViewModel(application) {
    private val store = (application as GemmoryApp).store
    private val app = application as GemmoryApp
    private var ref: CanvasRef? = null
    private var opening: Job? = null

    var ui by mutableStateOf<PlayUi>(PlayUi.Loading)
        private set

    /** Share progress (#36); kept here so a rotation keeps its sheet. */
    val sharing = ProgressSharing(application as GemmoryApp)

    /** Share progress tap: the time-lapse of [session]'s log so far. */
    fun shareProgress(session: PlaySession) {
        // An unfinished mystery (T43, D23) is shared only from its completion screen.
        if (session.hiddenAs != null) return
        sharing.share(session.ref, session.pattern, session.log(), session.title)
    }

    /** First-time hints (design D12); kept here so a rotation doesn't lose the one showing. */
    val hints = CoachHints(application)

    /**
     * Opens [ref] unless it's already open (a recreated screen after rotation calls this again). [entry]: where it was
     * opened from, for the canvas events and `day_played` (T36).
     */
    fun open(ref: CanvasRef, entry: CanvasEntry = CanvasEntry.GALLERY) {
        if (ref == this.ref) return
        close()
        this.ref = ref
        opening = viewModelScope.launch {
            val opened = store.open(ref, entry)
            ui = when (opened) {
                is CanvasStore.Opened.Ready -> PlayUi.Ready(store.start(opened))
                is CanvasStore.Opened.Damaged -> PlayUi.Damaged
            }
            val session = (ui as? PlayUi.Ready)?.session ?: return@launch
            // A fill spent on this canvas (#38) is written to the inventory even if the canvas closes right after.
            session.spendFill = { useId, done ->
                app.appScope.launch {
                    if (app.monetization.spend(Item.FILLS, useId)) app.analytics.log(AnalyticsEvent.inventorySpent(Item.FILLS))
                    withContext(Dispatchers.Main) { done() }
                }
            }
            // Today's picture (#34): decided now, at open, so a first gem placed after midnight still unlocks it for
            // good (a free player) and tells Analytics.
            val firstGem = if (session.placedCount == 0) app.todays.opened(ref, app.monetization.entitlements.value.vip) else null
            if (firstGem != null) {
                launch {
                    snapshotFlow { session.placedCount > 0 }.first { it }
                    firstGem.placed(session.pattern.width, session.pattern.height)
                }
            }
            // `day_played` (T36, eng D13): the day's first gem on any canvas, once a day, also across midnight.
            launch {
                var before = session.placedCount
                snapshotFlow { session.placedCount }.collect { placed ->
                    if (placed > before && app.dayPlayed.gemPlaced()) app.analytics.log(AnalyticsEvent.dayPlayed(entry))
                    before = placed
                }
            }
            // The daily play streak (#33) counts a day when a picture is finished here; reopening a finished one isn't.
            snapshotFlow { session.placedCount == session.cellCount }.drop(1).filter { it }.collect { app.streak.played(CanvasStore.reminderKey(ref)) }
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
