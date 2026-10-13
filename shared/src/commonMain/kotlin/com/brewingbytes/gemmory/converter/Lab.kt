package com.brewingbytes.gemmory.converter

import kotlin.math.pow
import kotlin.math.roundToInt

/** A CIELAB color (D65 white point). */
data class Lab(val l: Double, val a: Double, val b: Double) {
    fun distanceSquared(other: Lab): Double {
        val dl = l - other.l
        val da = a - other.a
        val db = b - other.b
        return dl * dl + da * da + db * db
    }
}

/** sRGB ⇄ CIELAB (D65). Pure Kotlin so it runs identically on every target. */
object ColorSpace {
    private const val XN = 0.95047
    private const val YN = 1.0
    private const val ZN = 1.08883
    private const val EPSILON = 216.0 / 24389.0
    private const val KAPPA = 24389.0 / 27.0

    private val SRGB_TO_LINEAR = DoubleArray(256) { i ->
        val c = i / 255.0
        if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    fun linear(channel: Int): Double = SRGB_TO_LINEAR[channel]

    /** Linear RGB in 0..1 → Lab. */
    fun linearToLab(r: Double, g: Double, b: Double): Lab {
        val x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / XN
        val y = (0.2126729 * r + 0.7151522 * g + 0.0721750 * b) / YN
        val z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / ZN
        val fx = f(x)
        val fy = f(y)
        val fz = f(z)
        return Lab(116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz))
    }

    /** Opaque ARGB → Lab (alpha ignored). */
    fun argbToLab(argb: Int): Lab =
        linearToLab(linear((argb shr 16) and 0xFF), linear((argb shr 8) and 0xFF), linear(argb and 0xFF))

    /** Lab → opaque ARGB, clamped to the sRGB gamut. */
    fun labToArgb(lab: Lab): Int {
        val fy = (lab.l + 16.0) / 116.0
        val fx = fy + lab.a / 500.0
        val fz = fy - lab.b / 200.0
        val x = fInv(fx) * XN
        val y = (if (lab.l > KAPPA * EPSILON) fy * fy * fy else lab.l / KAPPA) * YN
        val z = fInv(fz) * ZN
        val r = 3.2404542 * x - 1.5371385 * y - 0.4985314 * z
        val g = -0.9692660 * x + 1.8760108 * y + 0.0415560 * z
        val b = 0.0556434 * x - 0.2040259 * y + 1.0572252 * z
        return (0xFF shl 24) or (toSrgb8(r) shl 16) or (toSrgb8(g) shl 8) or toSrgb8(b)
    }

    private fun f(t: Double): Double = if (t > EPSILON) t.pow(1.0 / 3.0) else (KAPPA * t + 16.0) / 116.0

    private fun fInv(t: Double): Double {
        val t3 = t * t * t
        return if (t3 > EPSILON) t3 else (116.0 * t - 16.0) / KAPPA
    }

    private fun toSrgb8(linear: Double): Int {
        val c = linear.coerceIn(0.0, 1.0)
        val s = if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1.0 / 2.4) - 0.055
        return (s * 255.0).roundToInt().coerceIn(0, 255)
    }
}
