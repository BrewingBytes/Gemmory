package com.brewingbytes.gemmory.timelapse

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Which picked apps get the feed cut (T51). */
class FeedTargetsTest {
    @Test
    fun tikTokInstagramAndYouTubeGetTheFeedCut() {
        for (p in listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.instagram.android", "com.google.android.youtube")) {
            assertTrue(FeedTargets.isFeed(p), p)
        }
    }

    @Test
    fun everyOtherAppGetsTheFullVideo() {
        for (p in listOf("com.whatsapp", "com.google.android.gm", "com.instagram.barcelona", "com.google.android.youtube.music", "", null)) {
            assertFalse(FeedTargets.isFeed(p), "$p")
        }
    }
}
