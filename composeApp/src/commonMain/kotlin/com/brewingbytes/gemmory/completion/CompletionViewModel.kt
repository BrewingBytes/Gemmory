package com.brewingbytes.gemmory.completion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.brewingbytes.gemmory.app.AppGraph
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.copy.Words
import com.brewingbytes.gemmory.play.CanvasStore
import com.brewingbytes.gemmory.policy.FirstRunAsk
import com.brewingbytes.gemmory.policy.isFirstFinish
import com.brewingbytes.gemmory.render.decodeImage
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent
import com.brewingbytes.gemmory.timelapse.EncodeState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.TimeSource

/**
 * Canvases whose lights-down moment already ran in this process, so a rotation mid-sequence doesn't replay it.
 * A canvas is forgotten whenever it's finished again (after Start over or Play again).
 */
class LightsDownTracker {
    private val played = mutableSetOf<CanvasRef>()

    fun hasPlayed(ref: CanvasRef): Boolean = ref in played

    fun markPlayed(ref: CanvasRef) {
        played += ref
    }

    fun forget(ref: CanvasRef) {
        played -= ref
    }
}

/**
 * The canvas finished just now in this process, so its completion stays "fresh" (lights down, the interstitial check
 * on leaving) across a rotation: the back stack restores every completion as a still, which is right only after
 * process death, where this tracker is gone too. Cleared once the player has left that completion. [routeFresh] is
 * what the back stack's completion route says.
 */
class FreshFinishTracker {
    private var ref: CanvasRef? = null

    fun finished(ref: CanvasRef) {
        this.ref = ref
    }

    /** [ref]'s completion as the player sees it: fresh if the stack says so or [ref] was just finished. */
    fun isFresh(ref: CanvasRef, routeFresh: Boolean): Boolean = routeFresh || ref == this.ref

    /** The player left [ref]'s completion: true if it was fresh, and it is a still from now on. */
    fun left(ref: CanvasRef, routeFresh: Boolean): Boolean = isFresh(ref, routeFresh).also { if (ref == this.ref) this.ref = null }
}

/**
 * The finished picture and its meta line. [before] is the cropped photo (or the empty tint grid of a bundled
 * picture) for the before/after fallback sheet; [pattern], [log] and [title] are what Share encodes.
 */
class Finished(
    val ref: CanvasRef,
    val image: ImageBitmap,
    val before: ImageBitmap,
    val meta: String,
    val pattern: Pattern,
    val log: IntArray,
    val title: String,
) {
    val aspect: Float get() = pattern.width / pattern.height.toFloat()
    val cols: Int get() = pattern.width
    val rows: Int get() = pattern.height

    /** Gems per palette slot and each slot's color, for the confetti (#57). */
    val counts: IntArray = pattern.countsPerSlot()
    val colors: List<Color> = pattern.palette.map { Color(it.argb) }
}

/**
 * The completion screen's state (design system, Completion). Shared since iOS Stage 1c (T64, eng D5): its services come
 * from the [AppGraph] ([graph]); [savedState] keeps T56's row or thanks line across process death (#160 review). The
 * picture is drawn on [compute].
 */
