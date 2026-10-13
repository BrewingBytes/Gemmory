package com.brewingbytes.gemmory.save

/** Why a GMRY save, grid or view file was refused. The file on disk is never touched because of a refusal. */
sealed interface Refusal {
    /** Shorter than magic + version + CRC trailer. */
    data object TooShort : Refusal

    data object BadMagic : Refusal

    /** The CRC32 trailer doesn't match: a torn, truncated or bit-flipped file. */
    data object BadCrc : Refusal

    /** Written by a newer app. Refused so a downgrade never overwrites progress it can't read. */
    data class NewerVersion(val version: Int) : Refusal

    /** An older version with no registered migration step. */
    data class NoMigration(val fromVersion: Int) : Refusal

    /** The CRC matched but the body doesn't parse, has trailing bytes or holds impossible values. */
    data object Malformed : Refusal

    /** The save was made against a different grid than the one on disk (eng D11). */
    data class GridMismatch(val expectedCrc32: Int, val actualCrc32: Int) : Refusal

    data class DuplicateIndex(val index: Int) : Refusal

    data class IndexOutOfRange(val index: Int) : Refusal
}

sealed interface Decoded<out T> {
    data class Ok<T>(val value: T) : Decoded<T>

    data class Refused(val reason: Refusal) : Decoded<Nothing>
}

/**
 * The frame shared by every GMRY-family file, frozen across versions so any version can be identified:
 * 4-byte ASCII magic, u16 version (big-endian), body, u32 CRC32 of everything before the trailer.
 */
internal object Envelope {
    const val HEADER_SIZE = 6
    const val TRAILER_SIZE = 4

    fun wrap(magic: ByteArray, version: Int, body: ByteArray): ByteArray {
        val w = ByteWriter(HEADER_SIZE + body.size + TRAILER_SIZE)
        w.bytes(magic)
        w.u16(version)
        w.bytes(body)
        val unsealed = w.toByteArray()
        w.u32(Crc32.of(unsealed))
        return w.toByteArray()
    }

    class Opened(val version: Int, val body: ByteArray, val crc32: Int)

    /** Checks length, magic and CRC (in that order). The version is left to the codec. */
    fun open(bytes: ByteArray, magic: ByteArray): Decoded<Opened> {
        if (bytes.size < HEADER_SIZE + TRAILER_SIZE) return Decoded.Refused(Refusal.TooShort)
        for (i in magic.indices) if (bytes[i] != magic[i]) return Decoded.Refused(Refusal.BadMagic)
        val end = bytes.size - TRAILER_SIZE
        val stored = ByteReader(bytes, end).u32()
        if (Crc32.of(bytes, 0, end) != stored) return Decoded.Refused(Refusal.BadCrc)
        val version = ByteReader(bytes, magic.size).u16()
        return Decoded.Ok(Opened(version, bytes.copyOfRange(HEADER_SIZE, end), stored))
    }
}
