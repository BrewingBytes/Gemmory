package com.brewingbytes.gemmory.tools

import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.MultipleGradientPaint
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.font.LineBreakMeasurer
import java.awt.font.TextAttribute
import java.awt.font.TextLayout
import java.awt.geom.AffineTransform
import java.awt.geom.Point2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import java.text.AttributedString
import javax.imageio.ImageIO

/**
 * Build-time only (T49 X2, #136; photo set): the Play listing's five phone screenshots and its feature graphic, per
 * locale. It takes the raw shots `StoreScreenshots.photoCanvases` captures in that language (`-e appLocale de` →
 * `store-de-p-preview.png`) and each locale's `tools/store/listing/<locale>/` `captions.txt` (a title per shot),
 * `subtitles.txt` (a line under it) and `feature.txt` (the feature graphic's tagline and line), and draws the frames
 * the founder uploads: 1080×1920, the title in Bone in the language's display face at 600 and the subtitle in
 * text-muted in its body face, over the felt's top-lit gradient, with the shot in a scrim-colored phone below; and the
 * 1024×500 feature graphic, "Gemmory" and the two lines beside the puppy's gem art (`store-r-puppy-full.png`); and
 * the 1920×1080 tablet set ([TABLET_SHOTS]) for the 7" and 10" listings.
 * README → Store listings. AWT draws a variable font only at its default instance (Bricolage's is 800, Geologica's
 * 100), so the 600 instances live in `tools/store/fonts/`.
 */
object StoreFrames {
    /** Play listing locale → the app language its shots and faces come from. es-419 shares Spain's text (C3). */
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

    /** The five shots, in listing order (`StoreScreenshots.photoCanvases`), and the names their frames get. */
    val SHOTS = listOf("p-preview", "p-zoom", "p-canvas", "p-gallery", "p-completion")
    val FRAMES = listOf("1-your-photo", "2-place-every-gem", "3-watch-it-appear", "4-pets-people-places", "5-finish-and-share")

    /**
     * The tablet set (7" and 10" listings, one 1920×1080 image each): the same titles and subtitles in the same order,
     * the first on the puppy's gem art with the privacy line (`tablet.txt`) under it, the rest on the shots
     * `photoCanvases` captures with `-e storeDisplay tablet` (`store-de-t-p-zoom.png`).
     */
    val TABLET_SHOTS = listOf(null, "t-p-zoom", "t-p-canvas", "t-p-gallery", "t-p-completion")
    const val TABLET_WIDTH = 1920
    const val TABLET_HEIGHT = 1080

    /** The feature graphic's art: the puppy's finished gems, the same in every language. */
    const val FEATURE_ART = "store-r-puppy-full.png"
    const val FEATURE_NAME = "feature-graphic.png"

    const val WIDTH = 1080
    const val HEIGHT = 1920
    const val FEATURE_WIDTH = 1024
    const val FEATURE_HEIGHT = 500

    private val FELT = Color(0x4F, 0x5A, 0x52)
    private val FELT_DEEP = Color(0x3E, 0x47, 0x41)
    private val FELT_RAISED = Color(0x5C, 0x68, 0x5F)
    private val BONE = Color(0xE9, 0xE4, 0xD8)
    private val TEXT_MUTED = Color(0xCD, 0xD3, 0xC9)
    private val LAMP = Color(0xF4, 0xE3, 0xB1)
    private val SCRIM = Color(0x14, 0x18, 0x15)

    private const val TITLE_PX = 92f
    private const val TITLE_TOP = 120f
    private const val SUBTITLE_PX = 40f
    private const val SUBTITLE_GAP = 28f
    private const val TEXT_SIDE = 80
    private const val PHONE_GAP = 70
    private const val PHONE_WIDTH = 760
    private const val PHONE_HEIGHT = 1351
    private const val PHONE_PAD = 18
    private const val PHONE_CORNER = 64.0
    private const val SCREEN_CORNER = 48.0

    fun captions(listing: File, locale: String): List<String> = lines(listing, locale, "captions")
    fun subtitles(listing: File, locale: String): List<String> = lines(listing, locale, "subtitles")

    /** The tablet set's privacy line, under the first frame's subtitle. */
    fun privacy(listing: File, locale: String): String = lines(listing, locale, "tablet").single()

    /** The feature graphic's tagline and line. */
    fun feature(listing: File, locale: String): List<String> = lines(listing, locale, "feature")

