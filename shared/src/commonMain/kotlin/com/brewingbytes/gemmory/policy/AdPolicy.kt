package com.brewingbytes.gemmory.policy

import com.brewingbytes.gemmory.save.ByteReader
import com.brewingbytes.gemmory.save.ByteWriter
import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.save.Envelope
import com.brewingbytes.gemmory.save.MalformedException
import com.brewingbytes.gemmory.save.Refusal
import com.brewingbytes.gemmory.save.i64

/** Where the app is when it asks whether an interstitial may show. */
enum class AdMoment {
    /** Done or back on the completion screen reached by finishing the picture just now. The only allowed moment. */
    LEAVE_FRESH_COMPLETION,

    /** Leaving a completion still (a finished tile reopened from the gallery). Founder call for #7: never. */
    LEAVE_COMPLETION_STILL,

    PLAY_CANVAS,
    SHARE,
}

/** Why an interstitial may not show. Rule order is the order checked. */
enum class AdBlock {
    WRONG_MOMENT,
    ADS_REMOVED,
    SHARING,
    FIRST_SESSION,
    BEFORE_SECOND_FINISH,
    DAILY_CAP,
    TOO_SOON,
    AFTER_REWARDED,
}

/**
 * [sharing]: an encode or the share flow is still running for the picture, so the ad would land during share.
 */
data class InterstitialContext(val moment: AdMoment, val entitlements: Entitlements, val sharing: Boolean = false)

/**
 * Persisted ad-cap state. [launchedBefore] tells a later cold start from the first; [finishes] counts pictures
 * finished on this install. Times are wall-clock millis.
 */
data class AdPolicyState(
    val launchedBefore: Boolean = false,
    val firstSessionOver: Boolean = false,
    val finishes: Int = 0,
    val lastInterstitialAt: Long? = null,
    val lastRewardedAt: Long? = null,
    val interstitialDay: Long = 0,
    val interstitialsToday: Int = 0,
)

/**
 * The interstitial cap (design doc, Ad rules R3-9; CEO D6; eng E5, D6): only when leaving the completion screen of a
 * picture just finished, never before or during share; never in the first session, which ends on the first
 * foreground after ≥ 30 min in the background or on a later cold start; never before the 2nd finished picture; at
 * least 10 min apart and at most 4 a local day; never within 2 min of a rewarded ad; never for VIP or remove-ads.
 * Not thread-safe: one caller at a time.
 */
class AdPolicy(private val clock: DayClock, private val store: StateStore<AdPolicyState>) {
    private var state: AdPolicyState = store.load() ?: AdPolicyState()

    /** In memory only: a process killed in the background comes back as a cold start, which ends the session anyway. */
    private var backgroundSince: Long? = null

    private fun commit(next: AdPolicyState) {
        if (next == state) return
        state = next
        store.save(next)
    }

    /** The first session is over (D6): from now on the gallery offers its quiet "VIP" action (design D3). */
    val firstSessionOver: Boolean get() = state.firstSessionOver

    /** Once per process. A cold start after the first one ends the first session (D6). */
    fun onProcessStart() {
        commit(if (state.launchedBefore) state.copy(firstSessionOver = true) else state.copy(launchedBefore = true))
    }

    fun onBackground() {
        backgroundSince = clock.nowMillis()
    }

    fun onForeground() {
        val since = backgroundSince ?: return
        backgroundSince = null
        if (clock.nowMillis() - since >= FIRST_SESSION_BACKGROUND_MS) commit(state.copy(firstSessionOver = true))
    }

    fun onPictureFinished() {
        commit(state.copy(finishes = state.finishes + 1))
    }

    /** A rewarded ad closed (watched or not): no interstitial for the next 2 min. */
    fun onRewardedShown() {
        commit(state.copy(lastRewardedAt = clock.nowMillis()))
    }

    fun onInterstitialShown() {
        val today = clock.today()
        val count = if (state.interstitialDay == today) state.interstitialsToday else 0
        commit(state.copy(lastInterstitialAt = clock.nowMillis(), interstitialDay = today, interstitialsToday = count + 1))
    }

