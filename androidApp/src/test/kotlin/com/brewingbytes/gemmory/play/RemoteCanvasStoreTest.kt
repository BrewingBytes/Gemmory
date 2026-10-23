package com.brewingbytes.gemmory.play

import com.brewingbytes.gemmory.copy.XmlStrings
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
    private val earned = mutableSetOf<String>()
    private val events = java.util.Collections.synchronizedList(mutableListOf<AnalyticsEvent>())
    private val bundledGrid = TestCatalog.grid(CanvasSize.QUICK, 1)
    private val gridReads = java.util.Collections.synchronizedList(mutableListOf<String>())

    private val store by lazy {
        CanvasStore(
            filesDir = tmp.root,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            writer = Dispatchers.IO.limitedParallelism(1),
            nowMillis = { 0L },
            strings = XmlStrings.en,
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
                override fun grid(id: String): ByteArray? {
                    gridReads += id
                    return catalog.files["grids/$id.gmrg"].takeIf { listed.any { it.id == id } }
                }

                override fun earned(id: String) = id in earned

                override fun collectionOf(id: String) = "seaside".takeIf { id == "owl" }
            },
        )
    }

    private fun ref(id: String) = CanvasRef(CanvasKind.REMOTE, id)

    /** T37: an earned keepsake is copied into its canvas, then stays after the catalog withdraws it, never started. */
    @Test
    fun anEarnedKeepsakeSurvivesWithdrawal() = runBlocking<Unit> {
        val keepsake = catalog.add("harbour-lights", rewardOf = "seaside")
        assertEquals(KeepResult.MISSING, store.keep("harbour-lights"), "not listed until earned")
        earned += "harbour-lights"
        listed = listOf(keepsake)
        assertEquals(KeepResult.CREATED, store.keep("harbour-lights"))
        assertEquals(KeepResult.PRESENT, store.keep("harbour-lights"))
        listed = emptyList()
        val kept = store.summaries().first.single { it.ref == ref("harbour-lights") }
        assertIs<CanvasSummary.Playable>(kept)
        assertEquals("Harbour lights", kept.title)
        assertEquals(0, kept.state.placedCount)
        // An unstarted withdrawn picture that isn't a keepsake still leaves (the withdraw rule, unchanged).
        earned.clear()
        assertTrue(store.summaries().first.none { it.ref == ref("harbour-lights") })
    }

    /** T37: canvas events name the newest collection listing the picture. */
    @Test
    fun canvasEventsCarryTheCollectionId() = runBlocking<Unit> {
        listed = listOf(catalog.add("owl", CanvasSize.QUICK, locked = false))
        assertIs<CanvasStore.Opened.Ready>(store.open(ref("owl")))
        val started = events.first { it.name == "canvas_started" }
        assertEquals("seaside", started.params["collection_id"])
    }

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
        assertEquals(listOf(AnalyticsEvent("canvas_started", mapOf("source" to "remote", "size" to "big", "entry" to "gallery"))), events.toList())
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

    /** T36, eng D12: an unstarted remote picture is summarized from its catalog entry; its grid is read only to draw it. */
    @Test
    fun anUnstartedRemotePictureIsSummarizedWithoutItsGrid() = runBlocking<Unit> {
        val fox = catalog.add("fox", CanvasSize.BIG)
        listed = listOf(fox)
        gridReads.clear()
        val summary = assertIs<CanvasSummary.Listed>(store.summaries().first.single { it.ref.kind == CanvasKind.REMOTE })
        assertEquals(listOf<String>(), gridReads.toList(), "no grid read for the gallery")
        assertEquals(fox.width to fox.height, summary.width to summary.height)
        assertEquals("Fox", summary.title)
        assertEquals(CanvasSize.BIG, summary.size)
        assertEquals(fox.colors, store.pattern(ref("fox"))?.palette?.size, "read when its thumbnail is drawn")
        // Started, it's summarized from its own canvas like any other.
        store.open(ref("fox"))
        assertIs<CanvasSummary.Playable>(store.summaries().first.single { it.ref.kind == CanvasKind.REMOTE })
    }

    /**
     * T43: a mystery is summarized and opened under its neutral name until it's finished, its record keeps it hidden
     * after a withdrawal, and its start says `mystery`. The finish shows its title again.
     */
    @Test
    fun aMysteryIsHiddenUntilFinishedEvenAfterWithdrawal() = runBlocking<Unit> {
        val lantern = catalog.add("lantern", availableFrom = "2026-10-01", mystery = true)
        listed = listOf(lantern)
        val unopened = store.summaries().first.single { it.ref == ref("lantern") }
        assertEquals("Mystery picture", unopened.hiddenAs)
        assertEquals("Mystery picture", store.hiddenAs(ref("lantern")))
        val opened = assertIs<CanvasStore.Opened.Ready>(store.open(ref("lantern")))
        assertEquals("Mystery picture", opened.hiddenAs)
        assertEquals("true", events.single { it.name == "canvas_started" }.params["mystery"])
        val grid = (GridCodec.decode(catalog.files[lantern.grid]!!) as Decoded.Ok<GridFile>).value
        store.repository.writeSave(ref("lantern"), grid.binding, intArrayOf(0))
        listed = emptyList()
        val withdrawn = store.summaries().first.single { it.ref == ref("lantern") }
        assertEquals("Mystery picture", withdrawn.hiddenAs, "its record keeps it hidden")
        assertEquals("Lantern", withdrawn.title, "the real title is kept for the finish")
        store.repository.writeSave(ref("lantern"), grid.binding, IntArray(grid.pattern.cells.size) { it })
        val finished = store.summaries().first.single { it.ref == ref("lantern") }
        assertEquals(null, finished.hiddenAs)
        assertEquals(null, store.hiddenAs(ref("lantern")))
        assertEquals("Lantern", store.record(ref("lantern")).first, "the completion meta line names it")
    }

    /** R1: an ordinary remote picture is never hidden, and its start carries no `mystery`. */
    @Test
    fun anOrdinaryPictureIsNeverHidden() = runBlocking<Unit> {
        listed = listOf(catalog.add("fox", availableFrom = "2026-10-01"))
        assertEquals(null, store.summaries().first.single { it.ref == ref("fox") }.hiddenAs)
        assertEquals(null, assertIs<CanvasStore.Opened.Ready>(store.open(ref("fox"))).hiddenAs)
        assertEquals(null, events.single { it.name == "canvas_started" }.params["mystery"])
    }

    /** T36: an id the catalog doesn't list now (tomorrow's drop, crafted or stale) never gets a canvas. */
    @Test
    fun aHiddenPictureCantBeOpenedById() = runBlocking<Unit> {
        catalog.add("lighthouse", availableFrom = "2026-11-02")
        listed = emptyList()
        assertIs<CanvasStore.Opened.Damaged>(store.open(ref("lighthouse")))
        assertEquals(false, store.repository.dirOf(ref("lighthouse")).isDirectory, "nothing copied in")
        assertEquals(null, store.pattern(ref("lighthouse")))
        assertEquals(emptyList(), events.toList(), "no canvas_started")
    }
}
