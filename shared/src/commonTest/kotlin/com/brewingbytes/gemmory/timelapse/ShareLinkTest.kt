package com.brewingbytes.gemmory.timelapse

import com.brewingbytes.gemmory.policy.ReferrerParse
import kotlin.test.Test
import kotlin.test.assertEquals

class ShareLinkTest {
    @Test
    fun photoShareText() {
        assertEquals(
            "Made with Gemmory: https://play.google.com/store/apps/details?id=com.brewingbytes.gemmory" +
                "&referrer=utm_source%3Dshare%26utm_medium%3Dphoto%26utm_campaign%3Dtimelapse",
            ShareLink.text(ShareLink.Medium.PHOTO),
        )
    }

    @Test
    fun bundledShareText() {
        assertEquals(
            "Made with Gemmory: https://play.google.com/store/apps/details?id=com.brewingbytes.gemmory" +
                "&referrer=utm_source%3Dshare%26utm_medium%3Dbundled%26utm_campaign%3Dtimelapse",
            ShareLink.text(ShareLink.Medium.BUNDLED),
        )
    }

    @Test
    fun streakCardShareText() {
        assertEquals(
            "Made with Gemmory: https://play.google.com/store/apps/details?id=com.brewingbytes.gemmory" +
                "&referrer=utm_source%3Dshare%26utm_medium%3Dstreak%26utm_campaign%3Dstreak_card",
            ShareLink.streakText(),
        )
    }

    @Test
    fun aPictureShareCarriesItsId() {
        assertEquals(
            "Made with Gemmory: https://play.google.com/store/apps/details?id=com.brewingbytes.gemmory" +
                "&referrer=utm_source%3Dshare%26utm_medium%3Dbundled%26utm_campaign%3Dtimelapse%26utm_content%3Dfox-in-snow",
            ShareLink.text(ShareLink.Medium.BUNDLED, "fox-in-snow"),
        )
    }

    @Test
    fun aPhotoShareNeverCarriesAnId() {
        assertEquals(ShareLink.text(ShareLink.Medium.PHOTO), ShareLink.text(ShareLink.Medium.PHOTO, "photo-123"))
    }

    @Test
    fun theReferrerParsesBackToThePictureId() {
        val referrer = ShareLink.url(ShareLink.Medium.BUNDLED, pictureId = "harbor-lights").substringAfter("&referrer=")
        // Play hands the app the referrer decoded once.
        val decoded = referrer.replace("%3D", "=").replace("%26", "&")
        assertEquals("harbor-lights", ReferrerParse.parse(decoded).content)
    }
}
