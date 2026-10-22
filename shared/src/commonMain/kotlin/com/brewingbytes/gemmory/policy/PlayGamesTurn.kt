package com.brewingbytes.gemmory.policy

/**
 * When Play Games may start (T45, round-3 CEO O2, eng O-E9). Starting it runs its automatic sign-in, which can show
 * Google's own "Create a Play Games profile" sheet, so it waits until the player has finished a picture and then for a
 * gallery visit with nothing else going on: never the return from a fresh finish (that return has its own one
 * interruption) and never over a canvas. Until then every Play Games call is a no-op and unlocks wait in the ledger.
 */
object PlayGamesTurn {
    /**
     * The player has earned Play Games' turn: a finish counted on this phone (a fresh one, or the upgrade backfill's
     * count of finished canvases) or any achievement earned. Kept as a backed-up flag once true.
     */
    fun ready(finishes: Int, unlocked: Set<Achievement>): Boolean = finishes > 0 || unlocked.isNotEmpty()

    /** Play Games starts now: it's [ready] and the gallery shows on a visit that isn't a fresh-finish return. */
    fun startNow(ready: Boolean, onGallery: Boolean, finishReturn: Boolean): Boolean = ready && onGallery && !finishReturn
}
