package com.brewingbytes.gemmory.policy

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Play Games' turn (T45): nothing before the first finish; then with the app, and in that finish's session at a
 * gallery visit that isn't its return.
 */
class PlayGamesTurnTest {
    @Test
    fun aFreshInstallIsNotReady() {
        assertFalse(PlayGamesTurn.ready(finishes = 0, unlocked = emptySet()))
        assertFalse(PlayGamesTurn.startNow(ready = false, onGallery = true, finishReturn = false))
    }

    @Test
    fun theFirstFinishMakesItReady() {
        assertTrue(PlayGamesTurn.ready(finishes = 1, unlocked = setOf(Achievement.FIRST_SPARKLE)))
    }

    /** Upgrade: the backfill's finished canvases, or an achievement alone (a streak one), count at once. */
    @Test
    fun anUpgraderWithAFinishOrAnAchievementIsReady() {
        assertTrue(PlayGamesTurn.ready(finishes = 3, unlocked = emptySet()))
        assertTrue(PlayGamesTurn.ready(finishes = 0, unlocked = setOf(Achievement.A_WEEK_OF_GEMS)))
    }

    @Test
    fun theReturnFromAFreshFinishIsNotItsTurn() {
        assertFalse(PlayGamesTurn.startNow(ready = true, onGallery = true, finishReturn = true))
    }

    @Test
    fun aLaterGalleryVisitIsItsTurn() {
        assertTrue(PlayGamesTurn.startNow(ready = true, onGallery = true, finishReturn = false))
    }

    /** In the first finish's session, only the gallery starts it, not a canvas the player went on to. */
    @Test
    fun inTheFirstFinishSessionOnlyAGalleryVisitStartsIt() {
        assertFalse(PlayGamesTurn.startNow(ready = true, onGallery = false, finishReturn = false))
    }

    /** Past the first finish it starts with the app, wherever the launch lands; before it, never. */
    @Test
    fun itStartsWithTheAppOnceReady() {
        assertTrue(PlayGamesTurn.startAtLaunch(ready = true))
        assertFalse(PlayGamesTurn.startAtLaunch(ready = false))
    }

    /** A progress share needs only a gem (#36), so Work in Progress alone isn't a first finish. */
    @Test
    fun workInProgressAloneIsNotReady() {
        assertFalse(PlayGamesTurn.ready(finishes = 0, unlocked = setOf(Achievement.WORK_IN_PROGRESS)))
        assertTrue(PlayGamesTurn.ready(finishes = 0, unlocked = setOf(Achievement.WORK_IN_PROGRESS, Achievement.SHOW_AND_TELL)))
    }
}
