package com.brewingbytes.gemmory.collections

import com.brewingbytes.gemmory.remote.titleIn
import com.brewingbytes.gemmory.R
import com.brewingbytes.gemmory.copy.Strings

/** A collection or event row's lines (T37/T38, design system "Collection and event rows", Lines). Calm: no dates, no "missed". */
class CollectionCopy(private val s: Strings) {
    /** "3 of 7 finished"; above 1.5× font the well's words join it: "3 of 7 finished · 2 more coming" (D18). */
    fun count(finished: Int, size: Int, coming: Int, largeFont: Boolean, oneADay: Boolean = false): String {
        val count = s.plural(R.plurals.collection_count, finished, finished, size)
        return if (largeFont && coming > 0) s.text(R.string.collection_count_coming, count, coming(coming, oneADay)) else count
    }

    /** The recessed well at the strip's end (D14); an event in its window drops one a day (T38). */
    fun coming(coming: Int, oneADay: Boolean = false): String =
        s.plural(if (oneADay) R.plurals.collection_coming_daily else R.plurals.collection_coming, coming, coming)

    /**
     * The row's one line. In progress: the keepsake to earn, or (a collection the catalog gives no keepsake) the
     * token, as an event after its window does. Complete: what was added, no token for one completed with VIP.
     */
    fun line(status: CollectionStatus, vip: Boolean): String {
        val size = status.progress.size
        if (status.event) return eventLine(status, vip)
        if (!status.complete) {
            return when {
                status.keepsake != null -> s.plural(R.plurals.collection_for_keepsake, size, size)
                vip -> s.text(R.string.collection_to_complete, size)
                else -> s.plural(R.plurals.collection_for_token, size, size)
            }
        }
        return when (status.keepsakeState) {
            Keepsake.ADDED -> withToken(status, s.text(R.string.collection_keepsake_added, status.keepsake?.titleIn(s.locale.toLanguageTag()).toString()))
            Keepsake.PENDING_OFFLINE -> s.text(R.string.collection_keepsake_offline)
            Keepsake.PENDING_SPACE -> s.text(R.string.collection_keepsake_space)
            Keepsake.NONE -> withToken(status, s.text(R.string.collection_complete))
        }
    }

    /** "Complete. 1 unlock token added.": the token line, unless it was completed with VIP. */
    private fun withToken(status: CollectionStatus, line: String): String =
        if (status.vipAtCompletion) line else s.text(R.string.sentences, line, s.text(R.string.collection_token_added))

    /**
     * A weekly event's line (T38). In its window: the 3-finish keepsake, then "Keepsake earned: <title>." once it's
     * in. After it: an ordinary collection row for the token, with no "missed", "ended" or dates. Its keepsake was
     * earned at the 3rd, so completing all 7 adds the token only. A keepsake still on its way says so at any time.
     */
    private fun eventLine(status: CollectionStatus, vip: Boolean): String {
        val size = status.progress.size
        return when {
            status.keepsakeState == Keepsake.PENDING_OFFLINE -> s.text(R.string.collection_keepsake_offline)
            status.keepsakeState == Keepsake.PENDING_SPACE -> s.text(R.string.collection_keepsake_space)
            status.complete -> withToken(status, s.text(R.string.collection_complete))
            status.inWindow && status.keepsakeState == Keepsake.ADDED -> s.text(R.string.event_keepsake_earned, status.keepsake?.titleIn(s.locale.toLanguageTag()).toString())
            status.inWindow -> s.text(R.string.event_line)
            vip -> s.text(R.string.collection_to_complete, size)
            else -> s.plural(R.plurals.collection_for_token, size, size)
        }
    }

    companion object {
        /** The well above 1.5× font: the count only, in mono (D18). */
        fun comingShort(coming: Int): String = "+$coming"
    }
}
