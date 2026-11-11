package com.brewingbytes.gemmory.spike

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The renderer golden bar's arithmetic (design doc, Success Criteria), on ARGB pixel arrays so Android and iOS
 * tests share it. Geometry: the share of unmasked pixels off by more than 8/255 in a channel, and the largest
 * difference. SSIM: the mean over 8×8 luma windows, of the whole image or of the windows mostly inside a region.
 */
object GoldenMath {
    class Diff(val differingShare: Double, val maxDiff: Int) {
        /** ≤ 0.5% of pixels off by more than 8/255, none by more than 32/255. */
        val geometryPasses: Boolean get() = differingShare <= 0.005 && maxDiff <= 32
    }

    fun diff(a: IntArray, b: IntArray, masked: BooleanArray? = null): Diff {
        require(a.size == b.size) { "sizes ${a.size} and ${b.size}" }
        var differing = 0
        var counted = 0
        var maxDiff = 0
        for (i in a.indices) {
            if (masked != null && masked[i]) continue
            counted++
            var d = 0
            for (shift in SHIFTS) d = max(d, abs(((a[i] shr shift) and 0xFF) - ((b[i] shr shift) and 0xFF)))
            if (d > 8) differing++
            if (d > maxDiff) maxDiff = d
        }
        return Diff(differing.toDouble() / max(1, counted), maxDiff)
    }

    /** A mask of [w]×[h] with each box (left, top, right, bottom; right and bottom exclusive) set. */
    fun mask(w: Int, h: Int, boxes: List<IntArray>): BooleanArray {
        val masked = BooleanArray(w * h)
        for (box in boxes) {
            for (y in max(0, box[1]) until min(h, box[3])) for (x in max(0, box[0]) until min(w, box[2])) masked[y * w + x] = true
        }
        return masked
    }

    /**
     * Mean SSIM of 8×8 luma windows: all of them, or only those with at least half their pixels in [region]. NaN when
     * no window qualifies, so a mask that misses its text or shadow fails a `>=` bar instead of passing as 1.0.
     */
    fun ssim(a: IntArray, b: IntArray, w: Int, region: BooleanArray? = null): Double {
        val h = a.size / w
        val c1 = (0.01 * 255) * (0.01 * 255)
        val c2 = (0.03 * 255) * (0.03 * 255)
        var total = 0.0
        var windows = 0
        var y = 0
        while (y + 8 <= h) {
            var x = 0
            while (x + 8 <= w) {
                var inRegion = 0
                var ma = 0.0
                var mb = 0.0
                for (dy in 0 until 8) for (dx in 0 until 8) {
                    val i = (y + dy) * w + x + dx
                    if (region == null || region[i]) inRegion++
                    ma += luma(a[i])
                    mb += luma(b[i])
                }
                if (inRegion >= 32) {
                    ma /= 64
                    mb /= 64
                    var va = 0.0
                    var vb = 0.0
                    var cov = 0.0
                    for (dy in 0 until 8) for (dx in 0 until 8) {
                        val i = (y + dy) * w + x + dx
                        val da = luma(a[i]) - ma
                        val db = luma(b[i]) - mb
                        va += da * da
                        vb += db * db
                        cov += da * db
                    }
                    total += ((2 * ma * mb + c1) * (2 * cov / 63 + c2)) / ((ma * ma + mb * mb + c1) * ((va + vb) / 63 + c2))
                    windows++
                }
                x += 8
            }
            y += 8
        }
        return if (windows == 0) Double.NaN else total / windows
    }

    private fun luma(p: Int) = 0.299 * ((p shr 16) and 0xFF) + 0.587 * ((p shr 8) and 0xFF) + 0.114 * (p and 0xFF)

    private val SHIFTS = intArrayOf(16, 8, 0)

    fun fmt(v: Double): String = if (v.isNaN()) "no windows" else ((v * 10000).toLong() / 10000.0).toString()

    fun pct(share: Double): String = "${(share * 10000).toLong() / 100.0}%"
}
