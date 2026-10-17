package com.brewingbytes.gemmory.play

import com.brewingbytes.gemmory.bundled.BundledPicture
import com.brewingbytes.gemmory.bundled.BundledPictures
import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.remote.RemoteCatalogView
import com.brewingbytes.gemmory.remote.RemotePicture
import com.brewingbytes.gemmory.remote.TestCatalog
import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.save.GridCodec
import com.brewingbytes.gemmory.save.GridFile
import com.brewingbytes.gemmory.share.EncodeCanceller
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.telemetry.Analytics
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent
import com.brewingbytes.gemmory.telemetry.NonFatalReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Remote pictures (#43) through the canvas store: listed in their size, copied in on first open, kept once started. */
class RemoteCanvasStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val catalog = TestCatalog()
    private var listed = listOf<RemotePicture>()
    private val events = java.util.Collections.synchronizedList(mutableListOf<AnalyticsEvent>())
    private val bundledGrid = TestCatalog.grid(CanvasSize.QUICK, 1)

    private val store by lazy {
        CanvasStore(
            filesDir = tmp.root,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            writer = Dispatchers.IO.limitedParallelism(1),
            nowMillis = { 0L },
            bundledGrid = { _: BundledPicture -> bundledGrid },
            encodes = object : EncodeCanceller {
                override suspend fun cancelAndClear(ref: CanvasRef) = Unit
            },
            reporter = NonFatalReporter { },
            analytics = object : Analytics {
                override fun log(event: AnalyticsEvent) {
                    events += event
                }
            },
            remote = object : RemoteCatalogView {
                override fun listed() = listed
                override fun grid(id: String) = catalog.files["grids/$id.gmrg"].takeIf { listed.any { it.id == id } }
            },
        )
    }

    private fun ref(id: String) = CanvasRef(CanvasKind.REMOTE, id)

    @Test
    fun remotePicturesJoinTheirSizeAfterTheBundledOnes() = runBlocking<Unit> {
        val owl = catalog.add("owl", CanvasSize.QUICK, locked = false)
        val fox = catalog.add("fox", CanvasSize.BIG, locked = true)
        listed = listOf(fox, owl)
        val pictures = store.summaries().first
        val ids = pictures.map { it.ref.id }
        val lastQuick = BundledPictures.ALL.last { BundledPictures.canvasSizeOf(it.id) == CanvasSize.QUICK }.id
        assertEquals(ids.indexOf(lastQuick) + 1, ids.indexOf("owl"))
        assertEquals("fox", ids.last())
        val foxSummary = pictures.last()
        assertEquals(ref("fox"), foxSummary.ref)
        assertEquals("Fox", foxSummary.title)
        assertEquals(CanvasSize.BIG, foxSummary.size)
        assertTrue(foxSummary.locked)
        assertEquals(false, pictures.first { it.ref.id == "owl" }.locked)
    }

    @Test
    fun openingCopiesTheGridInWithItsTitleAndCountsARemoteStart() = runBlocking<Unit> {
        val fox = catalog.add("fox", CanvasSize.BIG)
        listed = listOf(fox)
        val opened = assertIs<CanvasStore.Opened.Ready>(store.open(ref("fox")))
        assertEquals("Fox", opened.title)
        assertEquals(fox.crc32, opened.grid.crc32)
        assertEquals(listOf(AnalyticsEvent("canvas_started", mapOf("source" to "remote", "size" to "big"))), events.toList())
        assertEquals("Fox", store.record(ref("fox")).first)
    }

    @Test
    fun aWithdrawnPictureStaysOnlyOnceStarted() = runBlocking<Unit> {
        val fox = catalog.add("fox", CanvasSize.BIG)
        val owl = catalog.add("owl", CanvasSize.QUICK)
        listed = listOf(fox, owl)
        store.open(ref("fox"))
        store.open(ref("owl"))
        val grid = (GridCodec.decode(catalog.files[fox.grid]!!) as Decoded.Ok<GridFile>).value
        store.repository.writeSave(ref("fox"), grid.binding, intArrayOf(0))
        listed = emptyList()
        val pictures = store.summaries().first
        val kept = pictures.filter { it.ref.kind == CanvasKind.REMOTE }
        assertEquals(listOf("fox"), kept.map { it.ref.id }, "started kept, opened-but-empty gone")
        assertEquals("Fox", kept.single().title, "the title comes from its record")
        assertEquals(CanvasSize.BIG, kept.single().size, "sized by its grid")
        assertEquals(1, (kept.single() as CanvasSummary.Playable).state.placedCount)
        assertIs<CanvasStore.Opened.Ready>(store.open(ref("fox")), "still plays offline, from its own grid copy")
    }
}
