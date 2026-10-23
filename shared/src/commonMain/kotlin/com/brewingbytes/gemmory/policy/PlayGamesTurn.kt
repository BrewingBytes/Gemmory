package com.brewingbytes.gemmory.policy

/**
 * When Play Games may start (T45, round-3 CEO O2, eng O-E9, and the founder's call after the E3 spike). Starting it
 * runs its automatic sign-in, which can show Google's own "Create a Play Games profile" sheet, so nothing of it runs
 * before the player's first finish. From then on it starts with the app ([startAtLaunch]); in the session of that
 * first finish, whose launch has passed, at the next gallery visit that isn't the finish's return ([startNow]: that
 * return has its own one interruption). Its one-time sign-in UI lands on whatever shows when the sign-in completes,
 * a canvas included (accepted: Play Games v2 can't pin its pop-ups). Until it starts every Play Games call is a no-op
 * and unlocks wait in the ledger.
 */
object PlayGamesTurn {
    /**
     * The player is past the first finish: a finish counted on this phone (a fresh one, or the upgrade backfill's
     * count of finished canvases) or an achievement that takes one. Work in Progress doesn't: a progress share needs
     * only a gem. Kept as a backed-up flag once true.
     */
    fun ready(finishes: Int, unlocked: Set<Achievement>): Boolean = finishes > 0 || (unlocked - Achievement.WORK_IN_PROGRESS).isNotEmpty()

    /** Play Games starts as the app comes to the foreground for the first time in a process: whenever it's [ready]. */
    fun startAtLaunch(ready: Boolean): Boolean = ready

    /** In the session of the first finish: Play Games starts at a gallery visit that isn't a fresh-finish return. */
    fun startNow(ready: Boolean, onGallery: Boolean, finishReturn: Boolean): Boolean = ready && onGallery && !finishReturn
}
