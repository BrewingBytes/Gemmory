package com.brewingbytes.gemmory.remote

import kotlinx.coroutines.flow.StateFlow

/**
 * The remote pictures (#43, T36) as the gallery reads them (iOS Stage 1c, T64): Android's `RemotePictures` until it
 * moves to common code over `FileStore`. Blocking reads: call them off the main thread.
 */
interface RemoteShelf {
    /** Counts up each time a check brings something new. */
    val updates: StateFlow<Int>

    /** Starts a background catalog check when one is due. */
    fun checkIfDue()

    /** Pictures whose grid is downloaded and whose date has come, in catalog order. */
    fun listed(): List<RemotePicture>

    /** The dated drops among [listed]. */
    fun drops(): Set<String>

    /** The drops dated today. */
    fun todaysDrops(): Set<String>

    /** [id] arrived in the last 7 days and hasn't been opened: the "New" label. */
    fun isNew(id: String): Boolean

    /** Tomorrow's drop's verified grid, for the Tomorrow line's frost; null when there's none. */
    fun tomorrowGrid(): ByteArray?

    /** [id]'s tile was tapped: its "New" label goes. */
    fun opened(id: String)
}
