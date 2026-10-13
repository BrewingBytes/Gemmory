package com.brewingbytes.gemmory.play

import com.brewingbytes.gemmory.bundled.BundledPicture
import com.brewingbytes.gemmory.bundled.BundledPictures
import com.brewingbytes.gemmory.converter.Lab
import com.brewingbytes.gemmory.converter.PaletteColor
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.save.CanvasRecord
import com.brewingbytes.gemmory.save.GridCodec
import com.brewingbytes.gemmory.save.ViewState
import com.brewingbytes.gemmory.share.EncodeCanceller
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.storage.WriteResult
import com.brewingbytes.gemmory.telemetry.NonFatalReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Canvas records, Start over and deletes through the store, on the real filesystem with a fake clock. */
class CanvasStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var now = 0L
    private val cancelled = mutableListOf<CanvasRef>()

    private val pattern = Pattern(
        width = 4,
        height = 3,
        palette = listOf(
            PaletteColor(1, 0xFF102030.toInt(), Lab(12.0, 1.0, -9.0)),
            PaletteColor(2, 0xFFE0D0C0.toInt(), Lab(85.0, 1.5, 5.0)),
        ),
        cells = IntArray(12) { it % 2 },
    )
    private val bundledGrid = GridCodec.encode(pattern)
    private val photo = CanvasRef(CanvasKind.PHOTO, "photo-1")
    private val starter = CanvasRef(CanvasKind.BUNDLED, BundledPictures.STARTER.id)

    private val store by lazy {
        CanvasStore(
            filesDir = tmp.root,
            scope = scope,
            writer = Dispatchers.IO.limitedParallelism(1),
            nowMillis = { now },
            bundledGrid = { _: BundledPicture -> bundledGrid },
            encodes = EncodeCanceller { cancelled += it },
            reporter = NonFatalReporter { },
        )
    }

    @After
    fun tearDown() = scope.cancel()

    private fun createPhoto(title: String = "My photo · 13 Oct") = runBlocking {
        assertEquals(WriteResult.Written, store.createPhoto(photo, pattern, CanvasRecord(title, 1_000, 0), byteArrayOf(7)))
    }

    /** Opens, places [cells], waits for the closing write (progress, view and play time). */
    private fun play(ref: CanvasRef, vararg cells: Int, playedMillis: Long = 0) = runBlocking {
        val session = store.start(assertIs<CanvasStore.Opened.Ready>(store.open(ref)))
        for (cell in cells) {
            session.select(pattern.cells[cell])
            session.place(cell)
        }
        now += playedMillis
        session.viewport.onLayout(400, 300, 1f)
        session.close()
        store.open(ref) // joins the closing write
    }

    @Test
    fun photoTitleComesFromTheRecordAndDefaultsWhenMissing() = runBlocking<Unit> {
        createPhoto()
        assertEquals("My photo · 13 Oct", assertIs<CanvasStore.Opened.Ready>(store.open(photo)).title)
        File(store.repository.dirOf(photo), "record").delete()
        assertEquals("My photo", assertIs<CanvasStore.Opened.Ready>(store.open(photo)).title)
    }

    @Test
    fun createPhotoIsIdempotentSoARepeatedStartMakesOneCanvas() = runBlocking<Unit> {
        createPhoto("first")
        createPhoto("second")
        assertEquals(listOf(photo.id), store.list(CanvasKind.PHOTO))
        assertEquals("first", store.record(photo).first)
    }

    @Test
    fun renameEditsTheTitleOnly() = runBlocking<Unit> {
        createPhoto()
        play(photo, 0, 1, playedMillis = 90_000)
        store.rename(photo, "Beach")
        val (title, record) = store.record(photo)
        assertEquals("Beach", title)
        assertEquals(CanvasRecord("Beach", 1_000, 90_000), record)
    }

    @Test
    fun playTimeAccumulatesOnlyWhileStarted() = runBlocking<Unit> {
        createPhoto()
        val session = store.start(assertIs<CanvasStore.Opened.Ready>(store.open(photo)))
        now += 10_000
        session.onStop().await()
        now += 1_000_000 // in the background: not counted
        session.onStart()
        now += 5_000
        session.close()
        assertEquals(15_000, store.record(photo).second?.playMillis)
    }

    @Test
    fun aBundledPictureIsCopiedFromItsFrozenGridOnFirstOpen() = runBlocking<Unit> {
        assertFalse(store.repository.dirOf(starter).exists())
        val opened = assertIs<CanvasStore.Opened.Ready>(store.open(starter))
        assertEquals(BundledPictures.STARTER.title, opened.title)
        assertContentEquals(bundledGrid, File(store.repository.dirOf(starter), "grid").readBytes())
    }

    @Test
    fun startOverClearsProgressAndViewAndCancelsTheEncodeFirst() = runBlocking<Unit> {
        createPhoto()
        play(photo, 0, 1, 2, playedMillis = 30_000)
        assertTrue(store.startOver(photo))
        assertEquals(listOf(photo), cancelled)
        val opened = assertIs<CanvasStore.Opened.Ready>(store.open(photo))
        assertEquals(0, opened.state.placedCount)
        assertNull(opened.view)
        assertEquals(0, opened.record?.playMillis)
        assertEquals("My photo · 13 Oct", opened.title)
        assertTrue(store.repository.sourceFile(photo).isFile, "the photo stays")
    }

    @Test
    fun startOverOnABundledPictureStartsFromTheFrozenGridAgain() = runBlocking<Unit> {
        play(starter, 0, 1)
        store.repository.writeView(starter, ViewState(24f, 1f, 1f, 1))
        assertTrue(store.startOver(starter))
        val opened = assertIs<CanvasStore.Opened.Ready>(store.open(starter))
        assertEquals(0, opened.state.placedCount)
        assertNull(opened.view)
    }

    @Test
    fun deleteRemovesPhotoGridProgressAndView() = runBlocking<Unit> {
        createPhoto()
        play(photo, 0, 1)
        val dir = store.repository.dirOf(photo)
        assertTrue(store.delete(photo))
        assertEquals(listOf(photo), cancelled)
        assertFalse(dir.exists())
        assertTrue(store.list(CanvasKind.PHOTO).isEmpty())
    }

    @Test
    fun deleteAllPhotosKeepsBundledPictures() = runBlocking<Unit> {
        createPhoto()
        assertEquals(
            WriteResult.Written,
            store.createPhoto(CanvasRef(CanvasKind.PHOTO, "photo-2"), pattern, CanvasRecord("b", 2, 0), byteArrayOf(1)),
        )
        play(starter, 0)
        assertTrue(store.deleteAllPhotos())
        assertTrue(store.list(CanvasKind.PHOTO).isEmpty())
        assertEquals(2, cancelled.size)
        assertEquals(1, assertIs<CanvasStore.Opened.Ready>(store.open(starter)).state.placedCount)
    }

    @Test
    fun summariesListTheStarterFirstThenPhotosNewestFirstAndFlagDamage() = runBlocking<Unit> {
        createPhoto()
        store.createPhoto(CanvasRef(CanvasKind.PHOTO, "photo-2"), pattern, CanvasRecord("newer", 2_000, 0), byteArrayOf(1))
        File(store.repository.dirOf(photo), "grid").writeBytes(byteArrayOf(1, 2, 3))
        val (pictures, photos) = store.summaries()
        assertEquals(listOf(starter), pictures.map { it.ref })
        assertEquals(0, assertIs<CanvasSummary.Playable>(pictures[0]).state.placedCount)
        assertEquals(listOf("photo-2", "photo-1"), photos.map { it.ref.id })
        assertIs<CanvasSummary.Damaged>(photos[1])
    }
}