class CompletionViewModel(
    private val graph: AppGraph,
    private val savedState: SavedStateHandle,
    private val compute: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val store: CanvasStore get() = graph.store
    private val shares: TimelapseShares get() = graph.timelapses
    private val hooks: CompletionHooks get() = graph.completion
    private val clock = TimeSource.Monotonic.markNow()

    /** "Where did you find Gemmory?" (T56) on this completion. */
    val whereFound = WhereFoundFlow(
        scope = viewModelScope,
        answered = { hooks.whereFoundAnswered(it) },
        earn = { hooks.earnCanvasOnce(WHERE_FOUND_EARN_ID) },
        log = { graph.analytics.log(it) },
        now = { clock.elapsedNow().inWholeMilliseconds },
        onChange = { saveWhereFound() },
    )

    /** The completion T56's row or thanks line belongs to; it shows there, fresh or (after the process died) as a still. */
    private var whereFoundRef: CanvasRef? = null

    init {
        // A Share round-trip can cost the process (#160 review): the row or the thanks line comes back with the completion.
        savedState.get<String>(WHERE_FOUND_KEY)?.let { saved ->
            val ref = saved.substringBefore('|').split(':').takeIf { it.size == 2 }
                ?.let { (kind, id) -> CanvasKind.entries.firstOrNull { it.name == kind }?.let { CanvasRef(it, id) } }
            if (ref != null) {
                whereFoundRef = ref
                whereFound.restore(saved.substringAfter('|'), open = hooks.whereFoundOpen())
            }
        }
    }

    private fun saveWhereFound() {
        savedState[WHERE_FOUND_KEY] = whereFoundRef?.let { ref -> whereFound.saved()?.let { "${ref.kind.name}:${ref.id}|$it" } }
    }

    /** T56's row or thanks line shows on [ref]'s completion. */
    fun whereFoundOn(ref: CanvasRef) = whereFoundRef == ref

    /** The player left [ref]'s completion (not a rotation): T56's row or thanks line stays behind. */
    fun leftCompletion(ref: CanvasRef) {
        if (whereFoundRef != ref) return
        whereFound.reset()
        whereFoundRef = null
    }

    var finished by mutableStateOf<Finished?>(null)
        private set

    var damaged by mutableStateOf(false)
        private set

    /** "The video stopped when the app closed." Shown for this visit, once per killed encode (design D8). */
    var killedNotice by mutableStateOf(false)
        private set

    private val played = LightsDownTracker()

    /** Survives rotation, not process death (see [FreshFinishTracker]). */
    val fresh = FreshFinishTracker()

    fun encodeState(ref: CanvasRef): StateFlow<EncodeState> = shares.state(ref)

    /** The completion screen was left: the killed-encode notice belonged to that visit. Kept across rotation. */
    fun onLeft() {
        killedNotice = false
    }

    fun hasPlayed(ref: CanvasRef) = played.hasPlayed(ref)

    /**
     * A fresh completion of [ref] opened (T56): decide the question once per canvas here, so a rotation keeps the same
     * answer. The first-run rule, the consent check and the save come first (Android's `FirstRun`). Only while T56 is
     * undecided does it look for an earlier finish ([isFirstFinish]: the canvases' small `play` files and the streak
     * log), so an install that updated into T56 after finishing a picture isn't asked. On the main thread, like the
     * `commit()` (eng #12): the row must be laid out before the picture draws, or the picture would resize.
     */
    fun decideWhereFound(ref: CanvasRef) {
        if (whereFoundRef == ref) return
        whereFoundRef = ref
        if (!hooks.whereFoundDue()) return
        val repository = store.repository
        val firstFinish = isFirstFinish(
            otherCanvasFinished = CanvasKind.entries.any { kind ->
                repository.list(kind).any { id -> CanvasRef(kind, id).let { it != ref && repository.readPlay(it)?.firstFinishedDay != null } }
            },
            thisCanvasFirstFinishedDay = repository.readPlay(ref)?.firstFinishedDay,
            streakFinishDays = hooks.finishDays(),
            today = graph.dayClock.today(),
        )
        val ask = hooks.whereFoundAsk(firstFinish)
        if (ask is FirstRunAsk.Show) whereFound.ask(ask.order, vip = graph.access.entitlements.value.vip)
    }

    /** T56's row is on the screen (the text has faded in). */
    fun whereFoundShown() = hooks.whereFoundShown()

    fun markPlayed(ref: CanvasRef) = played.markPlayed(ref)

    /** A canvas was just finished: its lights-down moment plays again, whatever ran for it before. */
    fun onFinished(ref: CanvasRef) {
        fresh.finished(ref)
        played.forget(ref)
        if (finished?.ref == ref) finished = null
        // T56: a new finish is decided anew (a later one is never asked), so an earlier row or thanks line never shows.
        whereFoundRef?.let(::leftCompletion)
    }

    /** Always re-reads, so a rename or a new play time since the last visit shows. The old picture stays until then. */
    fun load(ref: CanvasRef) {
        if (finished?.ref != ref) {
            finished = null
            killedNotice = false
        }
        damaged = false
        viewModelScope.launch {
            if (shares.takeKilledNotice(ref)) killedNotice = true
            when (val opened = store.open(ref)) {
                is CanvasStore.Opened.Damaged -> damaged = true
                is CanvasStore.Opened.Ready -> finished = withContext(compute) { finishedOf(ref, opened) }
            }
        }
    }

    private fun finishedOf(ref: CanvasRef, opened: CanvasStore.Opened.Ready): Finished {
        val pattern = opened.grid.pattern
        val cellPx = (720 / pattern.width).coerceIn(4, 24)
        val image = graph.renderGems(pattern, cellPx, null)
        val before = sourcePhoto(ref, image.width, image.height) ?: graph.renderGems(pattern, cellPx) { false }
        val words = graph.words
        val meta = words.text("completion_meta", opened.title, words.number(pattern.cells.size), playTime(words, opened.record?.playMillis ?: 0L))
        return Finished(ref, image, before, meta, pattern, opened.state.log(), opened.title)
    }

    /** A photo canvas's cropped source at the canvas's size; null if it's missing or unreadable (tints only then). */
    private fun sourcePhoto(ref: CanvasRef, width: Int, height: Int): ImageBitmap? {
        val repository = store.repository
        if (!repository.hasSource(ref)) return null
        val decoded = graph.files.read(repository.sourcePath(ref))?.let(::decodeImage) ?: return null
        if (decoded.width == width && decoded.height == height) return decoded
        return ImageBitmap(width, height).also { scaled ->
            Canvas(scaled).drawImageRect(decoded, dstSize = IntSize(width, height), paint = Paint())
        }
    }

    /**
     * Share tap (eng D2): starts the encode, or reuses the cached MP4. VIP gets no watermark, and the cache key
     * carries the flag, so a video made before VIP isn't reused.
     */
    fun share(ref: CanvasRef) {
        val f = finished?.takeIf { it.ref == ref } ?: return
        killedNotice = false
        graph.analytics.log(AnalyticsEvent.shareTapped(ref, f.pattern.width, f.pattern.height, progress = false))
        shares.share(ref, f.pattern, f.log, f.title, watermark = !graph.access.entitlements.value.vip)
    }

    fun cancelShare(ref: CanvasRef) = shares.cancel(ref)

    /** The share sheet was opened for the result, or the player closed the message. */
    fun shareHandled(ref: CanvasRef) = shares.acknowledge(ref)

    /** Play again (design D3): the Start over confirm, then progress, view and play time are cleared. */
    fun playAgain(ref: CanvasRef, then: () -> Unit) {
        viewModelScope.launch {
            store.startOver(ref)
            played.forget(ref)
            finished = null
            then()
        }
    }

    companion object {
        /** T56's reward, earned once per install (Inventory's earn id). */
        const val WHERE_FOUND_EARN_ID = "where-found"

        /** T56's row or thanks line in saved state: `<kind>:<id>|<WhereFoundFlow.saved>`. */
        private const val WHERE_FOUND_KEY = "where_found"

        /** "5 min", "1 h 12 min"; under a minute rounds up to "1 min". */
        fun playTime(words: Words, millis: Long): String {
            val minutes = ((millis + 59_999) / 60_000).coerceAtLeast(1)
            return if (minutes < 60) words.text("play_time_minutes", minutes) else words.text("play_time_hours", minutes / 60, minutes % 60)
        }
    }
}
