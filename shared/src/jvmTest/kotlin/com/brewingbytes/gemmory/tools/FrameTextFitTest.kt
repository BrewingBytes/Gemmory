package com.brewingbytes.gemmory.tools

import com.brewingbytes.gemmory.timelapse.ClipLayout
import com.brewingbytes.gemmory.timelapse.ShareLayout
import java.awt.Font
import java.awt.font.FontRenderContext
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * T49 (#136): the share frame and the streak card draw some lines on one line, unmeasured (the Gemmory row's tagline
 * beside the Play badge, the end card's tagline, the app line, the progress line, the milestone title). Every
 * language's words must fit their room, measured with the faces that draw them at the weight they're drawn in,
 * plus 3% for the difference between AWT's and Android's text metrics. Sizes are at 1080 wide (design system → 9:16 time-lapse frame, milestone share card).
 * The milestone title shrinks from 104px to fit (`StreakShareCard`), so it must fit at its 64px floor. A founder clip's
 * lockup (#144) shrinks too, but every language fits it at full size, and its promise fits one line.
 */
class FrameTextFitTest {
    private val fonts = HashMap<String, Font>()
    private val context = FontRenderContext(null, true, true)

    private fun width(text: String, file: File, px: Float): Double {
        val font = fonts.getOrPut(file.path) { Font.createFont(Font.TRUETYPE_FONT, file) }
        return font.deriveFont(px).getStringBounds(text, context).width * SLACK
    }

    /**
     * The file at the weight the frame draws a [role] in: titles and strong lines at 600 (the static instances in
     * `tools/store/fonts/`, since AWT can't set a variable font's weight), body at the face's own default, as the frame
     * draws Figtree (its default instance) and Onest (400, its default).
     */
    private fun face(role: String, language: String): File {
        val res = File(AndroidStringsFile.RES, "font")
        val static = File("../tools/store/fonts")
        return when (role) {
            "display" -> StoreFrames.displayFace(language, res, static)
            "strong" -> when (language) {
                "ja" -> File(res, "zen_kaku_gothic_new_bold.ttf")
                "bg" -> File(static, "onest_600.ttf")
                else -> File(static, "figtree_600.ttf")
            }
            else -> File(res, GlyphCheck.faces(language).last())
        }
    }

    /** One line: [name] in [role] ("display" or "body") at [px] must fit [room] pixels, in every language. */
    private class Line(val name: String, val role: String, val px: Float, val room: Int, val arg: String? = null)

    @Test
    fun everyOneLineTextFitsItsRoom() {
        val badgeLeft = 1080 - ShareLayout.MARGIN - ShareLayout.ROW_BADGE_WIDTH
        val lines = listOf(
            Line("frame_tagline", "body", 28f, badgeLeft - ShareLayout.ROW_TEXT_LEFT - 16),
            Line("frame_tagline", "body", 44f, 1080 - 2 * 60),
            Line("frame_app_line", "body", 36f, 1080 - 2 * ShareLayout.MARGIN),
            Line("progress_just_started", "strong", 36f, 1080 - 2 * ShareLayout.MARGIN),
            Line("frame_watermark", "strong", 30f, 1080 / 2),
            Line("frame_before", "body", 30f, 1080 / 2 - ShareLayout.MARGIN),
            Line("frame_so_far", "body", 30f, 1080 / 2 - ShareLayout.MARGIN),
            Line("frame_after", "body", 30f, 1080 / 2 - ShareLayout.MARGIN),
            // The store line is measured all in the strong face: "Google Play" in it is bold.
            Line("frame_clip_free", "strong", 38f, ClipLayout.LOCKUP_TEXT_MAX, arg = "Google Play"),
            Line("frame_clip_promise", "body", 46f, ClipLayout.PROMISE_WIDTH),
        ) + listOf(7, 30, 50, 100, 365).map { Line("streak_milestone_$it", "display", MILESTONE_MIN, 1080 - 2 * 80) }
        val problems = AndroidStringsFile.all().flatMap { file ->
            lines.mapNotNull { line ->
                val text = file.texts.getValue(line.name).let { t -> line.arg?.let { t.replace("%1\$s", it) } ?: t }
                val w = width(text, face(line.role, file.language), line.px)
                if (w > line.room) "${file.folder.name}/${line.name} at ${line.px.toInt()}px: ${w.toInt()} > ${line.room} (\"$text\")" else null
            }
        }
        assertEquals(emptyList(), problems)
    }

    private companion object {
        const val SLACK = 1.03
        const val MILESTONE_MIN = 64f
    }
}
