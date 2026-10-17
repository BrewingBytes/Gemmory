package com.brewingbytes.gemmory.remote

import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.policy.DayClock
import com.brewingbytes.gemmory.storage.AtomicFiles
import com.brewingbytes.gemmory.storage.StorageLayout
import com.brewingbytes.gemmory.storage.SyncedWriter
import com.brewingbytes.gemmory.telemetry.NonFatalReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The remote-picture fetcher (#43) against a fake HTTP layer, on the real filesystem with a fixed clock. */
class RemotePicturesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val catalog = TestCatalog()
    private val source = FakeSource(catalog)
    private val reports = java.util.Collections.synchronizedList(mutableListOf<String>())
    private val reporter = NonFatalReporter { reports += it.message.orEmpty() }
    private var now = 1_792_000_000_000L
    private var diskFull = false
    private val writer = SyncedWriter { file, bytes ->
        if (diskFull) throw IOException("write failed: ENOSPC (No space left on device)")
        SyncedWriter.Default.write(file, bytes)
    }

    private fun remote(appVersion: Int = 12) = RemotePictures(
        filesDir = tmp.root,
        source = source,
        keys = listOf(TestCatalog.KEY),
        appVersion = appVersion,
        reservedIds = setOf("starter", "lemons"),
        clock = DayClock({ now }) { TimeZone.UTC },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        io = Dispatchers.IO.limitedParallelism(1),
        reporter = reporter,
        files = AtomicFiles(writer),
    )

    private fun RemotePictures.ids() = listed().map { it.id }

    private fun day(n: Int = 1) { now += n * RemoteSchedule.DAY_MILLIS }

    @Test
    fun a200StoresTheCatalogAndEveryGridAndASecondAppStartReadsThemOffline() = runBlocking {
        val fox = catalog.add("fox", CanvasSize.BIG, locked = false)
        catalog.add("lanterns", CanvasSize.MEDIUM)
        val r = remote()
        assertEquals(CheckOutcome.UPDATED, r.check())
        assertEquals(listOf("fox", "lanterns"), r.ids())
        assertContentEquals(catalog.files[fox.grid], r.grid("fox"))
        assertEquals(emptyList(), reports)
        // A new process, offline: the cache alone shows the same pictures.
        source.offline = true
        assertEquals(listOf("fox", "lanterns"), remote().ids())
    }

    @Test
    fun a304KeepsTheCacheAndSendsTheEtag() = runBlocking {
        catalog.add("fox")
        val r = remote()
        r.check()
        day()
        source.requests.clear()
        assertEquals(CheckOutcome.UNCHANGED, r.check())
        assertEquals(listOf(RemotePictures.CATALOG), source.requests.map { it.first }, "no signature or grid on a 304")
        assertTrue(source.requests.single().second != null, "If-None-Match sent")
        assertEquals(listOf("fox"), r.ids())
    }

    @Test
    fun onceADayRetryingAnHourAfterAFailure() = runBlocking {
        catalog.add("fox")
        val r = remote()
        r.check()
        assertEquals(CheckOutcome.NOT_DUE, r.check())
        day()
        source.offline = true
        assertEquals(CheckOutcome.OFFLINE, r.check())
        assertEquals(CheckOutcome.NOT_DUE, r.check(), "no retry within the hour")
        now += RemoteSchedule.RETRY_MILLIS
        source.offline = false
        assertEquals(CheckOutcome.UNCHANGED, r.check())
    }

    @Test
    fun offlineKeepsWorkingAndReportsNothing() = runBlocking {
        catalog.add("fox")
        val r = remote()
        r.check()
        day()
        source.offline = true
        assertEquals(CheckOutcome.OFFLINE, r.check())
        assertEquals(listOf("fox"), r.ids())
        assertEquals(emptyList(), reports)
        // Never fetched and offline: no remote pictures, no error.
        val empty = RemotePictures(tmp.newFolder(), source, listOf(TestCatalog.KEY), 12, emptySet(), DayClock({ now }) { TimeZone.UTC },
            CoroutineScope(Dispatchers.Default), Dispatchers.IO.limitedParallelism(1), reporter)
        assertEquals(CheckOutcome.OFFLINE, empty.check())
        assertEquals(emptyList(), empty.ids())
    }

    @Test
    fun aPartialDownloadShowsWhatArrivedAndRetries() = runBlocking {
        catalog.add("fox")
        catalog.add("lanterns")
        source.cutShort = setOf(RemoteCatalogs.gridPath("lanterns"))
        val r = remote()
        assertEquals(CheckOutcome.INCOMPLETE, r.check())
        assertEquals(listOf("fox"), r.ids(), "only verified grids show")
        assertEquals(1, reports.size, "a cut-off body is reported: $reports")
        assertTrue(RemoteSchedule.isDue(RemoteState(lastAttemptMillis = now - RemoteSchedule.RETRY_MILLIS), now))
        now += RemoteSchedule.RETRY_MILLIS
        source.cutShort = emptySet()
        assertEquals(CheckOutcome.UPDATED, r.check(), "retried after an hour, not a day")
        assertEquals(listOf("fox", "lanterns"), r.ids())
    }

    @Test
    fun aFullDiskIsReportedAndStoresNothingHalfWritten() = runBlocking {
        catalog.add("fox")
        diskFull = true
        val r = remote()
        // The grid can't be written, then neither can the catalog: nothing is stored, and the next try is in an hour.
        assertEquals(CheckOutcome.FAILED, r.check())
        assertEquals(emptyList(), r.ids())
        assertTrue(reports.any { "disk full" in it }, "$reports")
        assertFalse(File(tmp.root, StorageLayout.REMOTE_CATALOG).exists())
        assertEquals(emptyList(), File(tmp.root, StorageLayout.REMOTE_GRIDS).listFiles().orEmpty().filter { it.name.endsWith(".tmp") })
    }

    @Test
    fun aTamperedCatalogOrGridIsRefusedAndTheCacheKept() = runBlocking {
        val fox = catalog.add("fox")
        val r = remote()
        r.check()
        // Someone edits the catalog without the key.
        catalog.add("lanterns")
        catalog.files[RemotePictures.CATALOG] = catalog.files[RemotePictures.CATALOG]!!.decodeToString().replace("\"locked\": true", "\"locked\": false").encodeToByteArray()
        day()
        assertEquals(CheckOutcome.FAILED, r.check())
        assertTrue(reports.single().contains("bad_signature"), "$reports")
        assertEquals(listOf("fox"), r.ids())
        // A swapped grid under a correctly signed catalog.
        catalog.publish()
        catalog.files[RemoteCatalogs.gridPath("lanterns")] = TestCatalog.grid(CanvasSize.QUICK, 99)
        now += RemoteSchedule.RETRY_MILLIS
        assertEquals(CheckOutcome.INCOMPLETE, r.check())
        assertTrue(reports.last().contains("lanterns: bad_hash"), "$reports")
        assertEquals(listOf("fox"), r.ids())
        // A grid changed on the phone is refused when read.
        File(tmp.root, "${StorageLayout.REMOTE_GRIDS}/fox.gmrg").writeBytes(TestCatalog.grid(CanvasSize.QUICK, 5))
        assertNull(r.grid("fox"))
        assertEquals(fox.id, r.listed().first().id)
    }

    @Test
    fun anOlderSignedCatalogIsRefused() = runBlocking {
        catalog.add("fox")
        val old = catalog.files.toMap()
        catalog.add("lanterns")
        val r = remote()
        r.check()
        catalog.files.clear()
        catalog.files.putAll(old)
        day()
        assertEquals(CheckOutcome.FAILED, r.check())
        assertTrue(reports.single().contains("rollback"), "$reports")
        assertEquals(listOf("fox", "lanterns"), r.ids())
    }

    @Test
    fun aWithdrawnPictureLosesItsDownloadedGrid() = runBlocking {
        catalog.add("fox")
        catalog.add("lanterns")
        val r = remote()
        r.check()
        catalog.remove("lanterns")
        day()
        assertEquals(CheckOutcome.UPDATED, r.check())
        assertEquals(listOf("fox"), r.ids())
        assertFalse(File(tmp.root, "${StorageLayout.REMOTE_GRIDS}/lanterns.gmrg").exists())
    }

    @Test
    fun aPictureForANewerAppWaitsForTheUpdate() = runBlocking {
        catalog.add("fox")
        catalog.pictures[0] = catalog.pictures[0].copy(minAppVersion = 13)
        catalog.publish()
        val r = remote(appVersion = 12)
        r.check()
        assertEquals(emptyList(), r.ids())
        assertFalse(source.requests.any { it.first.startsWith("grids/") })
        // After the update: the cached catalog lists it and the next check (a 304) downloads its grid.
        val updated = remote(appVersion = 13)
        day()
        updated.check()
        assertEquals(listOf("fox"), updated.ids())
    }

    @Test
    fun newUntilOpenedAndNothingNewOnTheFirstSync() = runBlocking {
        catalog.add("fox")
        val r = remote()
        r.check()
        assertFalse(r.isNew("fox"), "a new install marks nothing new")
        catalog.add("lanterns")
        day()
        r.check()
        assertTrue(r.isNew("lanterns"))
        r.opened("lanterns")
        // opened() writes on the io dispatcher; the next check queues behind it.
        r.check(force = true)
        assertFalse(r.isNew("lanterns"))
        assertFalse(remote().isNew("lanterns"), "kept across launches")
    }

    @Test
    fun aCatalogClashingWithABundledIdIsRefused() = runBlocking {
        catalog.add("lemons")
        assertEquals(CheckOutcome.FAILED, remote().check())
        assertTrue(reports.single().contains("id_clash"))
    }
}
