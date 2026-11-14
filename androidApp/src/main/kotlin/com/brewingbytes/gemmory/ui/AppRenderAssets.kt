package com.brewingbytes.gemmory.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.util.Log
import androidx.annotation.VisibleForTesting
import android.graphics.fonts.FontStyle
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.res.ResourcesCompat
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Typeface
import com.brewingbytes.gemmory.R
import com.brewingbytes.gemmory.copy.Messages
import com.brewingbytes.gemmory.copy.strings
import com.brewingbytes.gemmory.render.ArtAssets
import com.brewingbytes.gemmory.render.DrawnWords
import com.brewingbytes.gemmory.render.Face
import com.brewingbytes.gemmory.render.Faces
import com.brewingbytes.gemmory.render.LineMetrics
import com.brewingbytes.gemmory.render.RenderAssets
import com.brewingbytes.gemmory.timelapse.FrameAssets

/**
 * The shared renderers' fonts on Android (iOS Stage 1a, T62): the app's own `R.font` faces, the same ones the UI
 * uses. Interim, as [RenderAssets] is: Stage 1b reads them from the shared Compose resources instead.
 */
object AppRenderAssets : RenderAssets {
    override val numbers = AtkinsonMono
}

/**
 * What the shared time-lapse frame and streak card draw with on Android (T62): the faces of the app's language as the
 * old `FrameStyle` and `StreakShareCardRenderer` set them on a `Paint` ([AppFonts]), the `frame_*` strings, the app
 * icon and the language's Play badge from the assets. Made per encode or card, so it follows the app's language.
 */
class AppArtAssets(private val context: Context) : ArtAssets {
    private val strings = context.strings
    private val fonts = AppFonts.forLanguage(strings.locale.language)

    // The streak card's hero number, Bold on the axis as the old card's Paint set it (see [face] on Bold text).
    override val numbers = FontFamily(Typeface(drawn(R.font.atkinson_hyperlegible_mono, NUMBER_WEIGHT, variation = true)))
    override val language: String = strings.locale.language

    override val faces = Faces(
        display = face(fonts.display),
        // The frame drew Latin body text at the file's default instance, as before T49: Figtree's is 300.
        body = if (fonts == AppFonts.LATIN) face(fonts.body, weight = LATIN_BODY_DEFAULT, variation = false) else face(fonts.body),
        bodyStrong = face(fonts.bodyStrong),
        bodyBold = face(fonts.bodyStrong, weight = FrameAssets.LOCKUP_STORE_WEIGHT),
    )

    override val words = DrawnWords(
        appName = strings.text(R.string.app_name),
        appLine = strings.text(R.string.frame_app_line),
        tagline = strings.text(R.string.frame_tagline),
        watermark = strings.text(R.string.frame_watermark),
        before = strings.text(R.string.frame_before),
        after = strings.text(R.string.frame_after),
        soFar = strings.text(R.string.frame_so_far),
        clipStore = strings.text(R.string.frame_clip_store),
        clipFree = strings.text(R.string.frame_clip_free, strings.text(R.string.frame_clip_store)),
        clipPromise = strings.text(R.string.frame_clip_promise),
        progress = { Messages.progress(strings, it) },
    )

    override val appIcon: ImageBitmap? by lazy { asset(FrameAssets.ICON_ASSET) }
    override val playBadge: ImageBitmap? by lazy { asset(FrameAssets.badgeAsset(language)) ?: asset(FrameAssets.BADGE_ASSET) }

    /**
     * [face] at [weight] on its `wght` axis (a static file draws as is), with the metrics a `Paint` set up the same way
     * reports: the old renderers' line positions.
     *
     * Compose gets the face as a finished `Typeface`, its axis already set: a Compose `Font` with `variationSettings`
     * adds the system's Bold text setting (`fontWeightAdjustment`, Android 12+) to the `wght` axis, read from the app's
     * own configuration, so the public video and card drew heavier for a viewer who had it on. The old `Paint` didn't.
     */
    private fun face(face: AppFonts.Face, weight: Int = face.weight, variation: Boolean = face.variable): Face {
        val fontWeight = FontWeight(weight)
        val metricsTypeface = face.typeface(context)
        return Face(FontFamily(Typeface(drawn(face.res, weight, variation))), fontWeight) { size ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = metricsTypeface
                if (variation) fontVariationSettings = "'wght' $weight"
                textSize = size
            }
            val fm = paint.fontMetrics
            val fmi = paint.fontMetricsInt
            LineMetrics(fm.ascent, fm.descent, fm.leading, fmi.top, fmi.bottom)
        }
    }

    /**
     * The font file [res] as the `Typeface` Compose draws, at [weight] (also the style the system fallback fonts are
     * matched at, as `ResourcesCompat` sets it), its `wght` axis set when [variation]. Built once per process: each
     * build reads the whole file. Should the file not load, the plain resource font draws, as the old card's did.
     */
    private fun drawn(res: Int, weight: Int, variation: Boolean): android.graphics.Typeface {
        val key = Triple(res, weight, variation)
        DRAWN[key]?.let { return it }
        val built = try {
            // A static file's weight changes nothing in it: one font per file, whatever weight its faces draw at.
            val font = FONTS.getOrPut(res to if (variation) weight else 0) {
                android.graphics.fonts.Font.Builder(context.resources, res)
                    .setWeight(weight)
                    .apply { if (variation) setFontVariationSettings("'wght' $weight") }
                    .build()
            }
            android.graphics.Typeface.CustomFallbackBuilder(android.graphics.fonts.FontFamily.Builder(font).build())
                .setStyle(FontStyle(weight, FontStyle.FONT_SLANT_UPRIGHT))
                .build()
        } catch (e: Exception) {
            // Not kept: the next encode or card tries again.
            Log.w(TAG, "font $res at $weight didn't load; drawing the plain resource font", e)
            return runCatching { ResourcesCompat.getFont(context, res) }.getOrNull() ?: android.graphics.Typeface.DEFAULT
        }
        return DRAWN.putIfAbsent(key, built) ?: built
    }

    private fun asset(path: String): ImageBitmap? =
        runCatching { context.assets.open(path).use(BitmapFactory::decodeStream)?.asImageBitmap() }.getOrNull()

    internal companion object {
        /** Figtree's default instance, which a `Paint` without variation settings draws. */
        private const val LATIN_BODY_DEFAULT = 300

        /** Atkinson Hyperlegible Mono Bold, as the old card's number Paint (`'wght' 700`). */
        private const val NUMBER_WEIGHT = 700

        /** [drawn]'s typefaces by font file, weight and variation; encodes and cards run on several threads. */
        private val DRAWN = java.util.concurrent.ConcurrentHashMap<Triple<Int, Int, Boolean>, android.graphics.Typeface>()

        /** The loaded font files, by file and the `wght` set on it (0 for a static file). */
        private val FONTS = java.util.concurrent.ConcurrentHashMap<Pair<Int, Int>, android.graphics.fonts.Font>()

        private const val TAG = "GemmoryFonts"

        /** Connected tests only: drops the built typefaces, so the next frame or card builds them again. */
        @VisibleForTesting
        internal fun clearFontsForTest() {
            DRAWN.clear()
            FONTS.clear()
        }
    }
}
