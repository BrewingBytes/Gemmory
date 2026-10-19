package com.brewingbytes.gemmory.remote

import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.save.ByteReader
import com.brewingbytes.gemmory.save.ByteWriter
import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.save.Envelope
import com.brewingbytes.gemmory.save.MalformedException
import com.brewingbytes.gemmory.save.Refusal
import com.brewingbytes.gemmory.save.i64
import kotlinx.datetime.LocalDate

/**
 * This phone's view of the remote catalog (#43), kept beside the cached catalog under `remote/` (never backed up).
 * [etag] is the cached catalog's, sent as If-None-Match. Times are wall-clock millis. [firstSeen] is the local day
 * (epoch day) each picture id first came in a catalog here, [opened] the ids the player has opened since: together
 * they drive the gallery's "New" label. A dated drop (T36) counts from its `availableFrom` instead of the day it came.
 */
data class RemoteState(
    val etag: String? = null,
    val lastSuccessMillis: Long? = null,
    val lastAttemptMillis: Long? = null,
    val issuedAt: Long? = null,
    val firstSeen: Map<String, Long> = emptyMap(),
    val opened: Set<String> = emptySet(),
) {
    /**
     * A catalog was accepted (or confirmed unchanged by a 304) and every grid it lists is on the phone. The first
     * success ever records its pictures as seen before the player arrived, so a new install marks nothing "New". A
     * dated drop (T36) is seen on its `availableFrom`, often days ahead, so it's New from its date (today's drop on a
     * new install too); one whose date had already passed on the first success counts as seen before the player.
     */
    fun succeeded(nowMillis: Long, today: LocalDate, etag: String?, catalog: RemoteCatalog): RemoteState {
        val ids = catalog.pictures.map { it.id }.toSet()
        val first = lastSuccessMillis == null
        val day = if (first) INSTALL_DAY else today.toEpochDays()
        val added = catalog.pictures.filter { it.id !in firstSeen }.associate { p ->
            val from = p.availableFrom
            p.id to when {
                from == null -> day
                first && from < today -> INSTALL_DAY
                else -> from.toEpochDays()
            }
        }
        val seen = firstSeen.filterKeys { it in ids } + added
        return copy(
            etag = etag,
            lastSuccessMillis = nowMillis,
            lastAttemptMillis = nowMillis,
            issuedAt = catalog.issuedAt,
            firstSeen = seen,
            opened = opened.filter { it in ids }.toSet(),
        )
    }

    fun attempted(nowMillis: Long): RemoteState = copy(lastAttemptMillis = nowMillis)

    fun opened(id: String): RemoteState = if (id in firstSeen && id !in opened) copy(opened = opened + id) else this

    /** The "New" label: came (a drop: opened to all) in the last [NEW_DAYS] days and not opened yet. */
    fun isNew(id: String, today: LocalDate): Boolean {
        val day = firstSeen[id] ?: return false
        return id !in opened && today.toEpochDays() - day in 0 until NEW_DAYS
    }

    companion object {
        const val NEW_DAYS = 7

        /** "Seen before the player arrived": any day minus this is far past [NEW_DAYS]. */
        const val INSTALL_DAY = 0L
    }
}

/** When to check the catalog (#43): once a day, retrying a failed check at most once an hour. */
object RemoteSchedule {
    const val DAY_MILLIS = 24L * 60 * 60 * 1000
    const val RETRY_MILLIS = 60L * 60 * 1000

    /**
     * Due when the last success is a day old (or never, or in the future: the clock went back) and the last attempt
     * an hour old.
     */
    fun isDue(state: RemoteState, nowMillis: Long): Boolean {
        fun older(at: Long?, gap: Long) = at == null || nowMillis < at || nowMillis - at >= gap
        return older(state.lastSuccessMillis, DAY_MILLIS) && older(state.lastAttemptMillis, RETRY_MILLIS)
    }

    /** The If-None-Match to send: the cached catalog's ETag, only while that catalog is still on the phone. */
    fun ifNoneMatch(state: RemoteState, hasCachedCatalog: Boolean): String? = state.etag?.takeIf { hasCachedCatalog }
}

/** Which remote pictures the gallery shows, and in what order (#43). */
object RemotePictureRules {
    /**
     * Withdraw rule: a picture shows while the catalog lists it (and its grid is on the phone), or once it's been
     * started (a gem placed), whatever the catalog says later. The same started rule as a locked picture's (eng E4c).
     */
    fun shows(inCatalog: Boolean, gridReady: Boolean, started: Boolean): Boolean = (inCatalog && gridReady) || started

    /** Downloaded grids the catalog no longer lists: deleted. A started picture keeps its own copy in its canvas. */
    fun withdrawn(cachedGrids: Set<String>, catalog: RemoteCatalog): Set<String> = cachedGrids - catalog.pictures.map { it.id }.toSet()

