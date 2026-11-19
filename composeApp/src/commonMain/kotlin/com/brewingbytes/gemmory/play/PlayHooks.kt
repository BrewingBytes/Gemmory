package com.brewingbytes.gemmory.play

import com.brewingbytes.gemmory.storage.CanvasRef

/**
 * What an open canvas tells the rest of the app, and the one thing it takes from it (iOS Stage 1c, T64): Android
 * services that haven't moved to common code yet (the share landing, the first-run funnel, `day_played` and the
 * daily streak). Android reads them through on each call, as connected tests replace some.
 */
interface PlayHooks {
    /** A share link's "Shared with you: <title>" (T50, DR1) for [ref]'s canvas, once; null for any other canvas. */
    fun takeSharedToast(ref: CanvasRef): String?

    /**
     * A gem was placed on any canvas. Returns the time since the first launch the first time on a fresh install (T46
     * `first_gem`), else null.
     */
    fun firstGem(nowMillis: Long): Long?

    /** A gem was placed: true for the day's first gem on any canvas, once a day (T36 `day_played`, eng D13). */
    fun dayPlayed(): Boolean

    /** A picture was finished on the canvas whose reminder key is [canvasKey] ([Canvases.reminderKey]): the daily streak counts the day (#33). */
    fun finished(canvasKey: String)
}
