package com.brewingbytes.gemmory.ads

import android.app.Activity
import com.brewingbytes.gemmory.policy.EarnSource
import com.brewingbytes.gemmory.policy.Spin
import com.brewingbytes.gemmory.policy.SpotRules
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.telemetry.Analytics
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent
import com.brewingbytes.gemmory.telemetry.UnavailableReason
import com.brewingbytes.gemmory.policy.LoadFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The 1.0.7 ad spots on Android (ad-spots.md): each spot's own unit through the [gateway], played at once on a tap and
 * never waited for (eng D4), the reward added in the same write as its once-id (CEO D3), and the funnel logged by
 * placement (analytics D22). Main thread.
 */
class AndroidAdSpots(
    private val monetization: Monetization,
    private val gateway: AdGateway,
    /** The Activity on screen (ResumedActivity::current), for the video. */
    private val activity: () -> Activity?,
    private val analytics: Analytics,
    private val scope: CoroutineScope,
) : AdSpots {
    override val ready: StateFlow<Set<Spot>> =
        gateway.slotsReady.map { slots -> Spot.entries.filterTo(mutableSetOf()) { slotOf(it) in slots } }
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    /** Spots already counted as seen in this offer moment (DR-D20); a new moment ([want]) counts again. */
    private val seenNow = mutableSetOf<Spot>()

    /** Completions whose bonus video was watched: leaving them shows no interstitial of either format (R2-1). */
    private val bonusWatched = mutableSetOf<CanvasRef>()

    /** Today's popup, once it opened today (the inventory write lands a moment later). */
    private var popupDay: Long? = null

    override fun want(spot: Spot) {
        seenNow -= spot
        gateway.want(slotOf(spot))
    }

    override fun unwant(spot: Spot) = gateway.unwant(slotOf(spot))

    private val vip get() = monetization.entitlements.value.vip

    override fun bonusAllowed(ref: CanvasRef): Boolean = SpotRules.bonusAllowed(
        fresh = true,
        finishes = monetization.finishes.value,
        claimed = monetization.inventory.value.rewarded.contains(SpotRules.bonusId(key(ref))),
        vip = vip,
        adsOn = monetization.adsOn,
    )

    override suspend fun playBonus(ref: CanvasRef): SpotOutcome =
        play(Spot.BONUS, onShown = { bonusWatched += ref }) {
            if (monetization.earnBonusOnce(SpotRules.bonusId(key(ref)))) SpotRules.BONUS_FILLS else null
        }

    /** Whether the bonus was watched on [ref]'s completion; read once, when leaving it. */
    fun takeBonusWatched(ref: CanvasRef): Boolean = bonusWatched.remove(ref)

    override fun giftAllowed(): Boolean = SpotRules.giftAllowed(
        everFinished = monetization.finishes.value >= 1,
        openedToday = monetization.inventory.value.rewarded.contains(SpotRules.giftId(monetization.today())),
        vip = vip,
        adsOn = monetization.adsOn,
    )

    override fun giftPopupDue(): Boolean {
        val today = monetization.today()
        val shown = popupDay == today || monetization.inventory.value.rewarded.contains(SpotRules.giftPopupId(today))
        return SpotRules.giftPopupDue(giftAllowed(), shown)
    }

    override fun giftPopupShown() {
        val today = monetization.today()
        popupDay = today
        scope.launch { monetization.markOnce(SpotRules.giftPopupId(today)) }
    }

    override suspend fun playGift(): SpotOutcome {
        // The day of the tap (ad-spots.md §4): a video that ends after midnight pays today's gift, not tomorrow's.
        val day = monetization.today()
        return play(Spot.GIFT) { monetization.openGift(SpotRules.giftId(day)) }
    }

    override suspend fun playAgain(spin: Spin): SpotOutcome = play(Spot.AGAIN) { monetization.sameAgain(spin) }

    override fun seen(spot: Spot) {
        if (seenNow.add(spot)) analytics.log(AnalyticsEvent.rewardedOfferSeen(spot.type))
    }

    /**
     * Plays [spot]'s ready video now. On the reward, [grant] adds it (null: nothing left to pay). Closed early or a
     * failed show grants nothing; any shown video starts the interstitial's 2-min quiet.
     */
    private suspend fun play(spot: Spot, onShown: () -> Unit = {}, grant: suspend () -> Spin?): SpotOutcome {
        val type = spot.type
        analytics.log(AnalyticsEvent.rewardedOffered(type))
        if (!gateway.online()) {
            analytics.log(AnalyticsEvent.rewardedUnavailable(type, UnavailableReason.OFFLINE))
            return SpotOutcome.Offline
        }
        val activity = activity()
        val done = CompletableDeferred<SpotOutcome>()
        var earned = false
        val showing = activity != null && gateway.showSlot(
            activity,
            slotOf(spot),
            onEarned = { earned = true },
            onClosed = { shown ->
                if (!shown) {
                    done.complete(SpotOutcome.NoVideo)
                    return@showSlot
                }
                onShown()
                monetization.onRewardedShown()
                if (!earned) {
                    done.complete(SpotOutcome.ClosedEarly)
                    return@showSlot
                }
                scope.launch {
                    val spin = grant()
                    if (spin == null) {
                        done.complete(SpotOutcome.Gone)
                    } else {
                        analytics.log(AnalyticsEvent.rewardedWatched(type))
                        analytics.log(AnalyticsEvent.inventoryEarned(spin.item, EarnSource.VIDEO, spin.amount))
                        done.complete(SpotOutcome.Granted(spin))
                    }
                }
            },
        )
        if (!showing) {
            analytics.log(AnalyticsEvent.rewardedUnavailable(type, UnavailableReason.of(LoadFailure.NO_FILL, online = true)))
            return SpotOutcome.NoVideo
        }
        val outcome = done.await()
        if (outcome == SpotOutcome.NoVideo) {
            analytics.log(AnalyticsEvent.rewardedUnavailable(type, UnavailableReason.ERROR))
        }
        return outcome
    }

    companion object {
        fun slotOf(spot: Spot): AdSlot = when (spot) {
            Spot.BONUS -> AdSlot.BONUS
            Spot.GIFT -> AdSlot.GIFT
            Spot.AGAIN -> AdSlot.AGAIN
        }

        /** The bonus's once-id names the canvas by kind and id, never the player. */
        fun key(ref: CanvasRef): String = "${ref.kind.name.lowercase()}/${ref.id}"
    }
}