    /**
     * Today's picture (#34) candidates: today's drop (T36) when one is eligible, else the new remote ones, so new
     * content gets the card without reordering the grid; otherwise all of them.
     */
    fun todayCandidates(eligible: List<String>, isDrop: (String) -> Boolean = { false }, isNew: (String) -> Boolean): List<String> =
        eligible.filter(isDrop).ifEmpty { eligible.filter(isNew) }.ifEmpty { eligible }

    /**
     * A dated drop (T36) shows from its [availableFrom] local date on; before it, it's stored but hidden everywhere
     * (the gallery, Today's picture, an open by id). Undated pictures always show.
     */
    fun isVisible(availableFrom: LocalDate?, today: LocalDate): Boolean = availableFrom == null || availableFrom <= today

    /**
     * Today's drop (T36, eng D4): free to start without a video on its [availableFrom] local date only, beside Today's
     * picture (eng D9). A drop that reaches the phone later follows its catalog lock.
     */
    fun isTodaysDrop(availableFrom: LocalDate?, today: LocalDate): Boolean = availableFrom == today

    /** Tomorrow's drop (T36): the one the Tomorrow line frosts, once stored and verified. */
    fun isTomorrowsDrop(availableFrom: LocalDate?, today: LocalDate): Boolean =
        availableFrom != null && availableFrom.toEpochDays() == today.toEpochDays() + 1

    /**
     * The catalog order with remote pictures merged in: each joins its size's run of bundled pictures (Quick, Medium,
     * Big, #31), after the bundled ones, oldest published first ([addedAt]; null, a withdrawn but started one, last).
     * A size with no bundled picture goes at the end, in size order. [sizeOf] is null for the starter.
     */
    fun <T> merge(
        bundled: List<T>,
        remote: List<T>,
        sizeOf: (T) -> CanvasSize?,
        addedAt: (T) -> LocalDate?,
        idOf: (T) -> String,
    ): List<T> {
        val bySize = remote
            .sortedWith(compareBy<T>({ addedAt(it) == null }, { addedAt(it) }, { idOf(it) }))
            .groupBy(sizeOf)
            .toMutableMap()
        val out = ArrayList<T>(bundled.size + remote.size)
        for ((i, b) in bundled.withIndex()) {
            out += b
            val size = sizeOf(b) ?: continue
            if (i == bundled.lastIndex || sizeOf(bundled[i + 1]) != size) bySize.remove(size)?.let { out += it }
        }
        for (size in CanvasSize.entries) bySize.remove(size)?.let { out += it }
        bySize.values.forEach { out += it }
        return out
    }
}

/** [RemoteState] on disk, "GMRR". */
object RemoteStateCodec {
    val MAGIC = "GMRR".encodeToByteArray()
    const val VERSION = 1

    fun encode(state: RemoteState): ByteArray {
        val w = ByteWriter()
        w.string(state.etag ?: "")
        w.i64(state.lastSuccessMillis ?: -1)
        w.i64(state.lastAttemptMillis ?: -1)
        w.i64(state.issuedAt ?: -1)
        w.varint(state.firstSeen.size)
        for ((id, day) in state.firstSeen.entries.sortedBy { it.key }) {
            w.string(id)
            w.i64(day)
        }
        w.varint(state.opened.size)
        for (id in state.opened.sorted()) w.string(id)
        return Envelope.wrap(MAGIC, VERSION, w.toByteArray())
    }

    fun decode(bytes: ByteArray): Decoded<RemoteState> {
        val opened = when (val o = Envelope.open(bytes, MAGIC)) {
            is Decoded.Refused -> return o
            is Decoded.Ok -> o.value
        }
        if (opened.version > VERSION) return Decoded.Refused(Refusal.NewerVersion(opened.version))
        if (opened.version < VERSION) return Decoded.Refused(Refusal.NoMigration(opened.version))
        val state = try {
            val r = ByteReader(opened.body)
            val etag = r.string().ifEmpty { null }
            val success = r.i64().takeIf { it >= 0 }
            val attempt = r.i64().takeIf { it >= 0 }
            val issued = r.i64().takeIf { it >= 0 }
            val seen = List(r.varint()) { r.string() to r.i64() }.toMap()
            val ids = List(r.varint()) { r.string() }.toSet()
            if (r.remaining != 0) throw MalformedException()
            RemoteState(etag, success, attempt, issued, seen, ids)
        } catch (_: MalformedException) {
            null
        } ?: return Decoded.Refused(Refusal.Malformed)
        return Decoded.Ok(state)
    }

    private fun ByteWriter.string(s: String) {
        val b = s.encodeToByteArray()
        u16(b.size)
        bytes(b)
    }

    private fun ByteReader.string(): String {
        val length = u16()
        if (length > remaining) throw MalformedException()
        return ByteArray(length) { u8().toByte() }.decodeToString()
    }
}