    /** Null when an interstitial may show now, else the first rule that blocks it. */
    fun blockFor(ctx: InterstitialContext): AdBlock? {
        normalizeMovedClock()
        val now = clock.nowMillis()
        val s = state
        return when {
            ctx.moment != AdMoment.LEAVE_FRESH_COMPLETION -> AdBlock.WRONG_MOMENT
            ctx.entitlements.adsRemoved -> AdBlock.ADS_REMOVED
            ctx.sharing -> AdBlock.SHARING
            !s.firstSessionOver -> AdBlock.FIRST_SESSION
            s.finishes < MIN_FINISHES -> AdBlock.BEFORE_SECOND_FINISH
            s.interstitialDay == clock.today() && s.interstitialsToday >= MAX_PER_DAY -> AdBlock.DAILY_CAP
            s.lastInterstitialAt != null && now - s.lastInterstitialAt < MIN_GAP_MS -> AdBlock.TOO_SOON
            s.lastRewardedAt != null && now - s.lastRewardedAt < REWARDED_QUIET_MS -> AdBlock.AFTER_REWARDED
            else -> null
        }
    }

    fun canShowInterstitial(ctx: InterstitialContext): Boolean = blockFor(ctx) == null

    /**
     * A clock moved back leaves times in the future, which would block for as long as it was moved. They are pulled
     * back to now, so the gap rules count again from this moment (conservative, and bounded).
     */
    private fun normalizeMovedClock() {
        val now = clock.nowMillis()
        val s = state
        commit(
            s.copy(
                lastInterstitialAt = s.lastInterstitialAt?.coerceAtMost(now),
                lastRewardedAt = s.lastRewardedAt?.coerceAtMost(now),
            ),
        )
    }

    companion object {
        const val FIRST_SESSION_BACKGROUND_MS = 30 * 60_000L
        const val MIN_FINISHES = 2
        const val MIN_GAP_MS = 10 * 60_000L
        const val MAX_PER_DAY = 4
        const val REWARDED_QUIET_MS = 2 * 60_000L
    }
}

/**
 * The ad-cap file `filesDir/ledger/ads`, "GMRA" v1. Body, big-endian: u8 flags (1 launched before, 2 first session
 * over, 4 has last interstitial, 8 has last rewarded), u32 finishes, i64 last interstitial, i64 last rewarded (0 when
 * absent), i64 interstitial day, u8 interstitials that day.
 */
object AdPolicyCodec {
    val MAGIC = "GMRA".encodeToByteArray()
    const val VERSION = 1

    fun encode(state: AdPolicyState): ByteArray {
        val w = ByteWriter()
        var flags = 0
        if (state.launchedBefore) flags = flags or 1
        if (state.firstSessionOver) flags = flags or 2
        if (state.lastInterstitialAt != null) flags = flags or 4
        if (state.lastRewardedAt != null) flags = flags or 8
        w.u8(flags)
        w.u32(state.finishes)
        w.i64(state.lastInterstitialAt ?: 0)
        w.i64(state.lastRewardedAt ?: 0)
        w.i64(state.interstitialDay)
        w.u8(state.interstitialsToday.coerceAtMost(0xFF))
        return Envelope.wrap(MAGIC, VERSION, w.toByteArray())
    }

    fun decode(bytes: ByteArray): Decoded<AdPolicyState> {
        val opened = when (val o = Envelope.open(bytes, MAGIC)) {
            is Decoded.Refused -> return o
            is Decoded.Ok -> o.value
        }
        if (opened.version > VERSION) return Decoded.Refused(Refusal.NewerVersion(opened.version))
        if (opened.version < VERSION) return Decoded.Refused(Refusal.NoMigration(opened.version))
        val state = try {
            val r = ByteReader(opened.body)
            val flags = r.u8()
            val finishes = r.u32()
            val lastInterstitial = r.i64()
            val lastRewarded = r.i64()
            val day = r.i64()
            val today = r.u8()
            if (r.remaining != 0 || flags > 0xF || finishes < 0) throw MalformedException()
            AdPolicyState(
                launchedBefore = flags and 1 != 0,
                firstSessionOver = flags and 2 != 0,
                finishes = finishes,
                lastInterstitialAt = lastInterstitial.takeIf { flags and 4 != 0 },
                lastRewardedAt = lastRewarded.takeIf { flags and 8 != 0 },
                interstitialDay = day,
                interstitialsToday = today,
            )
        } catch (_: MalformedException) {
            null
        } ?: return Decoded.Refused(Refusal.Malformed)
        return Decoded.Ok(state)
    }
}
