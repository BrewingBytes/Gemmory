package com.brewingbytes.gemmory.remote

/**
 * What the canvas store needs of the remote catalog: the pictures ready to show and their verified grids. A dated drop
 * (T36) is in neither before its local date, so no caller can show or open it early.
 */
interface RemoteCatalogView {
    /** Catalog pictures (for this app version) whose grid is downloaded and whose date has come, in catalog order. */
    fun listed(): List<RemotePicture>

    /** [id]'s downloaded grid, checked against its catalog entry; null when it isn't listed or the file was refused. */
    fun grid(id: String): ByteArray?

    /** Local days since [id]'s drop date (T36), for Analytics; null for an undated or unlisted picture. */
    fun daysSinceAvailable(id: String): Long? = null

    /** [id] is an earned keepsake (T37): its canvas stays after the catalog withdraws it, started or not. */
    fun earned(id: String): Boolean = false

    /** The newest catalog collection listing picture [id] (T37), for the canvas events' `collection_id`. */
    fun collectionOf(id: String): String? = null

    /** The newest weekly event listing picture [id] (T38), for the canvas events' `event_id`. */
    fun eventOf(id: String): String? = null

    object None : RemoteCatalogView {
        override fun listed(): List<RemotePicture> = emptyList()

        override fun grid(id: String): ByteArray? = null
    }
}
