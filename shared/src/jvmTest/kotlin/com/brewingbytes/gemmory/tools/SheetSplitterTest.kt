package com.brewingbytes.gemmory.tools

import com.brewingbytes.gemmory.converter.AspectRatio
import java.awt.image.BufferedImage
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The sheet splitter (T44): six tiles in reading order, gutters trimmed, a named error for anything else. */
class SheetSplitterTest {
    companion object {
        /** One distinct hue per tile, so a test can tell which tile came out where. */
        val HUES = listOf(0xC0392B, 0x2E86C1, 0x27AE60, 0x8E44AD, 0xD68910, 0x5D4037)

        /**
         * A sheet like ChatGPT's: [columns]×[rows] noisy tiles on white, with [gutter] px between them and around them,
         * and each tile drawn [margin] px inside its cell (a white edge the trim must remove). [blank] cells stay white.
         */
        fun sheet(width: Int, height: Int, columns: Int, rows: Int, gutter: Int = 24, margin: Int = 6, blank: Set<Int> = emptySet()): BufferedImage {
            val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            for (y in 0 until height) for (x in 0 until width) image.setRGB(x, y, 0xFFFFFF)
            val cellW = (width - gutter * (columns + 1)) / columns
            val cellH = (height - gutter * (rows + 1)) / rows
            val random = Random(7)
            for (r in 0 until rows) for (c in 0 until columns) {
                val i = r * columns + c
                if (i in blank) continue
                val x0 = gutter + c * (cellW + gutter) + margin
                val y0 = gutter + r * (cellH + gutter) + margin
                for (y in y0 until y0 + cellH - 2 * margin) for (x in x0 until x0 + cellW - 2 * margin) {
                    val shade = random.nextInt(48)
                    val hue = HUES[i % HUES.size]
                    val rgb = (((hue shr 16 and 0xFF) - shade).coerceAtLeast(0) shl 16) or
                        (((hue shr 8 and 0xFF) - shade).coerceAtLeast(0) shl 8) or ((hue and 0xFF) - shade).coerceAtLeast(0)
                    image.setRGB(x, y, rgb)
                }
            }
            return image
        }

        private fun isWhite(rgb: Int) = (rgb shr 16 and 0xFF) > 230 && (rgb shr 8 and 0xFF) > 230 && (rgb and 0xFF) > 230
    }

    private fun hueOf(tile: BufferedImage): Int {
        val rgb = tile.getRGB(tile.width / 2, tile.height / 2)
        return HUES.minBy { h ->
            listOf(16, 8, 0).sumOf { s -> kotlin.math.abs((h shr s and 0xFF) - (rgb shr s and 0xFF)) }
        }
    }

    @Test
    fun aSquareSheetGivesSixSquaresInReadingOrder() {
        val tiles = SheetSplitter.split(sheet(1536, 1024, columns = 3, rows = 2), AspectRatio.SQUARE)
        assertEquals(6, tiles.size)
        assertTrue(tiles.all { it.width == it.height && it.width >= SheetSplitter.MIN_TILE_PX }, tiles.map { "${it.width}×${it.height}" }.toString())
        assertEquals(HUES, tiles.map(::hueOf))
    }

    @Test
    fun theGuttersAndTheTilesWhiteEdgeAreTrimmed() {
        for (tile in SheetSplitter.split(sheet(1536, 1024, columns = 3, rows = 2, margin = 10), AspectRatio.SQUARE)) {
            val edges = (0 until tile.width).flatMap { x -> listOf(tile.getRGB(x, 0), tile.getRGB(x, tile.height - 1)) } +
                (0 until tile.height).flatMap { y -> listOf(tile.getRGB(0, y), tile.getRGB(tile.width - 1, y)) }
            assertTrue(edges.none(::isWhite), "a white edge pixel is left")
        }
    }

    @Test
    fun portraitAndLandscapeTilesAreCroppedToTheSheetsAspect() {
        val portrait = SheetSplitter.split(sheet(1024, 1024, columns = 3, rows = 2), AspectRatio.PORTRAIT)
        assertTrue(portrait.all { kotlin.math.abs(it.width * 4 - it.height * 3) <= 4 }, portrait.map { "${it.width}×${it.height}" }.toString())
        val landscape = SheetSplitter.split(sheet(1024, 1024, columns = 2, rows = 3), AspectRatio.LANDSCAPE)
        assertTrue(landscape.all { kotlin.math.abs(it.width * 3 - it.height * 4) <= 4 }, landscape.map { "${it.width}×${it.height}" }.toString())
        assertEquals(HUES, landscape.map(::hueOf), "a 2×3 sheet reads left to right, then down")
    }

    @Test
    fun aSheetWithFiveTilesStopsWithANamedError() {
        val error = assertFailsWith<SheetSplitter.SplitError.TileCount> {
            SheetSplitter.split(sheet(1536, 1024, columns = 3, rows = 2, blank = setOf(4)), AspectRatio.SQUARE)
        }
        assertEquals(5, error.found)
        assertEquals(4, assertFailsWith<SheetSplitter.SplitError.TileCount> {
            SheetSplitter.split(sheet(1536, 1024, columns = 2, rows = 2), AspectRatio.SQUARE)
        }.found)
    }

    @Test
    fun aThreeByThreeSheetWithBlanksIsNotSixTiles() {
        assertFailsWith<SheetSplitter.SplitError.Layout> {
            SheetSplitter.split(sheet(1536, 1536, columns = 3, rows = 3, blank = setOf(1, 5, 6)), AspectRatio.SQUARE)
        }
    }

    @Test
    fun aSheetWithoutGuttersOrTooSmallStops() {
        assertFailsWith<SheetSplitter.SplitError.NoGutters> {
            SheetSplitter.split(sheet(1536, 1024, columns = 1, rows = 1, gutter = 0, margin = 0), AspectRatio.SQUARE)
        }
        val small = assertFailsWith<SheetSplitter.SplitError.TooSmall> { SheetSplitter.split(sheet(768, 512, columns = 3, rows = 2), AspectRatio.SQUARE) }
        assertEquals(1, small.tile)
    }
}
