package com.brewingbytes.gemmory.bundled

import com.brewingbytes.gemmory.remote.RemotePicture
import com.brewingbytes.gemmory.storage.CanvasRef

/** Today's picture (#34) as the gallery reads it (iOS Stage 1c, T64): Android's `TodaysPictures` until it moves with its prefs. */
interface TodaysPicks {
    /**
     * Today's picture id, picking it on the day's first call (the gallery's card and the evening reminder agree).
     * [started]: pictures with a gem placed; [listed], [drops] and [isNew]: the remote pictures shown, the dated drops
     * among them and their "New" mark.
     */
    fun pick(started: Set<String>, vip: Boolean, listed: List<RemotePicture>, drops: Set<String>, isNew: (String) -> Boolean): String?

    /** The ids that open without a video today: today's picture, today's drop, the share link's picture. */
    fun freeToday(): Set<String>

    /** Whether [ref] opens without a video right now. */
    fun isFreeToday(ref: CanvasRef): Boolean

    /**
     * A canvas opened with no gem placed: if it's today's picture, today's drop or the share link's picture now, the
     * grant is decided here, so its first gem still unlocks it when it lands after midnight. Null when it's none of them.
     */
    fun opened(ref: CanvasRef, vip: Boolean): FirstGem?
}

/** Today's picture, or a share link's (T50), opened while it was free ([TodaysPicks.opened]). */
fun interface FirstGem {
    /** The first gem on a [width] × [height] grid: a free player's grant becomes a permanent unlock, and Analytics hears of Today's picture. */
    fun placed(width: Int, height: Int)
}
