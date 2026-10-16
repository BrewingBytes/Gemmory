package com.brewingbytes.gemmory.timelapse

/**
 * Sharing a canvas before it's finished (#36): offered from [MIN_PERCENT], and the video's band says how far along it
 * is. The percentage rounds down, like the gallery tile and the top bar, so it never reads 100% before the last gem.
 */
object ProgressShare {
    const val MIN_PERCENT = 10

    fun percent(placed: Int, total: Int): Int = if (total <= 0) 0 else (placed.toLong() * 100 / total).toInt()

    /** Share progress shows on the canvas and the gallery tile: unfinished, and at least [MIN_PERCENT] placed. */
    fun isOffered(placed: Int, total: Int): Boolean = placed < total && percent(placed, total) >= MIN_PERCENT

    /** The band's line under the title: "47% done". */
    fun line(placed: Int, total: Int): String = "${percent(placed, total)}% done"
}