    private fun lines(listing: File, locale: String, name: String): List<String> =
        File(listing, "$locale/$name.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() }

    /** The display face at 600 for [language]: the static instances in [staticFonts], Japanese's Bold from [fontDir]. */
    fun displayFace(language: String, fontDir: File, staticFonts: File): File = when (language) {
        "ja" -> File(fontDir, "zen_kaku_gothic_new_bold.ttf")
        "bg" -> File(staticFonts, "geologica_600.ttf")
        else -> File(staticFonts, "bricolage_grotesque_600.ttf")
    }

    /** The body face for [language] at its default instance (Figtree, Onest, Zen Kaku Gothic New Regular). */
    fun bodyFace(language: String, fontDir: File): File = File(fontDir, GlyphCheck.faces(language).last())

    private fun font(file: File, px: Float): Font = Font.createFont(Font.TRUETYPE_FONT, file).deriveFont(px)

    private fun canvas(width: Int, height: Int): Pair<BufferedImage, Graphics2D> {
        val out = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        return out to g
    }

    /** The felt lit from the top center: raised under the lamp, deep at the far corners. */
    private fun ground(g: Graphics2D, width: Int, height: Int, center: Point2D.Float) {
        val radius = maxOf(width, height).toFloat()
        // An ellipse as wide as the image and as tall as it, from a circle scaled on x.
        val sx = width.toDouble() / maxOf(width, height)
        val transform = AffineTransform().apply {
            translate(center.x.toDouble(), center.y.toDouble())
            scale(sx * 1.4, 1.0)
            translate(-center.x.toDouble(), -center.y.toDouble())
        }
        g.paint = RadialGradientPaint(
            center, radius, center, floatArrayOf(0f, 0.55f, 1f), arrayOf(FELT_RAISED, FELT, FELT_DEEP),
            MultipleGradientPaint.CycleMethod.NO_CYCLE, MultipleGradientPaint.ColorSpaceType.SRGB, transform,
        )
        g.fillRect(0, 0, width, height)
    }

    /** [text] broken to [width] in [face]; Japanese breaks at any character in AWT, so it must fit one line. */
    private fun wrap(g: Graphics2D, text: String, face: Font, width: Float): List<TextLayout> {
        val attributed = AttributedString(text).apply { addAttribute(TextAttribute.FONT, face) }
        val measurer = LineBreakMeasurer(attributed.iterator, g.fontRenderContext)
        val lines = ArrayList<TextLayout>()
        while (measurer.position < text.length) lines += measurer.nextLayout(width)
        return lines
    }

    /** One frame of [shot]: [title] (at most two lines; Japanese one) and [subtitle] (one line) above the phone. */
    fun frame(shot: BufferedImage, title: String, subtitle: String, language: String, fontDir: File, staticFonts: File): BufferedImage {
        val (out, g) = canvas(WIDTH, HEIGHT)
        ground(g, WIDTH, HEIGHT, Point2D.Float(WIDTH / 2f, 0f))
        val room = (WIDTH - 2 * TEXT_SIDE).toFloat()

        val titleLines = wrap(g, title, font(displayFace(language, fontDir, staticFonts), TITLE_PX), room)
        require(titleLines.size <= 2) { "\"$title\" takes ${titleLines.size} lines; keep a title to two" }
        require(language != "ja" || titleLines.size == 1) { "\"$title\" wraps; keep a Japanese title to one line" }
        val subLines = wrap(g, subtitle, font(bodyFace(language, fontDir), SUBTITLE_PX), room)
        require(subLines.size == 1) { "\"$subtitle\" wraps; keep a subtitle to one line" }

        val titleLeading = TITLE_PX * if (language == "ja") 1.25f else 1.02f
        var y = TITLE_TOP
        g.color = BONE
        for (line in titleLines) {
            line.draw(g, (WIDTH - line.advance) / 2, y + line.ascent)
            y += titleLeading
        }
        y += SUBTITLE_GAP
        g.color = TEXT_MUTED
        val sub = subLines.single()
        sub.draw(g, (WIDTH - sub.advance) / 2, y + sub.ascent)
        y += SUBTITLE_PX * 1.25f

        // The phone: a scrim body with a soft shadow, the shot filling its screen from the top.
        val left = (WIDTH - PHONE_WIDTH) / 2.0
        val top = (y + PHONE_GAP).toDouble()
        require(top + PHONE_HEIGHT <= HEIGHT) { "\"$title\" leaves no room for the phone" }
        for (i in 24 downTo 1) {
            g.color = Color(0, 0, 0, 5)
            g.fill(RoundRectangle2D.Double(left - i, top + 30.0 - i, PHONE_WIDTH + 2.0 * i, PHONE_HEIGHT + 2.0 * i, PHONE_CORNER * 2 + i, PHONE_CORNER * 2 + i))
        }
        g.color = SCRIM
        g.fill(RoundRectangle2D.Double(left, top, PHONE_WIDTH.toDouble(), PHONE_HEIGHT.toDouble(), PHONE_CORNER * 2, PHONE_CORNER * 2))
        val sw = PHONE_WIDTH - 2 * PHONE_PAD
        val sh = PHONE_HEIGHT - 2 * PHONE_PAD
        val sl = left + PHONE_PAD
        val st = top + PHONE_PAD
        g.clip = RoundRectangle2D.Double(sl, st, sw.toDouble(), sh.toDouble(), SCREEN_CORNER * 2, SCREEN_CORNER * 2)
        // Cover the screen, anchored at the top (the shot is a little taller than the screen's aspect).
        val scale = maxOf(sw.toDouble() / shot.width, sh.toDouble() / shot.height)
        val dw = shot.width * scale
        g.drawImage(shot, (sl + (sw - dw) / 2).toInt(), st.toInt(), dw.toInt(), (shot.height * scale).toInt(), null)
        g.dispose()
        return out
    }

    /** The 1024×500 feature graphic: "Gemmory", [tagline] in lamp and [line] beside [art] on a card. */
    fun featureGraphic(art: BufferedImage, tagline: String, line: String, language: String, fontDir: File, staticFonts: File): BufferedImage {
        val (out, g) = canvas(FEATURE_WIDTH, FEATURE_HEIGHT)
        ground(g, FEATURE_WIDTH, FEATURE_HEIGHT, Point2D.Float(FEATURE_WIDTH * 0.3f, 0f))
        val textLeft = 64f
        val textRoom = CARD_LEFT - textLeft - 32
        // The name is Latin in every language, so it's always Bricolage.
        val name = wrap(g, "Gemmory", font(displayFace("en", fontDir, staticFonts), 104f), textRoom).single()
        val tag = wrap(g, tagline, font(displayFace(language, fontDir, staticFonts), 36f), textRoom)
        val small = wrap(g, line, font(bodyFace(language, fontDir), 24f), textRoom)
        require(tag.size == 1) { "\"$tagline\" wraps; keep the feature tagline to one line" }
        require(small.size == 1) { "\"$line\" wraps; keep the feature line to one line" }
        val block = name.ascent + 22 + tag.single().ascent + 16 + small.single().ascent
        var y = (FEATURE_HEIGHT - block) / 2 + name.ascent
        g.color = BONE
        name.draw(g, textLeft, y)
        y += 22 + tag.single().ascent
        g.color = LAMP
        tag.single().draw(g, textLeft, y)
        y += 16 + small.single().ascent
        g.color = TEXT_MUTED
        small.single().draw(g, textLeft, y)

        // The art on a card with a soft shadow, cropped square from its center.
        for (i in 16 downTo 1) {
            g.color = Color(0, 0, 0, 7)
            g.fill(RoundRectangle2D.Double(CARD_LEFT - i.toDouble(), CARD_TOP + 14.0 - i, CARD_SIZE + 2.0 * i, CARD_SIZE + 2.0 * i, 40.0 + i, 40.0 + i))
        }
        g.clip = RoundRectangle2D.Double(CARD_LEFT.toDouble(), CARD_TOP.toDouble(), CARD_SIZE.toDouble(), CARD_SIZE.toDouble(), 40.0, 40.0)
        val side = minOf(art.width, art.height)
        val sx = (art.width - side) / 2
        val sy = (art.height - side) / 2
        g.drawImage(art, CARD_LEFT, CARD_TOP, CARD_LEFT + CARD_SIZE, CARD_TOP + CARD_SIZE, sx, sy, sx + side, sy + side, null)
        g.dispose()
        return out
    }

    private const val CARD_SIZE = 412
    private const val CARD_LEFT = 572
    private const val CARD_TOP = (FEATURE_HEIGHT - CARD_SIZE) / 2

    /**
     * One tablet frame: [title] (at most three lines) and [subtitle] (at most two) on the left, then [privacy] if
     * given; on the right [shot] in a landscape `scrim` tablet, or [art] on a card. A Japanese title shrinks to one line.
     */
    fun tabletFrame(
        shot: BufferedImage?, art: BufferedImage?, title: String, subtitle: String, privacy: String?, language: String,
        fontDir: File, staticFonts: File,
    ): BufferedImage {
        val (out, g) = canvas(TABLET_WIDTH, TABLET_HEIGHT)
        ground(g, TABLET_WIDTH, TABLET_HEIGHT, Point2D.Float(TABLET_WIDTH * 0.3f, 0f))
        val room = TAB_TEXT_ROOM.toFloat()
        var titlePx = TAB_TITLE_PX
        var titleLines = wrap(g, title, font(displayFace(language, fontDir, staticFonts), titlePx), room)
        while (language == "ja" && titleLines.size > 1 && titlePx > TAB_TITLE_MIN_JA) {
            titlePx -= 4f
            titleLines = wrap(g, title, font(displayFace(language, fontDir, staticFonts), titlePx), room)
        }
        require(titleLines.size <= 3) { "\"$title\" takes ${titleLines.size} lines on a tablet; keep it to three" }
        require(language != "ja" || titleLines.size == 1) { "\"$title\" wraps on a tablet; keep a Japanese title shorter" }
        val subLines = wrap(g, subtitle, font(bodyFace(language, fontDir), TAB_SUB_PX), room)
        require(subLines.size <= 2) { "\"$subtitle\" takes ${subLines.size} lines on a tablet; keep it to two" }
        val privacyLines = privacy?.let { wrap(g, it, font(bodyFace(language, fontDir), TAB_PRIVACY_PX), room) }.orEmpty()
        require(privacyLines.size <= 2) { "\"$privacy\" takes ${privacyLines.size} lines on a tablet; keep it to two" }
        // AWT breaks Japanese at any character, so its lines stay whole.
        require(language != "ja" || (subLines.size == 1 && privacyLines.size <= 1)) { "\"$subtitle\" or \"$privacy\" wraps on a tablet; keep Japanese to one line" }

        val titleLeading = titlePx * if (language == "ja") 1.25f else 1.04f
        val subLeading = TAB_SUB_PX * 1.3f
        val privacyLeading = TAB_PRIVACY_PX * 1.3f
        val height = titleLeading * titleLines.size + 24 + subLeading * subLines.size +
            (if (privacyLines.isEmpty()) 0f else 18 + privacyLeading * privacyLines.size)
        var y = (TABLET_HEIGHT - height) / 2
        g.color = BONE
        for (line in titleLines) {
            line.draw(g, TAB_TEXT_LEFT.toFloat(), y + line.ascent)
            y += titleLeading
        }
        y += 24
        g.color = TEXT_MUTED
        for (line in subLines) {
            line.draw(g, TAB_TEXT_LEFT.toFloat(), y + line.ascent)
            y += subLeading
        }
        if (privacyLines.isNotEmpty()) y += 18
        for (line in privacyLines) {
            line.draw(g, TAB_TEXT_LEFT.toFloat(), y + line.ascent)
            y += privacyLeading
        }

        if (shot != null) {
            val left = (TABLET_WIDTH - TAB_RIGHT - TAB_WIDTH).toDouble()
            val top = (TABLET_HEIGHT - TAB_HEIGHT) / 2.0
            for (i in 24 downTo 1) {
                g.color = Color(0, 0, 0, 5)
                g.fill(RoundRectangle2D.Double(left - i, top + 30.0 - i, TAB_WIDTH + 2.0 * i, TAB_HEIGHT + 2.0 * i, TAB_CORNER * 2 + i, TAB_CORNER * 2 + i))
            }
            g.color = SCRIM
            g.fill(RoundRectangle2D.Double(left, top, TAB_WIDTH.toDouble(), TAB_HEIGHT.toDouble(), TAB_CORNER * 2, TAB_CORNER * 2))
            val sw = TAB_WIDTH - 2 * TAB_PAD
            val sh = TAB_HEIGHT - 2 * TAB_PAD
            g.clip = RoundRectangle2D.Double(left + TAB_PAD, top + TAB_PAD, sw.toDouble(), sh.toDouble(), TAB_SCREEN_CORNER * 2, TAB_SCREEN_CORNER * 2)
            val scale = maxOf(sw.toDouble() / shot.width, sh.toDouble() / shot.height)
            val dw = shot.width * scale
            val dh = shot.height * scale
            g.drawImage(shot, (left + TAB_PAD + (sw - dw) / 2).toInt(), (top + TAB_PAD + (sh - dh) / 2).toInt(), dw.toInt(), dh.toInt(), null)
        } else {
            requireNotNull(art) { "the tablet's first frame needs the puppy's art ($FEATURE_ART)" }
            val left = TABLET_WIDTH - TAB_CARD_RIGHT - TAB_CARD
            val top = (TABLET_HEIGHT - TAB_CARD) / 2
            for (i in 20 downTo 1) {
                g.color = Color(0, 0, 0, 6)
                g.fill(RoundRectangle2D.Double(left - i.toDouble(), top + 18.0 - i, TAB_CARD + 2.0 * i, TAB_CARD + 2.0 * i, 48.0 + i, 48.0 + i))
            }
            g.clip = RoundRectangle2D.Double(left.toDouble(), top.toDouble(), TAB_CARD.toDouble(), TAB_CARD.toDouble(), 48.0, 48.0)
            val side = minOf(art.width, art.height)
            val sx = (art.width - side) / 2
            val sy = (art.height - side) / 2
            g.drawImage(art, left, top, left + TAB_CARD, top + TAB_CARD, sx, sy, sx + side, sy + side, null)
        }
        g.dispose()
        return out
    }

    private const val TAB_TEXT_LEFT = 120
    private const val TAB_TEXT_ROOM = 620
    private const val TAB_TITLE_PX = 88f
    private const val TAB_TITLE_MIN_JA = 60f
    private const val TAB_SUB_PX = 36f
    private const val TAB_PRIVACY_PX = 30f
    private const val TAB_WIDTH = 1040
    private const val TAB_HEIGHT = 602
    private const val TAB_PAD = 20
    private const val TAB_RIGHT = 100
    private const val TAB_CORNER = 56.0
    private const val TAB_SCREEN_CORNER = 38.0
    private const val TAB_CARD = 660
    private const val TAB_CARD_RIGHT = 160

    /** Frames every listed locale whose raw shots are in [shots] (or just [only]); returns the files written. */
    fun run(shots: File, listing: File, fontDir: File, staticFonts: File, out: File, only: String?): List<File> {
        val locales = if (only != null) listOf(only.also { require(it in LOCALES) { "unknown locale $it; one of ${LOCALES.keys}" } }) else LOCALES.keys.toList()
        val art = File(shots, FEATURE_ART).takeIf { it.exists() }?.let(ImageIO::read)
        val written = ArrayList<File>()
        for (locale in locales) {
            val prefix = appTag(locale)?.let { "$it-" }.orEmpty()
            val raw = SHOTS.map { File(shots, "store-$prefix$it.png") }
            val tabletRaw = TABLET_SHOTS.map { it?.let { name -> File(shots, "store-$prefix$name.png") } }
            val phone = raw.all { it.exists() }
            val tablet = art != null && tabletRaw.all { it == null || it.exists() }
            if (!phone && !tablet) {
                require(only == null) { "missing ${raw.filter { !it.exists() }.joinToString { it.name }} in $shots" }
                continue
            }
            val language = LOCALES.getValue(locale)
            val titles = captions(listing, locale)
            val subs = subtitles(listing, locale)
            require(titles.size == SHOTS.size && subs.size == SHOTS.size) { "$locale needs ${SHOTS.size} captions and subtitles" }
            val dir = File(out, locale).apply { mkdirs() }
            fun save(image: BufferedImage, name: String) {
                written += File(dir, name).also { check(ImageIO.write(image, "png", it)) { "cannot write $it" } }
            }
            if (phone) {
                for ((i, file) in raw.withIndex()) save(frame(ImageIO.read(file), titles[i], subs[i], language, fontDir, staticFonts), "${FRAMES[i]}.png")
            }
            if (tablet) {
                for ((i, file) in tabletRaw.withIndex()) {
                    val image = tabletFrame(
                        file?.let(ImageIO::read), art, titles[i], subs[i], if (i == 0) privacy(listing, locale) else null, language, fontDir, staticFonts,
                    )
                    save(image, "tablet-${FRAMES[i]}.png")
                }
            }
            if (art != null) {
                val (tagline, line) = feature(listing, locale)
                save(featureGraphic(art, tagline, line, language, fontDir, staticFonts), FEATURE_NAME)
            }
        }
        return written
    }

    /** Gradle passes `key=value` pairs (`frameStoreShots` in shared/build.gradle.kts). */
    @JvmStatic
    fun main(args: Array<String>) {
        val a = args.associate { it.substringBefore('=') to it.substringAfter('=', "") }
        fun arg(name: String) = a[name]?.takeIf { it.isNotBlank() } ?: error("missing -P$name")
        val written = run(File(arg("shots")), File(arg("listing")), File(arg("fonts")), File(arg("staticFonts")), File(arg("out")), a["locale"]?.takeIf { it.isNotBlank() })
        require(written.isNotEmpty()) { "no shots found in ${arg("shots")}: run StoreScreenshots#photoCanvases with -e appLocale <tag> and pull them first" }
        println("wrote ${written.size} images to ${arg("out")} (one folder per listing locale)")
    }
}
