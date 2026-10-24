package com.brewingbytes.gemmory.share

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The feed apps' own share-sheet entries (T51): each carries the feed cut as both its stream and its ClipData, so the
 * app gets the feed cut whichever it reads, and replaces exactly the component the sheet leaves out.
 */
@RunWith(AndroidJUnit4::class)
class FeedShareTargetsTest {
    private val video = Uri.parse("content://com.brewingbytes.gemmory.share/timelapse/starter.12.wm.v2.en.mp4")
    private val feed = Uri.parse("content://com.brewingbytes.gemmory.share/timelapse/starter.12.feed.v1.en.mp4")
    private val send = Intent(Intent.ACTION_SEND)
        .setType("video/mp4")
        .putExtra(Intent.EXTRA_STREAM, video)
        .putExtra(Intent.EXTRA_TEXT, "Made with Gemmory: link")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .apply { clipData = ClipData.newRawUri(null, video) }

    /** Installed: TikTok, Instagram with three share screens, YouTube with two. */
    private val everything = mapOf(
        "com.ss.android.ugc.trill" to listOf("com.ss.android.ugc.aweme.share.SystemShareActivity"),
        "com.instagram.android" to listOf("com.instagram.share.handleractivity.ShareHandlerActivity", "com.instagram.share.handleractivity.StoryShareHandlerActivity", "com.instagram.direct.share.handler.DirectShareHandlerActivity"),
        "com.google.android.youtube" to listOf("com.google.android.youtube.UploadActivity", "com.google.android.youtube.ShortsActivity"),
    )

    private fun targets(installed: Map<String, List<String>>) = ShareIntents.feedTargets(send, feed) { query ->
        installed[query.`package`].orEmpty().map { query.`package`!! to it }
    }

    @Test
    fun atMostTwoEntriesOneScreenPerAppInFeedTargetsOrder() {
        // Android's sheet shows only the first two initial intents; YouTube, which reads the swapped stream, waits.
        val targets = targets(everything)
        assertEquals(ShareIntents.MAX_FEED_ENTRIES, targets.size)
        assertEquals(
            listOf("com.ss.android.ugc.aweme.share.SystemShareActivity", "com.instagram.share.handleractivity.ShareHandlerActivity"),
            targets.map { it.first.className },
        )
        // Only YouTube installed: its first screen.
        assertEquals(listOf("com.google.android.youtube.UploadActivity"), targets(everything.filterKeys { "youtube" in it }).map { it.first.className })
    }

    @Test
    fun eachEntryGetsTheFeedCutAsStreamAndClipData() {
        for ((component, intent) in targets(everything)) {
            assertEquals(component, intent.component)
            @Suppress("DEPRECATION")
            assertEquals(feed, intent.getParcelableExtra(Intent.EXTRA_STREAM))
            assertEquals(1, intent.clipData!!.itemCount)
            assertEquals(feed, intent.clipData!!.getItemAt(0).uri, "no grant on the video")
            assertEquals(Intent.ACTION_SEND, intent.action)
            assertEquals("video/mp4", intent.type)
            assertEquals("Made with Gemmory: link", intent.getStringExtra(Intent.EXTRA_TEXT))
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        }
    }

    @Test
    fun theSharedIntentItselfKeepsTheVideo() {
        targets(everything)
        @Suppress("DEPRECATION")
        assertEquals(video, send.getParcelableExtra(Intent.EXTRA_STREAM))
        assertEquals(video, send.clipData!!.getItemAt(0).uri)
    }

    @Test
    fun noFeedAppInstalledMeansNoEntries() {
        assertEquals(emptyList(), ShareIntents.feedTargets(send, feed) { emptyList() })
    }
}
