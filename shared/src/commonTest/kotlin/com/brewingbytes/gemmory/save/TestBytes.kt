package com.brewingbytes.gemmory.save

internal fun hex(s: String): ByteArray = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

internal fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

/** Re-seals [bytes] with a correct CRC32 trailer, to test what the body checks catch on their own. */
internal fun reseal(bytes: ByteArray): ByteArray {
    val end = bytes.size - 4
    val crc = Crc32.of(bytes, 0, end)
    return bytes.copyOf(end) + byteArrayOf((crc ushr 24).toByte(), (crc ushr 16).toByte(), (crc ushr 8).toByte(), crc.toByte())
}
