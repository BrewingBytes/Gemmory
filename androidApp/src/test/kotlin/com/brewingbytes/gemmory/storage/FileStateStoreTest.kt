package com.brewingbytes.gemmory.storage

import com.brewingbytes.gemmory.policy.LedgerCodec
import com.brewingbytes.gemmory.policy.LedgerState
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileStateStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val reports = mutableListOf<Throwable>()

    /** The policy file at [file] under the temp folder, through the shared [FileStore] seam (T64) over [AtomicFiles]. */
    private fun store(file: File, writer: SyncedWriter = SyncedWriter.Default) =
        FileStateStore(AndroidFileStore(tmp.root, writer), file.relativeTo(tmp.root).path, LedgerCodec::encode, LedgerCodec::decode, { reports += it })

    @Test
    fun roundTripsAndCreatesTheLedgerDir() {
        val file = File(tmp.root, StorageLayout.LEDGER_DAILY)
        val state = LedgerState(day = 20_740, freeUsed = true)
        store(file).save(state)
        assertEquals(state, store(file).load())
        assertTrue(reports.isEmpty())
    }

    @Test
    fun aMissingFileLoadsAsNothingQuietly() {
        assertNull(store(File(tmp.root, "ledger/daily")).load())
        assertTrue(reports.isEmpty())
    }

    @Test
    fun aDamagedFileStartsFreshAndIsReported() {
        val file = File(tmp.root, "ledger/daily").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)) }
        assertNull(store(file).load())
        assertEquals(1, reports.size)
    }

    @Test
    fun aFullDiskIsReportedAsSuchAndKeepsTheOldFile() {
        val file = File(tmp.root, "ledger/daily")
        store(file).save(LedgerState(day = 1))
        store(file, writer = { _, _ -> throw IOException("write failed: ENOSPC (No space left on device)") }).save(LedgerState(day = 2))
        assertTrue((reports.single() as FileStoreException).diskFull)
        assertTrue(File(tmp.root, "ledger").list()!!.none { it.endsWith(AtomicFiles.TEMP_SUFFIX) }, "the temp file is cleaned up")
    }

    @Test
    fun aFailedWriteIsReportedAndKeepsTheOldFile() {
        val file = File(tmp.root, "ledger/daily")
        store(file).save(LedgerState(day = 1))
        store(file, writer = { _, _ -> throw IOException("write failed: ENOSPC (No space left on device)") }).save(LedgerState(day = 2))
        assertEquals(LedgerState(day = 1), store(file).load())
        assertEquals(1, reports.size)
    }
}
