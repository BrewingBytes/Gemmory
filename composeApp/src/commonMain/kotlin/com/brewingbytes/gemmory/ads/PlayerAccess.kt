package com.brewingbytes.gemmory.ads

import com.brewingbytes.gemmory.policy.Allowance
import com.brewingbytes.gemmory.policy.ConsumeResult
import com.brewingbytes.gemmory.policy.Entitlements
import kotlinx.coroutines.flow.StateFlow

/** What the player may open (iOS Stage 1c, T64): Android's `Monetization` until billing and unlocks move to common code. */
interface PlayerAccess {
    /** VIP or not, as last confirmed. */
    val entitlements: StateFlow<Entitlements>

    /** Pictures unlocked by a video or a token, backed up. */
    val unlocked: StateFlow<Set<String>>

    /**
     * Rewards come from videos (#203). False while ads are off (a release before AdMob links the app): nothing asks for
     * a video, and what one would give is free.
     */
    val adsOn: Boolean

    /** Today's photo canvases: the Preview allowance line and the gallery note (design D5). */
    val allowance: StateFlow<Allowance>

    /** Start on Preview (eng E4a): uses one canvas, idempotent per draft. */
    suspend fun consume(draftId: String): ConsumeResult

    /** The canvas couldn't be written: give the allowance back. */
    suspend fun release(draftId: String)
}

/**
 * The same rule for any picture by its catalog flag [locked]: bundled, or remote (#43). While ads are off ([adsOn]
 * false, #203) nothing is locked: the video that would open it can't come.
 */
fun isLocked(locked: Boolean, id: String, unlocked: Set<String>, entitlements: Entitlements, started: Boolean, adsOn: Boolean): Boolean =
    adsOn && locked && id !in unlocked && !entitlements.vip && !started
