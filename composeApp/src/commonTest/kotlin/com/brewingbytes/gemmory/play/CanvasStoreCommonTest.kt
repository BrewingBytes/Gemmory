package com.brewingbytes.gemmory.play

import com.brewingbytes.gemmory.bundled.BundledPictures
import com.brewingbytes.gemmory.converter.Lab
import com.brewingbytes.gemmory.converter.PaletteColor
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.copy.Words
import com.brewingbytes.gemmory.save.CanvasRecord
import com.brewingbytes.gemmory.save.GridCodec
import com.brewingbytes.gemmory.share.EncodeCanceller
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.storage.FileStore
import com.brewingbytes.gemmory.storage.MemoryFileStore
import com.brewingbytes.gemmory.storage.WriteResult
import com.brewingbytes.gemmory.telemetry.NonFatalReporter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The canvas store on every platform (iOS Stage 1c, T64): the same files and steps over a [FileStore] as Android's
 * JVM tests check on disk (`CanvasStoreTest`, `CanvasRepositoryTest`), here in memory so iOS runs them too.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CanvasStoreCommonTest {
    private val pattern = Pattern(
        width = 4,
        height = 3,
        palette = listOf(
            PaletteColor(1, 0xFF102030.toInt(), Lab(12.0, 1.0, -9.0)),
            PaletteColor(2, 0xFFE0D0C0.toInt(), Lab(85.0, 1.5, 5.0)),
        ),
        cells = IntArray(12) { it % 2 },
    )
    private val grid = GridCodec.encode(pattern)
    private val words = Words.load("en") {
        val titles = BundledPictures.ALL.joinToString("") { "<string name=\"${it.titleKey}\">${it.id} title</string>" }
        "<resources>$titles<string name=\"photo_default_title\">My photo</string></resources>"
    }
    private val files = MemoryFileStore()
    private val cancelled = mutableListOf<CanvasRef>()
    private val photo = CanvasRef(CanvasKind.PHOTO, "photo-1")
    private val starter = CanvasRef(CanvasKind.BUNDLED, BundledPictures.STARTER.id)

    private fun TestScope.store(): CanvasStore {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        return CanvasStore(
            files = files,
            scope = backgroundScope,
            writer = dispatcher,
            nowMillis = { 0L },
            bundledGrid = { grid },
            encodes = object : EncodeCanceller {
                override suspend fun cancelAndClear(ref: CanvasRef) {
                    cancelled += ref
                }
            },
            reporter = NonFatalReporter { },
            words = { words },
            wallMillis = { 1_000L },
        )
    }

    @Test
    fun aPhotoCanvasIsMadeOnceWithItsSourceUnderThePhotoFolder() = runTest {
        val store = store()
        val jpeg = byteArrayOf(1, 2, 3)
        assertEquals(WriteResult.Written, store.createPhoto(photo, pattern, CanvasRecord("", 5L, 0L), jpeg))
        assertEquals(WriteResult.Written, store.createPhoto(photo, pattern, CanvasRecord("", 6L, 0L), byteArrayOf(9)), "a repeated Start makes one canvas")
        assertContentEquals(jpeg, files.read("canvases/photo/photo-1/source.jpg"))
        assertTrue(store.repository.hasSource(photo))
        val (_, photos) = store.summaries()
        val tile = assertIs<CanvasSummary.Playable>(photos.single())
        assertEquals("My photo", tile.title, "no title yet: the default")
        assertEquals(5L, tile.createdAtMillis)
    }

    @Test
    fun aBundledPictureIsCopiedInOnFirstOpenAndItsProgressComesBack() = runTest {
        val store = store()
        assertFalse(store.repository.exists(starter))
        val opened = assertIs<CanvasStore.Opened.Ready>(store.open(starter))
        assertEquals("starter title", opened.title)
        assertContentEquals(grid, files.read("canvases/bundled/starter/grid"), "the frozen grid, as is")
        assertEquals(WriteResult.Written, store.repository.writeSave(starter, opened.grid.binding, intArrayOf(0, 2)))
        val again = assertIs<CanvasStore.Opened.Ready>(store.open(starter))
        assertContentEquals(intArrayOf(0, 2), again.state.log())
        assertTrue(store.isStarted(starter))
    }

    @Test
    fun startOverAndDeleteRemoveTheFilesAndCancelEncodes() = runTest {
        val store = store()
        store.open(starter)
        store.createPhoto(photo, pattern, CanvasRecord("Mine", 5L, 0L), byteArrayOf(1))
        assertTrue(store.startOver(starter))
        assertFalse(store.repository.exists(starter), "copied fresh on the next open")
        assertTrue(store.delete(photo))
        assertFalse(files.exists("canvases/photo/photo-1"))
        assertEquals(listOf(starter, photo), cancelled)
    }

    @Test
    fun aRenameIsKeptAndShownInTheGallery() = runTest {
        val store = store()
        store.createPhoto(photo, pattern, CanvasRecord("", 5L, 0L), byteArrayOf(1))
        assertEquals(WriteResult.Written, store.rename(photo, "Harbour"))
        assertEquals("Harbour", store.summaries().second.single().title)
    }

    @Test
    fun theSweepClearsInterruptedCreatesDeletesAndWrites() = runTest {
        files.mkdirs("canvases/photo/.photo-2.new")
        files.writeAtomic("canvases/photo/.photo-2.new/grid", grid)
        files.mkdirs("canvases/bundled/.starter.deleted")
        files.mkdirs("canvases/photo/photo-1")
        files.writeAtomic("canvases/photo/photo-1/grid", grid)
        files.writeAtomic("canvases/photo/photo-1/save${FileStore.TEMP_SUFFIX}", byteArrayOf(1))
        val store = store()
        assertEquals(listOf("photo-1"), store.list(CanvasKind.PHOTO))
        assertEquals(listOf("photo-1"), files.list("canvases/photo"))
        assertEquals(emptyList<String>(), files.list("canvases/bundled"))
        assertEquals(listOf("grid"), files.list("canvases/photo/photo-1"))
    }

    @Test
    fun aFullDiskFailsTheWriteAndSaysSo() = runTest {
        val store = store()
        val opened = assertIs<CanvasStore.Opened.Ready>(store.open(starter))
        files.failWrites = true
        files.diskFull = true
        assertEquals(WriteResult.WriteFailed(diskFull = true), store.repository.writeSave(starter, opened.grid.binding, intArrayOf(0)))
        assertEquals(WriteResult.WriteFailed(diskFull = true), store.createPhoto(photo, pattern, CanvasRecord("", 5L, 0L), byteArrayOf(1)))
        assertFalse(files.exists("canvases/photo/.photo-1.new"), "the staging folder is cleared")
    }
}
