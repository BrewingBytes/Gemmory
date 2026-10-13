package com.brewingbytes.gemmory.converter

import kotlin.random.Random

/**
 * Spike tuning knobs. Defaults come from the 2026-10-13 face tuning pass (11 portraits + pet + landscape at
 * Quick/Medium/Big): plain saturation, no speck merging, lightness-weighted clustering and light sharpening.
 */
data class ConverterOptions(
    /** Lightness contrast around L=50. 1.0 = unchanged. */
    val contrast: Double = 1.0,
    /** Chroma multiplier on a/b. 1.0 = unchanged (1.1 pushed skin tones toward lavender/orange). */
    val saturation: Double = 1.0,
    /**
     * Same-color regions smaller than this many cells merge into their dominant neighbor. 1 = off.
     * Off by default: merging single cells erased eyes, teeth and lip lines and left hard blotches on skin.
     */
    val minRegionCells: Int = 1,
    /**
     * Scales the L axis when clustering, so the palette spends more slots on light/dark steps than on hue.
     * Facial structure (eyes, nose, jaw) is mostly lightness. 1.0 = plain CIELAB distance.
     */
    val lightnessWeight: Double = 2.5,
    /** Unsharp-mask amount on L at grid resolution (3×3 box blur). Recovers edges lost to area averaging. 0 = off. */
    val sharpen: Double = 0.65,
    val maxIterations: Int = 24,
) {
    init {
        require(contrast > 0.0 && saturation >= 0.0) { "contrast must be > 0 and saturation >= 0" }
        require(lightnessWeight > 0.0 && sharpen >= 0.0) { "lightnessWeight must be > 0 and sharpen >= 0" }
        require(minRegionCells >= 1 && maxIterations >= 1)
    }
}

/**
 * Photo → gem grid (eng E3). Deterministic for a given input, spec, seed and options.
 *
 * 1. Area-average the source to grid resolution in Lab (transparent pixels are composited over white).
 * 2. Optional contrast/saturation boost and lightness sharpening.
 * 3. Seeded k-means++ on the grid samples, at most [GridSpec.paletteCap] colors (no dithering),
 *    optionally weighting lightness over hue.
 * 4. Optionally merge tiny same-color regions into their dominant neighbor (off by default).
 * 5. Number the slots by cell count, most common first.
 *
 * @param pixels ARGB, row-major, `width * height` long. Callers downscale to ≤1024 px first.
 * @throws IllegalArgumentException on empty or mis-sized input.
 */
fun convert(
    pixels: IntArray,
    width: Int,
    height: Int,
    spec: GridSpec,
    seed: Long,
    options: ConverterOptions = ConverterOptions(),
): Pattern {
    require(width > 0 && height > 0) { "image must be at least 1×1, was ${width}×$height" }
    require(pixels.size == width * height) { "pixels.size ${pixels.size} != width*height ${width * height}" }

    val averaged = areaAverage(pixels, width, height, spec.width, spec.height)
        .map { boost(it, options) }
    val samples = sharpenLightness(averaged, spec.width, spec.height, options.sharpen)
    val w = options.lightnessWeight
    val weighted = if (w == 1.0) samples else samples.map { Lab(it.l * w, it.a, it.b) }
    val weightedCenters = clusterCenters(weighted, spec.paletteCap, seed, options.maxIterations)
    val assignment = IntArray(weighted.size) { nearest(weighted[it], weightedCenters) }
    val centers = if (w == 1.0) weightedCenters else weightedCenters.map { Lab(it.l / w, it.a, it.b) }
    if (options.minRegionCells > 1) {
        mergeSmallRegions(assignment, spec.width, spec.height, centers, options.minRegionCells)
    }
    return numberSlots(assignment, centers, spec)
}

private fun areaAverage(pixels: IntArray, width: Int, height: Int, gridW: Int, gridH: Int): List<Lab> {
    val out = ArrayList<Lab>(gridW * gridH)
    for (gy in 0 until gridH) {
        val y0 = gy * height / gridH
        val y1 = maxOf(y0 + 1, (gy + 1) * height / gridH)
        for (gx in 0 until gridW) {
            val x0 = gx * width / gridW
            val x1 = maxOf(x0 + 1, (gx + 1) * width / gridW)
            var l = 0.0
            var a = 0.0
            var b = 0.0
            for (y in y0 until y1) {
                val row = y * width
                for (x in x0 until x1) {
                    val lab = pixelToLab(pixels[row + x])
                    l += lab.l
                    a += lab.a
                    b += lab.b
                }
            }
            val n = ((y1 - y0) * (x1 - x0)).toDouble()
            out += Lab(l / n, a / n, b / n)
        }
    }
    return out
}

private fun pixelToLab(argb: Int): Lab {
    val alpha = (argb ushr 24) / 255.0
    if (alpha >= 1.0) return ColorSpace.argbToLab(argb)
    val white = 1.0 - alpha
    return ColorSpace.linearToLab(
        ColorSpace.linear((argb shr 16) and 0xFF) * alpha + white,
        ColorSpace.linear((argb shr 8) and 0xFF) * alpha + white,
        ColorSpace.linear(argb and 0xFF) * alpha + white,
    )
}

private fun boost(lab: Lab, options: ConverterOptions): Lab =
    if (options.contrast == 1.0 && options.saturation == 1.0) {
        lab
    } else {
        Lab(
            (50.0 + (lab.l - 50.0) * options.contrast).coerceIn(0.0, 100.0),
            lab.a * options.saturation,
            lab.b * options.saturation,
        )
    }

