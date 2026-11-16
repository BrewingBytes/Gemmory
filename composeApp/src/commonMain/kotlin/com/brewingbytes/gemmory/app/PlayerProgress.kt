package com.brewingbytes.gemmory.app

import com.brewingbytes.gemmory.policy.ReminderCanvas

/**
 * How far the player is, as the gallery reads it (iOS Stage 1c, T64), from Android services that haven't moved to
 * common code yet (the achievements ledger, the first-run prefs, the build's switches, the bundled catalog).
 */
interface PlayerProgress {
    /** Pictures finished, ever (the achievements ledger, backed up): past the starter once it's above 0 (T45). */
    val finishes: Int

    /** A share link landed this player on its picture (T50): in the normal game from then on (DR4). */
    val landed: Boolean

    /** The keep-it-alive nudge is on in this build (#102). */
    val nudgeOn: Boolean

    /** The Quick pictures a tap opens now, given the [started] canvases, for the nudge's target (#102). */
    fun quickOpenable(started: List<ReminderCanvas>): List<String>
}
