package com.brewingbytes.gemmory.timelapse

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.withTranslation
import com.brewingbytes.gemmory.copy.Messages
import com.brewingbytes.gemmory.copy.strings
import com.brewingbytes.gemmory.R
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.ui.GemRenderer
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The 9:16 time-lapse frame (design system, 9:16 time-lapse frame), drawn at 1080×1920 and scaled by width for
 * smaller targets, over a band made of the finished canvas blurred and darkened.
 *
 * The player's share ([ShareFrame.PLAYER], T53, [ShareLayout]): the whole picture at full width, centered above the
 * Gemmory row, with the title and app line over a dark fade ([overlay], drawn over the canvas), the row on every
 * non-VIP frame, and the non-VIP end card ([card] plus the canvas itself at [cardPicture]). A founder clip
 * ([ShareFrame.CLIP], T47) keeps the frame from before T53: the canvas under a 36px top margin ([FrameLayout]; the
 * 4:5 fits it above the text), with the title, app line and "Made with Gemmory" in the [background].
 *
 * Built once per attempt. The canvas lives in one bitmap that starts as the empty tint grid and gets each frame's
 * new gems drawn onto it, so a whole video costs O(gems + frames) draws (eng finding 12).
 */
class FrameArt(context: Context, private val request: EncodeRequest, val width: Int, val height: Int) {
    private val style = FrameStyle(context, width)
    private val pattern = request.pattern
    private val share: ShareLayout? =
        if (request.frame == ShareFrame.PLAYER) ShareLayout.of(width, height, pattern.width, pattern.height, request.watermark) else null
    private val layout = share?.board ?: FrameLayout.board(width, height, pattern.width, pattern.height)
    private val painter = GemRenderer.GemPainter(pattern, layout.width, layout.height)
    private val board: Bitmap = createBitmap(layout.width, layout.height)
    private val boardCanvas = Canvas(board)
    private val chrome: Bitmap = createBitmap(width, height)
    private val photo: Bitmap? = request.source?.let { decodeSource(it, layout.width, layout.height) }
    private val overlayTop = share?.fadeTop ?: 0
    private val overlayBitmap: Bitmap? = share?.let { createBitmap(width, height - it.fadeTop) }
    private val cardArea: Rect? = share?.card?.let { Rect(0, it.top - style.px(FrameStyle.CARD_SHADOW_ROOM), width, it.bottom) }
    private val cardBitmap: Bitmap? = cardArea?.let { createBitmap(it.width(), it.height()) }
    private var drawn = 0

    /** The canvas's place in the frame ([ShareLayout] for the player's share, [FrameLayout] for a founder clip). */
    val boardRect = Rect(layout.left, layout.top, layout.right, layout.bottom)

    /** The static frame behind the canvas: the band, plus a founder clip's title, app line and watermark row. */
    val background: Bitmap get() = chrome

    /** The canvas as of the last [apply]: the tint grid plus every gem placed so far. */
    val canvas: Bitmap get() = board

    /** The cropped source photo at the canvas's size, for the photo intro; null for bundled pictures. */
    val source: Bitmap? get() = photo

    /** The player's share: fade, title, app line and (non-VIP) the Gemmory row, drawn over the canvas at [overlayRect]. */
    val overlay: Bitmap? get() = overlayBitmap
    val overlayRect: Rect? = overlayBitmap?.let { Rect(0, overlayTop, width, height) }

    /** The end card's shadow, icon, name, tagline and badge (non-VIP player share); null without an end card. */
    val card: Bitmap? get() = cardBitmap

    init {
        for (cell in pattern.cells.indices) painter.drawCell(boardCanvas, cell, isPlaced = false)
        val c = Canvas(chrome)
        style.drawBand(c, pattern, height)
        if (share == null) {
            style.drawText(c, request.title, layout.bottom, height, request.watermark, request.progressLine)
        } else {
            val o = Canvas(overlayBitmap!!)
            o.translate(0f, -overlayTop.toFloat())
            style.drawShareText(o, share, request.title, request.progressLine)
            share.row?.let { style.drawRow(o, it) }
            share.card?.let { card ->
                val k = Canvas(cardBitmap!!)
                k.translate(0f, -cardArea!!.top.toFloat())
                style.drawCard(k, card)
            }
        }
    }

