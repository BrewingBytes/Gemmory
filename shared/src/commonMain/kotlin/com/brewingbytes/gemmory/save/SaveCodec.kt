package com.brewingbytes.gemmory.save

/**
 * One step of the save migration chain: turns a version-N body into a version-(N+1) body, or returns null when
 * the old body can't be migrated (the load is then refused as [Refusal.Malformed]).
 */
fun interface SaveMigration {
    fun migrate(body: ByteArray, grid: GridBinding): ByteArray?
}

/** Migration steps keyed by the version they upgrade from. They run in order up to [SaveCodec.VERSION]. */
class MigrationRegistry(private val steps: Map<Int, SaveMigration>) {
    fun stepFrom(version: Int): SaveMigration? = steps[version]

    companion object {
        /** v1 is the first shipped format, so production has nothing to migrate yet. */
        val NONE = MigrationRegistry(emptyMap())
    }
}

/**
 * Canvas progress file "GMRY" (eng E2, D3, D4, D11). The only thing persisted is the ordered log of placed cell
 * indices: a wrong-color tap is rejected with no state change, so each cell appears at most once and the in-memory
 * placed set is rebuilt from the log on load.
 *
 * v1 body, big-endian: u32 grid CRC32, varint count, then `count` varint cell indices in placement order.
 */
class SaveCodec(private val migrations: MigrationRegistry = MigrationRegistry.NONE) {
    /** Encodes [log]. Throws if the log is one a load would refuse, so a bad file is never written. */
    fun encode(grid: GridBinding, log: IntArray): ByteArray {
        validate(log, grid.cellCount)?.let { throw IllegalArgumentException("invalid placement log: $it") }
        val w = ByteWriter(8 + log.size * 2)
        w.u32(grid.crc32)
        w.varint(log.size)
        for (index in log) w.varint(index)
        return Envelope.wrap(MAGIC, VERSION, w.toByteArray())
    }

    /** Decodes a save made on [grid]. Refuses newer versions, bad CRCs, grid mismatches and bad logs. */
    fun decode(bytes: ByteArray, grid: GridBinding): Decoded<IntArray> {
        val opened = when (val o = Envelope.open(bytes, MAGIC)) {
            is Decoded.Refused -> return o
            is Decoded.Ok -> o.value
        }
        if (opened.version > VERSION) return Decoded.Refused(Refusal.NewerVersion(opened.version))
        var version = opened.version
        var body = opened.body
        while (version < VERSION) {
            val step = migrations.stepFrom(version) ?: return Decoded.Refused(Refusal.NoMigration(version))
            body = step.migrate(body, grid) ?: return Decoded.Refused(Refusal.Malformed)
            version++
        }
        return try {
            readV1(ByteReader(body), grid)
        } catch (_: MalformedException) {
            Decoded.Refused(Refusal.Malformed)
        }
    }

    private fun readV1(r: ByteReader, grid: GridBinding): Decoded<IntArray> {
        val gridCrc = r.u32()
        if (gridCrc != grid.crc32) return Decoded.Refused(Refusal.GridMismatch(grid.crc32, gridCrc))
        val count = r.varint()
        // Each index takes at least one byte; checked before allocating so a corrupt count can't ask for gigabytes.
        if (count > grid.cellCount || count > r.remaining) return Decoded.Refused(Refusal.Malformed)
        val log = IntArray(count) { r.varint() }
        if (r.remaining != 0) return Decoded.Refused(Refusal.Malformed)
        validate(log, grid.cellCount)?.let { return Decoded.Refused(it) }
        return Decoded.Ok(log)
    }

    private fun validate(log: IntArray, cellCount: Int): Refusal? {
        val placed = BooleanArray(cellCount)
        for (index in log) {
            if (index !in 0 until cellCount) return Refusal.IndexOutOfRange(index)
            if (placed[index]) return Refusal.DuplicateIndex(index)
            placed[index] = true
        }
        return null
    }

    companion object {
        val MAGIC = "GMRY".encodeToByteArray()
        const val VERSION = 1
    }
}
