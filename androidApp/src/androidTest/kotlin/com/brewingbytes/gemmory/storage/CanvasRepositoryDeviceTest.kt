package com.brewingbytes.gemmory.storage

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.converter.Lab
import com.brewingbytes.gemmory.converter.PaletteColor
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.save.GridCodec
import com.brewingbytes.gemmory.save.GridFile
import com.brewingbytes.gemmory.telemetry.NonFatalReporter
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * What the JVM tests can't show: the storage layer on ART, the device filesystem and SELinux — atomic renames,
 * directory fsync, hard links being denied to apps, the ENOSPC message format and write cost for a Big canvas.
 */
@RunWith(AndroidJUnit4::class)
class CanvasRepositoryDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.filesDir, "device-test").also { it.deleteRecursively() }
    private val reports = mutableListOf<Throwable>()
    private val repo = CanvasRepository(root, NonFatalReporter { reports += it })
    private val ref = CanvasRef(CanvasKind.PHOTO, "device1")

    // Big-sized: 120×128 = 15,360 cells.
    private val pattern = Pattern(
        width = 120,
        height = 128,
        palette = List(40) { PaletteColor(it + 1, 0xFF000000.toInt() or (it * 0x060504), Lab(it * 2.0, 0.0, 0.0)) },
        cells = IntArray(120 * 128) { it % 40 },
    )
    private val gridBytes = GridCodec.encode(pattern)
    private val grid = (GridCodec.decode(gridBytes) as Decoded.Ok<GridFile>).value

    @After
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun ino(f: File) = Os.stat(f.path).st_ino

    @Test
    fun lastGoodIsTheOldSaveAndSaveIsReplacedByRename() {
        assertEquals(WriteResult.Written, repo.create(ref, gridBytes))
        val dir = repo.dirOf(ref)
        assertEquals(WriteResult.Written, repo.writeSave(ref, grid.binding, intArrayOf(1, 2)))
        val first = File(dir, "save").readBytes()
        val firstIno = ino(File(dir, "save"))
        assertEquals(WriteResult.Written, repo.writeSave(ref, grid.binding, intArrayOf(1, 2, 3)))
        assertContentEquals(first, File(dir, "save.last-good").readBytes())
        assertNotEquals(firstIno, ino(File(dir, "save")), "save should be a new inode (rename path)")
        assertContentEquals(intArrayOf(1, 2, 3), assertIs<LoadResult.Loaded>(repo.load(ref)).log)
        assertTrue(dir.list()!!.none { it.endsWith(".tmp") })
        assertTrue(reports.isEmpty())
    }

    @Test
    fun appsCannotHardLinkTheirOwnFiles() {
        // Why writeSave copies the old save to last-good. If this starts passing on a newer Android, a link would save one write.
        root.mkdirs()
        val a = File(root, "a").also { it.writeBytes(byteArrayOf(1)) }
        val denied = runCatching { Os.link(a.path, File(root, "b").path) }.exceptionOrNull()
        Log.i(TAG, "hard link: ${denied ?: "allowed"}")
        assertIs<ErrnoException>(denied)
    }

    @Test
    fun recoversFromATornSaveOnDevice() {
        repo.create(ref, gridBytes)
        repo.writeSave(ref, grid.binding, intArrayOf(5))
        repo.writeSave(ref, grid.binding, intArrayOf(5, 6))
        val save = File(repo.dirOf(ref), "save")
        save.writeBytes(save.readBytes().copyOf(12))
        assertContentEquals(intArrayOf(5), assertIs<LoadResult.RecoveredFromLastGood>(repo.load(ref)).log)
    }

    @Test
    fun directoryFsyncWorksOnArt() {
        // AtomicFiles.syncDir swallows failures; this shows whether it actually syncs here or silently skips.
        root.mkdirs()
        FileChannel.open(root.toPath(), StandardOpenOption.READ).use { it.force(true) }
    }

    @Test
    fun enospcFromArtIsRecognizedAsDiskFull() {
        val errno = ErrnoException("write", OsConstants.ENOSPC)
        Log.i(TAG, "ErrnoException message: ${errno.message}")
        assertTrue(AtomicFiles.isDiskFull(IOException(errno.message, errno)))
        assertTrue(AtomicFiles.isDiskFull(errno))
        assertFalse(AtomicFiles.isDiskFull(IOException(ErrnoException("write", OsConstants.EIO))))
    }

    @Test
    fun deleteAndSweepOnDevice() {
        repo.create(ref, gridBytes)
        repo.writeSave(ref, grid.binding, intArrayOf(1))
        File(repo.dirOf(ref).parentFile, ".device2.new").mkdirs()
        repo.sweep()
        assertEquals(listOf("device1"), repo.dirOf(ref).parentFile!!.list()!!.toList())
        assertTrue(repo.delete(ref))
        assertFalse(repo.dirOf(ref).exists())
    }

    @Test
    fun aFullBigCanvasSaveIsCheap() {
        repo.create(ref, gridBytes)
        val log = IntArray(pattern.cells.size) { pattern.cells.size - 1 - it }
        repo.writeSave(ref, grid.binding, log)
        val runs = 20
        val start = System.nanoTime()
        repeat(runs) { assertEquals(WriteResult.Written, repo.writeSave(ref, grid.binding, log)) }
        val avgMs = (System.nanoTime() - start) / runs / 1e6
        val bytes = File(repo.dirOf(ref), "save").length()
        Log.i(TAG, "full Big save: $bytes bytes, avg %.2f ms per write (decode old + copy to last-good + write + fsync + rename)".format(avgMs))
        assertTrue(avgMs < 100, "avg write $avgMs ms")
        assertContentEquals(log, assertIs<LoadResult.Loaded>(repo.load(ref)).log)
    }

    private companion object {
        const val TAG = "GemmoryDeviceTest"
    }
}
