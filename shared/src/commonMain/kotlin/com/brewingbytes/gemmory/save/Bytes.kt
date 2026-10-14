package com.brewingbytes.gemmory.save

/** Thrown inside the codecs when a body doesn't parse; mapped to [Refusal.Malformed] before it leaves them. */
internal class MalformedException : Exception()

/** Big-endian writer for the GMRY file formats. */
internal class ByteWriter(initialCapacity: Int = 64) {
    private var buf = ByteArray(initialCapacity)
    private var size = 0

    private fun ensure(extra: Int) {
        if (size + extra > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, size + extra))
    }

    fun u8(v: Int) {
        ensure(1)
        buf[size++] = v.toByte()
    }

    fun u16(v: Int) {
        require(v in 0..0xFFFF) { "u16 out of range: $v" }
        u8(v ushr 8)
        u8(v)
    }

    fun u32(v: Int) {
        u8(v ushr 24)
        u8(v ushr 16)
        u8(v ushr 8)
        u8(v)
    }

    fun f32(v: Float) = u32(v.toRawBits())

    fun f64(v: Double) {
        val bits = v.toRawBits()
        u32((bits ushr 32).toInt())
        u32(bits.toInt())
    }

    fun bytes(b: ByteArray) {
        ensure(b.size)
        b.copyInto(buf, size)
        size += b.size
    }

    /** Unsigned LEB128, at most 5 bytes for a non-negative Int. */
    fun varint(v: Int) {
        require(v >= 0) { "varint must be non-negative: $v" }
        var x = v
        while (x >= 0x80) {
            u8((x and 0x7F) or 0x80)
            x = x ushr 7
        }
        u8(x)
    }

    fun toByteArray(): ByteArray = buf.copyOf(size)
}

/** Big-endian reader over `data[from until to]`; throws [MalformedException] past the end or on a bad varint. */
internal class ByteReader(private val data: ByteArray, from: Int = 0, private val to: Int = data.size) {
    private var pos = from

    val remaining: Int get() = to - pos

    fun u8(): Int {
        if (pos >= to) throw MalformedException()
        return data[pos++].toInt() and 0xFF
    }

    fun u16(): Int = (u8() shl 8) or u8()

    fun u32(): Int = (u8() shl 24) or (u8() shl 16) or (u8() shl 8) or u8()

    fun f32(): Float = Float.fromBits(u32())

    fun f64(): Double {
        val hi = u32().toLong()
        val lo = u32().toLong() and 0xFFFFFFFFL
        return Double.fromBits((hi shl 32) or lo)
    }

    fun varint(): Int {
        var result = 0
        var shift = 0
        while (true) {
            val b = u8()
            // The 5th byte may only carry bits 28-30: no continuation and no sign bit.
            if (shift == 28 && b and 0xF8 != 0) throw MalformedException()
            result = result or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
    }
}

internal fun ByteWriter.i64(v: Long) {
    u32((v ushr 32).toInt())
    u32(v.toInt())
}

internal fun ByteReader.i64(): Long = (u32().toLong() shl 32) or (u32().toLong() and 0xFFFFFFFFL)
