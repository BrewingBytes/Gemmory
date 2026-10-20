package com.brewingbytes.gemmory.save

/**
 * When a canvas was last played and first finished (T35, eng D2), for the evening reminder: [lastGemMillis] is the wall
 * time of the last gem saved (null after a Start over, or before T35), [firstFinishedDay] the local epoch day it was
 * first finished (null if never, or finished before T35). A Start over keeps [firstFinishedDay].
 */
data class PlayTimes(val lastGemMillis: Long? = null, val firstFinishedDay: Long? = null)

/**
 * The `play` file, "GMRT" v1, one in each canvas directory (same envelope and CRC trailer as the other canvas files).
 * Body, big-endian: i64 last gem (epoch millis), i64 first finished day (local epoch days); [NONE] for "not known".
 * An older app ignores the file; nothing migrates.
 */
object PlayFileCodec {
    val MAGIC = "GMRT".encodeToByteArray()
    const val VERSION = 1
    private const val NONE = Long.MIN_VALUE

    fun encode(times: PlayTimes): ByteArray {
        val w = ByteWriter(16)
        w.i64(times.lastGemMillis ?: NONE)
        w.i64(times.firstFinishedDay ?: NONE)
        return Envelope.wrap(MAGIC, VERSION, w.toByteArray())
    }

    fun decode(bytes: ByteArray): Decoded<PlayTimes> {
        val opened = when (val o = Envelope.open(bytes, MAGIC)) {
            is Decoded.Refused -> return o
            is Decoded.Ok -> o.value
        }
        if (opened.version > VERSION) return Decoded.Refused(Refusal.NewerVersion(opened.version))
        if (opened.version < VERSION) return Decoded.Refused(Refusal.NoMigration(opened.version))
        if (opened.body.size != 16) return Decoded.Refused(Refusal.Malformed)
        val r = ByteReader(opened.body)
        val lastGem = r.i64()
        val finished = r.i64()
        return Decoded.Ok(PlayTimes(lastGem.takeIf { it != NONE }, finished.takeIf { it != NONE }))
    }
}
