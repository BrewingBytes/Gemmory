package com.brewingbytes.gemmory.save

/**
 * Decides when canvas progress is written (eng D8): 1 s after the last placement, at most 5 s after the first
 * unsaved one while placements continue, and immediately on Activity onStop and on canvas completion.
 *
 * Pure state machine with an injected clock. The caller runs writes one at a time on a background dispatcher:
 * after each event it waits until [dueAt], calls [takeDueWrite], writes, then reports [onWriteFinished].
 * A failed write (e.g. storage full) keeps the progress unsaved and retries no sooner than [maxWaitMillis] later,
 * so a full disk isn't hammered; [takeImmediateWrite] ignores that backoff.
 */
class SaveScheduler(
    private val nowMillis: () -> Long,
    private val idleMillis: Long = 1_000,
    private val maxWaitMillis: Long = 5_000,
) {
    /** A write in flight. It covers every placement made before it was taken. */
    class Ticket internal constructor(internal val dirtySince: Long)

    private var dirtySince: Long? = null
    private var lastPlacementAt = 0L
    private var retryNotBefore = Long.MIN_VALUE

    /** True while some placement isn't covered by a taken write. */
    val hasUnsavedPlacements: Boolean get() = dirtySince != null

    fun onPlacement() {
        val now = nowMillis()
        if (dirtySince == null) dirtySince = now
        lastPlacementAt = now
    }

    /** When the debounced write is due, or null when nothing is unsaved. */
    fun dueAt(): Long? {
        val since = dirtySince ?: return null
        return maxOf(retryNotBefore, minOf(lastPlacementAt + idleMillis, since + maxWaitMillis))
    }

    /** Takes the debounced write if it's due now. */
    fun takeDueWrite(): Ticket? {
        val due = dueAt() ?: return null
        return if (nowMillis() >= due) take() else null
    }

    /** Takes a write right away if anything is unsaved: on Activity onStop and on canvas completion. */
    fun takeImmediateWrite(): Ticket? = if (dirtySince != null) take() else null

    fun onWriteFinished(ticket: Ticket, success: Boolean) {
        if (success) {
            retryNotBefore = Long.MIN_VALUE
            return
        }
        // Placements made during the failed write keep their own lastPlacementAt; the debounce restarts from the
        // oldest placement that is still unsaved.
        dirtySince = minOf(ticket.dirtySince, dirtySince ?: ticket.dirtySince)
        retryNotBefore = nowMillis() + maxWaitMillis
    }

    private fun take(): Ticket {
        val ticket = Ticket(dirtySince!!)
        dirtySince = null
        return ticket
    }
}
