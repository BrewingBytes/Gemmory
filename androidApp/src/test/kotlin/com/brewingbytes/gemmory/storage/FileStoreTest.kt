package com.brewingbytes.gemmory.storage

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The shared [FileStore] seam (eng D8, OV-4; T64) on Android: [AndroidFileStore] over a folder, with [AtomicFiles]'
 * crash safety. The same paths and bytes as `java.io.File` gave before, so no save moves.
 */
class FileStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun store(writer: SyncedWriter = SyncedWriter.Default) = AndroidFileStore(tmp.root, writer)

    @Test
    fun writesWhereFileWroteBeforeByteForByte() {
        val s = store()
        assertTrue(s.mkdirs("canvases/bundled/wave"))
        s.writeAtomic("canvases/bundled/wave/progress", byteArrayOf(1, 2, 3))
        assertContentEquals(byteArrayOf(1, 2, 3), File(tmp.root, "canvases/bundled/wave/progress").readBytes())
        assertContentEquals(byteArrayOf(1, 2, 3), s.read("canvases/bundled/wave/progress"))
        assertTrue(s.exists("canvases/bundled/wave/progress"))
        assertEquals(listOf("progress"), s.list("canvases/bundled/wave"))
    }

    @Test
    fun aMissingFileReadsAsNullAndAMissingFolderListsEmpty() {
        assertNull(store().read("ledger/daily"))
        assertEquals(emptyList(), store().list("canvases/photo"))
        assertFalse(store().exists("ledger"))
    }

    @Test
    fun aFullDiskKeepsTheOldFileLeavesNoTempAndSaysSo() {
        store().apply { mkdirs("ledger") }.writeAtomic("ledger/daily", byteArrayOf(1))
        val full = store { _, _ -> throw IOException("write failed: ENOSPC (No space left on device)") }
        val e = assertFailsWith<FileStoreException> { full.writeAtomic("ledger/daily", byteArrayOf(2)) }
        assertTrue(e.diskFull)
        assertContentEquals(byteArrayOf(1), store().read("ledger/daily"))
        assertEquals(listOf("daily"), store().list("ledger"))
    }

    @Test
    fun anotherWriteFailureIsNotADiskFull() {
        store().mkdirs("ledger")
        val broken = store { _, _ -> throw IOException("EIO") }
        assertFalse(assertFailsWith<FileStoreException> { broken.writeAtomic("ledger/ads", byteArrayOf(1)) }.diskFull)
        assertFalse(store().exists("ledger/ads"))
    }

    @Test
    fun aStagedFolderMovesInWholeAndNeverOverAnother() {
        val s = store()
        s.mkdirs("canvases/photo/.staging-1")
        s.writeAtomic("canvases/photo/.staging-1/grid", byteArrayOf(7))
        s.moveDir("canvases/photo/.staging-1", "canvases/photo/abc")
        assertContentEquals(byteArrayOf(7), s.read("canvases/photo/abc/grid"))
        assertFalse(s.exists("canvases/photo/.staging-1"))
        s.mkdirs("canvases/photo/.staging-2")
        assertFailsWith<FileStoreException> { s.moveDir("canvases/photo/.staging-2", "canvases/photo/abc") }
        assertContentEquals(byteArrayOf(7), s.read("canvases/photo/abc/grid"))
    }

    @Test
    fun copiesDeletesAndDeletesFolders() {
        val s = store()
        s.mkdirs("remote/grids")
        s.writeAtomic("remote/grids/wave.gmrg", byteArrayOf(4, 5))
        s.mkdirs("canvases/remote/wave")
        s.copy("remote/grids/wave.gmrg", "canvases/remote/wave/grid")
        assertContentEquals(byteArrayOf(4, 5), s.read("canvases/remote/wave/grid"))
        assertTrue(s.delete("remote/grids/wave.gmrg"))
        assertFalse(s.delete("remote/grids/wave.gmrg"))
        assertTrue(s.deleteRecursive("canvases/remote"))
        assertFalse(s.exists("canvases/remote/wave/grid"))
        assertTrue(s.deleteRecursive("canvases/remote"), "nothing left is still a success")
    }

    @Test
    fun pathsStayInsideTheStore() {
        for (bad in listOf("", "/etc/passwd", "../outside", "canvases/../../outside")) {
            assertFailsWith<IllegalArgumentException>(bad) { store().read(bad) }
        }
    }

    /** The never-backed-up folders are real store paths (iOS excludes them by these names). */
    @Test
    fun theNeverBackedUpFoldersAreStorePaths() {
        val folders = StorageLayout.neverBackedUp.filterIsInstance<StorageLayout.Kept.Folder>().map { it.path }
        assertEquals(listOf(StorageLayout.PHOTO_CANVASES, StorageLayout.LEDGER, StorageLayout.REMOTE), folders)
        folders.forEach { assertTrue(store().mkdirs(it), it) }
    }
}
