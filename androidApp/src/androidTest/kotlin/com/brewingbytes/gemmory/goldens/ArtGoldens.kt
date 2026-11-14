package com.brewingbytes.gemmory.goldens

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import com.brewingbytes.gemmory.R
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.copy.strings
import com.brewingbytes.gemmory.play.CanvasStore
import com.brewingbytes.gemmory.policy.CardDay
import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.save.GridCodec
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.streak.StreakText
import com.brewingbytes.gemmory.timelapse.EncodeRequest
import com.brewingbytes.gemmory.timelapse.ShareFrame
import com.brewingbytes.gemmory.ui.GemRenderer
import java.util.Locale
import kotlin.math.min

/**
 * The time-lapse frame's and the streak card's fixed inputs for the renderer golden bar (iOS Stage 1a, T62): English
 * and Japanese, each frame kind, a progress and a finished share, VIP and not, one- and two-line titles, and a streak
 * card per flame tier, and a Japanese founder clip title cut to 2 lines. [RendererGoldenCapture] draws them with the old renderers into `goldens/renderer/`, and
 * `render.SharedArtGoldenTest` draws them with the shared ones and compares.
 */
object ArtGoldens {
    /** One time-lapse: its layers are `<name>-overlay.png`, `-card.png`, `-canvas.png`, `-source.png`, `-band.png`. */
    data class FrameCase(
        val name: String,
        val language: String,
        val picture: String,
        val frame: ShareFrame,
        val watermark: Boolean,
        val log: String,
        val title: String?,
        /** The band (`-band.png`): one per picture is enough. */
        val band: Boolean = false,
    )

    /** One before/after picture (eng E1 fallback): `<name>.png`. */
    data class BeforeAfterCase(val name: String, val language: String, val picture: String, val frame: ShareFrame, val watermark: Boolean, val log: String)

    /** One streak share card (#104): `<name>.png`. */
    data class StreakCase(val name: String, val language: String, val length: Int)

    const val EN_LONG = "The Great Wave off Kanagawa, a woodblock print by Hokusai"
    const val JA_LONG = "神奈川沖浪裏、葛飾北斎の木版画、富士山を望む"

    /** Past a founder clip title's 2 lines: ellipsized, with Zen Kaku's tall glyphs at the bottom of its 70px lines. */
    const val JA_TRUNCATED = "神奈川沖浪裏、葛飾北斎の木版画、富士山を望む江戸時代の名所を描いた浮世絵の傑作として知られる一枚"

    /** A null title is the picture's own `title_<id>` in that language. */
    val FRAMES = listOf(
        FrameCase("frame-en-player", "en", "deco-peacock", ShareFrame.PLAYER, watermark = true, log = "half", title = EN_LONG),
        FrameCase("frame-en-player-vip", "en", "puppy", ShareFrame.PLAYER, watermark = false, log = "full", title = null, band = true),
        FrameCase("frame-en-clip", "en", "japanese-garden", ShareFrame.CLIP, watermark = true, log = "full", title = EN_LONG, band = true),
        FrameCase("frame-en-feed", "en", "deco-peacock", ShareFrame.FEED, watermark = true, log = "full", title = null),
        FrameCase("frame-ja-player", "ja", "deco-peacock", ShareFrame.PLAYER, watermark = true, log = "half", title = JA_LONG),
        FrameCase("frame-ja-clip", "ja", "puppy", ShareFrame.CLIP, watermark = true, log = "full", title = null),
        FrameCase("frame-ja-clip-truncated", "ja", "japanese-garden", ShareFrame.CLIP, watermark = true, log = "full", title = JA_TRUNCATED),
    )

    val BEFORE_AFTER = listOf(
        BeforeAfterCase("beforeafter-en-player", "en", "deco-peacock", ShareFrame.PLAYER, watermark = true, log = "half"),
        BeforeAfterCase("beforeafter-en-clip", "en", "puppy", ShareFrame.CLIP, watermark = true, log = "full"),
        BeforeAfterCase("beforeafter-ja-player", "ja", "japanese-garden", ShareFrame.PLAYER, watermark = false, log = "full"),
    )

    /** The three flame tiers (G1), in English and Japanese. */
    val STREAKS = listOf("en", "ja").flatMap { language -> listOf(7, 30, 100).map { StreakCase("streak-$language-$it", language, it) } }

    /** The milestone week: three pictures, three flames and an empty day. */
    val WEEK: List<CardDay> = listOf(
        CardDay.Picture(key("deco-peacock")), CardDay.Flame, CardDay.Picture(key("puppy")), CardDay.Flame, CardDay.Flame,
        CardDay.Picture(key("japanese-garden")), CardDay.Empty,
    )

    /** Seven days in a row from a fixed epoch day, for the weekday letters. */
    private const val FIRST_DAY = 20_000L

    fun localized(context: Context, language: String): Context =
        context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) })

    fun patterns(context: Context): Map<String, Pattern> = GoldenCases.PICTURES.associateWith { name ->
        val bytes = context.assets.open("pictures/$name.gmrg").use { it.readBytes() }
        (GridCodec.decode(bytes) as Decoded.Ok).value.pattern
    }

    fun request(context: Context, pattern: Pattern, picture: String, frame: ShareFrame, watermark: Boolean, log: String, title: String?) =
        EncodeRequest(
            ref = CanvasRef(CanvasKind.BUNDLED, picture),
            pattern = pattern,
            log = GoldenCases.log(pattern, log),
            title = title ?: pictureTitle(context, picture),
            source = null,
            watermark = watermark,
            frame = frame,
        )

    fun pictureTitle(context: Context, picture: String): String = context.strings.text(TITLES.getValue(picture))

    private val TITLES = mapOf(
        "puppy" to R.string.title_puppy,
        "deco-peacock" to R.string.title_deco_peacock,
        "japanese-garden" to R.string.title_japanese_garden,
    )

    fun milestone(context: Context, length: Int): String = StreakText(context.strings).milestone(length)!!

    fun letters(context: Context): List<String> = (0 until 7).map { StreakText(context.strings).weekday(FIRST_DAY + it).take(1) }

    /** A picture day's art, the old renderer's (both sides draw the same art), as `CardPictures.art` sizes it. */
    fun thumbnail(patterns: Map<String, Pattern>): (CanvasRef) -> Bitmap? = { ref ->
        patterns[ref.id]?.let { p ->
            val side = min(p.width, p.height)
            GemRenderer.renderOld(p, (THUMBNAIL_PX + side - 1) / side)
        }
    }

    /** Every bundled picture's Japanese title (README: `title_<id>` in every language), for `ja-wrap.txt`. */
    fun japaneseTitles(context: Context): List<String> {
        val ja = localized(context, "ja").resources
        return R.string::class.java.fields.filter { it.name.startsWith("title_") }.sortedBy { it.name }.map { ja.getString(it.getInt(null)) }
    }

    private fun key(picture: String) = CanvasStore.reminderKey(CanvasRef(CanvasKind.BUNDLED, picture))

    private const val THUMBNAIL_PX = 112
}
