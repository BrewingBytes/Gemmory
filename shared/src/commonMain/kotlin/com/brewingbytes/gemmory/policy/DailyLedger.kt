package com.brewingbytes.gemmory.policy

import com.brewingbytes.gemmory.save.ByteReader
import com.brewingbytes.gemmory.save.ByteWriter
import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.save.Envelope
import com.brewingbytes.gemmory.save.MalformedException
import com.brewingbytes.gemmory.save.Refusal
import com.brewingbytes.gemmory.save.i64

/**
 * What a rewarded video (or a grace unlock) is for (design doc, Ad rules); [FILLS] is the inventory's fills (#38),
 * [COLORS] the More colors choice for one photo canvas (#29), [STREAK_REPAIR] a broken streak brought back (#102). New
 * entries go last: the ledger stores grace as bits by ordinal.
 */
enum class RewardType { PHOTO_CANVAS, PICTURE, FILLS, COLORS, STREAK_REPAIR }

/** Which allowance a Start used: today's free one, a bonus earned today (before #38), or a kept canvas (#38). */
enum class Spent { FREE, BONUS, KEPT }

/** One Start, remembered so a repeat with the same draft id uses nothing more (eng E4a). */
data class Consumption(val draftId: String, val spent: Spent, val day: Long)

/**
 * The ledger's state for [day] (local epoch days). [extrasEarned] counts extra photo canvases granted today, by a
 * video or a grace unlock (no cap, founder call #7); [bonusLeft] of them are not used yet. [consumed] keeps
 * the most recent Starts across days, so a repeated Start stays idempotent over midnight too.
 */
data class LedgerState(
    val day: Long,
    val freeUsed: Boolean = false,
    val extrasEarned: Int = 0,
    val bonusLeft: Int = 0,
    val graceUsed: Set<RewardType> = emptySet(),
    val consumed: List<Consumption> = emptyList(),
)

/** What the Preview allowance line and the gallery note show (design D5). */
sealed interface Allowance {
    /** VIP: no line. */
    data object Unlimited : Allowance

    /** "Uses today's free photo canvas." */
    data object Free : Allowance

    /** "Uses a kept photo canvas (2 left)." (#38; before it, a bonus from today's video). */
    data object Bonus : Allowance

    /** "Today's free one is used. Watch a video for another." */
    data object WatchVideo : Allowance

    /** Start can make a canvas right now. */
    val canStart: Boolean get() = this == Unlimited || this == Free || this == Bonus
}

sealed interface ConsumeResult {
    /** A canvas may be made. [spent] is null for VIP, which uses nothing. */
    data class Ok(val spent: Spent?, val repeat: Boolean) : ConsumeResult

    /** No free or bonus canvas left: a video comes first; Start shouldn't have been enabled. */
    data object NoneLeft : ConsumeResult
}

/**
 * The photo canvas allowance (design doc, Photo canvas allowance rules; CEO D7; eng E4, D15): one free photo canvas a
 * day, plus one more for every rewarded video watched (no daily cap, founder call #7), and one grace unlock per day
 * per [RewardType]. Everything resets when the local date changes. Not thread-safe: one caller at a time.
 *
 * With a [bank] (#38), an extra canvas from a video or today's grace goes into the inventory and is kept until it's
 * used, after today's free one; a bonus left from before the update still counts today. Without one, extras are
 * today's bonus, as before.
 */
