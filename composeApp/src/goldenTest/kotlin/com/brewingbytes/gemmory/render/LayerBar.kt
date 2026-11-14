package com.brewingbytes.gemmory.render

import kotlin.math.abs

/**
 * The golden bar for a drawn layer with text (T62: the time-lapse frame's overlay, end card and before/after picture,
 * and the streak card): compared as seen, flattened over mid-grey, since overlays are mostly transparent. Detail (text,
 * icons, the badge, picture edges: pixels near a luma edge in either image) takes the text bar, SSIM ≥ 0.98; the rest
 * (fills, the fade, the band, the grain) the geometry bar.
 */
object LayerBar {
    class Result(val detailSsim: Double, val rest: GoldenMath.Diff) {
        /** NaN detail (a layer with no edges) passes on the rest alone. */
        val passes: Boolean get() = (detailSsim.isNaN() || detailSsim >= TEXT_SSIM) && rest.geometryPasses

        val figures: String
            get() = "detailSsim=${GoldenMath.fmt(detailSsim)} rest differing=${GoldenMath.pct(rest.differingShare)} maxDiff=${rest.maxDiff}"
    }

    const val TEXT_SSIM = 0.98

    fun compare(golden: IntArray, actual: IntArray, w: Int): Result {
        require(golden.size == actual.size) { "sizes ${golden.size} and ${actual.size}" }
        val a = flatten(golden)
        val b = flatten(actual)
        val detail = detail(a, b, w)
        return Result(GoldenMath.ssim(a, b, w, detail), GoldenMath.diff(a, b, detail))
    }

    /** Each pixel over opaque mid-grey (unpremultiplied ARGB in, opaque ARGB out). */
    fun flatten(argb: IntArray): IntArray = IntArray(argb.size) { i ->
        val p = argb[i]
        val alpha = (p ushr 24) and 0xFF
        fun channel(shift: Int): Int = (((p shr shift) and 0xFF) * alpha + GREY * (255 - alpha) + 127) / 255
        (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    /** Pixels within [radius] of an edge (a right or lower neighbour more than [EDGE] luma levels away) in [a] or [b]. */
    fun detail(a: IntArray, b: IntArray, w: Int, radius: Int = 6): BooleanArray {
        val h = a.size / w
        val edge = BooleanArray(a.size)
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            fun edgeIn(p: IntArray): Boolean =
                (x + 1 < w && abs(luma(p[i]) - luma(p[i + 1])) > EDGE) || (y + 1 < h && abs(luma(p[i]) - luma(p[i + w])) > EDGE)
            edge[i] = edgeIn(a) || edgeIn(b)
        }
        // Dilate: a window of 2 * radius + 1 along rows, then along columns.
        val rows = BooleanArray(a.size)
        for (y in 0 until h) {
            var last = -radius - 1
            for (x in 0 until w + radius) {
                if (x < w && edge[y * w + x]) last = x
                val at = x - radius
                if (at in 0 until w && x - last <= 2 * radius) rows[y * w + at] = true
            }
        }
        val out = BooleanArray(a.size)
        for (x in 0 until w) {
            var last = -radius - 1
            for (y in 0 until h + radius) {
                if (y < h && rows[y * w + x]) last = y
                val at = y - radius
                if (at in 0 until h && y - last <= 2 * radius) out[at * w + x] = true
            }
        }
        return out
    }

    private fun luma(p: Int): Int = (299 * ((p shr 16) and 0xFF) + 587 * ((p shr 8) and 0xFF) + 114 * (p and 0xFF)) / 1000

    private const val GREY = 0x80
    private const val EDGE = 24
}
