package com.brewingbytes.gemmory.render

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Japanese titles break where they did before T62 (eng R10-3): `goldens/renderer/ja-wrap.txt` holds, for every
 * bundled picture's Japanese title at four widths, the line starts the old `StaticLayout` with `wrapFor("ja")` gave
 * (phrase breaks, Android 13+), captured by androidApp's `RendererGoldenCapture`. [check] lays each out the way the
 * shared frame does ([TextWrap], Zen Kaku Gothic New Bold at 60px, -0.01em) and returns the rows that break elsewhere.
 */
object JapaneseWrap {
    /** One fixture row: [title] laid out [width] px wide starts its lines at [starts]. */
    data class Row(val width: Int, val title: String, val starts: List<Int>)

    /** `width|title|0,7,12` per line; `#` lines are comments. */
    fun parse(text: String): List<Row> = text.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
        val (width, title, starts) = line.split('|')
        Row(width.toInt(), title, starts.split(',').map(String::toInt))
    }.toList()

    fun format(rows: List<Row>): String = buildString {
        append("# width|title|line starts: the old StaticLayout + wrapFor(\"ja\"), Zen Kaku Gothic New Bold 60px, -0.01em (T62)\n")
        for (row in rows) append("${row.width}|${row.title}|${row.starts.joinToString(",")}\n")
    }

    /** The rows [measurer] breaks differently, as "expected → actual" lines; empty when all match. */
    fun check(rows: List<Row>, measurer: TextMeasurer, display: FontFamily): List<String> = rows.mapNotNull { row ->
        val layout = measurer.measure(
            row.title,
            TextWrap.style("ja").merge(TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = SIZE.sp, letterSpacing = (-0.01).em)),
            constraints = Constraints(maxWidth = row.width),
            density = Density(1f),
        )
        val starts = (0 until layout.lineCount).map(layout::getLineStart)
        if (starts == row.starts) null else "${row.width} ${row.title}: ${row.starts} → $starts"
    }

    /** The frame title's size at 1080 wide. */
    const val SIZE = 60

    /** The player's share title (1080 - 2 × 48), the founder clip's (920 - 64), and two narrower boxes that force breaks. */
    val WIDTHS = listOf(984, 856, 600, 400)
}
