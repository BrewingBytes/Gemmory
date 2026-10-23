package com.brewingbytes.gemmory.share

import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The landing's wiring (T50): what it reads, what it records and what the gallery is told. The rule is ShareLandingTest's. */
class SharedLandingTest {
    private class FakeSource : LandingSource {
        override var referrer: String? = "utm_source=share&utm_medium=bundled&utm_content=fox-in-snow"
        override var referrerRead = true
        val listed = mutableMapOf("fox-in-snow" to CanvasRef(CanvasKind.BUNDLED, "fox-in-snow"))
        val ended = mutableListOf<String?>()
        override fun openable(id: String) = listed[id]
        override fun title(ref: CanvasRef) = "Fox in Snow"
        override fun ended(sharedId: String?) {
            ended += sharedId
        }
    }

    private val source = FakeSource()

    // Unconfined: every input is handled before the call returns.
    private fun landing(active: Boolean = true) = SharedLanding(active, source, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined)

    private val fox = CanvasRef(CanvasKind.BUNDLED, "fox-in-snow")

    @Test
    fun aListablePictureOpensWithItsToastOnce() {
        val landing = landing()
        landing.referrerRead()
        assertNull(landing.ui.value, "nothing before the gallery is in view")
        landing.galleryInView()
        assertEquals(LandingUi.Open(fox), landing.ui.value)
        assertEquals(listOf<String?>("fox-in-snow"), source.ended, "free today, the referrer dropped")
        landing.opened()
        assertNull(landing.takeCanvasToast(CanvasRef(CanvasKind.BUNDLED, "starter")), "only on the shared picture's canvas")
        assertEquals("Shared with you: Fox in Snow", landing.takeCanvasToast(fox))
        assertNull(landing.takeCanvasToast(fox), "once")
    }

    @Test
    fun anyOtherLaunchNeverLands() {
        val landing = landing(active = false)
        landing.galleryInView()
        assertNull(landing.ui.value)
        assertEquals(emptyList(), source.ended)
    }

    @Test
    fun aHiddenIdOpensNothingOnceTheCheckHasEnded() {
        source.referrer = "utm_content=harbor-lights"
        val landing = landing()
        landing.galleryInView()
        assertNull(landing.ui.value, "waits for the session's first catalog check")
        landing.checkEnded()
        assertNull(landing.ui.value)
        assertEquals(listOf<String?>(null), source.ended)
    }

    @Test
    fun aRemotePictureDownloadedAfterTheGalleryDrewIsOfferedAndOpenMakesItFree() {
        source.referrer = "utm_content=harbor-lights"
        val landing = landing()
        landing.galleryInView()
        source.listed["harbor-lights"] = CanvasRef(CanvasKind.REMOTE, "harbor-lights")
        landing.checkEnded()
        val offer = LandingUi.Offer(CanvasRef(CanvasKind.REMOTE, "harbor-lights"), "Fox in Snow")
        assertEquals(offer, landing.ui.value)
        assertEquals(listOf<String?>(null), source.ended, "offered: nothing free until Open")
        landing.offerTaken(offer)
        assertEquals(LandingUi.Open(offer.ref), landing.ui.value, "opened like a picture ready at the first look")
        assertEquals(listOf(null, "harbor-lights"), source.ended)
        assertEquals("Shared with you: Fox in Snow", landing.takeCanvasToast(offer.ref))
    }

    @Test
    fun leavingTheGalleryEndsTheLandingAndItsOffer() {
        source.referrerRead = false
        val landing = landing()
        landing.galleryInView()
        landing.leftGallery()
        source.referrerRead = true
        landing.referrerRead()
        assertNull(landing.ui.value, "a canvas opened by hand first: no landing")
        assertEquals(listOf<String?>(null), source.ended)
    }
}
