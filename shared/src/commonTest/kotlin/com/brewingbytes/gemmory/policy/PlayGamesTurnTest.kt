package com.brewingbytes.gemmory.policy

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Play Games' turn (T45): after the first finish, at a later gallery visit, never over a canvas. */
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

    @Test
    fun neverOverACanvas() {
        assertFalse(PlayGamesTurn.startNow(ready = true, onGallery = false, finishReturn = false))
    }
}
