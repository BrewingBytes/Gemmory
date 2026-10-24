package com.brewingbytes.gemmory.tools

import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.font.LineBreakMeasurer
import java.awt.font.TextAttribute
import java.awt.font.TextLayout
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import java.text.AttributedString
import javax.imageio.ImageIO

/**
 * Build-time only (T49 X2, #136): the Play listing's five phone screenshots, captioned, per locale. It takes the raw
 * shots `StoreScreenshots` captures in that language (`-e appLocale de` → `store-de-1-gallery.png`) and each locale's
 * `tools/store/listing/<locale>/captions.txt`, and draws 1080×1920 frames: the caption in Bone on the felt ground, in
 * the language's display face, and the screenshot below it with rounded corners. The founder uploads them.
 * README → Store listings.
 */
object StoreFrames {
    /** Play listing locale → the app language its shots and caption face come from. es-419 shares Spain's text (C3). */
    val LOCALES = linkedMapOf(
        "en-US" to "en", "de-DE" to "de", "es-ES" to "es", "es-419" to "es", "pt-BR" to "pt", "fr-FR" to "fr",
        "it-IT" to "it", "ro" to "ro", "pl-PL" to "pl", "tr-TR" to "tr", "hu-HU" to "hu", "bg" to "bg", "ja-JP" to "ja",
    )

    /** The app tag `StoreScreenshots` ran with for a listing locale (none for English, so its names are as before). */
    fun appTag(locale: String): String? = when (locale) {
        "en-US" -> null
        "pt-BR" -> "pt-BR"
        else -> LOCALES.getValue(locale)
    }

    /** The five shots, in listing order (`StoreScreenshots.capture`). */
    val SHOTS = listOf("1-gallery", "2-canvas", "3-canvas-zoom", "4-completion", "5-paywall")

    const val WIDTH = 1080
    const val HEIGHT = 1920

    private val FELT = Color(0x4F, 0x5A, 0x52)
    private val BONE = Color(0xE9, 0xE4, 0xD8)
    private const val CAPTION_PX = 68f
    private const val CAPTION_SIDE = 72
    private const val CAPTION_BAND = 392
    private const val SHOT_WIDTH = 820
    private const val SHOT_TOP = 400
    private const val CORNER = 36.0

    fun captions(listing: File, locale: String): List<String> =
        File(listing, "$locale/captions.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() }

    /** One captioned frame of [shot] in [language]'s display face from [fontDir]. */
    fun frame(shot: BufferedImage, caption: String, language: String, fontDir: File): BufferedImage {
        val out = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.color = FELT
        g.fillRect(0, 0, WIDTH, HEIGHT)

        // The caption: at most two lines, centered in the band above the shot.
        val face = Font.createFont(Font.TRUETYPE_FONT, File(fontDir, GlyphCheck.faces(language).first())).deriveFont(CAPTION_PX)
        val text = AttributedString(caption).apply { addAttribute(TextAttribute.FONT, face) }
        val measurer = LineBreakMeasurer(text.iterator, g.fontRenderContext)
        val lines = ArrayList<TextLayout>()
        while (measurer.position < caption.length) lines += measurer.nextLayout((WIDTH - 2 * CAPTION_SIDE).toFloat())
        require(lines.size <= 2) { "\"$caption\" takes ${lines.size} lines; keep a caption to two" }
        val lineHeight = CAPTION_PX * if (language == "ja") 1.4f else 1.2f
        var baseline = (CAPTION_BAND - lineHeight * lines.size) / 2 + lineHeight * 0.8f
        g.color = BONE
        for (line in lines) {
            line.draw(g, (WIDTH - line.advance) / 2, baseline)
            baseline += lineHeight
        }

        // The shot, with a soft shadow and rounded corners.
        val h = shot.height * SHOT_WIDTH / shot.width
        val left = (WIDTH - SHOT_WIDTH) / 2.0
        for (i in 12 downTo 1) {
            g.color = Color(0, 0, 0, 6)
            g.fill(RoundRectangle2D.Double(left - i, SHOT_TOP + 14.0 - i, SHOT_WIDTH + 2.0 * i, h + 2.0 * i, CORNER + i, CORNER + i))
        }
        val clip = RoundRectangle2D.Double(left, SHOT_TOP.toDouble(), SHOT_WIDTH.toDouble(), h.toDouble(), CORNER * 2, CORNER * 2)
        g.clip = clip
        g.drawImage(shot, left.toInt(), SHOT_TOP, SHOT_WIDTH, h, null)
        g.dispose()
        return out
    }

    /** Frames every listed locale whose raw shots are in [shots] (or just [only]); returns the files written. */
    fun run(shots: File, listing: File, fontDir: File, out: File, only: String?): List<File> {
        val locales = if (only != null) listOf(only.also { require(it in LOCALES) { "unknown locale $it; one of ${LOCALES.keys}" } }) else LOCALES.keys.toList()
        val written = ArrayList<File>()
        for (locale in locales) {
            val prefix = appTag(locale)?.let { "$it-" }.orEmpty()
            val raw = SHOTS.map { File(shots, "store-$prefix$it.png") }
            if (raw.any { !it.exists() }) {
                require(only == null) { "missing ${raw.filter { !it.exists() }.joinToString { it.name }} in $shots" }
                continue
            }
            val captions = captions(listing, locale)
            require(captions.size == SHOTS.size) { "$locale/captions.txt needs ${SHOTS.size} lines" }
            val dir = File(out, locale).apply { mkdirs() }
            for ((i, file) in raw.withIndex()) {
                val frame = frame(ImageIO.read(file), captions[i], LOCALES.getValue(locale), fontDir)
                written += File(dir, "${SHOTS[i]}.png").also { check(ImageIO.write(frame, "png", it)) { "cannot write $it" } }
            }
        }
        return written
    }

    /** Gradle passes `key=value` pairs (`frameStoreShots` in shared/build.gradle.kts). */
    @JvmStatic
    fun main(args: Array<String>) {
        val a = args.associate { it.substringBefore('=') to it.substringAfter('=', "") }
        fun arg(name: String) = a[name]?.takeIf { it.isNotBlank() } ?: error("missing -P$name")
        val written = run(File(arg("shots")), File(arg("listing")), File(arg("fonts")), File(arg("out")), a["locale"]?.takeIf { it.isNotBlank() })
        require(written.isNotEmpty()) { "no shots found in ${arg("shots")}: run StoreScreenshots with -e appLocale <tag> and pull them first" }
        println("wrote ${written.size} frames to ${arg("out")} (one folder per listing locale)")
    }
}
