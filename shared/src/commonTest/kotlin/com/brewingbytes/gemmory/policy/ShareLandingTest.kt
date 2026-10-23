package com.brewingbytes.gemmory.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShareLandingTest {
    private fun input(
        inView: Boolean = true,
        referrerRead: Boolean = true,
        pictureId: String? = "fox-in-snow",
        openable: Boolean = true,
        checkEnded: Boolean = false,
    ) = LandingInput(inView, referrerRead, pictureId, openable, checkEnded)

    @Test
    fun aListablePictureOpensAtTheFirstLook() {
        val landing = ShareLanding()
        assertEquals(LandingAction.OPEN, landing.look(input()))
        assertTrue(landing.over)
        assertEquals(LandingAction.DROP, landing.look(input()), "once")
    }

    @Test
    fun nothingHappensBeforeTheGalleryIsInView() {
        val landing = ShareLanding()
        assertEquals(LandingAction.WAIT, landing.look(input(inView = false)))
        assertEquals(LandingAction.OPEN, landing.look(input()), "consent settled after: still the first look")
    }

    @Test
    fun aHiddenUnknownOrCraftedIdOpensNothing() {
        // Hidden drop, unearned keepsake, unknown or crafted: openableRef says no; once the check has ended, it's dropped.
        assertEquals(LandingAction.DROP, ShareLanding().look(input(openable = false, checkEnded = true)))
    }

    @Test
    fun aRemoteIdWaitsForTheFirstCatalogCheckThenIsOffered() {
        val landing = ShareLanding()
        assertEquals(LandingAction.WAIT, landing.look(input(openable = false)))
        assertEquals(LandingAction.OFFER, landing.look(input(openable = true, checkEnded = true)), "ready after the gallery drew")
    }

    @Test
    fun aRemoteIdStillUnknownAfterTheCheckIsDropped() {
        val landing = ShareLanding()
        assertEquals(LandingAction.WAIT, landing.look(input(openable = false)))
        assertEquals(LandingAction.DROP, landing.look(input(openable = false, checkEnded = true)))
    }

    @Test
    fun aReferrerReadAfterTheGalleryDrewIsOffered() {
        val landing = ShareLanding()
        assertEquals(LandingAction.WAIT, landing.look(input(referrerRead = false, pictureId = null)))
        assertEquals(LandingAction.OFFER, landing.look(input()))
    }

    @Test
    fun noIdNoLanding() {
        assertEquals(LandingAction.DROP, ShareLanding().look(input(pictureId = null)))
    }

    @Test
    fun aCanvasOpenedFirstEndsIt() {
        val landing = ShareLanding()
        assertEquals(LandingAction.WAIT, landing.look(input(openable = false)))
        landing.left()
        assertEquals(LandingAction.DROP, landing.look(input(openable = true, checkEnded = true)))
    }

    @Test
    fun theIdIsUtmContentWhateverTheSource() {
        assertEquals("fox-in-snow", ShareLanding.pictureId(ReferrerParse.parse("utm_source=share&utm_medium=bundled&utm_content=fox-in-snow")))
        assertEquals("fox-in-snow", ShareLanding.pictureId(ReferrerParse.parse("utm_source=tiktok&utm_content=fox-in-snow")))
        assertNull(ShareLanding.pictureId(ReferrerParse.parse("utm_source=share&utm_content=%3Cscript%3E")), "garbage is dropped by the parser")
        assertNull(ShareLanding.pictureId(ReferrerParse.parse("utm_source=google-play&utm_medium=organic")))
    }

    @Test
    fun theOfferStaysUntilUsedUnderTalkBack() {
        assertEquals(8_000L, ShareLanding.offerTimeoutMs(touchExploration = false))
        assertNull(ShareLanding.offerTimeoutMs(touchExploration = true))
    }
}
