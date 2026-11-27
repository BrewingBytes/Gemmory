package com.brewingbytes.gemmory.ads

import androidx.compose.runtime.staticCompositionLocalOf
import com.brewingbytes.gemmory.policy.RewardType
import com.brewingbytes.gemmory.policy.Spin
import com.brewingbytes.gemmory.storage.CanvasRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The 1.0.7 opt-in ad spots (ad-spots.md), each with its own ad unit (CEO D2). */
enum class Spot(val type: RewardType) {
    /** "Watch a video: +1 fill" under Done on a fresh completion (DR-D3/D4). */
    BONUS(RewardType.COMPLETION_BONUS),

    /** Today's gift: the daily popup and the Inventory row (DR-D5..D8, D11). */
    GIFT(RewardType.DAILY_GIFT),

    /** "Same again" after a 5-day spin (DR-D12). */
    AGAIN(RewardType.SAME_AGAIN),
}

/** How a tap on a spot ended (DR-D9, the shared tap-spot states). */
sealed interface SpotOutcome {
    /** The video was watched to the end and [spin] was added. */
    data class Granted(val spin: Spin) : SpotOutcome

    /** Closed before its reward: the offer stays ("Watch to the end to get it."). */
    data object ClosedEarly : SpotOutcome

    /** No video at the tap (expired or failed to show): "No video right now." (eng D4: never waits). */
    data object NoVideo : SpotOutcome

    /** Offline at the tap. */
    data object Offline : SpotOutcome

    /** Nothing to pay any more (already paid, or the spot no longer applies). */
    data object Gone : SpotOutcome
}

/**
 * The ad spots as the shared screens see them (IOS-1: the rules and the UI are shared, each platform plays the
 * videos). A spot shows only while its own video is ready ([ready], CEO D2); a tap plays that video at once and never
 * waits ([play], eng D4). The rules behind each spot are [com.brewingbytes.gemmory.policy.SpotRules].
 */
interface AdSpots {
    /** The spots whose video is loaded right now. */
    val ready: StateFlow<Set<Spot>>

    /** Keep [spot]'s video loaded while its moment is on screen (eng D2/D5: never shows the consent form). */
    fun want(spot: Spot)

    fun unwant(spot: Spot)

    /** The completion bonus may be laid out for [ref] on a fresh completion (SpotRules.bonusAllowed). */
    fun bonusAllowed(ref: CanvasRef): Boolean

    /** Plays the bonus video for [ref]; on the reward, 1 fill once per canvas. */
    suspend fun playBonus(ref: CanvasRef): SpotOutcome

    /** Today's gift may be offered (SpotRules.giftAllowed): not yet opened today, after a first finish, no VIP. */
    fun giftAllowed(): Boolean

    /** Today's popup is still due today (DR-D5: once a day). */
    fun giftPopupDue(): Boolean

    /** The popup opened today (shown or dismissed, it doesn't come back today). */
    fun giftPopupShown()

    /** Plays the gift video; on the reward, rolls and adds Today's gift. */
    suspend fun playGift(): SpotOutcome

    /** Plays the "same again" video for the 5-day [spin] that just landed; on the reward, the slice once more. */
    suspend fun playAgain(spin: Spin): SpotOutcome

    /** An offer was fully shown and at least half visible (DR-D20), once per offer moment. */
    fun seen(spot: Spot)

    companion object {
        /** No ads (iOS until Stage 3, previews, tests): nothing is ever ready, so no spot shows. */
        val None: AdSpots = object : AdSpots {
            override val ready: StateFlow<Set<Spot>> = MutableStateFlow(emptySet<Spot>()).asStateFlow()

            override fun want(spot: Spot) = Unit

            override fun unwant(spot: Spot) = Unit

            override fun bonusAllowed(ref: CanvasRef) = false

            override suspend fun playBonus(ref: CanvasRef) = SpotOutcome.Gone

            override fun giftAllowed() = false

            override fun giftPopupDue() = false

            override fun giftPopupShown() = Unit

            override suspend fun playGift() = SpotOutcome.Gone

            override suspend fun playAgain(spin: Spin) = SpotOutcome.Gone

            override fun seen(spot: Spot) = Unit
        }
    }
}

/** The platform's spots; [AdSpots.None] unless the shell provides them. */
val LocalAdSpots = staticCompositionLocalOf { AdSpots.None }