    /**
     * Where the end card draws at [alpha]: the canvas at its picture rect, the [card] layer at its rect, both slid
     * down by the part of the 30px slide still to go. Null without an end card.
     */
    fun endCard(alpha: Float): EndCardRects? {
        val card = share?.card ?: return null
        val dy = ((1f - alpha) * card.slide).roundToInt()
        val p = card.picture
        return EndCardRects(
            alpha = alpha,
            picture = Rect(p.left, p.top + dy, p.right, p.bottom + dy),
            content = Rect(cardArea!!).apply { offset(0, dy) },
        )
    }

    /**
     * Brings [canvas] up to [frame] by drawing only the gems placed since the last call. Returns whether it changed
     * (so it needs uploading again) and the photo's opacity over it.
     */
    fun apply(frame: Frame): Pair<Boolean, Float> {
        val before = drawn
        when (frame) {
            is Frame.Build -> advanceTo(frame.placed)
            Frame.Hold, is Frame.EndCard -> advanceTo(request.log.size)
            is Frame.Intro -> Unit
        }
        val alpha = if (frame is Frame.Intro && photo != null) frame.photoAlpha else 0f
        return (drawn != before) to alpha
    }

    fun release() {
        board.recycle()
        chrome.recycle()
        photo?.recycle()
        overlayBitmap?.recycle()
        cardBitmap?.recycle()
    }

    private fun advanceTo(placed: Int) {
        val log = request.log
        while (drawn < placed && drawn < log.size) painter.drawCell(boardCanvas, log[drawn++], isPlaced = true)
    }

    companion object {
        /** The cropped source at the canvas's size; null if it's missing or unreadable (the intro is then tints only). */
        fun decodeSource(file: File, width: Int, height: Int): Bitmap? {
            if (!file.isFile) return null
            val decoded = BitmapFactory.decodeFile(file.path) ?: return null
            if (decoded.width == width && decoded.height == height) return decoded
            return decoded.scale(width, height).also { if (it !== decoded) decoded.recycle() }
        }

        /**
         * The before/after picture (eng E1 fallback), 1080×1920: the cropped photo (or, for a bundled picture, the
         * empty tint grid) above the finished canvas, on the same band with the same text. A progress share (#36)
         * shows the canvas as it stands, labelled "So far". The player's share (T53) has the Gemmory row at the
         * bottom for non-VIP, in place of the watermark, and slightly shorter boxes so the text clears it.
         */
        fun beforeAfter(context: Context, request: EncodeRequest): Bitmap {
            val width = 1080
            val height = 1920
            val style = FrameStyle(context, width)
            val out = createBitmap(width, height)
            val canvas = Canvas(out)
            style.drawBand(canvas, request.pattern, height)
            val pattern = request.pattern
            val cellPx = max(4, (width + pattern.width - 1) / pattern.width)
            val placed = BooleanArray(pattern.cells.size).also { for (cell in request.log) it[cell] = true }
            val after = GemRenderer.render(pattern, cellPx) { placed[it] }
            val before = request.source?.let { decodeSource(it, after.width, after.height) }
                ?: GemRenderer.render(pattern, cellPx) { false }
            val player = request.frame == ShareFrame.PLAYER
            val boxW = width - 2 * style.px(FrameStyle.MARGIN)
            val boxH = if (player) 620 else 640
            val label = style.labelPaint()
            var top = 48
            val strings = context.strings
            val afterLabel = strings.text(if (request.inProgress) R.string.frame_so_far else R.string.frame_after)
            for ((name, bitmap) in listOf(strings.text(R.string.frame_before) to before, afterLabel to after)) {
                val scale = min(boxW.toFloat() / bitmap.width, boxH.toFloat() / bitmap.height)
                val w = (bitmap.width * scale).roundToInt()
                val h = (bitmap.height * scale).roundToInt()
                val left = (width - w) / 2
                val imageTop = top + 44 + (boxH - h) / 2
                canvas.drawText(name, left.toFloat(), (imageTop - 14).toFloat(), label)
                canvas.drawBitmap(bitmap, null, Rect(left, imageTop, left + w, imageTop + h), Paint(Paint.FILTER_BITMAP_FLAG))
                top += 44 + boxH + 40
            }
            before.recycle()
            after.recycle()
            if (player) {
                style.drawText(canvas, request.title, top, height, watermark = false, request.progressLine)
                if (request.watermark) ShareLayout.of(width, height, pattern.width, pattern.height, branded = true).row?.let { style.drawRow(canvas, it) }
            } else {
                style.drawText(canvas, request.title, top, height, request.watermark, request.progressLine)
            }
            return out
        }

    }
}

