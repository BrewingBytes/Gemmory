package com.brewingbytes.gemmory.converter

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.fail

/**
 * Frozen converter output (eng D19, frozen at spike GO on 2026-10-13): 9 picker specs × face/pet/landscape.
 * Any change to the converter's output fails here. If the change is intended, run
 * `./gradlew :shared:updateConverterGoldens`, review the regenerated PNGs, and commit both.
 *
 * Lives in jvmTest (not commonTest) because it reads PNG fixtures and golden files from disk.
 */
class ConverterGoldenTest {
    private val fixturesDir = File(System.getProperty("gemmory.fixturesDir") ?: "src/jvmTest/fixtures")
    private val goldensDir = File(System.getProperty("gemmory.goldensDir") ?: "src/jvmTest/goldens")
    private val update = System.getProperty("gemmory.updateGoldens") == "true"

    @Test
    fun outputMatchesTheFrozenGoldens() {
        val mismatches = mutableListOf<String>()
        for (fixture in FIXTURES) {
            val image = ImageIO.read(File(fixturesDir, "$fixture.png")) ?: fail("cannot read fixture $fixture.png")
            for (spec in GridSpec.PICKER) {
                val name = "$fixture-${spec.id.replace(':', 'x')}"
                val crop = centerCrop(image, spec)
                val pixels = crop.getRGB(0, 0, crop.width, crop.height, null, 0, crop.width)
                val pattern = convert(pixels, crop.width, crop.height, spec, SEED)
                val actual = encode(pattern, spec, fixture)
                val golden = File(goldensDir, "$name.txt")
                if (update) {
                    goldensDir.mkdirs()
                    golden.writeText(actual)
                    ImageIO.write(preview(pattern), "png", File(goldensDir, "$name.png"))
                } else if (!golden.exists()) {
                    mismatches += "$name: missing golden"
                } else if (golden.readText() != actual) {
                    mismatches += "$name: output differs"
                }
            }
        }
        if (mismatches.isNotEmpty()) {
            fail(
                "Converter output changed for ${mismatches.size} of ${FIXTURES.size * GridSpec.PICKER.size} goldens:\n" +
                    mismatches.joinToString("\n") +
                    "\nIf intended: ./gradlew :shared:updateConverterGoldens, review the PNGs, commit both.",
            )
        }
    }

    /** Crops the fixture to the spec's aspect ratio, centered, the way a player's crop would. */
    private fun centerCrop(image: BufferedImage, spec: GridSpec): BufferedImage {
        val (w, h) = if (image.width.toLong() * spec.height > image.height.toLong() * spec.width) {
            (image.height * spec.width / spec.height) to image.height
        } else {
            image.width to (image.width * spec.height / spec.width)
        }
        return image.getSubimage((image.width - w) / 2, (image.height - h) / 2, w, h)
    }

    /** Text golden: header, palette as ARGB hex, then one row per grid line with one base-36 digit per cell. */
    private fun encode(p: Pattern, spec: GridSpec, fixture: String): String = buildString {
        appendLine("gemmory-converter-golden v1")
        appendLine("spec ${spec.id} ${spec.width}x${spec.height} cap ${spec.paletteCap} fixture $fixture seed $SEED")
        appendLine("palette ${p.palette.size}")
        p.palette.forEach { appendLine("%08X".format(it.argb)) }
        appendLine("cells")
        for (y in 0 until p.height) {
            for (x in 0 until p.width) append(p.slotAt(x, y).toString(36))
            appendLine()
        }
    }

    private fun preview(p: Pattern): BufferedImage {
        val cell = 4
        val out = BufferedImage(p.width * cell, p.height * cell, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until out.height) for (x in 0 until out.width) {
            out.setRGB(x, y, p.palette[p.slotAt(x / cell, y / cell)].argb)
        }
        return out
    }

    private companion object {
        val FIXTURES = listOf("face", "pet", "landscape")
        /** Same seed the app uses. */
        const val SEED = 0x47_4D_52_59L
    }
}
