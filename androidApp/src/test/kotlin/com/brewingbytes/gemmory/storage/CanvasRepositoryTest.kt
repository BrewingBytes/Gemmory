package com.brewingbytes.gemmory.storage

import com.brewingbytes.gemmory.converter.Lab
import com.brewingbytes.gemmory.converter.PaletteColor
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.save.CanvasRecord
import com.brewingbytes.gemmory.save.Crc32
import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.save.GridBinding
import com.brewingbytes.gemmory.save.GridCodec
import com.brewingbytes.gemmory.save.PlayTimes
import com.brewingbytes.gemmory.save.Refusal
import com.brewingbytes.gemmory.save.SaveCodec
import com.brewingbytes.gemmory.save.ViewState
import com.brewingbytes.gemmory.save.ViewStateCodec
import com.brewingbytes.gemmory.telemetry.NonFatalReporter
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CanvasRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val reports = mutableListOf<Throwable>()
    private val reporter = NonFatalReporter { reports += it }
    private val ref = CanvasRef(CanvasKind.PHOTO, "c1")

    private val pattern = Pattern(
        width = 4,
        height = 3,
        palette = listOf(
            PaletteColor(1, 0xFF102030.toInt(), Lab(12.0, 1.0, -9.0)),
            PaletteColor(2, 0xFFE0D0C0.toInt(), Lab(85.0, 1.5, 5.0)),
        ),
        cells = IntArray(12) { it % 2 },
    )
    private val gridBytes = GridCodec.encode(pattern)
    private val binding = (GridCodec.decode(gridBytes) as Decoded.Ok).value.binding

    private fun repo(writer: SyncedWriter = SyncedWriter.Default) =
        CanvasRepository(tmp.root, reporter, SaveCodec(), AtomicFiles(writer))

    /** A repository with [ref] already created. */
    private val started by lazy { repo().also { assertEquals(WriteResult.Written, it.create(ref, gridBytes)) } }

    private fun file(name: String) = File(started.dirOf(ref), name)

    @Test
    fun photoAndBundledCanvasesLiveInTheirBackupDirs() {
        val r = repo()
        assertEquals(File(tmp.root, "canvases/photo/c1"), r.dirOf(ref))
        assertEquals(File(tmp.root, "canvases/bundled/c1"), r.dirOf(CanvasRef(CanvasKind.BUNDLED, "c1")))
    }

    @Test
    fun rejectsIdsThatAreNotAPlainDirName() {
        for (bad in listOf("", "../x", "a/b", ".hidden", "A1", "x".repeat(65))) {
            assertFailsWith<IllegalArgumentException>(bad) { CanvasRef(CanvasKind.PHOTO, bad) }
        }
    }

    @Test
    fun aFreshCanvasLoadsWithAnEmptyLog() {
        val loaded = assertIs<LoadResult.Loaded>(started.load(ref))
        assertEquals(pattern, loaded.grid.pattern)
        assertEquals(0, loaded.log.size)
        assertEquals(listOf("c1"), started.list(CanvasKind.PHOTO))
        assertTrue(reports.isEmpty())
    }

    @Test
    fun createRefusesBadGridsAndExistingCanvases() {
        assertFailsWith<IllegalArgumentException> { repo().create(ref, byteArrayOf(1, 2, 3)) }
        started
        assertFailsWith<IllegalArgumentException> { started.create(ref, gridBytes) }
    }

    @Test
    fun writesAndLoadsTheLogInOrder() {
        assertEquals(WriteResult.Written, started.writeSave(ref, binding, intArrayOf(3, 1, 7)))
        assertContentEquals(intArrayOf(3, 1, 7), assertIs<LoadResult.Loaded>(started.load(ref)).log)
        assertFalse(file("save.tmp").exists())
    }

    @Test
    fun aCorruptSaveRecoversFromTheLastGoodOne() {
        started.writeSave(ref, binding, intArrayOf(3))
        started.writeSave(ref, binding, intArrayOf(3, 1))
        file("save").writeBytes(file("save").readBytes().copyOf(9))
        val recovered = assertIs<LoadResult.RecoveredFromLastGood>(started.load(ref))
        assertContentEquals(intArrayOf(3), recovered.log)
        assertEquals(LoadProblem.Refused(CanvasFile.SAVE, Refusal.TooShort), recovered.problem)
        assertEquals(1, reports.size)
    }

    /** R1 (eng D10): a `play` file beside the save (T35) changes nothing about last-good recovery. */
    @Test
    fun lastGoodRecoveryIsUnchangedWithAPlayFile() {
        started.writeSave(ref, binding, intArrayOf(3))
        started.writePlay(ref, PlayTimes(lastGemMillis = 1_000, firstFinishedDay = null))
        started.writeSave(ref, binding, intArrayOf(3, 1))
        started.writePlay(ref, PlayTimes(lastGemMillis = 2_000, firstFinishedDay = null))
        file("save").writeBytes(file("save").readBytes().copyOf(9))
        val recovered = assertIs<LoadResult.RecoveredFromLastGood>(started.load(ref))
        assertContentEquals(intArrayOf(3), recovered.log)
        assertEquals(PlayTimes(2_000, null), started.readPlay(ref))
    }

    @Test
    fun aMissingOrRefusedPlayFileReadsAsUnknown() {
        assertNull(started.readPlay(ref))
        started.writePlay(ref, PlayTimes(5, 6))
        file("play").writeBytes(file("play").readBytes().copyOf(10))
        assertNull(started.readPlay(ref))
        assertEquals(WriteResult.WriteFailed(diskFull = false), repo().writePlay(CanvasRef(CanvasKind.PHOTO, "gone"), PlayTimes()))
    }

    @Test
    fun aMissingSaveRecoversFromTheLastGoodOne() {
        started.writeSave(ref, binding, intArrayOf(3))
        started.writeSave(ref, binding, intArrayOf(3, 1))
        file("save").delete()
        val recovered = assertIs<LoadResult.RecoveredFromLastGood>(started.load(ref))
        assertContentEquals(intArrayOf(3), recovered.log)
    }

    @Test
    fun aRefusedSaveIsNeverPromotedToLastGood() {
        started.writeSave(ref, binding, intArrayOf(3))
        started.writeSave(ref, binding, intArrayOf(3, 1))
        val bad = file("save").readBytes().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        file("save").writeBytes(bad)
        // The player keeps going from the recovered log; the next write must not turn the bad file into last-good.
        assertEquals(WriteResult.Written, started.writeSave(ref, binding, intArrayOf(3, 5)))
        file("save").delete()
        assertContentEquals(intArrayOf(3), assertIs<LoadResult.RecoveredFromLastGood>(started.load(ref)).log)
    }

    @Test
    fun aCorruptSaveWithNoLastGoodIsDamaged() {
        started.writeSave(ref, binding, intArrayOf(3))
        file("save").writeBytes(file("save").readBytes().also { it[8] = 0x55 })
        val damaged = assertIs<LoadResult.Damaged>(started.load(ref))
        assertEquals(LoadProblem.Refused(CanvasFile.SAVE, Refusal.BadCrc), damaged.problem)
        assertEquals(1, reports.size)
        assertTrue(file("save").exists(), "a refused file is kept")
    }

    @Test
    fun aGridMismatchWithNoLastGoodIsDamaged() {
        val otherGrid = GridBinding(crc32 = binding.crc32 xor 1, cellCount = binding.cellCount)
        file("save").writeBytes(SaveCodec().encode(otherGrid, intArrayOf(2)))
        val damaged = assertIs<LoadResult.Damaged>(started.load(ref))
        assertEquals(
            LoadProblem.Refused(CanvasFile.SAVE, Refusal.GridMismatch(binding.crc32, otherGrid.crc32)),
            damaged.problem,
        )
    }

    @Test
    fun duplicateAndOutOfRangeLogsWithNoLastGoodAreDamaged() {
        file("save").writeBytes(rawSave(binding.crc32, intArrayOf(2, 5, 2)))
        assertEquals(
            LoadProblem.Refused(CanvasFile.SAVE, Refusal.DuplicateIndex(2)),
            assertIs<LoadResult.Damaged>(started.load(ref)).problem,
        )
        file("save").writeBytes(rawSave(binding.crc32, intArrayOf(2, 12)))
        assertEquals(
            LoadProblem.Refused(CanvasFile.SAVE, Refusal.IndexOutOfRange(12)),
            assertIs<LoadResult.Damaged>(started.load(ref)).problem,
        )
    }

    @Test
    fun refusedLogsAndGridMismatchesRecoverFromTheLastGoodOne() {
        val cases = mapOf(
            "dup" to (rawSave(binding.crc32, intArrayOf(3, 1, 3)) to Refusal.DuplicateIndex(3)),
            "range" to (rawSave(binding.crc32, intArrayOf(3, 12)) to Refusal.IndexOutOfRange(12)),
            "grid" to (rawSave(binding.crc32 xor 1, intArrayOf(3)) to Refusal.GridMismatch(binding.crc32, binding.crc32 xor 1)),
        )
        val r = repo()
        for ((id, case) in cases) {
            val (bytes, refusal) = case
            val canvas = CanvasRef(CanvasKind.PHOTO, id)
            r.create(canvas, gridBytes)
            r.writeSave(canvas, binding, intArrayOf(3))
            r.writeSave(canvas, binding, intArrayOf(3, 1))
            File(r.dirOf(canvas), "save").writeBytes(bytes)
            val recovered = assertIs<LoadResult.RecoveredFromLastGood>(r.load(canvas), id)
            assertContentEquals(intArrayOf(3), recovered.log, id)
            assertEquals(LoadProblem.Refused(CanvasFile.SAVE, refusal), recovered.problem, id)
        }
        assertEquals(cases.size, reports.size, "one non-fatal per recovery")
    }

    @Test
    fun aNewerSaveIsDamagedAndNeverOverwritten() {
        started.writeSave(ref, binding, intArrayOf(3))
        started.writeSave(ref, binding, intArrayOf(3, 1))
        val newer = SaveCodec().encode(binding, intArrayOf(3, 1)).also { it[5] = 2 }.let(::reseal)
        file("save").writeBytes(newer)
        val damaged = assertIs<LoadResult.Damaged>(started.load(ref))
        assertEquals(LoadProblem.Refused(CanvasFile.SAVE, Refusal.NewerVersion(2)), damaged.problem)
        assertEquals(WriteResult.WriteFailed(diskFull = false), started.writeSave(ref, binding, intArrayOf(4)))
        assertContentEquals(newer, file("save").readBytes())
    }

    @Test
    fun aCorruptGridIsDamaged() {
        file("grid").writeBytes(gridBytes.copyOf(gridBytes.size - 2))
        assertEquals(
            LoadProblem.Refused(CanvasFile.GRID, Refusal.BadCrc),
            assertIs<LoadResult.Damaged>(started.load(ref)).problem,
        )
    }

    @Test
    fun aMissingCanvasIsDamaged() {
        assertEquals(LoadProblem.Missing(CanvasFile.DIR), assertIs<LoadResult.Damaged>(repo().load(ref)).problem)
    }

    @Test
    fun aFullDiskIsReportedToTheCanvasAndKeepsTheOldSave() {
        started.writeSave(ref, binding, intArrayOf(3))
        val full = repo { _, _ -> throw IOException("write failed: ENOSPC (No space left on device)") }
        assertEquals(WriteResult.WriteFailed(diskFull = true), full.writeSave(ref, binding, intArrayOf(3, 1)))
        assertContentEquals(intArrayOf(3), assertIs<LoadResult.Loaded>(started.load(ref)).log)
        assertFalse(file("save.tmp").exists())
        assertTrue(reports.isEmpty(), "a full disk is the player's to fix, not a non-fatal")
    }

    @Test
    fun otherWriteErrorsAreReportedAsNonFatals() {
        started
        val broken = repo { _, _ -> throw IOException("EIO") }
        assertEquals(WriteResult.WriteFailed(diskFull = false), broken.writeSave(ref, binding, intArrayOf(3)))
        assertEquals(1, reports.size)
    }

    @Test
    fun anInvalidLogIsNeverWritten() {
        assertEquals(WriteResult.WriteFailed(diskFull = false), started.writeSave(ref, binding, intArrayOf(1, 1)))
        assertFalse(file("save").exists())
        assertEquals(1, reports.size)
    }

    @Test
    fun viewStateRoundTripsAndFallsBackToDefaults() {
        assertNull(started.readView(ref, paletteSize = 2))
        val view = ViewState(zoom = 24f, panX = 10f, panY = -4f, slot = 1)
        assertEquals(WriteResult.Written, started.writeView(ref, view))
        assertEquals(view, started.readView(ref, paletteSize = 2))
        assertNull(started.readView(ref, paletteSize = 1), "slot out of range")
        file("view").writeBytes(ViewStateCodec.encode(view).copyOf(5))
        assertNull(started.readView(ref, paletteSize = 2), "corrupt")
    }

    @Test
    fun deleteRemovesEverythingAndAPendingWriteDoesNotResurrectIt() {
        started.writeSave(ref, binding, intArrayOf(3))
        started.writeSave(ref, binding, intArrayOf(3, 1))
        started.writeView(ref, ViewState(24f, 0f, 0f, 0))
        val dir = started.dirOf(ref)
        assertTrue(started.delete(ref))
        assertFalse(dir.exists())
        assertTrue(dir.parentFile!!.listFiles()!!.isEmpty())
        assertEquals(WriteResult.WriteFailed(diskFull = false), started.writeSave(ref, binding, intArrayOf(3)))
        assertEquals(WriteResult.WriteFailed(diskFull = false), started.writeView(ref, ViewState(24f, 0f, 0f, 0)))
        assertFalse(dir.exists())
        assertTrue(started.list(CanvasKind.PHOTO).isEmpty())
    }

    @Test
    fun createWritesTheRecordAndSourcePhotoInTheExcludedPhotoDir() {
        val r = repo()
        val record = CanvasRecord("My photo · 13 Oct", createdAtMillis = 1_000, playMillis = 0)
        assertEquals(WriteResult.Written, r.create(ref, gridBytes, record, source = byteArrayOf(1, 2, 3)))
        assertEquals(record, r.readRecord(ref))
        assertContentEquals(byteArrayOf(1, 2, 3), r.sourceFile(ref).readBytes())
        assertTrue(r.sourceFile(ref).path.startsWith(File(tmp.root, StorageLayout.PHOTO_CANVASES).path))
        assertFailsWith<IllegalArgumentException> {
            r.create(CanvasRef(CanvasKind.BUNDLED, "b1"), gridBytes, source = byteArrayOf(1))
        }
    }

    @Test
    fun renameRewritesTheRecordAndAMissingOrCorruptRecordReadsAsNull() {
        assertNull(started.readRecord(ref), "created without a record")
        assertEquals(WriteResult.Written, started.writeRecord(ref, CanvasRecord("Beach", 5, 60_000)))
        assertEquals(CanvasRecord("Beach", 5, 60_000), started.readRecord(ref))
        file("record").writeBytes(byteArrayOf(1, 2, 3))
        assertNull(started.readRecord(ref))
    }

    @Test
    fun clearProgressRemovesProgressViewAndThumbAndResetsPlayTime() {
        started.writeRecord(ref, CanvasRecord("Beach", 5, 60_000))
        started.writeSave(ref, binding, intArrayOf(3))
        started.writeSave(ref, binding, intArrayOf(3, 1))
        started.writeView(ref, ViewState(24f, 0f, 0f, 1))
        started.writeThumb(ref, byteArrayOf(9))
        assertTrue(started.clearProgress(ref))
        for (name in listOf("save", "save.last-good", "view", "thumb.png")) assertFalse(file(name).exists(), name)
        assertContentEquals(IntArray(0), assertIs<LoadResult.Loaded>(started.load(ref)).log)
        assertNull(started.readView(ref, paletteSize = 2))
        assertEquals(CanvasRecord("Beach", 5, 0), started.readRecord(ref), "title kept, play time reset")
        assertTrue(file("grid").exists())
    }

    /** R1 (eng D10, intended change): Start over clears the last gem (T35) and keeps the first finished day. */
    @Test
    fun clearProgressResetsTheLastGemAndKeepsTheFirstFinishedDay() {
        started.writeSave(ref, binding, IntArray(12) { it })
        started.writePlay(ref, PlayTimes(lastGemMillis = 9_000, firstFinishedDay = 20_380))
        assertTrue(started.clearProgress(ref))
        assertEquals(PlayTimes(lastGemMillis = null, firstFinishedDay = 20_380), started.readPlay(ref))
    }

    @Test
    fun deleteAlsoRemovesTheRecordAndSourcePhoto() {
        val r = repo()
        r.create(ref, gridBytes, CanvasRecord("x", 1, 0), byteArrayOf(1))
        r.writeSave(ref, binding, intArrayOf(3))
        r.writeView(ref, ViewState(24f, 0f, 0f, 0))
        r.writePlay(ref, PlayTimes(1, 2))
        assertTrue(r.delete(ref))
        assertFalse(r.dirOf(ref).exists())
        assertNull(r.readRecord(ref))
        assertNull(r.readPlay(ref))
        assertFalse(r.sourceFile(ref).exists())
    }

    @Test
    fun sweepClearsLeftoversOfInterruptedOperations() {
        val parent = started.dirOf(ref).parentFile!!
        File(parent, ".c2.new").mkdirs()
        File(parent, ".c3.deleted").mkdirs()
        file("save.tmp").writeBytes(byteArrayOf(1))
        assertEquals(listOf("c1"), started.list(CanvasKind.PHOTO))
        started.sweep()
        assertEquals(listOf("c1"), parent.list()!!.toList())
        assertFalse(file("save.tmp").exists())
        assertTrue(file("grid").exists())
    }

    @Test
    fun anUnreadableSaveIsReplacedNotAPermanentWriteFailure() {
        started.writeSave(ref, binding, intArrayOf(3))
        started.writeSave(ref, binding, intArrayOf(3, 1))
        val save = file("save")
        assertTrue(save.setReadable(false), "can't make the file unreadable on this host")
        try {
            assertIs<LoadResult.RecoveredFromLastGood>(started.load(ref))
            assertEquals(WriteResult.Written, started.writeSave(ref, binding, intArrayOf(3, 7)))
            assertEquals(1, reports.size, "only the recovery is reported, not a write failure")
        } finally {
            save.setReadable(true)
        }
        assertContentEquals(intArrayOf(3, 7), assertIs<LoadResult.Loaded>(started.load(ref)).log)
        // The unreadable file was never promoted: last-good still holds the older progress.
        file("save").delete()
        assertContentEquals(intArrayOf(3), assertIs<LoadResult.RecoveredFromLastGood>(started.load(ref)).log)
    }

    /** A v1 save with a valid CRC that SaveCodec.encode would refuse to write (it validates the log). */
    private fun rawSave(gridCrc: Int, indices: IntArray): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write("GMRY".toByteArray())
        out.write(byteArrayOf(0, 1))
        out.write(byteArrayOf((gridCrc ushr 24).toByte(), (gridCrc ushr 16).toByte(), (gridCrc ushr 8).toByte(), gridCrc.toByte()))
        // Every value here is < 128, so each varint is one byte.
        out.write(indices.size)
        indices.forEach { out.write(it) }
        out.write(ByteArray(4))
        return reseal(out.toByteArray())
    }

    private fun reseal(bytes: ByteArray): ByteArray {
        val end = bytes.size - 4
        val crc = Crc32.of(bytes, 0, end)
        return bytes.copyOf(end) + byteArrayOf((crc ushr 24).toByte(), (crc ushr 16).toByte(), (crc ushr 8).toByte(), crc.toByte())
    }
}
