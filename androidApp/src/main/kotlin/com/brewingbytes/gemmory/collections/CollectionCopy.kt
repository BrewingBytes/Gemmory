package com.brewingbytes.gemmory.collections

/** A collection row's lines (T37, design system "Collection and event rows", Lines). Calm: no dates, no "missed". */
object CollectionCopy {
    /** "3 of 7 finished"; above 1.5× font the well's words join it: "3 of 7 finished · 2 more coming" (D18). */
    fun count(finished: Int, size: Int, coming: Int, largeFont: Boolean): String =
        "$finished of $size finished" + if (largeFont && coming > 0) " · ${coming(coming)}" else ""

    /** The recessed well at the strip's end (D14). */
    fun coming(coming: Int): String = "$coming more coming"

    /** The well above 1.5× font: the count only, in mono (D18). */
    fun comingShort(coming: Int): String = "+$coming"

    /**
     * The row's one line. In progress: the keepsake to earn, or (a collection the catalog gives no keepsake) the
     * token, as an event after its window does. Complete: what was added, no token for one completed with VIP.
     */
    fun line(status: CollectionStatus, vip: Boolean): String {
        val size = status.progress.size
        if (!status.complete) {
            return when {
                status.keepsake != null -> "Finish all $size for a keepsake picture."
                vip -> "Finish all $size to complete it."
                else -> "Finish all $size for an unlock token."
            }
        }
        val token = if (status.vipAtCompletion) "" else " 1 unlock token added."
        return when (status.keepsakeState) {
            Keepsake.ADDED -> "Keepsake added: ${status.keepsake?.title}.$token"
            Keepsake.PENDING_OFFLINE -> "Keepsake earned. It arrives next time you're online."
            Keepsake.PENDING_SPACE -> "Keepsake earned. It arrives when there's space on this phone."
            Keepsake.NONE -> "Complete.$token"
        }
    }
}
