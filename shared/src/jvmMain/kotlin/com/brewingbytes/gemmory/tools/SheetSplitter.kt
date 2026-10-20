package com.brewingbytes.gemmory.tools

import com.brewingbytes.gemmory.converter.AspectRatio
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Build-time only (T44): cuts a ChatGPT sheet of six pictures (`tools/remote/prompts/sheet-2x3.md`) into its tiles.
 * The gutters are the columns and rows that are almost all near-white; what's between them is a tile. Each tile is
 * trimmed of its own white edge, cut [INSET_PX] further in (the soft edge a generated gutter leaves) and cropped to
 * the sheet's aspect, centered. A sheet that doesn't give six usable tiles stops with a [SplitError].
 */
object SheetSplitter {
    /** A tile's short side after the crop: about 2× the biggest grid side (Big 1:1 is 122). */
    const val MIN_TILE_PX = 256

    const val INSET_PX = 4

    /** A channel this bright counts as gutter white. */
    private const val WHITE = 235

    /** A column or row this white (share of its pixels) is gutter. */
    private const val GUTTER_SHARE = 0.97

    /** Content bands thinner than this share of the sheet are noise in a gutter, not a tile. */
    private const val MIN_BAND_SHARE = 0.05

    sealed class SplitError(message: String) : IllegalArgumentException(message) {
        class NoGutters : SplitError("no white gutters found: the sheet must be 6 pictures on white, 3×2 or 2×3")

        class TileCount(val found: Int) : SplitError("found $found tiles, not 6: regenerate the sheet (3×2 or 2×3 on white gutters)")

        class Layout(val columns: Int, val rows: Int) :
            SplitError("the tiles are $columns×$rows with some blank, not 3×2 or 2×3: regenerate the sheet")

        class TooSmall(val tile: Int, val width: Int, val height: Int) :
            SplitError("tile $tile is ${width}×$height after the crop; its short side must be at least $MIN_TILE_PX px")
    }

    /** The six tiles in reading order (left to right, then top to bottom). */
    fun split(sheet: BufferedImage, ratio: AspectRatio): List<BufferedImage> {
        val w = sheet.width
        val h = sheet.height
        val white = BooleanArray(w * h) { i -> isWhite(sheet.getRGB(i % w, i / w)) }
        val columns = bands(w, (w * MIN_BAND_SHARE).toInt()) { x -> (0 until h).count { y -> white[y * w + x] } >= h * GUTTER_SHARE }
        val rows = bands(h, (h * MIN_BAND_SHARE).toInt()) { y -> (0 until w).count { x -> white[y * w + x] } >= w * GUTTER_SHARE }
        if (columns.size * rows.size == 1) throw SplitError.NoGutters()
        val cells = rows.flatMap { r -> columns.map { c -> trim(white, w, c, r) } }
        val found = cells.count { it != null }
        if (found != 6) throw SplitError.TileCount(found)
        if (columns.size * rows.size != 6) throw SplitError.Layout(columns.size, rows.size)
        return cells.mapIndexed { i, cell ->
            val (c, r) = cell!!
            val tile = crop(sheet.getSubimage(c.first + INSET_PX, r.first + INSET_PX, c.last - c.first + 1 - 2 * INSET_PX, r.last - r.first + 1 - 2 * INSET_PX), ratio)
            if (minOf(tile.width, tile.height) < MIN_TILE_PX) throw SplitError.TooSmall(i + 1, tile.width, tile.height)
            tile
        }
    }

    private fun isWhite(argb: Int): Boolean =
        (argb shr 16 and 0xFF) >= WHITE && (argb shr 8 and 0xFF) >= WHITE && (argb and 0xFF) >= WHITE

    /** The runs of non-gutter positions along one axis, dropping runs thinner than [minBand]. */
    private fun bands(length: Int, minBand: Int, gutter: (Int) -> Boolean): List<IntRange> {
        val out = ArrayList<IntRange>()
        var start = -1
        for (i in 0..length) {
            val content = i < length && !gutter(i)
            if (content && start < 0) start = i
            if (!content && start >= 0) {
                if (i - start >= minBand) out += start until i
                start = -1
            }
        }
        return out
    }

    /** The cell's own content box, without its white edge; null when the cell is blank. */
    private fun trim(white: BooleanArray, w: Int, columns: IntRange, rows: IntRange): Pair<IntRange, IntRange>? {
        fun columnWhite(x: Int) = rows.count { y -> white[y * w + x] } >= rows.count() * GUTTER_SHARE
        fun rowWhite(y: Int) = columns.count { x -> white[y * w + x] } >= columns.count() * GUTTER_SHARE
        val left = columns.firstOrNull { !columnWhite(it) } ?: return null
        val right = columns.last { !columnWhite(it) }
        val top = rows.firstOrNull { !rowWhite(it) } ?: return null
        val bottom = rows.last { !rowWhite(it) }
        if (right - left + 1 <= 2 * INSET_PX || bottom - top + 1 <= 2 * INSET_PX) return null
        return (left..right) to (top..bottom)
    }

    /** The largest centered [ratio] box inside [image]. */
    fun crop(image: BufferedImage, ratio: AspectRatio): BufferedImage {
        val (cw, ch) = if (image.width.toLong() * ratio.height > image.height.toLong() * ratio.width) {
            (image.height.toLong() * ratio.width / ratio.height).toInt() to image.height
        } else {
            image.width to (image.width.toLong() * ratio.height / ratio.width).toInt()
        }
        return image.getSubimage((image.width - cw) / 2, (image.height - ch) / 2, cw, ch)
    }

    /** `<sheet> <ratio 1:1|3:4|4:3> <out dir>`: writes `tile-1.jpg` … `tile-6.jpg` to look at before filling the CSV. */
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 3) { "usage: SheetSplitter <sheet image> <ratio 1:1|3:4|4:3> <out dir>" }
        val sheet = ImageIO.read(File(args[0])) ?: error("cannot read image ${args[0]}")
        val ratio = AspectRatio.entries.firstOrNull { it.label == args[1] } ?: error("ratio must be one of ${AspectRatio.entries.map { it.label }}")
        val out = File(args[2]).apply { mkdirs() }
        split(sheet, ratio).forEachIndexed { i, tile ->
            val rgb = BufferedImage(tile.width, tile.height, BufferedImage.TYPE_INT_RGB)
            rgb.createGraphics().apply { drawImage(tile, 0, 0, null); dispose() }
            ImageIO.write(rgb, "jpg", File(out, "tile-${i + 1}.jpg"))
            println("tile ${i + 1}: ${tile.width}×${tile.height}")
        }
        println("wrote 6 tiles to $out")
    }
}
