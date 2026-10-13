package com.brewingbytes.gemmory.save

/** CRC-32 (IEEE 802.3, the zip/PNG polynomial). Pure Kotlin so every target computes the same value. */
object Crc32 {
    private val TABLE = IntArray(256) { n ->
        var c = n
        repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1 }
        c
    }

    fun of(data: ByteArray, from: Int = 0, to: Int = data.size): Int {
        var c = -1
        for (i in from until to) c = TABLE[(c xor data[i].toInt()) and 0xFF] xor (c ushr 8)
        return c.inv()
    }
}
