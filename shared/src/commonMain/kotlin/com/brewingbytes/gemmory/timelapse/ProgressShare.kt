package com.brewingbytes.gemmory.timelapse

/**
 * Sharing a canvas before it's finished (#36): offered from the first gem until the last one (founder, 2026-10-17),
 * and the video's band says how far along it is. The percentage rounds down, like the gallery tile and the top bar,
 * so it never reads 100% before the last gem; under 1% the band says "Just started" rather than "0% done".
 */
object ProgressShare {
    const val JUST_STARTED = "Just started"

    fun percent(placed: Int, total: Int): Int = if (total <= 0) 0 else (placed.toLong() * 100 / total).toInt()

    /** Share progress shows on the canvas and the gallery tile: at least one gem placed, and not finished. */
    fun isOffered(placed: Int, total: Int): Boolean = placed in 1 until total

    /** The band's line under the title: "47% done", or "Just started" under 1%. */
    fun line(placed: Int, total: Int): String = percent(placed, total).let { if (it < 1) JUST_STARTED else "$it% done" }
}
