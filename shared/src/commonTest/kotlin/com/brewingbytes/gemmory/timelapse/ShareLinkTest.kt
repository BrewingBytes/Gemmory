package com.brewingbytes.gemmory.timelapse

import com.brewingbytes.gemmory.policy.ReferrerParse
import kotlin.test.Test
import kotlin.test.assertEquals

class ShareLinkTest {
    @Test
    fun photoShareLink() {
        assertEquals(
            "https://play.google.com/store/apps/details?id=com.brewingbytes.gemmory" +
                "&referrer=utm_source%3Dshare%26utm_medium%3Dphoto%26utm_campaign%3Dtimelapse",
            ShareLink.url(ShareLink.Medium.PHOTO),
        )
    }

    @Test
    fun bundledShareLink() {
        assertEquals(
            "https://play.google.com/store/apps/details?id=com.brewingbytes.gemmory" +
                "&referrer=utm_source%3Dshare%26utm_medium%3Dbundled%26utm_campaign%3Dtimelapse",
            ShareLink.url(ShareLink.Medium.BUNDLED),
        )
    }

    @Test
    fun streakCardShareLink() {
        assertEquals(
            "https://play.google.com/store/apps/details?id=com.brewingbytes.gemmory" +
                "&referrer=utm_source%3Dshare%26utm_medium%3Dstreak%26utm_campaign%3Dstreak_card",
            ShareLink.streakUrl(),
        )
    }

    @Test
    fun aPictureShareCarriesItsId() {
        assertEquals(
            "https://play.google.com/store/apps/details?id=com.brewingbytes.gemmory" +
                "&referrer=utm_source%3Dshare%26utm_medium%3Dbundled%26utm_campaign%3Dtimelapse%26utm_content%3Dfox-in-snow",
            ShareLink.url(ShareLink.Medium.BUNDLED, pictureId = "fox-in-snow"),
        )
    }

    @Test
    fun aPhotoShareNeverCarriesAnId() {
        assertEquals(ShareLink.url(ShareLink.Medium.PHOTO), ShareLink.url(ShareLink.Medium.PHOTO, pictureId = "photo-123"))
    }

    @Test
    fun theReferrerParsesBackToThePictureId() {
        val referrer = ShareLink.url(ShareLink.Medium.BUNDLED, pictureId = "harbor-lights").substringAfter("&referrer=")
        // Play hands the app the referrer decoded once.
        val decoded = referrer.replace("%3D", "=").replace("%26", "&")
        assertEquals("harbor-lights", ReferrerParse.parse(decoded).content)
    }
}
