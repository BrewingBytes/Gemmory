package com.brewingbytes.gemmory.policy

import com.brewingbytes.gemmory.save.ByteReader
import com.brewingbytes.gemmory.save.ByteWriter
import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.save.Envelope
import com.brewingbytes.gemmory.save.MalformedException
import com.brewingbytes.gemmory.save.Refusal
import kotlin.random.Random

/** What the inventory holds (#38). [param] is the analytics value. */
enum class Item(val param: String) {
    /** One use of a placement tool (#20): a Fill region tap, a Fill color tap or one Brush stroke. */
    FILLS("fills"),

    /** A photo canvas beyond today's free one, kept until it's used. */
    PHOTO_CANVAS("photo_canvas"),

    /** Opens one locked picture for good. */
    UNLOCK_TOKEN("unlock_token"),

    /** Covers a missed day after the day off (#102, E5); held up to [Inventory.MAX_FREEZES]. */
    STREAK_FREEZE("streak_freeze"),
}

/** Where an item came from (#38). [param] is the analytics value. */
enum class EarnSource(val param: String) {
    VIDEO("video"),
    GRACE("grace"),
    WHEEL("wheel"),

    /** A completed collection's token (T37, CEO C6). */
    COLLECTION("collection"),

    /** "Where did you find Gemmory?" answered (T56, CEO C3): one photo canvas. */
    WHERE_FOUND("where_found"),
}

/**
 * The gift wheel's prizes and their odds in percent (#38, founder pick 2026-10-17; the streak freeze from #102, E7);
 * they add up to 100.
 */
enum class WheelPrize(val item: Item, val amount: Int, val percent: Int) {
    FILLS_5(Item.FILLS, 5, 45),
    FILLS_10(Item.FILLS, 10, 20),
    PHOTO_CANVAS(Item.PHOTO_CANVAS, 1, 15),
    UNLOCK_TOKEN(Item.UNLOCK_TOKEN, 1, 10),
    STREAK_FREEZE(Item.STREAK_FREEZE, 1, 10),
    ;

    companion object {
        /** The prize for a roll in 0 until 100: the segments in order, each as wide as its odds. */
        fun forRoll(roll: Int): WheelPrize {
            require(roll in 0 until 100) { "roll $roll out of 0 until 100" }
            var edge = 0
            for (prize in entries) {
                edge += prize.percent
                if (roll < edge) return prize
            }
            error("odds add up to $edge, not 100")
        }
    }
}

/**
 * A spin's result (#102, E7): the wheel lands on [prize] and pays [amount] of [item], which is the prize itself except
 * for a streak freeze while [Inventory.MAX_FREEZES] are held: that pays 5 fills.
 */
data class Spin(val prize: WheelPrize, val item: Item, val amount: Int)

/**
 * The inventory's state (#38). [spinsEarned] counts the 5-day streak gifts ever earned and [spinsUsed] the ones spun,
 * so missed spins add up. [recent] keeps the latest spends' use ids, so a repeated spend (a double tap, a retry) takes
 * nothing more. [rewarded] keeps every one-time earn's id (T37: a collection's token), kept for good, so it's given once.
 */
data class InventoryState(
    val fills: Int = 0,
    val canvases: Int = 0,
    val tokens: Int = 0,
    val spinsEarned: Int = 0,
    val spinsUsed: Int = 0,
    val recent: List<Spend> = emptyList(),
    val rewarded: Set<String> = emptySet(),
    val freezes: Int = 0,
) {
    val spinsWaiting: Int get() = spinsEarned - spinsUsed

    fun count(item: Item): Int = when (item) {
        Item.FILLS -> fills
        Item.PHOTO_CANVAS -> canvases
        Item.UNLOCK_TOKEN -> tokens
        Item.STREAK_FREEZE -> freezes
    }

    internal fun with(item: Item, count: Int): InventoryState = when (item) {
        Item.FILLS -> copy(fills = count)
        Item.PHOTO_CANVAS -> copy(canvases = count)
        Item.UNLOCK_TOKEN -> copy(tokens = count)
        Item.STREAK_FREEZE -> copy(freezes = count.coerceAtMost(Inventory.MAX_FREEZES))
    }
}

