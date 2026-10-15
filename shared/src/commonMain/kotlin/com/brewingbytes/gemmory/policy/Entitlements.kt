package com.brewingbytes.gemmory.policy

import com.brewingbytes.gemmory.save.ByteReader
import com.brewingbytes.gemmory.save.ByteWriter
import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.save.Envelope
import com.brewingbytes.gemmory.save.MalformedException
import com.brewingbytes.gemmory.save.Refusal
import com.brewingbytes.gemmory.save.i64

/** The Play Console product ids (#8): two VIP subscriptions with one base plan each, and a one-time purchase. */
object Products {
    const val VIP_WEEKLY = "vip_weekly"
    const val VIP_YEARLY = "vip_yearly"
    const val REMOVE_ADS = "remove_ads"
}

enum class VipPlan(val productId: String) {
    WEEKLY(Products.VIP_WEEKLY),
    YEARLY(Products.VIP_YEARLY),
}

enum class PurchaseState { PURCHASED, PENDING, UNSPECIFIED }

/** One purchase as Play Billing reports it. A purchase can cover several [products]. */
data class Purchase(val products: List<String>, val state: PurchaseState)

/** What the player owns. [plan] is the VIP subscription, null without one. */
data class Entitlements(val plan: VipPlan? = null, val removeAds: Boolean = false) {
    /** Unlimited photo canvases, every picture unlocked, no watermark; rewarded ads are never offered. */
    val vip: Boolean get() = plan != null

    /** No interstitials (VIP or remove-ads). */
    val adsRemoved: Boolean get() = vip || removeAds

    companion object {
        val FREE = Entitlements()

        /**
         * Eng E6: VIP = `vip_weekly` or `vip_yearly` PURCHASED; adsRemoved = `remove_ads` PURCHASED or VIP. PENDING
         * grants nothing. Holding both plans (a switch in progress) reads as yearly.
         */
        fun from(purchases: List<Purchase>): Entitlements {
            val owned = purchases.filter { it.state == PurchaseState.PURCHASED }.flatMapTo(mutableSetOf()) { it.products }
            val plan = when {
                Products.VIP_YEARLY in owned -> VipPlan.YEARLY
                Products.VIP_WEEKLY in owned -> VipPlan.WEEKLY
                else -> null
            }
            return Entitlements(plan, Products.REMOVE_ADS in owned)
        }
    }
}

/** What Settings' Membership row says (design system, Settings; Phase 5 D7). */
enum class Membership { VIP_YEARLY, VIP_WEEKLY, VIP_UNCONFIRMED, ADS_REMOVED, FREE }

/**
 * The last purchases Play confirmed. [unverifiedMs] is wall time since that query, counted only while the clock moves
 * forward (eng D5), and [lastSeenAt] the wall clock when it was last counted. [lapseNoticeShown]: the gallery has
 * shown the one-time "couldn't confirm VIP" line for this lapse.
 */
data class EntitlementRecord(
    val verified: Entitlements = Entitlements.FREE,
    val unverifiedMs: Long = 0,
    val lastSeenAt: Long = 0,
    val lapseNoticeShown: Boolean = false,
)

/**
 * The entitlement cache (eng E6, D5 narrowed by D16). Every successful Play query overwrites it, downgrades
 * included; a failed query leaves it alone. VIP falls back to Free after 7 days without a successful query. A clock
 * moved backward doesn't add time, so it can't stretch the window; a move while the app isn't looking can still
 * stretch it by the time since the last look (accepted, like the allowance's clock risk). `remove_ads` never expires:
 * only a successful query without it (a refund) clears it. Not thread-safe: one caller at a time.
 */
class EntitlementCache(private val clock: WallClock, private val store: StateStore<EntitlementRecord>) {
    private var record: EntitlementRecord = store.load() ?: EntitlementRecord(lastSeenAt = clock.nowMillis())

    private fun commit(next: EntitlementRecord) {
        if (next == record) return
        record = next
        store.save(next)
    }

    /** Counts the time since the last look, forward moves only. */
    private fun observe(): EntitlementRecord {
        val now = clock.nowMillis()
        val step = (now - record.lastSeenAt).coerceAtLeast(0)
        val unverified = if (record.unverifiedMs > Long.MAX_VALUE - step) Long.MAX_VALUE else record.unverifiedMs + step
        commit(record.copy(unverifiedMs = unverified, lastSeenAt = now))
        return record
    }

