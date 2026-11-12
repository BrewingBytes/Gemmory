package com.brewingbytes.gemmory.play

/**
 * Short-lived canvas effects: placement glints (design system Motion: 180ms, from 20dp, skipped under reduced
 * motion), the Find pulse (400ms, skipped under reduced motion) and a batch's reveal (#20: its cells show ring by
 * ring, never under reduced motion). Times are the frame clock's nanoseconds (`System.nanoTime` on Android).
 */
class CanvasEffects {
    /** Per cell, the time its ring shows, or 0 when it isn't in the running reveal; allocated on the first batch. */
    private var revealAt: LongArray? = null
    private val revealCells = ArrayList<Int>()
    private var revealEnd = 0L

    /**
     * Reveals [cells] (in log order) with their [rings], starting at [startNanos], [ringNanos] apart. A reveal still
     * running is finished first, so its cells show at once.
     */
    fun reveal(cellCount: Int, cells: IntArray, rings: IntArray, startNanos: Long, ringNanos: Long) {
        val at = revealAt ?: LongArray(cellCount).also { revealAt = it }
        clearReveal(at)
        for (i in cells.indices) {
            val time = startNanos + rings[i] * ringNanos
            at[cells[i]] = time
            revealCells += cells[i]
            if (time > revealEnd) revealEnd = time
        }
    }

    /** Whether [cell] is placed but its ring of the running reveal hasn't shown yet. */
    fun hidden(cell: Int, nowNanos: Long): Boolean {
        val at = revealAt ?: return false
        return at[cell] > nowNanos
    }

    private fun clearReveal(at: LongArray) {
        for (cell in revealCells) at[cell] = 0L
        revealCells.clear()
        revealEnd = 0L
    }

    private val glintCells = ArrayList<Int>()
    private val glintStarts = ArrayList<Long>()
    private var pulseCell = -1
    private var pulseStart = 0L

    fun glint(cell: Int, nowNanos: Long) {
        glintCells += cell
        glintStarts += nowNanos
    }

    fun pulse(cell: Int, nowNanos: Long) {
        pulseCell = cell
        pulseStart = nowNanos
    }

    fun isActive(nowNanos: Long): Boolean {
        prune(nowNanos)
        revealAt?.let { if (revealCells.isNotEmpty() && nowNanos >= revealEnd) clearReveal(it) }
        return glintCells.isNotEmpty() || pulse(nowNanos) != null || revealCells.isNotEmpty()
    }

    fun forEachGlint(nowNanos: Long, block: (cell: Int, progress: Float) -> Unit) {
        prune(nowNanos)
        for (i in glintCells.indices) {
            block(glintCells[i], ((nowNanos - glintStarts[i]) / GLINT_NANOS.toFloat()).coerceIn(0f, 1f))
        }
    }

    fun pulse(nowNanos: Long): Pair<Int, Float>? {
        if (pulseCell < 0) return null
        val progress = (nowNanos - pulseStart) / PULSE_NANOS.toFloat()
        if (progress >= 1f) {
            pulseCell = -1
            return null
        }
        return pulseCell to progress.coerceAtLeast(0f)
    }

    private fun prune(nowNanos: Long) {
        while (glintStarts.isNotEmpty() && nowNanos - glintStarts[0] >= GLINT_NANOS) {
            glintStarts.removeAt(0)
            glintCells.removeAt(0)
        }
    }

    companion object {
        const val GLINT_NANOS = 180_000_000L
        const val PULSE_NANOS = 400_000_000L
    }
}
