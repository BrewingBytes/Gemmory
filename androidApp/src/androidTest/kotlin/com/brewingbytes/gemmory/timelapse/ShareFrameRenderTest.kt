package com.brewingbytes.gemmory.timelapse

import com.brewingbytes.gemmory.copy.strings
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.bundled.BundledPicture
import com.brewingbytes.gemmory.bundled.BundledPictures
import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.save.GridCodec
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The player's share frame (T53) as drawn on a device: a non-VIP share has the Gemmory row with Google's badge and the
 * end card, VIP has neither, and the progress share and the PNG fallback get the row too. A founder clip keeps its old
 * frame (no overlay, no card).
 */
@RunWith(AndroidJUnit4::class)
class ShareFrameRenderTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.cacheDir, "share-frame").apply { mkdirs() }

    @After
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun request(picture: BundledPicture = BundledPictures.STARTER, logSize: Int? = null, watermark: Boolean = true, frame: ShareFrame = ShareFrame.PLAYER): EncodeRequest {
        val bytes = context.assets.open(picture.asset).use { it.readBytes() }
        val pattern = (GridCodec.decode(bytes) as Decoded.Ok).value.pattern
        val log = pattern.cells.indices.shuffled(Random(7)).toIntArray()
        return EncodeRequest(CanvasRef(CanvasKind.BUNDLED, picture.id), pattern, log.copyOf(logSize ?: log.size), picture.title(context.strings), source = null, watermark = watermark, frame = frame)
    }

    /** Pixels of the badge's white lettering in [rect] of [bitmap]: nothing else in the frame is pure white. */
    private fun whitePixels(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Int {
        var n = 0
        for (y in maxOf(0, top) until minOf(bitmap.height, bottom)) for (x in maxOf(0, left) until minOf(bitmap.width, right)) {
            val p = bitmap.getPixel(x, y)
            if ((p ushr 24) > 200 && (p shr 16 and 0xFF) >= 250 && (p shr 8 and 0xFF) >= 250 && (p and 0xFF) >= 250) n++
        }
        return n
    }

    private fun layout(r: EncodeRequest) = ShareLayout.of(1080, 1920, r.pattern.width, r.pattern.height, branded = true)

    @Test
    fun aNonVipShareHasTheRowAndTheEndCard() {
        val r = request()
        val art = FrameArt(context, r, 1080, 1920)
        try {
            val l = layout(r)
            val overlay = assertNotNull(art.overlay)
            val overlayRect = assertNotNull(art.overlayRect)
            val badge = assertNotNull(l.row).badge
            assertTrue(whitePixels(overlay, badge.left, badge.top - overlayRect.top, badge.right, badge.bottom - overlayRect.top) > 200, "the row's badge")
            val card = assertNotNull(art.card)
            val rects = assertNotNull(art.endCard(1f))
            val cardBadge = assertNotNull(l.card).badge
            assertTrue(whitePixels(card, cardBadge.left, cardBadge.top - rects.content.top, cardBadge.right, cardBadge.bottom - rects.content.top) > 500, "the card's badge")
            assertEquals(l.card!!.picture.top, rects.picture.top, "in place once faded in")
            assertEquals(l.card!!.picture.top + 30, art.endCard(0f)!!.picture.top, "slides up 30px")
            assertEquals(l.board.top, art.boardRect.top)
        } finally {
            art.release()
        }
    }

    @Test
    fun vipHasNeitherTheRowNorTheEndCard() {
        val r = request(watermark = false)
        val art = FrameArt(context, r, 1080, 1920)
        try {
            assertNull(art.card)
            assertNull(art.endCard(1f))
            val overlay = assertNotNull(art.overlay, "the fade and title still")
            assertEquals(0, whitePixels(overlay, 0, 0, overlay.width, overlay.height), "no badge anywhere")
            assertEquals(1920, art.overlayRect!!.bottom)
            assertEquals(ShareLayout.of(1080, 1920, r.pattern.width, r.pattern.height, branded = false).board.top, art.boardRect.top)
        } finally {
            art.release()
        }
    }

    @Test
    fun aProgressShareHasTheRowAndItsCardShowsTheCanvasAsItStands() {
        val r = request(logSize = 300)
        assertTrue(r.inProgress && r.endCard)
        val art = FrameArt(context, r, 1080, 1920)
        try {
            val badge = layout(r).row!!.badge
            assertTrue(whitePixels(art.overlay!!, badge.left, badge.top - art.overlayRect!!.top, badge.right, badge.bottom - art.overlayRect.top) > 200)
            assertNotNull(art.card)
            // The card draws the canvas itself: exactly what the hold showed, 300 of the starter's 768 gems.
            art.apply(Frame.Hold)
            val held = art.canvas.copy(Bitmap.Config.ARGB_8888, false)
            assertEquals(false, art.apply(Frame.EndCard(1f)).first, "no gem added for the card")
            assertTrue(held.sameAs(art.canvas))
            held.recycle()
        } finally {
            art.release()
        }
    }

    @Test
    fun thePngFallbackHasTheRowForNonVipOnly() {
        for ((watermark, expectRow) in listOf(true to true, false to false)) {
            val r = request(logSize = 400, watermark = watermark)
            val png = FrameArt.beforeAfter(context, r)
            val badge = layout(r).row!!.badge
            val white = whitePixels(png, badge.left, badge.top, badge.right, badge.bottom)
            assertEquals(expectRow, white > 200, "watermark=$watermark: $white white pixels in the row's badge")
            png.recycle()
        }
    }

    /** A founder clip (#144): its own layout, the lockup over the canvas, the finished canvas as the hook, the E2b card. */
    @Test
    fun aFounderClipHasItsOwnFrame() {
        val r = request(frame = ShareFrame.CLIP)
        for ((w, h) in listOf(1080 to 1920, 1080 to 1350)) {
            val art = FrameArt(context, r, w, h)
            try {
                val layout = ClipLayout.of(w, h, r.pattern.width, r.pattern.height, titleLines = 1, lockupTextWidth = 0)
                assertEquals(layout.board.bottom, art.boardRect.bottom, "${w}x$h board above the title")
                assertEquals(layout.board.width, art.boardRect.width(), "${w}x$h")
                assertNotNull(art.overlay)
                assertNotNull(art.card)
                // The hook: the finished canvas, at full opacity in the hook only.
                assertEquals(false to 1f, art.apply(Frame.Hook))
                assertEquals(false to 0f, art.apply(Frame.Intro(0f)))
                val card = assertNotNull(art.endCard(0.5f))
                assertEquals(FrameStyle.CLIP_END_CARD_DIM, card.dim)
                assertEquals(art.endCard(1f)!!.picture, card.picture, "a crossfade, no slide")
            } finally {
                art.release()
            }
        }
    }

    /**
     * The feed cut (T51): the founder clip's 9:16 frame without the end card. Its hook puts the whole log on the
     * canvas, and the first build frame goes back to exactly the tint grid it started from.
     */
    @Test
    fun theFeedCutHasTheClipFrameWithoutTheCard() {
        val r = request(frame = ShareFrame.FEED)
        val art = FrameArt(context, r, 1080, 1920)
        val fresh = FrameArt(context, r, 1080, 1920)
        try {
            val layout = ClipLayout.of(1080, 1920, r.pattern.width, r.pattern.height, titleLines = 1, lockupTextWidth = 0)
            assertEquals(layout.board.bottom, art.boardRect.bottom, "board above the title, as a founder clip's")
            assertEquals(layout.board.width, art.boardRect.width())
            assertNotNull(art.overlay, "the title and the lockup on every frame")
            assertNull(art.card, "no end card")
            assertNull(art.endCard(1f))
            assertNull(art.source, "a bundled picture has no photo over its hook")
            assertEquals(true to 0f, art.apply(Frame.Reveal(0f)), "the hook draws every gem at once")
            assertEquals(false to 0f, art.apply(Frame.Reveal(0f)))
            assertTrue(!art.canvas.sameAs(fresh.canvas), "the hook shows the finished canvas")
            assertEquals(true to 0f, art.apply(Frame.Build(0)), "the cut")
            assertTrue(art.canvas.sameAs(fresh.canvas), "back to the very tint grid it started from")
            art.apply(Frame.Build(r.log.size))
            assertEquals(false to 0f, art.apply(Frame.Hold))
        } finally {
            art.release()
            fresh.release()
        }
    }

    /** #144 review: a clip title's lines are exactly 70px apart in every face, Bulgarian's and Japanese's taller ones too. */
    @Test
    fun aClipTitlesLinesAre70pxInEveryFace() {
        val titles = mapOf(
            "en" to "The Great Wave off Kanagawa, a woodblock print by Hokusai",
            "bg" to "Голямата вълна край Канагава, дърворез от Хокусай",
            "ja" to "神奈川沖浪裏、葛飾北斎の木版画、富士山を望む",
        )
        for ((tag, title) in titles) {
            val localized = context.createConfigurationContext(
                Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) },
            )
            for (width in listOf(1080, 720)) {
                val style = FrameStyle(localized, width)
                val layout = style.clipTitleLayout(title)
                assertEquals(2, layout.lineCount, "$tag at $width wraps to 2 lines")
                assertEquals(0, layout.getLineTop(0), tag)
                assertTrue(abs(layout.getLineTop(1) - style.px(ClipLayout.TITLE_LINE)) <= 1, "$tag at $width: line 2 at ${layout.getLineTop(1)}")
            }
        }
    }

    @Test
    fun theBadgeIsGooglesFileUnchanged() {
        val bytes = context.assets.open(FrameStyle.BADGE_ASSET).use { it.readBytes() }
        // SHA-1 of en_badge_web_generic.png from play.google.com/intl/en_us/badges (docs/designs/mockups/share-video-ad/badge/).
        val sha1 = java.security.MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals("cb8126b32c2274e0394246b40bd0b7f9f847e44c", sha1)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        assertEquals(ShareLayout.BADGE_FILE_WIDTH to ShareLayout.BADGE_FILE_HEIGHT, options.outWidth to options.outHeight)
    }
}