/** Where the end card draws in one frame ([FrameArt.endCard]): both layers at [alpha]. */
class EndCardRects(val alpha: Float, val picture: Rect, val content: Rect)

/** Band, type, Gemmory row, end card and watermark of the 9:16 frame. Sizes are given at 1080 wide and scaled by [width]. */
internal class FrameStyle(private val context: Context, private val width: Int) {
    private val s = width / 1080f

    /** The frame's words (T48), in the app's language. */
    private val strings = context.strings
    private val bricolage = ResourcesCompat.getFont(context, R.font.bricolage_grotesque) ?: Typeface.DEFAULT
    private val figtree = ResourcesCompat.getFont(context, R.font.figtree) ?: Typeface.DEFAULT
    private val icon: Bitmap? by lazy { asset(ICON_ASSET) }
    private val badge: Bitmap? by lazy { asset(BADGE_ASSET) }

    fun px(at1080: Int): Int = (at1080 * s).roundToInt()

    /** The finished canvas scaled to cover the frame, blurred (~40px at 1080) and darkened to 45% brightness. */
    fun drawBand(canvas: Canvas, pattern: Pattern, height: Int) {
        val finished = GemRenderer.render(pattern, cellPx = 2)
        // Blur at 1/8 scale: a 5px box there is ~40px at full size, and upscaling with filtering smooths it.
        val sw = max(1, width / 8)
        val sh = max(1, height / 8)
        val small = createBitmap(sw, sh)
        val cover = max(sw.toFloat() / finished.width, sh.toFloat() / finished.height)
        val dw = finished.width * cover
        val dh = finished.height * cover
        Canvas(small).drawBitmap(
            finished,
            null,
            RectF((sw - dw) / 2f, (sh - dh) / 2f, (sw + dw) / 2f, (sh + dh) / 2f),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        finished.recycle()
        boxBlur(small, radius = 5, passes = 3)
        val darken = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(BAND_BRIGHTNESS, BAND_BRIGHTNESS, BAND_BRIGHTNESS, 1f) })
        }
        canvas.drawBitmap(small, null, Rect(0, 0, width, height), darken)
        small.recycle()
    }

    /**
     * The title (Bricolage Grotesque 600, 60px, at most 2 lines then an ellipsis) and "Gemmory · Photo Diamond Art"
     * (Figtree 36px) from [textTop], left-aligned at 48px; the watermark row at the bottom (Figtree 600 30px, 70%,
     * bottom-right, over a 25% scrim band), so the title never collides with it. A progress share (#36) has its
     * [progressLine] ("47% done", Figtree 600 36px) between the title and the app line.
     */
    fun drawText(canvas: Canvas, title: String, textTop: Int, height: Int, watermark: Boolean, progress: ProgressLine? = null) {
        val progressLine = progress?.let { Messages.progress(strings, it) }
        val margin = px(MARGIN).toFloat()
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = bricolage
            fontVariationSettings = "'wght' 600"
            textSize = 60 * s
            letterSpacing = -0.01f
            color = FRAME_TEXT
        }
        val textWidth = (width - 2 * margin).toInt()
        val titleLayout = StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        val top = textTop + px(40).toFloat()
        canvas.withTranslation(margin, top) { titleLayout.draw(this) }

        val appPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = figtree
            textSize = 36 * s
            color = FRAME_TEXT
        }
        var appTop = top + titleLayout.height + px(8)
        if (progressLine != null) {
            val progressPaint = TextPaint(appPaint).apply { fontVariationSettings = "'wght' 600" }
            canvas.drawText(progressLine, margin, appTop - progressPaint.fontMetrics.ascent, progressPaint)
            appTop += progressPaint.fontSpacing + px(2)
        }
        canvas.drawText(strings.text(R.string.frame_app_line), margin, appTop - appPaint.fontMetrics.ascent, appPaint)

        if (!watermark) return
        val rowHeight = px(WATERMARK_ROW)
        val scrim = Paint().apply { color = SCRIM; alpha = (255 * 0.25f).roundToInt() }
        canvas.drawRect(0f, (height - rowHeight).toFloat(), width.toFloat(), height.toFloat(), scrim)
        val markPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = figtree
            fontVariationSettings = "'wght' 600"
            textSize = 30 * s
            color = FRAME_TEXT
            alpha = (255 * 0.7f).roundToInt()
            textAlign = Paint.Align.RIGHT
        }
        val fm = markPaint.fontMetrics
        val baseline = height - rowHeight / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(strings.text(R.string.frame_watermark), width - margin, baseline, markPaint)
    }

    /**
     * The player's share text (T53): a `scrim` fade from 0 to 78% across the 420px above the picture area's bottom,
     * then, upward from the bottom, "Gemmory · Photo Diamond Art" (Figtree 36px), a progress share's [progressLine]
     * (Figtree 600 36px) and the title (Bricolage Grotesque 600, 60px, at most 2 lines then an ellipsis), left-aligned
     * at 48px.
     */
    fun drawShareText(canvas: Canvas, layout: ShareLayout, title: String, progress: ProgressLine?) {
        val progressLine = progress?.let { Messages.progress(strings, it) }
        val fade = Paint().apply {
            shader = LinearGradient(
                0f, layout.fadeTop.toFloat(), 0f, layout.areaBottom.toFloat(),
                SCRIM and 0x00FFFFFF, (SCRIM and 0x00FFFFFF) or ((255 * FADE_ALPHA).roundToInt() shl 24), Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, layout.fadeTop.toFloat(), width.toFloat(), layout.areaBottom.toFloat(), fade)
        val left = layout.textLeft.toFloat()
        val appPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = figtree
            textSize = 36 * s
            color = FRAME_TEXT
        }
        var baseline = layout.appBaseline.toFloat()
        canvas.drawText(strings.text(R.string.frame_app_line), left, baseline, appPaint)
        if (progressLine != null) {
            baseline -= px(LINE_GAP)
            val progressPaint = TextPaint(appPaint).apply { fontVariationSettings = "'wght' 600" }
            canvas.drawText(progressLine, left, baseline, progressPaint)
        }
        baseline -= px(ShareLayout.TITLE_GAP)
        val titleLayout = titleLayout(title)
        val top = baseline - titleLayout.getLineBaseline(titleLayout.lineCount - 1)
        canvas.withTranslation(left, top) { titleLayout.draw(this) }
    }

    /**
     * The Gemmory row (T53 D1): a 150px `scrim` band at 55%, the app icon, "Gemmory" (Bricolage Grotesque 600, 44px),
     * "Your photos in gems" (Figtree 28px, 80%), and Google's Play badge at the right, the file unchanged.
     */
    fun drawRow(canvas: Canvas, row: ShareLayout.Row) {
        val scrim = Paint().apply { color = SCRIM; alpha = (255 * ROW_SCRIM_ALPHA).roundToInt() }
        canvas.drawRect(0f, row.top.toFloat(), width.toFloat(), (row.top + px(ShareLayout.ROW)).toFloat(), scrim)
        drawIcon(canvas, row.icon, row.iconRadius)
        canvas.drawText(strings.text(R.string.app_name), row.textLeft.toFloat(), row.nameBaseline.toFloat(), namePaint(44))
        val tagline = taglinePaint(28).apply { alpha = (255 * 0.8f).roundToInt() }
        canvas.drawText(strings.text(R.string.frame_tagline), row.textLeft.toFloat(), row.taglineBaseline.toFloat(), tagline)
        drawBadge(canvas, row.badge)
    }

    /**
     * The end card's layer (T53): the picture's shadow (the picture itself is the canvas, drawn over it), the icon at
     * 180px, "Gemmory" (Bricolage Grotesque 600, 104px), "Your photos in gems" (Figtree 44px) and the badge at 520px,
     * centered. Its backdrop, the band at ~30%, is the encoder's.
     */
    fun drawCard(canvas: Canvas, card: ShareLayout.Card) {
        val p = card.picture
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x80000000.toInt()
            maskFilter = BlurMaskFilter(px(CARD_SHADOW_BLUR).toFloat(), BlurMaskFilter.Blur.NORMAL)
        }
        val dy = px(CARD_SHADOW_Y).toFloat()
        canvas.drawRect(p.left.toFloat(), p.top + dy, p.right.toFloat(), p.bottom + dy, shadow)
        drawIcon(canvas, card.icon, card.iconRadius)
        val center = width / 2f
        canvas.drawText(strings.text(R.string.app_name), center, card.nameBaseline.toFloat(), namePaint(104).apply { textAlign = Paint.Align.CENTER })
        canvas.drawText(strings.text(R.string.frame_tagline), center, card.taglineBaseline.toFloat(), taglinePaint(44).apply { textAlign = Paint.Align.CENTER })
        drawBadge(canvas, card.badge)
    }

    private fun titleLayout(title: String): StaticLayout {
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = bricolage
            fontVariationSettings = "'wght' 600"
            textSize = 60 * s
            letterSpacing = -0.01f
            color = FRAME_TEXT
        }
        val textWidth = (width - 2 * px(MARGIN))
        return StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
    }

    private fun namePaint(size: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = bricolage
        fontVariationSettings = "'wght' 600"
        textSize = size * s
        color = FRAME_TEXT
    }

    private fun taglinePaint(size: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = figtree
        textSize = size * s
        color = FRAME_TEXT
    }

    private fun drawIcon(canvas: Canvas, at: BoardRect, radius: Int) {
        val bitmap = icon ?: return
        val rect = RectF(at.left.toFloat(), at.top.toFloat(), at.right.toFloat(), at.bottom.toFloat())
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(rect, radius.toFloat(), radius.toFloat(), Path.Direction.CW) })
        canvas.drawBitmap(bitmap, null, rect, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        canvas.restore()
    }

    private fun drawBadge(canvas: Canvas, at: BoardRect) {
        val bitmap = badge ?: return
        canvas.drawBitmap(bitmap, null, Rect(at.left, at.top, at.right, at.bottom), Paint(Paint.FILTER_BITMAP_FLAG))
    }

    private fun asset(path: String): Bitmap? = runCatching { context.assets.open(path).use(BitmapFactory::decodeStream) }.getOrNull()

    fun labelPaint(): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = figtree
        textSize = 30 * s
        color = FRAME_TEXT
    }

    companion object {
        const val MARGIN = 48
        const val WATERMARK_ROW = 78
        const val BAND_BRIGHTNESS = 0.45f
        const val ROW_SCRIM_ALPHA = 0.55f
        const val FADE_ALPHA = 0.78f

        /** The baseline step between two 36px lines over the fade (a progress line above the app line). */
        const val LINE_GAP = 48
        const val CARD_SHADOW_BLUR = 52
        const val CARD_SHADOW_Y = 24

        /** Room above the end card's picture for its shadow. */
        const val CARD_SHADOW_ROOM = 100

        /** Black over the band behind the end card: 45% brightness × 0.67 ≈ 30%. */
        const val END_CARD_DIM = 0.33f
        const val ICON_ASSET = "share/app_icon.png"

        /** Google's official "Get it on Google Play" badge (play.google.com/intl/en_us/badges), the file unchanged. */
        const val BADGE_ASSET = "share/play_badge_en.png"
        private const val FRAME_TEXT = 0xFFF2EEE4.toInt()
        private const val SCRIM = 0xFF141815.toInt()

        /** In place, on a small software bitmap: [passes] horizontal + vertical box blurs approach a Gaussian. */
        fun boxBlur(bitmap: Bitmap, radius: Int, passes: Int) {
            val w = bitmap.width
            val h = bitmap.height
            val px = IntArray(w * h)
            bitmap.getPixels(px, 0, w, 0, 0, w, h)
            val tmp = IntArray(w * h)
            repeat(passes) {
                blurLine(px, tmp, w, h, radius, horizontal = true)
                blurLine(tmp, px, w, h, radius, horizontal = false)
            }
            bitmap.setPixels(px, 0, w, 0, 0, w, h)
        }

        private fun blurLine(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int, horizontal: Boolean) {
            val lines = if (horizontal) h else w
            val length = if (horizontal) w else h
            val window = 2 * radius + 1
            for (line in 0 until lines) {
                fun at(i: Int): Int {
                    val c = i.coerceIn(0, length - 1)
                    return if (horizontal) src[line * w + c] else src[c * w + line]
                }
                var r = 0
                var g = 0
                var b = 0
                for (i in -radius..radius) {
                    val p = at(i)
                    r += (p shr 16) and 0xFF
                    g += (p shr 8) and 0xFF
                    b += p and 0xFF
                }
                for (i in 0 until length) {
                    val out = (0xFF shl 24) or ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
                    if (horizontal) dst[line * w + i] = out else dst[i * w + line] = out
                    val add = at(i + radius + 1)
                    val drop = at(i - radius)
                    r += ((add shr 16) and 0xFF) - ((drop shr 16) and 0xFF)
                    g += ((add shr 8) and 0xFF) - ((drop shr 8) and 0xFF)
                    b += (add and 0xFF) - (drop and 0xFF)
                }
            }
        }
    }
}
