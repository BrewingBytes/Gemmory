package com.brewingbytes.gemmory.tools

import com.brewingbytes.gemmory.converter.GridSpec
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.converter.convert
import com.brewingbytes.gemmory.save.GridCodec
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Build-time only (design D13, eng D11): converts a bundled picture's source image once and freezes the result as a
 * GridCodec file. The app copies that file as is when a player opens the picture and never re-converts it, so a
 * later converter change can't move a saved cell index.
 *
 * Run through `./gradlew :shared:freezeBundledGrid -Psource=… -Pspec=starter-3:4 -Pout=…`. Re-freezing a shipped
 * picture changes its grid CRC and orphans every save made on it, so only freeze new pictures.
 */
object BundledGridTool {
    /** Same seed as the photo flow, so a bundled picture converts like a player's photo would. */
    const val SEED = 0x47_4D_52_59L

    /** Same cap as the photo flow's decode (eng finding 14). */
    const val MAX_SOURCE_PX = 1024

    val SPECS: Map<String, GridSpec> = (GridSpec.PICKER + GridSpec.STARTER).associateBy { it.id }

    /**
     * Crops [image] to [spec]'s ratio, scales it to at most 1024 px on the long edge and converts it. [focus] places
     * the crop along the axis being cut: 0 = left/top, 0.5 = centered, 1 = right/bottom.
     */
    fun convertImage(image: BufferedImage, spec: GridSpec, focus: Double = 0.5): Pattern {
        val prepared = downscale(crop(image, spec, focus))
        val pixels = prepared.getRGB(0, 0, prepared.width, prepared.height, null, 0, prepared.width)
        return convert(pixels, prepared.width, prepared.height, spec, SEED)
    }

    fun freeze(image: BufferedImage, spec: GridSpec, focus: Double = 0.5): ByteArray =
        GridCodec.encode(convertImage(image, spec, focus))

    fun crop(image: BufferedImage, spec: GridSpec, focus: Double = 0.5): BufferedImage {
        require(focus in 0.0..1.0) { "focus must be in 0..1, was $focus" }
        val (w, h) = if (image.width.toLong() * spec.height > image.height.toLong() * spec.width) {
            (image.height.toLong() * spec.width / spec.height).toInt() to image.height
        } else {
            image.width to (image.width.toLong() * spec.height / spec.width).toInt()
        }
        return image.getSubimage(((image.width - w) * focus).roundToInt(), ((image.height - h) * focus).roundToInt(), w, h)
    }

    private fun downscale(image: BufferedImage): BufferedImage {
        val longEdge = max(image.width, image.height)
        val scale = if (longEdge > MAX_SOURCE_PX) MAX_SOURCE_PX.toDouble() / longEdge else 1.0
        val w = max(1, (image.width * scale).roundToInt())
        val h = max(1, (image.height * scale).roundToInt())
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.drawImage(image, 0, 0, w, h, null)
        } finally {
            g.dispose()
        }
        return out
    }

    /** A review image of the frozen grid: one flat [cellPx] square per cell. */
    fun preview(pattern: Pattern, cellPx: Int = 16): BufferedImage {
        val out = BufferedImage(pattern.width * cellPx, pattern.height * cellPx, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until out.height) for (x in 0 until out.width) {
            out.setRGB(x, y, pattern.palette[pattern.slotAt(x / cellPx, y / cellPx)].argb)
        }
        return out
    }

    /**
     * `<source image> <spec id> <focus 0..1> <out grid file> [review png]`. The review PNG must not go into the
     * app's assets.
     */
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size in 4..5) {
            "usage: BundledGridTool <source image> <spec id> <focus 0..1> <out grid file> [review png]"
        }
        val image = ImageIO.read(File(args[0])) ?: error("cannot read image ${args[0]}")
        val spec = SPECS[args[1]] ?: error("unknown spec ${args[1]}; one of ${SPECS.keys}")
        val focus = args[2].toDouble()
        val out = File(args[3])
        val pattern = convertImage(image, spec, focus)
        out.parentFile?.mkdirs()
        out.writeBytes(GridCodec.encode(pattern))
        args.getOrNull(4)?.let { ImageIO.write(preview(pattern), "png", File(it)) }
        println("froze ${spec.id} (${pattern.width}×${pattern.height}, ${pattern.palette.size} colors) → $out")
    }
}