    private val EntitlementRecord.lapsed: Boolean get() = verified.vip && unverifiedMs >= VIP_OFFLINE_MS

    /** What the player gets right now. */
    fun entitlements(): Entitlements {
        val r = observe()
        return if (r.lapsed) r.verified.copy(plan = null) else r.verified
    }

    fun membership(): Membership {
        val r = observe()
        return when {
            r.lapsed -> Membership.VIP_UNCONFIRMED
            r.verified.plan == VipPlan.YEARLY -> Membership.VIP_YEARLY
            r.verified.plan == VipPlan.WEEKLY -> Membership.VIP_WEEKLY
            r.verified.removeAds -> Membership.ADS_REMOVED
            else -> Membership.FREE
        }
    }

    /** A successful query: [purchases] is everything Play says the account holds now. */
    fun onVerified(purchases: List<Purchase>): Entitlements {
        commit(EntitlementRecord(Entitlements.from(purchases), unverifiedMs = 0, lastSeenAt = clock.nowMillis()))
        return record.verified
    }

    /**
     * A purchase flow just finished: [purchases] are Play-confirmed, so they count at once, added to what is already
     * known (only a query has the whole list). A new VIP plan starts a fresh 7-day window; remove_ads alone doesn't
     * touch the VIP window.
     */
    fun onPurchased(purchases: List<Purchase>): Entitlements {
        val bought = Entitlements.from(purchases)
        val known = record.verified
        val removeAds = known.removeAds || bought.removeAds
        commit(
            if (bought.plan != null) {
                EntitlementRecord(Entitlements(bought.plan, removeAds), unverifiedMs = 0, lastSeenAt = clock.nowMillis())
            } else {
                record.copy(verified = known.copy(removeAds = removeAds))
            },
        )
        return entitlements()
    }

    /** VIP lapsed offline and the gallery hasn't said so yet (design D7: one line, once). */
    fun lapseNoticeDue(): Boolean = observe().let { it.lapsed && !it.lapseNoticeShown }

    fun onLapseNoticeShown() {
        if (record.lapsed) commit(record.copy(lapseNoticeShown = true))
    }

    companion object {
        /** Eng D5: day 6 keeps VIP, day 7 falls back to Free. */
        const val VIP_OFFLINE_MS = 7 * 24 * 60 * 60_000L
    }
}

/**
 * The entitlement cache file `filesDir/ledger/entitlements`, "GMRE" v1 (same envelope and CRC trailer as the save
 * files; not DataStore, see gemmory.md E6). Body, big-endian: u8 flags (1 remove_ads, 2 lapse notice shown), u8 plan
 * (0 none, 1 weekly, 2 yearly), i64 unverified millis, i64 last seen at.
 */
object EntitlementCodec {
    val MAGIC = "GMRE".encodeToByteArray()
    const val VERSION = 1

    fun encode(record: EntitlementRecord): ByteArray {
        val w = ByteWriter()
        var flags = 0
        if (record.verified.removeAds) flags = flags or 1
        if (record.lapseNoticeShown) flags = flags or 2
        w.u8(flags)
        w.u8(record.verified.plan?.let { it.ordinal + 1 } ?: 0)
        w.i64(record.unverifiedMs)
        w.i64(record.lastSeenAt)
        return Envelope.wrap(MAGIC, VERSION, w.toByteArray())
    }

    fun decode(bytes: ByteArray): Decoded<EntitlementRecord> {
        val opened = when (val o = Envelope.open(bytes, MAGIC)) {
            is Decoded.Refused -> return o
            is Decoded.Ok -> o.value
        }
        if (opened.version > VERSION) return Decoded.Refused(Refusal.NewerVersion(opened.version))
        if (opened.version < VERSION) return Decoded.Refused(Refusal.NoMigration(opened.version))
        val record = try {
            val r = ByteReader(opened.body)
            val flags = r.u8()
            val plan = r.u8()
            val unverified = r.i64()
            val lastSeen = r.i64()
            if (r.remaining != 0 || flags > 3 || plan > VipPlan.entries.size || unverified < 0) throw MalformedException()
            EntitlementRecord(
                verified = Entitlements(plan = VipPlan.entries.getOrNull(plan - 1), removeAds = flags and 1 != 0),
                unverifiedMs = unverified,
                lastSeenAt = lastSeen,
                lapseNoticeShown = flags and 2 != 0,
            )
        } catch (_: MalformedException) {
            null
        } ?: return Decoded.Refused(Refusal.Malformed)
        return Decoded.Ok(record)
    }
}
