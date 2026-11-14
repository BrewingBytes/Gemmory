package com.brewingbytes.gemmory.render

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.brewingbytes.gemmory.timelapse.ProgressLine

/**
 * What the shared renderers draw text and pictures with (iOS Stage 1a, T62, eng R10-3). It's interim: each platform
 * shell fills it (Android from today's `R.font`, `Messages` and assets), and Stage 1b deletes the fonts and strings
 * here when the renderers read the shared Compose resources.
 */
interface RenderAssets {
    /** Atkinson Hyperlegible Mono, with a Bold face at 700 on its `wght` axis: the canvas's cell numbers. */
    val numbers: FontFamily
}

/**
 * [RenderAssets] plus what the time-lapse frame ([FrameArt]) and the streak share card ([StreakCard]) draw: the faces,
 * words and pictures of the app's language. The play canvas needs only [numbers].
 */
interface ArtAssets : RenderAssets {
    /** The app's language code ("en", "ja", "de", …): how a drawn title wraps ([TextWrap]). */
    val language: String

    /** The faces of [language] (T49, design system Typography → Other scripts). */
    val faces: Faces

    /** The frame's and the streak card's words in [language] (T48). */
    val words: DrawnWords

    /** The app icon for the Gemmory row, the end card and the lockup; null draws none. */
    val appIcon: ImageBitmap?

    /** Google's Play badge in [language] (T49), or English; null draws none (iOS until Stage 5, CEO D13). */
    val playBadge: ImageBitmap?
}

/**
 * One language's faces (T49): Bricolage Grotesque and Figtree for Latin scripts, Geologica and Onest for Bulgarian,
 * Zen Kaku Gothic New for Japanese. Numbers are [RenderAssets.numbers] everywhere.
 */
class Faces(
    /** Titles: 600. */
    val display: Face,
    /** Body text. Latin draws Figtree at its default instance (300) here, as the frame did before T49. */
    val body: Face,
    /** Body text at 600 (Japanese: its 700 file). */
    val bodyStrong: Face,
    /** Body text at 700: "Google Play" in the founder clip's lockup (#144). */
    val bodyBold: Face,
)

/**
 * A font family drawn at one [weight] (the family's only face), with its vertical metrics, which Compose doesn't
 * give: [metrics] for a text size in px, as Android's `Paint` reports them. The frame and the card place lines with
 * these exactly where the old `Paint`/`StaticLayout` code did.
 */
class Face(val family: FontFamily, val weight: FontWeight, val metrics: (sizePx: Float) -> LineMetrics)

/**
 * A face's vertical metrics at one size, in px (y down, so [ascent] and [topInt] are negative). [ascent], [descent] and
 * [leading] are Android's `Paint.FontMetrics`; [topInt] and [bottomInt] are `Paint.FontMetricsInt.top` and `bottom`,
 * the first line's top and the last line's bottom in a `StaticLayout` that includes font padding.
 */
data class LineMetrics(val ascent: Float, val descent: Float, val leading: Float, val topInt: Int, val bottomInt: Int) {
    /** `Paint.getFontSpacing()`: the recommended distance between baselines. */
    val spacing: Float get() = descent - ascent + leading

    companion object {
        /**
         * Metrics from a font file's own numbers per em (its hhea ascender, descender and line gap, and its head
         * table's yMax and yMin), as Skia derives them: the iOS shell's and the tests'.
         */
        fun perEm(ascent: Float, descent: Float, lineGap: Float, yMax: Float, yMin: Float): (Float) -> LineMetrics = { size ->
            LineMetrics(
                ascent = -ascent * size,
                descent = descent * size,
                leading = lineGap * size,
                topInt = kotlin.math.floor(-yMax * size).toInt(),
                bottomInt = kotlin.math.ceil(-yMin * size).toInt(),
            )
        }
    }
}

/** Every word the time-lapse frame and the streak card draw (T48), resolved in the app's language. */
class DrawnWords(
    /** "Gemmory". */
    val appName: String,
    /** "Gemmory · Photo Diamond Art". */
    val appLine: String,
    /** "Your photos in gems". */
    val tagline: String,
    /** "Made with Gemmory". */
    val watermark: String,
    val before: String,
    val after: String,
    val soFar: String,
    /** "Google Play", the bold part of [clipFree]. */
    val clipStore: String,
    /** "Free on Google Play" (#144). */
    val clipFree: String,
    /** "Turn any photo into gem art" (#144). */
    val clipPromise: String,
    /** A progress share's "47% done" or "Just started" (#36). */
    val progress: (ProgressLine) -> String,
)
