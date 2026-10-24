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

    /** Installed: YouTube with two share screens and Instagram with one; TikTok isn't. */
    private val installed = mapOf(
        "com.google.android.youtube" to listOf("com.google.android.youtube.UploadActivity", "com.google.android.youtube.ShortsActivity"),
        "com.instagram.android" to listOf("com.instagram.share.ShareHandlerActivity"),
    )

    private fun targets() = ShareIntents.feedTargets(send, feed) { query ->
        installed[query.`package`].orEmpty().map { query.`package`!! to it }
    }

    @Test
    fun eachFeedScreenGetsTheFeedCutAsStreamAndClipData() {
        val targets = targets()
        assertEquals(3, targets.size)
        for ((component, intent) in targets) {
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
        assertEquals(
            // In FeedTargets' order: TikTok (not installed), Instagram, YouTube.
            listOf("com.instagram.share.ShareHandlerActivity", "com.google.android.youtube.UploadActivity", "com.google.android.youtube.ShortsActivity"),
            targets.map { it.first.className },
        )
    }

    @Test
    fun theSharedIntentItselfKeepsTheVideo() {
        targets()
        @Suppress("DEPRECATION")
        assertEquals(video, send.getParcelableExtra(Intent.EXTRA_STREAM))
        assertEquals(video, send.clipData!!.getItemAt(0).uri)
    }

    @Test
    fun noFeedAppInstalledMeansNoEntries() {
        assertEquals(emptyList(), ShareIntents.feedTargets(send, feed) { emptyList() })
    }
}
