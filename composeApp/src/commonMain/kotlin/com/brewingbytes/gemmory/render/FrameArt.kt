package com.brewingbytes.gemmory.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.timelapse.BoardRect
import com.brewingbytes.gemmory.timelapse.ClipLayout
import com.brewingbytes.gemmory.timelapse.Frame
import com.brewingbytes.gemmory.timelapse.ProgressLine
import com.brewingbytes.gemmory.timelapse.ProgressShare
import com.brewingbytes.gemmory.timelapse.ShareFrame
import com.brewingbytes.gemmory.timelapse.ShareLayout
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * What one time-lapse frame is drawn from (androidApp's `EncodeRequest` without its files): the grid, the placement
 * log in order, the band title, the Gemmory branding for everyone but VIP ([watermark]) and which frame ([frame]).
 */
class FrameRequest(
    val pattern: Pattern,
    val log: IntArray,
    val title: String,
    val watermark: Boolean,
    val frame: ShareFrame = ShareFrame.PLAYER,
) {
    /** Shared before the last gem (#36). */
    val inProgress: Boolean get() = log.size < pattern.cells.size

    /** The band's "47% done" line for a progress share; null for a finished picture. */
    val progressLine: ProgressLine? get() = if (inProgress) ProgressShare.line(log.size, pattern.cells.size) else null
}

/**
 * The 9:16 time-lapse frame (design system, 9:16 time-lapse frame) in common code (iOS Stage 1a, T62): drawn at
 * 1080×1920 and scaled by width for smaller targets, over a band made of the finished canvas blurred and darkened.
 * Each platform composites the layers into video frames itself (eng R1-4).
 *
 * The player's share ([ShareFrame.PLAYER], T53, [ShareLayout]): the whole picture at full width, centered above the
 * Gemmory row, with the title and app line over a dark fade ([overlay], drawn over the canvas), the row on every
 * non-VIP frame, and the non-VIP end card ([card] plus the canvas itself at the card's picture). A founder clip
 * ([ShareFrame.CLIP], #144, [ClipLayout]): the canvas above the title and the store lockup, all inside the feeds' safe
 * area ([overlay]), the finished canvas as the hook ([source]), and the E2b end card ([card]). The feed cut
 * ([ShareFrame.FEED], T51): the founder clip's frame without the end card; its hook is the finished canvas itself,
 * under a photo canvas's photo ([source]) as it fades, and the canvas goes back to the tint grid at the cut.
 *
 * Built once per attempt. The canvas lives in one bitmap that starts as the empty tint grid and gets each frame's
 * new gems drawn onto it, so a whole video costs O(gems + frames) draws (eng finding 12). [photo] gives the cropped
 * source photo at the canvas's size, or null (a bundled picture, or a missing file).
 */
class FrameArt(
    private val request: FrameRequest,
    assets: ArtAssets,
    measurer: TextMeasurer,
    val width: Int,
    val height: Int,
    photo: (width: Int, height: Int) -> ImageBitmap?,
) {
    private val style = FrameStyle(assets, measurer, width)
    private val pattern = request.pattern
    private val share: ShareLayout? =
        if (request.frame == ShareFrame.PLAYER) ShareLayout.of(width, height, pattern.width, pattern.height, request.watermark) else null
    private val clip: ClipLayout? =
        if (request.frame == ShareFrame.CLIP || request.frame == ShareFrame.FEED) style.clipLayout(height, pattern, request.title) else null
    private val feed = request.frame == ShareFrame.FEED
    private val layout = share?.board ?: clip!!.board
    private val painter = GemRenderer.GemPainter(pattern, layout.width, layout.height)
    private val board = ImageBitmap(layout.width, layout.height)
    private val boardCanvas = Canvas(board)
    private val chrome = ImageBitmap(width, height)
    private val photoImage: ImageBitmap? = photo(layout.width, layout.height)

    /** A founder clip's hook (#144, H1): the finished canvas, shown in the photo's slot over the tint grid. */
    private val hook: ImageBitmap? = clip?.takeUnless { feed }?.let {
        ImageBitmap(layout.width, layout.height).also { b ->
            val c = Canvas(b)
            for (cell in pattern.cells.indices) painter.drawCell(c, cell, isPlaced = true)
        }
    }
    private val overlayTop = share?.fadeTop ?: clip?.let { it.title.top - style.px(FrameStyle.CLIP_TEXT_ROOM) } ?: 0
    private val overlayImage: ImageBitmap? = if (share != null || clip != null) ImageBitmap(width, height - overlayTop) else null
    private val cardArea: BoardRect? = share?.card?.let { BoardRect(0, it.top - style.px(FrameStyle.CARD_SHADOW_ROOM), width, it.bottom) }
        ?: clip?.takeUnless { feed }?.card?.area?.let { BoardRect(0, it.top, width, it.bottom) }
    private val cardImage: ImageBitmap? = cardArea?.let { ImageBitmap(it.width, it.height) }
    private var drawn = 0

    /** The feed cut's hook (T51) has the whole log on the canvas; the first build frame puts the tint grid back. */
    private var revealed = false

    /** The canvas's place in the frame ([ShareLayout] for the player's share, [ClipLayout] for a founder clip). */
    val boardRect: BoardRect = layout

    /** The static frame behind the canvas: the band. */
    val background: ImageBitmap get() = chrome

    /** The canvas as of the last [apply]: the tint grid plus every gem placed so far. */
    val canvas: ImageBitmap get() = board

    /**
     * Drawn over the canvas at the photo's opacity: the cropped source photo for a photo canvas's intro, or a founder
     * clip's finished canvas for its hook (#144); the feed cut's photo over its hook (T51); null otherwise.
     */
    val source: ImageBitmap? get() = photoImage ?: hook

    /**
     * Drawn over the canvas at [overlayRect]: the player's share's fade, title, app line and (non-VIP) Gemmory row, or a
     * founder clip's title and store lockup (#144).
     */
    val overlay: ImageBitmap? get() = overlayImage
    val overlayRect: BoardRect? = overlayImage?.let { BoardRect(0, overlayTop, width, height) }

    /**
     * The end card's layer: the shadow, icon, name, tagline and badge (non-VIP player share), or the shadow, lockup and
     * promise (founder clip, #144); null without an end card.
     */
    val card: ImageBitmap? get() = cardImage

    /**
     * Every bitmap this frame owns, for a platform that frees them itself (Android's `recycle`): the layers above plus a
     * founder clip's hook when a photo takes the [source] slot.
     */
    val images: List<ImageBitmap> get() = listOfNotNull(chrome, board, photoImage, hook, overlayImage, cardImage)

    init {
        drawTints()
        style.drawBand(Canvas(chrome), pattern, height)
        if (clip != null) {
            val o = Canvas(overlayImage!!)
            o.translate(0f, -overlayTop.toFloat())
            style.drawClipText(o, clip, request.title)
            if (cardImage != null) {
                val k = Canvas(cardImage)
                k.translate(0f, -cardArea!!.top.toFloat())
                style.drawClipCard(k, clip.card)
            }
        } else if (share != null) {
            val o = Canvas(overlayImage!!)
            o.translate(0f, -overlayTop.toFloat())
            style.drawShareText(o, share, request.title, request.progressLine)
            share.row?.let { style.drawRow(o, it) }
            share.card?.let { card ->
                val k = Canvas(cardImage!!)
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
        clip?.takeUnless { feed }?.let {
            // A founder clip's card crossfades in place, over the band darkened a further 20% (#144, E2b).
            return EndCardRects(alpha, it.card.picture, cardArea!!, FrameStyle.CLIP_END_CARD_DIM)
        }
        val card = share?.card ?: return null
        val dy = ((1f - alpha) * card.slide).roundToInt()
        val p = card.picture
        val c = cardArea!!
        return EndCardRects(
            alpha = alpha,
            picture = BoardRect(p.left, p.top + dy, p.right, p.bottom + dy),
            content = BoardRect(c.left, c.top + dy, c.right, c.bottom + dy),
            dim = FrameStyle.END_CARD_DIM,
        )
    }

    /**
     * Brings [canvas] up to [frame] by drawing only the gems placed since the last call. Returns whether it changed
     * (so it needs uploading again) and the opacity of [source] over it: 1 in a founder clip's hook. The feed cut's
     * hook (T51) draws the whole log at once, and its first build frame starts again from the tint grid.
     */
    fun apply(frame: Frame): Pair<Boolean, Float> {
        val before = drawn
        var reset = false
        when (frame) {
            is Frame.Reveal -> if (!revealed) {
                advanceTo(request.log.size)
                revealed = true
            }
            is Frame.Build -> {
                if (revealed) {
                    drawTints()
                    drawn = 0
                    revealed = false
                    reset = true
                }
                advanceTo(frame.placed)
            }
            Frame.Hold, is Frame.EndCard -> advanceTo(request.log.size)
            is Frame.Intro, Frame.Hook -> Unit
        }
        val alpha = when {
            frame is Frame.Intro && photoImage != null -> frame.photoAlpha
            frame == Frame.Hook && hook != null -> 1f
            frame is Frame.Reveal && photoImage != null -> frame.photoAlpha
            else -> 0f
        }
        return (reset || drawn != before) to alpha
    }

    /** The empty tint grid over a clear canvas: the same pixels at the start and after the feed cut's hook. */
    private fun drawTints() {
        boardCanvas.drawRect(0f, 0f, board.width.toFloat(), board.height.toFloat(), CLEAR)
        for (cell in pattern.cells.indices) painter.drawCell(boardCanvas, cell, isPlaced = false)
    }

    private fun advanceTo(placed: Int) {
        val log = request.log
        while (drawn < placed && drawn < log.size) painter.drawCell(boardCanvas, log[drawn++], isPlaced = true)
    }

    companion object {
        private val CLEAR = Paint().apply { blendMode = BlendMode.Clear }

        /**
         * The before/after picture (eng E1 fallback), 1080×1920: the cropped photo (or, for a bundled picture, the
         * empty tint grid) above the finished canvas, on the same band with the same text. A progress share (#36)
         * shows the canvas as it stands, labelled "So far". The player's share (T53) has the Gemmory row at the
         * bottom for non-VIP, in place of the watermark, and slightly shorter boxes so the text clears it.
         */
        fun beforeAfter(
            request: FrameRequest,
            assets: ArtAssets,
            measurer: TextMeasurer,
            photo: (width: Int, height: Int) -> ImageBitmap?,
            /** Frees a bitmap drawn and done with (Android's `recycle`; iOS leaves it to the collector). */
            release: (ImageBitmap) -> Unit = {},
        ): ImageBitmap {
            val width = 1080
            val height = 1920
            val style = FrameStyle(assets, measurer, width)
            val out = ImageBitmap(width, height)
            val canvas = Canvas(out)
            val pattern = request.pattern
            style.drawBand(canvas, pattern, height)
            val cellPx = max(4, (width + pattern.width - 1) / pattern.width)
            val placed = BooleanArray(pattern.cells.size).also { for (cell in request.log) it[cell] = true }
            val after = GemRenderer.render(pattern, cellPx) { placed[it] }
            val before = photo(after.width, after.height) ?: GemRenderer.render(pattern, cellPx) { false }
            val player = request.frame == ShareFrame.PLAYER
            val boxW = width - 2 * style.px(FrameStyle.MARGIN)
            val boxH = if (player) 620 else 640
            var top = 48
            val words = assets.words
            val afterLabel = if (request.inProgress) words.soFar else words.after
            val paint = Paint().apply { filterQuality = FilterQuality.Low }
            for ((name, image) in listOf(words.before to before, afterLabel to after)) {
                val scale = min(boxW.toFloat() / image.width, boxH.toFloat() / image.height)
                val w = (image.width * scale).roundToInt()
                val h = (image.height * scale).roundToInt()
                val left = (width - w) / 2
                val imageTop = top + 44 + (boxH - h) / 2
                style.drawLabel(canvas, name, left.toFloat(), (imageTop - 14).toFloat())
                canvas.drawImageRect(image, IntOffset.Zero, IntSize(image.width, image.height), IntOffset(left, imageTop), IntSize(w, h), paint)
                top += 44 + boxH + 40
            }
            release(before)
            release(after)
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

/** Where the end card draws in one frame ([FrameArt.endCard]): both layers at [alpha], over the band dimmed by [dim]. */
class EndCardRects(val alpha: Float, val picture: BoardRect, val content: BoardRect, val dim: Float)

/**
 * Band, type, Gemmory row, end card and watermark of the 9:16 frame. Sizes are given at 1080 wide and scaled by
 * [width]. The faces and words are the app language's ([ArtAssets]).
 */
class FrameStyle(private val assets: ArtAssets, measurer: TextMeasurer, private val width: Int) {
    private val s = width / 1080f
    private val words = assets.words
    private val faces = assets.faces
    private val text = DrawnText(measurer, assets.language)

    fun px(at1080: Int): Int = (at1080 * s).roundToInt()

    /** The finished canvas scaled to cover the frame, blurred (~40px at 1080) and darkened to 45% brightness. */
    fun drawBand(canvas: Canvas, pattern: Pattern, height: Int) {
        val finished = GemRenderer.render(pattern, cellPx = 2)
        // Blur at 1/8 scale: a 5px box there is ~40px at full size, and upscaling with filtering smooths it.
        val sw = max(1, width / 8)
        val sh = max(1, height / 8)
        val small = ImageBitmap(sw, sh)
        val cover = max(sw.toFloat() / finished.width, sh.toFloat() / finished.height)
        Canvas(small).apply {
            save()
            translate((sw - finished.width * cover) / 2f, (sh - finished.height * cover) / 2f)
            scale(cover, cover)
            drawImage(finished, Offset.Zero, Paint().apply { filterQuality = FilterQuality.Low })
            restore()
        }
        val pixels = IntArray(sw * sh).also { small.readPixels(it) }
        boxBlur(pixels, sw, sh, radius = 5, passes = 3)
        val blurred = bakedBitmap(sw, sh).apply { setAll(pixels) }.image
        canvas.save()
        canvas.scale(width.toFloat() / sw, height.toFloat() / sh)
        canvas.drawImage(
            blurred,
            Offset.Zero,
            Paint().apply {
                filterQuality = FilterQuality.Low
                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(BAND_BRIGHTNESS, BAND_BRIGHTNESS, BAND_BRIGHTNESS, 1f) })
            },
        )
        canvas.restore()
    }

    /**
     * The title (Bricolage Grotesque 600, 60px, at most 2 lines then an ellipsis) and "Gemmory · Photo Diamond Art"
     * (Figtree 36px) from [textTop], left-aligned at 48px; the watermark row at the bottom (Figtree 600 30px, 70%,
     * bottom-right, over a 25% scrim band), so the title never collides with it. A progress share (#36) has its
     * [progress] line ("47% done", Figtree 600 36px) between the title and the app line.
     */
    fun drawText(canvas: Canvas, title: String, textTop: Int, height: Int, watermark: Boolean, progress: ProgressLine? = null) {
        val margin = px(MARGIN).toFloat()
        val titlePen = titlePen()
        val titleLayout = text.block(title, titlePen, (width - 2 * margin).toInt(), maxLines = 2)
        val top = textTop + px(40).toFloat()
        text.drawBlock(canvas, titleLayout, titlePen, margin, top, includePad = true)

        val appPen = DrawnText.Pen(faces.body, 36 * s, FRAME_TEXT)
        // The StaticLayout's height: its padded top to its last line's padded bottom.
        var appTop = top + blockHeight(titleLayout, titlePen) + px(8)
        progress?.let { line ->
            val progressPen = DrawnText.Pen(faces.bodyStrong, 36 * s, FRAME_TEXT)
            text.draw(canvas, words.progress(line), progressPen, margin, appTop - progressPen.metrics.ascent)
            appTop += progressPen.metrics.spacing + px(2)
        }
        text.draw(canvas, words.appLine, appPen, margin, appTop - appPen.metrics.ascent)

        if (!watermark) return
        val rowHeight = px(WATERMARK_ROW)
        val scrim = Paint().apply { color = Color(alpha(SCRIM, 0.25f)) }
        canvas.drawRect(0f, (height - rowHeight).toFloat(), width.toFloat(), height.toFloat(), scrim)
        val markPen = DrawnText.Pen(faces.bodyStrong, 30 * s, FRAME_TEXT).withAlpha(0.7f)
        val m = markPen.metrics
        val baseline = height - rowHeight / 2f - (m.ascent + m.descent) / 2f
        text.draw(canvas, words.watermark, markPen, width - margin, baseline, DrawnText.Align.RIGHT)
    }

    /**
     * The player's share text (T53): a `scrim` fade from 0 to 78% across the 420px above the picture area's bottom,
     * then, upward from the bottom, "Gemmory · Photo Diamond Art" (Figtree 36px), a progress share's [progress] line
     * (Figtree 600 36px) and the title (Bricolage Grotesque 600, 60px, at most 2 lines then an ellipsis), left-aligned
     * at 48px.
     */
    fun drawShareText(canvas: Canvas, layout: ShareLayout, title: String, progress: ProgressLine?) {
        val fade = Paint().apply {
            shader = LinearGradientShader(
                Offset(0f, layout.fadeTop.toFloat()),
                Offset(0f, layout.areaBottom.toFloat()),
                listOf(Color(SCRIM and 0x00FFFFFF), Color(alpha(SCRIM, FADE_ALPHA))),
                tileMode = TileMode.Clamp,
            )
        }
        canvas.drawRect(0f, layout.fadeTop.toFloat(), width.toFloat(), layout.areaBottom.toFloat(), fade)
        val left = layout.textLeft.toFloat()
        var baseline = layout.appBaseline.toFloat()
        text.draw(canvas, words.appLine, DrawnText.Pen(faces.body, 36 * s, FRAME_TEXT), left, baseline)
        progress?.let { line ->
            baseline -= px(LINE_GAP)
            text.draw(canvas, words.progress(line), DrawnText.Pen(faces.bodyStrong, 36 * s, FRAME_TEXT), left, baseline)
        }
        baseline -= px(ShareLayout.TITLE_GAP)
        val pen = titlePen()
        text.drawBlockAbove(canvas, text.block(title, pen, width - 2 * px(MARGIN), maxLines = 2), pen, left, baseline)
    }

    /**
     * The Gemmory row (T53 D1): a 150px `scrim` band at 55%, the app icon, "Gemmory" (Bricolage Grotesque 600, 44px),
     * "Your photos in gems" (Figtree 28px, 80%), and Google's Play badge at the right, the file unchanged.
     */
    fun drawRow(canvas: Canvas, row: ShareLayout.Row) {
        val scrim = Paint().apply { color = Color(alpha(SCRIM, ROW_SCRIM_ALPHA)) }
        canvas.drawRect(0f, row.top.toFloat(), width.toFloat(), (row.top + px(ShareLayout.ROW)).toFloat(), scrim)
        drawIcon(canvas, row.icon, row.iconRadius)
        text.draw(canvas, words.appName, namePen(44), row.textLeft.toFloat(), row.nameBaseline.toFloat())
        text.draw(canvas, words.tagline, taglinePen(28).withAlpha(0.8f), row.textLeft.toFloat(), row.taglineBaseline.toFloat())
        drawBadge(canvas, row.badge)
    }

    /**
     * The end card's layer (T53): the picture's shadow (the picture itself is the canvas, drawn over it), the icon at
     * 180px, "Gemmory" (Bricolage Grotesque 600, 104px), "Your photos in gems" (Figtree 44px) and the badge at 520px,
     * centered. Its backdrop, the band at ~30%, is the compositor's.
     */
    fun drawCard(canvas: Canvas, card: ShareLayout.Card) {
        drawShadow(canvas, card.picture, CARD_SHADOW_BLUR)
        drawIcon(canvas, card.icon, card.iconRadius)
        val center = width / 2f
        text.draw(canvas, words.appName, namePen(104), center, card.nameBaseline.toFloat(), DrawnText.Align.CENTER)
        text.draw(canvas, words.tagline, taglinePen(44), center, card.taglineBaseline.toFloat(), DrawnText.Align.CENTER)
        drawBadge(canvas, card.badge)
    }

    /**
     * A founder clip's frame (#144) for this [width] and [height]: the title measured at [ClipLayout.titleWidth], the
     * lockup's text and the promise measured in the app's language.
     */
    fun clipLayout(height: Int, pattern: Pattern, title: String): ClipLayout = ClipLayout.of(
        width, height, pattern.width, pattern.height,
        titleLines = clipTitleLayout(title).lineCount,
        lockupTextWidth = lockupText().width.roundToInt(),
        promiseLines = promiseLayout().lineCount,
    )

    /** A founder clip's title (as the player's, at most 2 lines) and the store lockup under it (#144, C2). */
    fun drawClipText(canvas: Canvas, layout: ClipLayout, title: String) {
        text.drawBlock(canvas, clipTitleLayout(title), titlePen(), layout.title.left.toFloat(), layout.title.top.toFloat(), includePad = false)
        drawLockup(canvas, layout.lockup)
    }

    /**
     * A founder clip's end card layer (#144, E2b): the picture's shadow (0 24px, 60px blur, 50% black; the picture is
     * the canvas, drawn over it), the lockup, and "Turn any photo into gem art" (Figtree 46px), centered.
     */
    fun drawClipCard(canvas: Canvas, card: ClipLayout.Card) {
        drawShadow(canvas, card.picture, CLIP_SHADOW_BLUR)
        drawLockup(canvas, card.lockup)
        val promise = promiseLayout()
        text.drawBlock(canvas, promise, taglinePen(46), (width - promise.size.width) / 2f, card.promise.top.toFloat(), includePad = true)
    }

    /** A box-labelled picture's label ("Before", "After"), Figtree 30px, its baseline at [baseline]. */
    fun drawLabel(canvas: Canvas, label: String, left: Float, baseline: Float) {
        text.draw(canvas, label, taglinePen(30), left, baseline)
    }

    /** The lockup's two lines: "Gemmory" over "Free on Google Play", both drawn smaller when a translation is too wide. */
    private inner class LockupText(val namePen: DrawnText.Pen, val linePen: DrawnText.Pen, val storePen: DrawnText.Pen) {
        val line = words.clipFree
        val storeAt = line.indexOf(words.clipStore).coerceAtLeast(0)
        val store = if (line.indexOf(words.clipStore) >= 0) words.clipStore else ""
        val head = line.substring(0, storeAt)
        val tail = line.substring(storeAt + store.length)
        val lineWidth: Float get() = text.width(head, linePen) + text.width(store, storePen) + text.width(tail, linePen)
        val width: Float get() = max(text.width(words.appName, namePen), lineWidth)
    }

    private fun lockupText(): LockupText {
        fun build(scale: Float) = LockupText(namePen(56).let { it.withSize(it.sizePx * scale) }, taglinePen(38).let { it.withSize(it.sizePx * scale) }, DrawnText.Pen(faces.bodyBold, 38 * s * scale, FRAME_TEXT))
        val full = build(1f)
        val max = px(ClipLayout.LOCKUP_TEXT_MAX)
        return if (full.width <= max) full else build(max / full.width)
    }

    private fun drawLockup(canvas: Canvas, lockup: ClipLayout.Lockup) {
        drawIcon(canvas, lockup.icon, lockup.iconRadius)
        val t = lockupText()
        val name = t.namePen.metrics
        val line = t.linePen.metrics
        val gap = px(LOCKUP_LINE_GAP) * (t.linePen.sizePx / (38 * s))
        val block = (name.descent - name.ascent) + gap + (line.descent - line.ascent)
        // Centered on the icon; a block taller than it (Japanese's tall face) ends on its bottom, the safe area's.
        val blockTop = min((lockup.icon.top + lockup.icon.bottom) / 2f - block / 2f, lockup.icon.bottom - block)
        val nameBaseline = blockTop - name.ascent
        val lineBaseline = nameBaseline + name.descent + gap - line.ascent
        val x = lockup.textLeft.toFloat()
        text.draw(canvas, words.appName, t.namePen, x, nameBaseline)
        var at = x
        text.draw(canvas, t.head, t.linePen, at, lineBaseline)
        at += text.width(t.head, t.linePen)
        text.draw(canvas, t.store, t.storePen, at, lineBaseline)
        at += text.width(t.store, t.storePen)
        text.draw(canvas, t.tail, t.linePen, at, lineBaseline)
    }

    /** "Turn any photo into gem art" (Figtree 46px, centered), wrapped to at most 2 lines in [ClipLayout.PROMISE_WIDTH]. */
    private fun promiseLayout(): TextLayoutResult {
        val pen = taglinePen(46)
        val box = px(ClipLayout.PROMISE_WIDTH)
        val width = min(box, text.width(words.clipPromise, pen).roundToInt() + 1)
        return text.block(words.clipPromise, pen, width, maxLines = 2, align = DrawnText.Align.CENTER)
    }

    /**
     * A founder clip's title in [ClipLayout.titleWidth], each line exactly [ClipLayout.TITLE_LINE] tall whatever the
     * face's own line height (Bulgarian's and Japanese's are taller), so it fills the box [ClipLayout] gives it.
     */
    fun clipTitleLayout(title: String): TextLayoutResult =
        text.block(title, titlePen(), ClipLayout.titleWidth(width), maxLines = 2, lineHeightPx = px(ClipLayout.TITLE_LINE))

    /** A `StaticLayout`'s height with font padding: its first line's padded top to its last line's padded bottom. */
    private fun blockHeight(layout: TextLayoutResult, pen: DrawnText.Pen): Float {
        val m = pen.metrics
        return -m.topInt + (layout.lastBaseline - layout.firstBaseline) + m.bottomInt
    }

    private fun titlePen() = DrawnText.Pen(faces.display, 60 * s, FRAME_TEXT, letterSpacingEm = -0.01f)

    private fun namePen(size: Int) = DrawnText.Pen(faces.display, size * s, FRAME_TEXT)

    private fun taglinePen(size: Int) = DrawnText.Pen(faces.body, size * s, FRAME_TEXT)

    private fun drawShadow(canvas: Canvas, p: BoardRect, blur: Int) {
        val shadow = Paint().apply {
            color = Color(0x80000000.toInt())
            blurMask(px(blur).toFloat())
        }
        val dy = px(CARD_SHADOW_Y).toFloat()
        canvas.drawRect(p.left.toFloat(), p.top + dy, p.right.toFloat(), p.bottom + dy, shadow)
    }

    private fun drawIcon(canvas: Canvas, at: BoardRect, radius: Int) {
        val icon = assets.appIcon ?: return
        val rect = Rect(at.left.toFloat(), at.top.toFloat(), at.right.toFloat(), at.bottom.toFloat())
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(RoundRect(rect, radius.toFloat(), radius.toFloat())) })
        canvas.translate(rect.left, rect.top)
        canvas.scale(rect.width / icon.width, rect.height / icon.height)
        canvas.drawImage(icon, Offset.Zero, Paint().apply { filterQuality = FilterQuality.Low })
        canvas.restore()
    }

    private fun drawBadge(canvas: Canvas, at: BoardRect) {
        val badge = assets.playBadge ?: return
        canvas.drawImageRect(
            badge, IntOffset.Zero, IntSize(badge.width, badge.height), IntOffset(at.left, at.top), IntSize(at.width, at.height),
            Paint().apply { filterQuality = FilterQuality.Low },
        )
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

        /** A founder clip's end card (#144, E2b): the band darkened a further 20%. */
        const val CLIP_END_CARD_DIM = 0.2f
        const val CLIP_SHADOW_BLUR = 60

        /** The overlay layer starts this far above a founder clip's title, for the glyphs above its line box. */
        const val CLIP_TEXT_ROOM = 40
        const val LOCKUP_LINE_GAP = 6
        const val FRAME_TEXT = 0xFFF2EEE4.toInt()
        const val SCRIM = 0xFF141815.toInt()

        /** [argb] with its alpha set to [share], rounded as `Paint.alpha = (255 * share).roundToInt()` was. */
        fun alpha(argb: Int, share: Float): Int = (argb and 0x00FFFFFF) or ((255 * share).roundToInt() shl 24)

        /** In place: [passes] horizontal + vertical box blurs of [radius], clamped at the edges, approach a Gaussian. */
        fun boxBlur(px: IntArray, w: Int, h: Int, radius: Int, passes: Int) {
            val tmp = IntArray(w * h)
            repeat(passes) {
                blurLine(px, tmp, w, h, radius, horizontal = true)
                blurLine(tmp, px, w, h, radius, horizontal = false)
            }
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
