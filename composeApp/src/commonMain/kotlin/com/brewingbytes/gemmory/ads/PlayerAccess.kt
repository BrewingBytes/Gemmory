package com.brewingbytes.gemmory.ads

import com.brewingbytes.gemmory.policy.Entitlements
import kotlinx.coroutines.flow.StateFlow

/** What the player may open (iOS Stage 1c, T64): Android's `Monetization` until billing and unlocks move to common code. */
interface PlayerAccess {
    /** VIP or not, as last confirmed. */
    val entitlements: StateFlow<Entitlements>

    /** Pictures unlocked by a video or a token, backed up. */
    val unlocked: StateFlow<Set<String>>
}

/** The same rule for any picture by its catalog flag [locked]: bundled, or remote (#43). */
fun isLocked(locked: Boolean, id: String, unlocked: Set<String>, entitlements: Entitlements, started: Boolean): Boolean =
    locked && id !in unlocked && !entitlements.vip && !started
