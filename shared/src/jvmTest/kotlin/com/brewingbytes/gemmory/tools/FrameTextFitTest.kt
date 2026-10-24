package com.brewingbytes.gemmory.tools

import com.brewingbytes.gemmory.timelapse.ShareLayout
import java.awt.Font
import java.awt.font.FontRenderContext
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * T49 (#136): the share frame and the streak card draw some lines on one line, unmeasured (the Gemmory row's tagline
 * beside the Play badge, the end card's tagline, the app line, the progress line, the milestone title). Every
 * language's words must fit their room, measured with the faces that draw them, plus 8% for the weight the JVM
 * can't set on a variable font. Sizes are at 1080 wide (design system → 9:16 time-lapse frame, milestone share card).
 * The milestone title shrinks from 104px to fit (`StreakShareCard`), so it must fit at its 64px floor.
 */
class FrameTextFitTest {
    private val fonts = HashMap<String, Font>()
    private val context = FontRenderContext(null, true, true)

    private fun width(text: String, file: String, px: Float): Double {
        val font = fonts.getOrPut(file) { Font.createFont(Font.TRUETYPE_FONT, File(AndroidStringsFile.RES, "font/$file")) }
        return font.deriveFont(px).getStringBounds(text, context).width * SLACK
    }

    /** One line: [name] in [role] ("display" or "body") at [px] must fit [room] pixels, in every language. */
    private class Line(val name: String, val role: String, val px: Float, val room: Int)

    @Test
    fun everyOneLineTextFitsItsRoom() {
        val badgeLeft = 1080 - ShareLayout.MARGIN - ShareLayout.ROW_BADGE_WIDTH
        val lines = listOf(
            Line("frame_tagline", "body", 28f, badgeLeft - ShareLayout.ROW_TEXT_LEFT - 16),
            Line("frame_tagline", "body", 44f, 1080 - 2 * 60),
            Line("frame_app_line", "body", 36f, 1080 - 2 * ShareLayout.MARGIN),
            Line("progress_just_started", "body", 36f, 1080 - 2 * ShareLayout.MARGIN),
            Line("frame_watermark", "body", 30f, 1080 / 2),
            Line("frame_before", "body", 30f, 1080 / 2 - ShareLayout.MARGIN),
            Line("frame_so_far", "body", 30f, 1080 / 2 - ShareLayout.MARGIN),
            Line("frame_after", "body", 30f, 1080 / 2 - ShareLayout.MARGIN),
        ) + listOf(7, 30, 50, 100, 365).map { Line("streak_milestone_$it", "display", MILESTONE_MIN, 1080 - 2 * 80) }
        val problems = AndroidStringsFile.all().flatMap { file ->
            val (display, body) = GlyphCheck.faces(file.language)
            lines.mapNotNull { line ->
                val text = file.texts.getValue(line.name)
                val w = width(text, if (line.role == "display") display else body, line.px)
                if (w > line.room) "${file.folder.name}/${line.name} at ${line.px.toInt()}px: ${w.toInt()} > ${line.room} (\"$text\")" else null
            }
        }
        assertEquals(emptyList(), problems)
    }

    private companion object {
        const val SLACK = 1.08
        const val MILESTONE_MIN = 64f
    }
}
