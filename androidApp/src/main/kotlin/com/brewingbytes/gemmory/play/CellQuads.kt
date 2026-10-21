package com.brewingbytes.gemmory.play

/**
 * One frame's visible cells as textured quads for a single `Canvas.drawVertices` (#117): each cell is 4 vertices
 * (top-left, top-right, bottom-right, bottom-left) in [positions], its sprite's atlas rect at the same spots in [texs],
 * and two triangles in [indices]. Indices are 16-bit, so a batch holds at most [MAX_QUADS] cells; the renderer draws
 * a [full] batch and resets it. The arrays grow and are reused across frames.
 */
class CellQuads {
    var count = 0
        private set
    var positions = FloatArray(INITIAL * FLOATS)
        private set
    var texs = FloatArray(INITIAL * FLOATS)
        private set
    var indices = indicesFor(INITIAL)
        private set

    val full: Boolean get() = count == MAX_QUADS

    /** The floats of [positions] (and [texs]) in use: drawVertices' vertexCount. */
    val floatCount: Int get() = count * FLOATS

    val indexCount: Int get() = count * INDICES

    fun reset() {
        count = 0
    }

    /** Adds a [size] square cell at ([left], [top]) showing the [srcSize] square atlas rect at ([srcLeft], [srcTop]). */
    fun add(left: Float, top: Float, size: Float, srcLeft: Int, srcTop: Int, srcSize: Int) {
        check(count < MAX_QUADS) { "batch full" }
        if (count * FLOATS == positions.size) grow()
        val i = count * FLOATS
        corners(positions, i, left, top, left + size, top + size)
        corners(texs, i, srcLeft.toFloat(), srcTop.toFloat(), (srcLeft + srcSize).toFloat(), (srcTop + srcSize).toFloat())
        count++
    }

    private fun grow() {
        val quads = minOf(MAX_QUADS, positions.size / FLOATS * 2)
        positions = positions.copyOf(quads * FLOATS)
        texs = texs.copyOf(quads * FLOATS)
        indices = indicesFor(quads)
    }

    private fun corners(out: FloatArray, i: Int, l: Float, t: Float, r: Float, b: Float) {
        out[i] = l
        out[i + 1] = t
        out[i + 2] = r
        out[i + 3] = t
        out[i + 4] = r
        out[i + 5] = b
        out[i + 6] = l
        out[i + 7] = b
    }

    companion object {
        /** 4 vertices per quad, so the last vertex index is 65,535 at most (read unsigned). */
        const val MAX_QUADS = 16_383
        private const val INITIAL = 1_024
        private const val FLOATS = 8
        private const val INDICES = 6

        private fun indicesFor(quads: Int) = ShortArray(quads * INDICES).also {
            for (q in 0 until quads) {
                val v = q * 4
                val i = q * INDICES
                it[i] = v.toShort()
                it[i + 1] = (v + 1).toShort()
                it[i + 2] = (v + 2).toShort()
                it[i + 3] = v.toShort()
                it[i + 4] = (v + 2).toShort()
                it[i + 5] = (v + 3).toShort()
            }
        }
    }
}