/** One spend, remembered for idempotency. */
data class Spend(val useId: String, val item: Item)

/** Kept photo canvases, as the allowance sees them (#38): [DailyLedger] uses one after today's free canvas. */
interface CanvasBank {
    val canvases: Int

    fun addCanvas(source: EarnSource)

    /** Takes one for the Start [draftId]; true also for a repeat of the same draft. */
    fun takeCanvas(draftId: String): Boolean

    /** The canvas for [draftId] couldn't be made: give back what it took. */
    fun returnCanvas(draftId: String)
}

/**
 * The inventory (#38): fill charges, kept photo canvases, unlock tokens and streak freezes (#102), earned from rewarded
 * videos, today's grace and the 5-day streak wheel, and spent one at a time. On the phone only, backed up (no user content). VIP has
 * everything unlimited, so callers don't earn or spend for VIP. [roll] picks the wheel's prize (0 until 100).
 * Not thread-safe: one caller at a time.
 */
class Inventory(
    private val store: StateStore<InventoryState>,
    private val roll: () -> Int = { Random.nextInt(100) },
) : CanvasBank {
    var state: InventoryState = store.load() ?: InventoryState()
        private set

    private fun commit(next: InventoryState) {
        state = next
        store.save(next)
    }

    fun count(item: Item): Int = state.count(item)

    fun earn(item: Item, amount: Int) {
        require(amount > 0) { "earn $amount" }
        commit(state.with(item, state.count(item) + amount))
    }

    /**
     * Earns [amount] of [item] once for [earnId] (T37: `collection-<id>`). The id is kept in the same write as the
     * count, so a retry after a crash can neither give it twice nor lose it. False when [earnId] already earned.
     */
    fun earnOnce(item: Item, amount: Int, earnId: String): Boolean {
        require(amount > 0) { "earn $amount" }
        if (earnId in state.rewarded) return false
        commit(state.with(item, state.count(item) + amount).copy(rewarded = state.rewarded + earnId))
        return true
    }

    /**
     * Spends one [item] for [useId]. True when it was spent now or already was for [useId] (a repeat takes nothing
     * more); false when none is left.
     */
    fun spend(item: Item, useId: String): Boolean {
        if (state.recent.any { it.useId == useId && it.item == item }) return true
        val left = state.count(item)
        if (left <= 0) return false
        commit(state.with(item, left - 1).copy(recent = (state.recent + Spend(useId, item)).takeLast(MAX_REMEMBERED)))
        return true
    }

    /** Gives back what [useId] spent of [item], if it spent one. */
    fun refund(item: Item, useId: String) {
        val spend = state.recent.firstOrNull { it.useId == useId && it.item == item } ?: return
        commit(state.with(item, state.count(item) + 1).copy(recent = state.recent - spend))
    }

    /** The streak reached [length] days on a new day (#33): every 5th day earns a wheel spin. */
    fun onStreakDay(length: Int) {
        if (length > 0 && length % SPIN_EVERY == 0) commit(state.copy(spinsEarned = state.spinsEarned + 1))
    }

    /**
     * Spins the wheel: the prize is rolled and added in one step, so killing the app mid-animation can't spin again.
     * A streak freeze while [MAX_FREEZES] are held pays [FILLS_PER_VIDEO] fills instead (E7). Null when no spin is
     * waiting.
     */
    fun spin(): Spin? {
        if (state.spinsWaiting <= 0) return null
        val prize = WheelPrize.forRoll(roll())
        val spin = if (prize.item == Item.STREAK_FREEZE && state.freezes >= MAX_FREEZES) {
            Spin(prize, Item.FILLS, FILLS_PER_VIDEO)
        } else {
            Spin(prize, prize.item, prize.amount)
        }
        commit(state.with(spin.item, state.count(spin.item) + spin.amount).copy(spinsUsed = state.spinsUsed + 1))
        return spin
    }

    override val canvases: Int get() = state.canvases

    override fun addCanvas(source: EarnSource) = earn(Item.PHOTO_CANVAS, 1)

    override fun takeCanvas(draftId: String): Boolean = spend(Item.PHOTO_CANVAS, draftId)

    override fun returnCanvas(draftId: String) = refund(Item.PHOTO_CANVAS, draftId)

    companion object {
        /** The streak gift comes every this many days in a row. */
        const val SPIN_EVERY = 5

        /** Fills a rewarded video (or today's fills grace) gives. */
        const val FILLS_PER_VIDEO = 5

        /** Recent spends kept for idempotency. */
        const val MAX_REMEMBERED = 16

        /** Streak freezes held at most (#102, E5). */
        const val MAX_FREEZES = 2
    }
}

