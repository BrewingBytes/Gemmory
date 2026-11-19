package com.brewingbytes.gemmory.play

import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.converter.ColorChoice
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.game.CanvasState
import com.brewingbytes.gemmory.policy.ReminderCanvas
import com.brewingbytes.gemmory.save.CanvasRecord
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.storage.WriteResult

/**
 * One gallery tile's data. [size] is a picture's Quick / Medium / Big (null for the starter and photo canvases);
 * [locked] is its catalog flag (`BundledPictures.FREE`, or a remote picture's `locked`), before unlocks and VIP.
 * [hiddenAs]: an unfinished mystery picture's neutral name (T43); its [title] and art aren't shown while it's set.
 */
sealed interface CanvasSummary {
    val ref: CanvasRef
    val title: String
    val createdAtMillis: Long
    val size: CanvasSize?
    val locked: Boolean
    val hiddenAs: String?

    /**
     * [state] is the restored progress (empty for a picture never opened). [firstFinishedDay] is the local epoch day
     * a finished canvas was first finished, from its `play` file (T35, #89); null when unfinished or finished before T35.
     */
    class Playable(
        override val ref: CanvasRef,
        override val title: String,
        val state: CanvasState,
        override val createdAtMillis: Long,
        override val size: CanvasSize? = null,
        override val locked: Boolean = false,
        val firstFinishedDay: Long? = null,
        override val hiddenAs: String? = null,
    ) : CanvasSummary

    /**
     * A remote picture never opened (T36, eng D12), from its catalog entry alone: nothing is decoded until its
     * thumbnail is drawn ([Canvases.pattern]), so a year of daily drops doesn't slow the gallery. [width] ×
     * [height] is its grid.
     */
    class Listed(
        override val ref: CanvasRef,
        override val title: String,
        val width: Int,
        val height: Int,
        override val size: CanvasSize?,
        override val locked: Boolean,
        override val hiddenAs: String? = null,
    ) : CanvasSummary {
        override val createdAtMillis: Long get() = 0L
    }

    /** "Can't open" (design D9): [thumb] is the last thumbnail's PNG, if one was ever written. */
    class Damaged(
        override val ref: CanvasRef,
        override val title: String,
        override val createdAtMillis: Long,
        val thumb: ByteArray?,
        override val size: CanvasSize? = null,
        override val locked: Boolean = false,
        override val hiddenAs: String? = null,
    ) : CanvasSummary
}

/** How copying a keepsake into its canvas went (T37). */
enum class KeepResult {
    /** Copied in now. */
    CREATED,

    /** Its canvas was already there. */
    PRESENT,

    /** Not listed, or its grid isn't on the phone yet. */
    MISSING,

    /** The phone is out of space. */
    NO_SPACE,

    FAILED,
}

/** A canvas's progress as Share progress (#36) sends it: its [pattern], the [log] of placed cells so far and its [title]. */
class CanvasProgress(val pattern: Pattern, val log: IntArray, val title: String)

/**
 * The canvases on this phone as shared screens read them (iOS Stage 1c, T64, eng D6). [CanvasStore] (common, over
 * `FileStore`) is it; the shell makes the store and hands it over through `PlatformServices`, so tests can swap it.
 */
interface Canvases {
    /** Every picture (bundled and remote, in catalog order) and every photo canvas (newest first). */
    suspend fun summaries(): Pair<List<CanvasSummary>, List<CanvasSummary>>

    /** Every canvas with a gem placed, as the evening reminder reads it (T35); [ReminderCanvas.key] is [reminderKey]. */
    suspend fun reminderCanvases(): List<ReminderCanvas>

    /** A [CanvasSummary.Listed] remote picture's finished pattern; null when it isn't listed any more. Blocking: call it off the main thread. */
    fun pattern(ref: CanvasRef): Pattern?

    /** Keeps [png] as [ref]'s "Can't open" thumbnail (design D9). */
    suspend fun writeThumb(ref: CanvasRef, png: ByteArray)

    /** [ref]'s saved progress for Share progress, read fresh; null when it can't open or is an unfinished mystery (T43, D23). */
    suspend fun progress(ref: CanvasRef): CanvasProgress?

    /**
     * A photo canvas from Start (design D4), with its cropped photo as [sourceJpeg]. Idempotent per [ref], so a repeated
     * Start makes one canvas (eng E4a). [colors] is the Preview choice it was made with (#29).
     */
    suspend fun createPhoto(ref: CanvasRef, pattern: Pattern, record: CanvasRecord, sourceJpeg: ByteArray, colors: ColorChoice = ColorChoice.STANDARD): WriteResult

    suspend fun rename(ref: CanvasRef, title: String): WriteResult

    suspend fun startOver(ref: CanvasRef): Boolean

    suspend fun delete(ref: CanvasRef): Boolean

    companion object {
        /** A canvas's key in the evening reminder (T35): its kind and id. */
        fun reminderKey(ref: CanvasRef): String = "${ref.kind.name}/${ref.id}"
    }
}
