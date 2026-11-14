package com.brewingbytes.gemmory.render

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.brewingbytes.gemmory.timelapse.ProgressLine

/**
 * The English [ArtAssets] the golden checks draw the end card with (T62): composeApp's Latin fonts, as androidApp's
 * `AppFonts.LATIN` in the frame (Bricolage Grotesque at 600; Figtree at its default instance, 300, for body text), and
 * the English words. Metrics are the font files' own (hhea, head), which is what Android's `Paint` reports for them.
 */
object GoldenAssets {
    /** Bricolage Grotesque: hhea 930 / -270, head yMax 1057, yMin -338 (per 1000). */
    val BRICOLAGE = LineMetrics.perEm(ascent = 0.93f, descent = 0.27f, lineGap = 0f, yMax = 1.057f, yMin = -0.338f)

    /** Figtree: hhea 950 / -250, head yMax 912, yMin -220 (per 1000). */
    val FIGTREE = LineMetrics.perEm(ascent = 0.95f, descent = 0.25f, lineGap = 0f, yMax = 0.912f, yMin = -0.22f)

    /** [font] makes a platform font from a composeResources font file at a weight and `wght`. */
    fun english(font: (file: String, weight: FontWeight, wght: Int) -> Font, numbers: FontFamily, icon: ImageBitmap?): ArtAssets {
        fun face(file: String, weight: FontWeight, wght: Int, metrics: (Float) -> LineMetrics) =
            Face(FontFamily(font(file, weight, wght)), weight, metrics)
        val faces = Faces(
            display = face("bricolage_grotesque.ttf", FontWeight.SemiBold, 600, BRICOLAGE),
            body = face("figtree.ttf", FontWeight.Light, 300, FIGTREE),
            bodyStrong = face("figtree.ttf", FontWeight.SemiBold, 600, FIGTREE),
            bodyBold = face("figtree.ttf", FontWeight.Bold, 700, FIGTREE),
        )
        return object : ArtAssets {
            override val numbers = numbers
            override val language = "en"
            override val faces = faces
            override val words = ENGLISH
            override val appIcon = icon
            override val playBadge: ImageBitmap? = null
        }
    }

    /** androidApp's English `frame_*` strings. */
    val ENGLISH = DrawnWords(
        appName = "Gemmory",
        appLine = "Gemmory · Photo Diamond Art",
        tagline = "Your photos in gems",
        watermark = "Made with Gemmory",
        before = "Before",
        after = "After",
        soFar = "So far",
        clipStore = "Google Play",
        clipFree = "Free on Google Play",
        clipPromise = "Turn any photo into gem art",
        progress = { line ->
            when (line) {
                ProgressLine.JustStarted -> "Just started"
                is ProgressLine.Done -> "${line.percent}% done"
            }
        },
    )
}