/**
 * The inventory file `filesDir/inventory/items`, "GMRI" v1 (same envelope and CRC trailer as the save files). Body:
 * varint fills, varint canvases, varint tokens, varint spinsEarned, varint spinsUsed, u8 count, then per spend: u16
 * id byte length, the id in UTF-8, u8 item. v2 (T37) adds varint count, then per one-time earn id: u16 byte length,
 * the id in UTF-8. A v1 file reads as having none. v3 (#102) adds varint streak freezes; older files read 0.
 */
object InventoryCodec {
    val MAGIC = "GMRI".encodeToByteArray()
    const val VERSION = 3

    fun encode(state: InventoryState): ByteArray {
        val w = ByteWriter()
        w.varint(state.fills)
        w.varint(state.canvases)
        w.varint(state.tokens)
        w.varint(state.spinsEarned)
        w.varint(state.spinsUsed)
        w.u8(state.recent.size)
        for (spend in state.recent) {
            val id = spend.useId.encodeToByteArray()
            w.u16(id.size)
            w.bytes(id)
            w.u8(spend.item.ordinal)
        }
        w.varint(state.rewarded.size)
        for (id in state.rewarded.sorted()) {
            val bytes = id.encodeToByteArray()
            w.u16(bytes.size)
            w.bytes(bytes)
        }
        w.varint(state.freezes)
        return Envelope.wrap(MAGIC, VERSION, w.toByteArray())
    }

    fun decode(bytes: ByteArray): Decoded<InventoryState> {
        val opened = when (val o = Envelope.open(bytes, MAGIC)) {
            is Decoded.Refused -> return o
            is Decoded.Ok -> o.value
        }
        if (opened.version > VERSION) return Decoded.Refused(Refusal.NewerVersion(opened.version))
        if (opened.version < 1) return Decoded.Refused(Refusal.NoMigration(opened.version))
        val state = try {
            val r = ByteReader(opened.body)
            fun string(): String {
                val length = r.u16()
                if (length > r.remaining) throw MalformedException()
                return ByteArray(length) { r.u8().toByte() }.decodeToString()
            }
            val fills = r.varint()
            val canvases = r.varint()
            val tokens = r.varint()
            val earned = r.varint()
            val used = r.varint()
            val recent = List(r.u8()) {
                val id = string()
                Spend(id, Item.entries.getOrNull(r.u8()) ?: throw MalformedException())
            }
            val rewarded = if (opened.version >= 2) List(r.varint()) { string() }.toSet() else emptySet()
            val freezes = if (opened.version >= 3) r.varint() else 0
            val valid = r.remaining == 0 && fills >= 0 && canvases >= 0 && tokens >= 0 && used in 0..earned &&
                freezes in 0..Inventory.MAX_FREEZES
            if (!valid) throw MalformedException()
            InventoryState(fills, canvases, tokens, earned, used, recent, rewarded, freezes)
        } catch (_: MalformedException) {
            null
        } ?: return Decoded.Refused(Refusal.Malformed)
        return Decoded.Ok(state)
    }
}
