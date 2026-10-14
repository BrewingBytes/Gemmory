package com.brewingbytes.gemmory.timelapse

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
}
