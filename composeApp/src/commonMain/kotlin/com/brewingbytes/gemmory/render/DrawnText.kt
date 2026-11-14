package com.brewingbytes.gemmory.render

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.ceil

/**
 * How a drawn title wraps in a language (T49 DR6), as androidApp's `wrapFor` did on `StaticLayout`: German and
 * Hungarian hyphenate, Japanese breaks at phrases, and every other language breaks as `StaticLayout`'s defaults.
 * Phrase breaks and the line-break strategy are Android-only Compose APIs, so each platform says how ([wrapStyle]).
 */
object TextWrap {
    fun style(language: String): TextStyle = wrapStyle(language)
}

/**
 * A one-line layout's advance, unrounded: what a `Paint` with a centered or right `textAlign` places text by. The
 * intrinsic width is Android's `BoringLayout` width, rounded up to a whole pixel, which put centered text up to half a
 * pixel left of the old renderers'.
 */
internal fun TextLayoutResult.advance(): Float = getHorizontalPosition(layoutInput.text.length, usePrimaryDirection = true)

/** The drawn text's platform style: on Android, emoji as the system font draws them (see the actual). */
internal expect fun drawnPlatformStyle(): PlatformTextStyle?

/** [TextWrap.style] on this platform. */
internal expect fun wrapStyle(language: String): TextStyle

/**
 * Text drawn into a bitmap in px (density 1), where the old `Paint.drawText` and `StaticLayout` code put it: the
 * shared time-lapse frame's and streak card's text (iOS Stage 1a, T62). Lines are placed by baseline, from the face's
 * [LineMetrics], since Compose's own line metrics are whole pixels.
 */
internal class DrawnText(private val measurer: TextMeasurer, private val language: String) {
    /** A [face] at [sizePx]: its style, and how [Paint] measured it. */
    class Pen(val face: Face, val sizePx: Float, val argb: Int, val letterSpacingEm: Float = 0f) {
        val metrics: LineMetrics by lazy { face.metrics(sizePx) }

        /**
         * The face as given, never synthesized: each [Face] family is one font at its weight, as the old `Paint` drew
         * it, and Android 12+'s Bold text setting raises the weight a Compose resolver asks for, which would otherwise
         * fake-bold it.
         */
        fun style() = TextStyle(
            fontFamily = face.family,
            fontWeight = face.weight,
            fontSynthesis = FontSynthesis.None,
            platformStyle = drawnPlatformStyle(),
            fontSize = sizePx.sp,
            letterSpacing = letterSpacingEm.em,
        )

        fun withSize(sizePx: Float) = Pen(face, sizePx, argb, letterSpacingEm)

        /** As `Paint.alpha = (255 * alpha).roundToInt()`. */
        fun withAlpha(alpha: Float) = Pen(face, sizePx, FrameStyle.alpha(argb, alpha), letterSpacingEm)
    }

    enum class Align { LEFT, CENTER, RIGHT }

    /** One line, unwrapped: `Paint.measureText`, which rounds the advance up to a whole pixel. */
    fun width(text: String, pen: Pen): Float = ceil(line(text, pen).advance())

    /** `Canvas.drawText(text, x, baseline, paint)` with [align] as the paint's `textAlign`. */
    fun draw(canvas: Canvas, text: String, pen: Pen, x: Float, baseline: Float, align: Align = Align.LEFT) {
        if (text.isEmpty()) return
        val layout = line(text, pen)
        val w = layout.advance()
        val left = when (align) {
            Align.LEFT -> x
            Align.CENTER -> x - w / 2f
            Align.RIGHT -> x - w
        }
        draw(canvas, layout, pen, left, baseline - layout.firstBaseline)
    }

    /**
     * A wrapped block in [width] px, at most [maxLines] lines then an ellipsis, wrapping as [TextWrap] says for the
     * language: a `StaticLayout`. [lineHeightPx] sets each line's height with the extra below the text, as
     * `StaticLayout`'s line spacing does; null keeps the face's own.
     */
    fun block(
        text: String,
        pen: Pen,
        width: Int,
        maxLines: Int,
        align: Align = Align.LEFT,
        lineHeightPx: Int? = null,
    ): TextLayoutResult {
        var style = TextWrap.style(language).merge(pen.style()).copy(
            textAlign = when (align) {
                Align.LEFT -> TextAlign.Left
                Align.CENTER -> TextAlign.Center
                Align.RIGHT -> TextAlign.Right
            },
        )
        if (lineHeightPx != null) {
            style = style.copy(
                lineHeight = lineHeightPx.sp,
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Top, LineHeightStyle.Trim.None),
            )
        }
        return measurer.measure(
            text,
            style,
            overflow = TextOverflow.Ellipsis,
            softWrap = true,
            maxLines = maxLines,
            // A StaticLayout's width is fixed: lines align within all of it, however short they are.
            constraints = Constraints.fixedWidth(width),
            density = PX,
        )
    }

    /**
     * Draws [layout] with its top at [top]: a `StaticLayout` drawn there. [includePad] puts the first baseline below
     * the face's top as `StaticLayout`'s default font padding does; without it, below its ascent.
     */
    fun drawBlock(canvas: Canvas, layout: TextLayoutResult, pen: Pen, left: Float, top: Float, includePad: Boolean) {
        val firstBaseline = if (includePad) top - pen.metrics.topInt else top + layout.firstBaseline
        draw(canvas, layout, pen, left, firstBaseline - layout.firstBaseline)
    }

    /** Draws [layout] with its last line's baseline at [baseline]. */
    fun drawBlockAbove(canvas: Canvas, layout: TextLayoutResult, pen: Pen, left: Float, baseline: Float) {
        draw(canvas, layout, pen, left, baseline - layout.lastBaseline)
    }

    private fun line(text: String, pen: Pen): TextLayoutResult =
        measurer.measure(text, pen.style(), softWrap = false, maxLines = 1, density = PX)

    /**
     * Paints [layout] with its top left at ([left], [top]), unclipped as a `StaticLayout` draws: Compose's `drawText`
     * clips an ellipsized layout to its box, which cut the second line's descenders off a truncated founder clip title
     * (its lines are shorter than the face).
     */
    private fun draw(canvas: Canvas, layout: TextLayoutResult, pen: Pen, left: Float, top: Float) {
        canvas.save()
        canvas.translate(left, top)
        layout.multiParagraph.paint(canvas, color = Color(pen.argb))
        canvas.restore()
    }

    companion object {
        val PX = Density(1f, 1f)
    }
}