class DailyLedger(
    private val clock: DayClock,
    private val store: StateStore<LedgerState>,
    private val bank: CanvasBank? = null,
) {
    private var state: LedgerState = store.load() ?: LedgerState(clock.today())

    /** Today's state; a new local date (midnight, DST, a moved clock) starts a fresh day but keeps [LedgerState.consumed]. */
    private fun current(): LedgerState {
        val today = clock.today()
        if (state.day != today) state = LedgerState(day = today, consumed = state.consumed)
        return state
    }

    private fun commit(next: LedgerState) {
        state = next
        store.save(next)
    }

    fun allowance(entitlements: Entitlements): Allowance {
        if (entitlements.vip) return Allowance.Unlimited
        val s = current()
        return when {
            !s.freeUsed -> Allowance.Free
            s.bonusLeft > 0 || (bank?.canvases ?: 0) > 0 -> Allowance.Bonus
            else -> Allowance.WatchVideo
        }
    }

    /**
     * Start on Preview (eng E4a). Idempotent per [draftId]: a repeat (double tap, a retry after a failed write) uses
     * nothing more. The entitlement is checked here only, never when an existing canvas is opened (E4c).
     */
    fun consume(draftId: String, entitlements: Entitlements): ConsumeResult {
        val s = current()
        s.consumed.firstOrNull { it.draftId == draftId }?.let { return ConsumeResult.Ok(it.spent, repeat = true) }
        if (entitlements.vip) return ConsumeResult.Ok(null, repeat = false)
        val spent = when {
            !s.freeUsed -> Spent.FREE
            s.bonusLeft > 0 -> Spent.BONUS
            bank?.takeCanvas(draftId) == true -> Spent.KEPT
            else -> return ConsumeResult.NoneLeft
        }
        val consumed = (s.consumed + Consumption(draftId, spent, s.day)).takeLast(MAX_REMEMBERED)
        commit(
            when (spent) {
                Spent.FREE -> s.copy(freeUsed = true, consumed = consumed)
                Spent.BONUS -> s.copy(bonusLeft = s.bonusLeft - 1, consumed = consumed)
                Spent.KEPT -> s.copy(consumed = consumed)
            },
        )
        return ConsumeResult.Ok(spent, repeat = false)
    }

    /** The canvas for [draftId] couldn't be made: give back what its Start used, if that was today. */
    fun release(draftId: String) {
        val s = current()
        val c = s.consumed.firstOrNull { it.draftId == draftId } ?: return
        val rest = s.consumed - c
        commit(
            when {
                // A kept canvas goes back to the inventory on any day: it never expired.
                c.spent == Spent.KEPT -> s.copy(consumed = rest).also { bank?.returnCanvas(draftId) }
                c.day != s.day -> s.copy(consumed = rest)
                c.spent == Spent.FREE -> s.copy(freeUsed = false, consumed = rest)
                else -> s.copy(bonusLeft = s.bonusLeft + 1, consumed = rest)
            },
        )
    }

    /** A rewarded video for an extra photo canvas was watched to the end: one more canvas (kept, with a bank). */
    fun grantReward() {
        val s = current()
        val bank = bank
        if (bank != null) {
            bank.addCanvas(EarnSource.VIDEO)
            commit(s.copy(extrasEarned = s.extrasEarned + 1))
        } else {
            commit(s.copy(extrasEarned = s.extrasEarned + 1, bonusLeft = s.bonusLeft + 1))
        }
    }

    /** Whether today's grace unlock for [type] is still there (the caller checks online no-fill first, D15). */
    fun graceAvailable(type: RewardType): Boolean = type !in current().graceUsed

    /**
     * Uses today's grace unlock for [type]. For a photo canvas it grants a bonus canvas (kept, with a bank); for a
     * picture the caller unlocks it, for fills the caller adds them (#38), for colors the caller unlocks More on the
     * photo (#29), and for a streak repair the caller repairs it (#102). False when it was already used today.
     */
    fun useGrace(type: RewardType): Boolean {
        if (!graceAvailable(type)) return false
        val s = current()
        commit(
            when (type) {
                RewardType.PHOTO_CANVAS -> {
                    bank?.addCanvas(EarnSource.GRACE)
                    s.copy(
                        graceUsed = s.graceUsed + type,
                        extrasEarned = s.extrasEarned + 1,
                        bonusLeft = if (bank != null) s.bonusLeft else s.bonusLeft + 1,
                    )
                }
                RewardType.PICTURE, RewardType.FILLS, RewardType.COLORS, RewardType.STREAK_REPAIR -> s.copy(graceUsed = s.graceUsed + type)
            },
        )
        return true
    }

    /** Millis until the allowance resets, for a UI refresh at local midnight. */
    fun millisUntilReset(): Long = clock.millisUntilNextDay()

    companion object {
        /** Recent Starts kept for idempotency; a draft is cleared right after its Start, so a few are plenty. */
        const val MAX_REMEMBERED = 8
    }
}

/**
 * The ledger file `filesDir/ledger/daily`, "GMRL" v2 (same envelope and CRC trailer as the save files). Body,
 * big-endian: i64 day, u8 freeUsed, varint extrasEarned, varint bonusLeft, u8 grace bits (1 = photo canvas, 2 = picture,
 * 4 = fills, 8 = colors since #29, 16 = streak repair since v2, #102), u8 count, then per Start: u16 id byte length, the
 * id in UTF-8, u8 spent (0 free, 1 bonus, 2 kept), i64 day. v1 has the same body without the streak repair bit; an app
 * before v2 refuses a v2 file, so a downgrade starts a fresh day (O10).
 */
object LedgerCodec {
    val MAGIC = "GMRL".encodeToByteArray()
    const val VERSION = 2

    /** Reward types a v1 file knows. */
    private const val V1_TYPES = 4

    fun encode(state: LedgerState): ByteArray {
        val w = ByteWriter()
        w.i64(state.day)
        w.u8(if (state.freeUsed) 1 else 0)
        w.varint(state.extrasEarned)
        w.varint(state.bonusLeft)
        w.u8(state.graceUsed.fold(0) { bits, t -> bits or (1 shl t.ordinal) })
        w.u8(state.consumed.size)
        for (c in state.consumed) {
            val id = c.draftId.encodeToByteArray()
            w.u16(id.size)
            w.bytes(id)
            w.u8(c.spent.ordinal)
            w.i64(c.day)
        }
        return Envelope.wrap(MAGIC, VERSION, w.toByteArray())
    }

    fun decode(bytes: ByteArray): Decoded<LedgerState> {
        val opened = when (val o = Envelope.open(bytes, MAGIC)) {
            is Decoded.Refused -> return o
            is Decoded.Ok -> o.value
        }
        if (opened.version > VERSION) return Decoded.Refused(Refusal.NewerVersion(opened.version))
        if (opened.version < 1) return Decoded.Refused(Refusal.NoMigration(opened.version))
        val types = if (opened.version == 1) V1_TYPES else RewardType.entries.size
        val state = try {
            val r = ByteReader(opened.body)
            val day = r.i64()
            val freeUsed = r.u8()
            val extras = r.varint()
            val bonus = r.varint()
            val grace = r.u8()
            val count = r.u8()
            val consumed = List(count) {
                val length = r.u16()
                if (length > r.remaining) throw MalformedException()
                val id = ByteArray(length) { r.u8().toByte() }.decodeToString()
                val spent = Spent.entries.getOrNull(r.u8()) ?: throw MalformedException()
                Consumption(id, spent, r.i64())
            }
            val valid = r.remaining == 0 && freeUsed in 0..1 && bonus <= extras &&
                grace < (1 shl types)
            if (!valid) throw MalformedException()
            LedgerState(
                day = day,
                freeUsed = freeUsed == 1,
                extrasEarned = extras,
                bonusLeft = bonus,
                graceUsed = RewardType.entries.filter { grace and (1 shl it.ordinal) != 0 }.toSet(),
                consumed = consumed,
            )
        } catch (_: MalformedException) {
            null
        } ?: return Decoded.Refused(Refusal.Malformed)
        return Decoded.Ok(state)
    }
}
