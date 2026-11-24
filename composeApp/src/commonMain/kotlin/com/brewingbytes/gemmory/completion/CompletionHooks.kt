package com.brewingbytes.gemmory.completion

import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.policy.FirstRunAsk
import com.brewingbytes.gemmory.policy.WhereFoundAnswer
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.timelapse.EncodeState
import kotlinx.coroutines.flow.StateFlow

/**
 * The time-lapse shares of finished canvases (iOS Stage 1c, T64): Android's `EncodeManager` until the encoder has an
 * iOS side (Stage 2). App-scoped, so an encode goes on whatever screen shows (design D8).
 */
interface TimelapseShares {
    /** Where [ref]'s share stands. */
    fun state(ref: CanvasRef): StateFlow<EncodeState>

    /**
     * Share tap on a finished canvas (eng D2): starts the encode of [log] over [pattern], or reuses the cached video.
     * [watermark] is decided at the tap, so a player who just became VIP gets none.
     */
    fun share(ref: CanvasRef, pattern: Pattern, log: IntArray, title: String, watermark: Boolean)

    /** The sheet's Cancel: back to the completion, no message. */
    fun cancel(ref: CanvasRef)

    /** The result was handed to the share sheet, or its message closed. */
    fun acknowledge(ref: CanvasRef)

    /** True once when [ref]'s last encode died with the process (design D8). */
    suspend fun takeKilledNotice(ref: CanvasRef): Boolean
}

/**
 * What a completion asks of the Android services that haven't moved to common code yet (iOS Stage 1c, T64): T56's
 * first-run funnel ("Where did you find Gemmory?"), its reward and the streak log.
 */
interface CompletionHooks {
    /** T56 is still undecided on this install. */
    fun whereFoundDue(): Boolean

    /** T56 was asked and not yet answered or skipped. */
    fun whereFoundOpen(): Boolean

    /** T56's row is on the screen. */
    fun whereFoundShown()

    /** An answer or Skip: true the first time only (the funnel's once-only record). */
    fun whereFoundAnswered(answer: WhereFoundAnswer): Boolean

    /** The first-run rule for this finish ([firstFinish]), with the analytics consent and this install's order. */
    fun whereFoundAsk(firstFinish: Boolean): FirstRunAsk

    /** T56's reward, a photo canvas, once per install: true when this call added it. */
    suspend fun earnCanvasOnce(earnId: String): Boolean

    /** The days the streak log counted a finish (an install that updated into T56 after finishing isn't asked). */
    fun finishDays(): List<Long>
}
