package com.brewingbytes.gemmory.streak

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.converter.Lab
import com.brewingbytes.gemmory.converter.PaletteColor
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.policy.CalendarCell
import com.brewingbytes.gemmory.policy.CardDay
import com.brewingbytes.gemmory.policy.CellKind
import com.brewingbytes.gemmory.policy.StreakShareCard
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The milestone share card's renderer (#104, G14): 1080×1920, and a photo canvas is never drawn, not even when the
 * week logged one and a loader would hand its art over. Plus one golden card.
 */
@RunWith(AndroidJUnit4::class)
class StreakShareCardTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
    private val renderer = StreakShareCardRenderer(context)
    private val letters = listOf("M", "T", "W", "T", "F", "S", "S")
    private val monday = 20_000L

    /** A [width]×[height] grid in the one [argb] color. */
    private fun solid(argb: Int, width: Int = 8, height: Int = 8) =
        Pattern(width, height, listOf(PaletteColor(1, argb, Lab(50.0, 0.0, 0.0))), IntArray(width * height))

    /** Stand-in pictures: a few colored bands, so the golden shows real art in its cells. */
    private fun banded(seed: Int) = Pattern(
        12,
        12,
        List(4) { PaletteColor(it + 1, Color.rgb(40 + 50 * it, 90 + seed * 20 % 120, 160 - 30 * it), Lab(it * 20.0, 0.0, 0.0)) },
        IntArray(144) { (it / 12 + seed) / 3 % 4 },
    )

    private fun isRed(pixel: Int) = Color.red(pixel) > 200 && Color.green(pixel) < 40 && Color.blue(pixel) < 40

    @Test
    fun aPhotoCanvasIsNeverDrawn() {
        val red = solid(Color.RED)
        // Monday a photo only, Tuesday a photo then a bundled picture, Wednesday the milestone with a photo logged.
        val week = listOf(
            CalendarCell(monday, CellKind.FINISHED, "PHOTO/me"),
            CalendarCell(monday + 1, CellKind.FINISHED, "PHOTO/dog"),
            CalendarCell(monday + 2, CellKind.FINISHED, "PHOTO/cat", today = true),
        ) + (3 until 7).map { CalendarCell(monday + it, CellKind.FUTURE) }
        val finishes = mapOf(monday + 1 to listOf("PHOTO/dog", "BUNDLED/lighthouse"))
        val days = StreakShareCard.days(week, monday + 2, finishes, CardPictures::isPicture)
        assertEquals(listOf(CardDay.Flame, CardDay.Picture("BUNDLED/lighthouse"), CardDay.Flame), days.take(3))

        // A loader that would draw any canvas in pure red, photos included: only the bundled picture is asked for.
        val asked = mutableListOf<CanvasRef>()
        val card = renderer.render(7, "A week of gems", days, letters) { ref ->
            asked += ref
            CardPictures.art(ref, red)
        }
        assertEquals(listOf(CanvasRef(CanvasKind.BUNDLED, "lighthouse")), asked)
        assertEquals(StreakShareCardRenderer.WIDTH to StreakShareCardRenderer.HEIGHT, card.width to card.height)

        // And with photo-only days, nothing red reaches the card at all.
        val photosOnly = StreakShareCard.days(week, monday + 2, emptyMap(), CardPictures::isPicture)
        val clean = renderer.render(7, "A week of gems", photosOnly, letters) { ref -> CardPictures.art(ref, red) }
        val pixels = IntArray(clean.width * clean.height).also { clean.getPixels(it, 0, clean.width, 0, 0, clean.width, clean.height) }
        assertTrue(pixels.none(::isRed), "no photo pixels on the card")
    }

    /** The renderer's own guard: a photo canvas handed over as a picture is never asked for, nor drawn. */
    @Test
    fun theRendererRefusesAPhotoCanvasAsAPicture() {
        val asked = mutableListOf<CanvasRef>()
        val redArt = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val days = listOf(CardDay.Picture("PHOTO/me")) + List(6) { CardDay.Empty }
        val card = renderer.render(7, "A week of gems", days, letters) { ref ->
            asked += ref
            redArt
        }
        assertTrue(asked.isEmpty(), "asked for $asked")
        val pixels = IntArray(card.width * card.height).also { card.getPixels(it, 0, card.width, 0, 0, card.width, card.height) }
        assertTrue(pixels.none(::isRed), "no photo pixels on the card")
    }

    @Test
    fun aPhotoCanvasHasNoArt() {
        assertNull(CardPictures.art(CanvasRef(CanvasKind.PHOTO, "me"), solid(Color.RED)))
        assertTrue(!CardPictures.isPicture("PHOTO/me") && CardPictures.isPicture("BUNDLED/a") && CardPictures.isPicture("REMOTE/fox"))
        assertTrue(!CardPictures.isPicture("nonsense"))
    }

    /** The 7-day card of mockup streak-d state 1 matches its golden (made once on the emulator, see below). */
    @Test
    fun theSevenDayCardMatchesItsGolden() {
        val days = listOf(
            CardDay.Picture("BUNDLED/a"),
            CardDay.Flame,
            CardDay.Picture("BUNDLED/b"),
            CardDay.Picture("REMOTE/c"),
            CardDay.Empty,
            CardDay.Empty,
            CardDay.Empty,
        )
        val art = mapOf("a" to banded(1), "b" to banded(2), "c" to banded(3))
        val card = renderer.render(7, "A week of gems", days, letters) { ref -> art[ref.id]?.let { CardPictures.art(ref, it) } }
        val golden = runCatching { testAssets.open(GOLDEN).use(BitmapFactory::decodeStream) }.getOrNull()
        if (golden == null) {
            // First run: save the card for review and commit (androidTest/assets/streak-card-7.png).
            File(context.cacheDir, GOLDEN).outputStream().use { card.compress(Bitmap.CompressFormat.PNG, 100, it) }
            fail("No golden yet: pull ${context.cacheDir}/$GOLDEN")
        }
        assertEquals(golden.width to golden.height, card.width to card.height)
        val a = IntArray(card.width * card.height).also { card.getPixels(it, 0, card.width, 0, 0, card.width, card.height) }
        val b = IntArray(a.size).also { golden.getPixels(it, 0, golden.width, 0, 0, golden.width, golden.height) }
        // Font rasterizing can move an edge pixel between devices; the layout and colors can't move.
        val off = a.indices.count { i ->
            abs(Color.red(a[i]) - Color.red(b[i])) + abs(Color.green(a[i]) - Color.green(b[i])) + abs(Color.blue(a[i]) - Color.blue(b[i])) > 24
        }
        assertTrue(off < a.size / 200, "$off of ${a.size} pixels differ from the golden")
    }

    private companion object {
        const val GOLDEN = "streak-card-7.png"
    }
}
