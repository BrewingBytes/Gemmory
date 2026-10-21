package com.brewingbytes.gemmory.save

/**
 * What a canvas is, apart from its grid and progress: the title shown on the tile, in the top bar and on the
 * completion meta line ("My photo · 13 Oct" for a new photo canvas, design D26; Rename edits it), when it was
 * created, and how long it has been played (the completion meta line). Bundled pictures take their title from the
 * catalog, so [title] is empty for them. [mystery]: a remote mystery picture (T43), kept with the canvas so its title and
 * art stay hidden until it's finished even after the catalog withdraws it.
 */
data class CanvasRecord(val title: String, val createdAtMillis: Long, val playMillis: Long, val mystery: Boolean = false) {
    init {
        require(title.length <= CanvasRecordCodec.MAX_TITLE_CHARS) { "title too long: ${title.length}" }
        require(playMillis >= 0) { "playMillis must be >= 0, was $playMillis" }
    }
}

/**
 * The `record` file, "GMRC" (same envelope and CRC trailer as the save and grid files). Body, big-endian:
 * u16 title byte length, the title in UTF-8, i64 created-at (epoch millis), i64 play time (millis); v2 (T43) adds a
 * u8 flags byte (bit 0: mystery). Only a mystery is written as v2, so every other record stays v1, byte for byte.
 */
object CanvasRecordCodec {
    val MAGIC = "GMRC".encodeToByteArray()
    const val VERSION = 2
    private const val V1 = 1
    private const val MYSTERY = 1
    const val MAX_TITLE_CHARS = 80

    fun encode(record: CanvasRecord): ByteArray {
        val title = record.title.encodeToByteArray()
        val w = ByteWriter(2 + title.size + 17)
        w.u16(title.size)
        w.bytes(title)
        w.i64(record.createdAtMillis)
        w.i64(record.playMillis)
        if (!record.mystery) return Envelope.wrap(MAGIC, V1, w.toByteArray())
        w.u8(MYSTERY)
        return Envelope.wrap(MAGIC, VERSION, w.toByteArray())
    }

    fun decode(bytes: ByteArray): Decoded<CanvasRecord> {
        val opened = when (val o = Envelope.open(bytes, MAGIC)) {
            is Decoded.Refused -> return o
            is Decoded.Ok -> o.value
        }
        if (opened.version > VERSION) return Decoded.Refused(Refusal.NewerVersion(opened.version))
        if (opened.version < V1) return Decoded.Refused(Refusal.NoMigration(opened.version))
        val record = try {
            val r = ByteReader(opened.body)
            val length = r.u16()
            if (length > r.remaining) throw MalformedException()
            val title = ByteArray(length) { r.u8().toByte() }.decodeToString(throwOnInvalidSequence = true)
            val created = r.i64()
            val played = r.i64()
            val flags = if (opened.version >= 2) r.u8() else 0
            if (r.remaining != 0 || title.length > MAX_TITLE_CHARS || played < 0 || flags and MYSTERY.inv() != 0) throw MalformedException()
            CanvasRecord(title, created, played, mystery = flags and MYSTERY != 0)
        } catch (_: MalformedException) {
            null
        } catch (_: CharacterCodingException) {
            null
        } ?: return Decoded.Refused(Refusal.Malformed)
        return Decoded.Ok(record)
    }
}