private fun sharpenLightness(samples: List<Lab>, w: Int, h: Int, amount: Double): List<Lab> {
    if (amount == 0.0) return samples
    return List(samples.size) { i ->
        val x = i % w
        val y = i / w
        var sum = 0.0
        var n = 0
        for (dy in -1..1) {
            val yy = (y + dy).coerceIn(0, h - 1)
            for (dx in -1..1) {
                sum += samples[yy * w + (x + dx).coerceIn(0, w - 1)].l
                n++
            }
        }
        val s = samples[i]
        Lab((s.l + amount * (s.l - sum / n)).coerceIn(0.0, 100.0), s.a, s.b)
    }
}

/** Seeded k-means++ init followed by Lloyd iterations. Returns at most [k] centers. */
private fun clusterCenters(samples: List<Lab>, k: Int, seed: Long, maxIterations: Int): List<Lab> {
    val distinct = LinkedHashSet(samples)
    if (distinct.size <= k) return distinct.toList()

    val random = Random(seed)
    val n = samples.size
    val centers = mutableListOf(samples[random.nextInt(n)])
    val d2 = DoubleArray(n) { samples[it].distanceSquared(centers[0]) }
    while (centers.size < k) {
        val total = d2.sum()
        if (total <= 0.0) break
        var target = random.nextDouble() * total
        var pick = n - 1
        for (i in 0 until n) {
            target -= d2[i]
            if (target < 0.0 && d2[i] > 0.0) {
                pick = i
                break
            }
        }
        val c = samples[pick]
        centers += c
        for (i in 0 until n) d2[i] = minOf(d2[i], samples[i].distanceSquared(c))
    }

    val assignment = IntArray(n) { -1 }
    repeat(maxIterations) {
        var changed = false
        for (i in 0 until n) {
            val c = nearest(samples[i], centers)
            if (c != assignment[i]) {
                assignment[i] = c
                changed = true
            }
        }
        if (!changed) return centers
        val sums = Array(centers.size) { DoubleArray(4) }
        for (i in 0 until n) {
            val s = sums[assignment[i]]
            s[0] += samples[i].l
            s[1] += samples[i].a
            s[2] += samples[i].b
            s[3] += 1.0
        }
        for (c in centers.indices) {
            val s = sums[c]
            if (s[3] > 0.0) centers[c] = Lab(s[0] / s[3], s[1] / s[3], s[2] / s[3])
        }
    }
    return centers
}

private fun nearest(lab: Lab, centers: List<Lab>): Int {
    var best = 0
    var bestD = Double.MAX_VALUE
    for (c in centers.indices) {
        val d = lab.distanceSquared(centers[c])
        if (d < bestD) {
            bestD = d
            best = c
        }
    }
    return best
}

/**
 * Reassigns every 4-connected same-color region smaller than [minCells] to the neighboring color it
 * shares the most border with (ties: closest color, then lowest slot). Scans in row-major order.
 */
private fun mergeSmallRegions(assignment: IntArray, w: Int, h: Int, centers: List<Lab>, minCells: Int) {
    val visited = BooleanArray(assignment.size)
    val region = ArrayList<Int>()
    val queue = ArrayDeque<Int>()
    for (start in assignment.indices) {
        if (visited[start]) continue
        val color = assignment[start]
        region.clear()
        queue.addLast(start)
        visited[start] = true
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            region += cell
            forEachNeighbor(cell, w, h) { nb ->
                if (!visited[nb] && assignment[nb] == color) {
                    visited[nb] = true
                    queue.addLast(nb)
                }
            }
        }
        if (region.size >= minCells) continue

        val border = HashMap<Int, Int>()
        for (cell in region) {
            forEachNeighbor(cell, w, h) { nb ->
                val other = assignment[nb]
                if (other != color) border[other] = (border[other] ?: 0) + 1
            }
        }
        if (border.isEmpty()) continue
        val target = border.entries.minWith(
            compareByDescending<Map.Entry<Int, Int>> { it.value }
                .thenBy { centers[it.key].distanceSquared(centers[color]) }
                .thenBy { it.key },
        ).key
        for (cell in region) assignment[cell] = target
    }
}

private inline fun forEachNeighbor(cell: Int, w: Int, h: Int, action: (Int) -> Unit) {
    val x = cell % w
    val y = cell / w
    if (x > 0) action(cell - 1)
    if (x < w - 1) action(cell + 1)
    if (y > 0) action(cell - w)
    if (y < h - 1) action(cell + w)
}

/** Drops unused centers and numbers slots by cell count (desc), then lightness, then old index. */
private fun numberSlots(assignment: IntArray, centers: List<Lab>, spec: GridSpec): Pattern {
    val counts = IntArray(centers.size).also { c -> assignment.forEach { c[it]++ } }
    val order = centers.indices
        .filter { counts[it] > 0 }
        .sortedWith(compareByDescending<Int> { counts[it] }.thenBy { centers[it].l }.thenBy { it })
    val remap = IntArray(centers.size) { -1 }
    order.forEachIndexed { slot, old -> remap[old] = slot }
    val palette = order.mapIndexed { slot, old ->
        val argb = ColorSpace.labToArgb(centers[old])
        PaletteColor(number = slot + 1, argb = argb, lab = centers[old])
    }
    return Pattern(spec.width, spec.height, palette, IntArray(assignment.size) { remap[assignment[it]] })
}
