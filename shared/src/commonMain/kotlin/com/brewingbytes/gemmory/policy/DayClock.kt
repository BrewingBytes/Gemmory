package com.brewingbytes.gemmory.policy

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Wall-clock time in epoch millis. The player can move it, so nothing here trusts it beyond "today". */
fun interface WallClock {
    fun nowMillis(): Long
}

/**
 * Wall time plus the player's local date (eng E5: injected clock + time zone). [zone] is read on every call, so a
 * time-zone change while the app runs moves "today" with it. The allowance, the grace counters and the interstitial
 * day cap all reset when [today] changes: at local midnight, across DST, and when the clock is moved back (an
 * accepted risk, design doc "Photo canvas allowance rules").
 */
class DayClock(private val clock: WallClock, private val zone: () -> TimeZone) {
    fun nowMillis(): Long = clock.nowMillis()

    /** Today's local date as epoch days. */
    fun today(): Long = dateAt(nowMillis()).toEpochDays()

    /** Millis from now to the next local midnight, so the UI can refresh the allowance then. Always ≥ 1. */
    fun millisUntilNextDay(): Long {
        val now = nowMillis()
        val tz = zone()
        val next = dateAt(now).plus(DatePeriod(days = 1)).atStartOfDayIn(tz).toEpochMilliseconds()
        return (next - now).coerceAtLeast(1)
    }

    private fun dateAt(millis: Long): LocalDate = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone()).date
}

/** Where a policy's state lives between launches. Android keeps it under `filesDir/ledger`, outside every backup. */
interface StateStore<T> {
    /** The saved state, or null when there is none or it can't be read (the policy then starts fresh). */
    fun load(): T?

    fun save(value: T)
}

/** What the player has bought. Billing (#8) fills it in; until then everyone is [FREE]. */
enum class Tier { FREE, REMOVE_ADS, VIP }

data class Entitlements(val tier: Tier) {
    /** No interstitials (VIP or remove-ads). */
    val adsRemoved: Boolean get() = tier != Tier.FREE

    /** Unlimited photo canvases and every picture unlocked; rewarded ads are never offered. */
    val vip: Boolean get() = tier == Tier.VIP

    companion object {
        val FREE = Entitlements(Tier.FREE)
    }
}
