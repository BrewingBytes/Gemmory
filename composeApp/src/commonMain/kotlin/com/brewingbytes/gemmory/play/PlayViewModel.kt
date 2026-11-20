package com.brewingbytes.gemmory.play

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brewingbytes.gemmory.app.AppGraph
import com.brewingbytes.gemmory.policy.Item
import com.brewingbytes.gemmory.share.ProgressShareSession
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

/**
 * Holds the open canvas across rotation and fold changes. At most one session at a time. Shared since iOS Stage 1c
 * (T64, eng D5): its services come from the [AppGraph] ([graph]); [handle] isn't read yet, since a canvas reopens
 * from its route after process death.
 */
class PlayViewModel(private val graph: AppGraph, @Suppress("UNUSED_PARAMETER") handle: SavedStateHandle) : ViewModel() {
    private val store: CanvasStore get() = graph.store
    private var ref: CanvasRef? = null
    private var opening: Job? = null

    var ui by mutableStateOf<PlayUi>(PlayUi.Loading)
        private set

    /** Share progress (#36); kept here so a rotation keeps its sheet. */
    val sharing: ProgressShareSession = graph.progressSharing()

    /** Share progress tap: the time-lapse of [session]'s log so far. */
    fun shareProgress(session: PlaySession) {
        // An unfinished mystery (T43, D23) is shared only from its completion screen.
        if (session.hiddenAs != null) return
        // The title in the language the app shows now: the frame draws it (T49).
        val title = store.titleNow(session.ref, session.title, null)
        sharing.share(session.ref, session.pattern, session.log(), title)
    }

    /** The title [session] shows now, in the app's language (T49) or its mystery name (T43). */
    fun titleOf(session: PlaySession): String = store.titleNow(session.ref, session.title, session.hiddenAs)

    /** First-time hints (design D12); kept here so a rotation doesn't lose the one showing. */
    val hints = CoachHints(graph.services.keyValue(CoachHints.FILE))

    /** A share link's "Shared with you: <title>" (T50) while it shows on this canvas; kept here across rotation. */
    var sharedToast by mutableStateOf<String?>(null)
        private set

    fun sharedToastShown() {
        sharedToast = null
    }

    /**
     * Opens [ref] unless it's already open (a recreated screen after rotation calls this again). [entry]: where it was
     * opened from, for the canvas events and `day_played` (T36).
     */
    fun open(ref: CanvasRef, entry: CanvasEntry = CanvasEntry.GALLERY) {
        if (ref == this.ref) return
        close()
        this.ref = ref
        sharedToast = graph.play.takeSharedToast(ref)
        opening = viewModelScope.launch {
            val opened = store.open(ref, entry)
            ui = when (opened) {
                is CanvasStore.Opened.Ready -> PlayUi.Ready(store.start(opened))
                is CanvasStore.Opened.Damaged -> PlayUi.Damaged
            }
            val session = (ui as? PlayUi.Ready)?.session ?: return@launch
            // A fill spent on this canvas (#38) is written to the inventory even if the canvas closes right after.
            session.spendFill = { useId, done ->
                graph.appScope.launch {
                    if (graph.access.spend(Item.FILLS, useId)) graph.analytics.log(AnalyticsEvent.inventorySpent(Item.FILLS))
                    withContext(Dispatchers.Main) { done() }
                }
            }
            // Today's picture (#34): decided now, at open, so a first gem placed after midnight still unlocks it for
            // good (a free player) and tells Analytics.
            val firstGem = if (session.placedCount == 0) graph.todays.opened(ref, graph.access.entitlements.value.vip) else null
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
                    if (placed > before) {
                        // `first_gem` (T46): a fresh install's first gem on any canvas, once, with its time since the first launch.
                        graph.play.firstGem(graph.dayClock.nowMillis())?.let { graph.analytics.log(AnalyticsEvent.firstGem(it, ref, entry)) }
                        if (graph.play.dayPlayed()) graph.analytics.log(AnalyticsEvent.dayPlayed(entry))
                    }
                    before = placed
                }
            }
            // The daily play streak (#33) counts a day when a picture is finished here; reopening a finished one isn't.
            snapshotFlow { session.placedCount == session.cellCount }.drop(1).filter { it }.collect {
                graph.play.finished(Canvases.reminderKey(ref))
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
        sharedToast = null
        sharing.clear()
        hints.reset()
    }

    override fun onCleared() = close()
}
