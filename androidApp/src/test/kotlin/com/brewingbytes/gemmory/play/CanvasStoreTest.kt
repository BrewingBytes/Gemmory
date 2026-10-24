package com.brewingbytes.gemmory.play

import com.brewingbytes.gemmory.copy.XmlStrings
import com.brewingbytes.gemmory.bundled.BundledPicture
import com.brewingbytes.gemmory.bundled.BundledPictures
import com.brewingbytes.gemmory.converter.ColorChoice
import com.brewingbytes.gemmory.converter.Lab
import com.brewingbytes.gemmory.converter.PaletteColor
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.game.PlacementTool
import com.brewingbytes.gemmory.policy.DayClock
import com.brewingbytes.gemmory.policy.GameEvent
import com.brewingbytes.gemmory.policy.ReminderCanvas
import com.brewingbytes.gemmory.save.CanvasRecord
import com.brewingbytes.gemmory.save.GridCodec
import com.brewingbytes.gemmory.save.PlayTimes
import com.brewingbytes.gemmory.save.ViewState
import com.brewingbytes.gemmory.share.EncodeCanceller
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.storage.WriteResult
import com.brewingbytes.gemmory.telemetry.Analytics
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent
import com.brewingbytes.gemmory.telemetry.NonFatalReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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

    /** Wall time for the `play` file (T35), in UTC so a day is 86 400 000 ms. */
    private var wall = 1_792_000_000_000L
    private val day = DayClock({ wall }) { TimeZone.UTC }
    private val cancelled = mutableListOf<CanvasRef>()
    private val events = java.util.Collections.synchronizedList(mutableListOf<AnalyticsEvent>())
    private val earned = java.util.Collections.synchronizedList(mutableListOf<GameEvent>())

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
            strings = XmlStrings.en,
            bundledGrid = { _: BundledPicture -> bundledGrid },
            encodes = object : EncodeCanceller {
                override suspend fun cancelAndClear(ref: CanvasRef) {
                    cancelled += ref
                }
            },
            reporter = NonFatalReporter { },
            analytics = object : Analytics {
                override fun log(event: AnalyticsEvent) {
                    events += event
                }
            },
            achievements = { earned += it },
            dayClock = day,
        )
    }

    @After
    fun tearDown() = scope.cancel()

    private fun createPhoto(title: String = "My photo · 13 Oct", colors: ColorChoice = ColorChoice.STANDARD) = runBlocking {
        assertEquals(WriteResult.Written, store.createPhoto(photo, pattern, CanvasRecord(title, 1_000, 0), byteArrayOf(7), colors))
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
    fun aFreshFinishIsCountedForAchievementsOnce() = runBlocking<Unit> {
        play(starter, *(0 until 12).toList().toIntArray())
        createPhoto()
        play(photo, *(0 until 12).toList().toIntArray())
        play(starter)
        play(photo)
        // The test grid is 4×3, no picker size; the starter has none either (#49).
        assertEquals(listOf(GameEvent.Finished(null, photo = false), GameEvent.Finished(null, photo = true)), earned.toList())
    }

    @Test
    fun startsAndFinishesAreCountedOnceWithSourceAndSize() = runBlocking<Unit> {
        createPhoto(colors = ColorChoice.MORE)
        createPhoto(colors = ColorChoice.MORE)
        assertEquals(
            listOf(
                AnalyticsEvent("canvas_started", mapOf("source" to "photo", "size" to "other", "colors" to "more", "entry" to "gallery")),
                AnalyticsEvent("photo_canvas_started", mapOf("colors" to "more")),
            ),
            events.toList(),
            "a repeated Start makes one canvas, counted once",
        )
        events.clear()
        play(starter, 0, 1)
        assertEquals(listOf(AnalyticsEvent("canvas_started", mapOf("source" to "bundled", "size" to "starter", "entry" to "gallery"))), events.toList())
        events.clear()
        play(starter, *(2 until 12).toList().toIntArray())
        assertEquals(listOf(AnalyticsEvent("canvas_finished", mapOf("source" to "bundled", "size" to "starter", "entry" to "gallery"))), events.toList())
        events.clear()
        play(starter)
        assertTrue(events.isEmpty(), "reopening a finished canvas counts nothing")
    }

    @Test
    fun toolBatchesAreSavedInTheirOrderAndSurviveAReopen() = runBlocking<Unit> {
        // #20: columns 0 and 2 are color 1 (slot 0), columns 1 and 3 color 2 (slot 1).
        createPhoto()
        events.clear()
        val session = store.start(assertIs<CanvasStore.Opened.Ready>(store.open(photo)))
        session.select(0)
        assertNull(session.fillRegion(4), "no tools for a free player")
        session.offerTools(unlimited = true)
        session.place(2)
        // Column 0 is one region: rings from 4 are 4, then 0 and 8.
        assertContentEquals(intArrayOf(4, 0, 8), assertNotNull(session.fillRegion(4)).placed)
        // The rest of color 1 (6 and 10), nearest to cell 11 first, finishes it and selects color 2.
        val rest = assertNotNull(session.fillColor(from = 11))
        assertContentEquals(intArrayOf(10, 6), rest.placed)
        assertEquals(0, assertNotNull(rest.colorFinished).slot)
        assertEquals(1, session.selectedSlot)
        // A brush stamp on 5 places only color 2's cells around it, nearest first; a second one skips them.
        assertContentEquals(intArrayOf(5, 1, 9), assertNotNull(session.brush(5)).placed)
        assertNull(session.brush(5), "nothing left to place: no change")
        session.close()
        val reopened = assertIs<CanvasStore.Opened.Ready>(store.open(photo))
        assertContentEquals(intArrayOf(2, 4, 0, 8, 10, 6, 5, 1, 9), reopened.state.log())
        assertEquals(
            listOf("region", "color", "brush"),
            events.filter { it.name == "fill_tool_used" }.map { it.params["tool"] },
            "one event per tool per open",
        )
    }

    @Test
    fun withoutVipEachUseOrBrushStrokeSpendsOneFill() = runBlocking<Unit> {
        // #38: columns 0 and 2 are color 1 (slot 0), columns 1 and 3 color 2 (slot 1).
        createPhoto()
        events.clear()
        val session = store.start(assertIs<CanvasStore.Opened.Ready>(store.open(photo)))
        val spent = mutableListOf<String>()
        var held = 2
        session.spendFill = { id, done ->
            spent += id
            held--
            session.offerTools(unlimited = false, fillsHeld = held)
            done()
        }
        session.offerTools(unlimited = false, fillsHeld = held)
        assertTrue(session.toolsOn && session.toolsUsable)
        session.select(0)
        assertNull(session.fillRegion(5), "a region of another color places nothing, and spends nothing")
        assertTrue(spent.isEmpty())
        assertNotNull(session.fillRegion(4))
        assertEquals(1, session.fills)
        // One brush stroke: three stamps, one fill, even though it's the last one.
        session.select(1)
        session.startStroke()
        assertNotNull(session.brush(1))
        assertEquals(0, session.fills)
        assertNotNull(session.brush(9), "the rest of the stroke is paid for")
        assertEquals(2, spent.size)
        // Out of fills: the well stays (dimmed), the tools don't place, and the tool is back to Tap.
        assertTrue(session.toolsOn)
        assertFalse(session.toolsUsable)
        assertEquals(PlacementTool.TAP, session.tool)
        session.startStroke()
        assertNull(session.brush(11))
        assertNull(session.fillRegionNext())
        assertEquals(2, spent.size)
        assertEquals(
            listOf("free", "free"),
            events.filter { it.name == "fill_tool_used" }.map { it.params["tier"] },
        )
        session.close()
    }

    @Test
    fun aCanvasIsFinishedOnDiskAsSoonAsItsSessionClosesWithTheLastGem() = runBlocking<Unit> {
        // #50: a tile tap right after Done reads this, so it must not race the closing write.
        createPhoto()
        assertFalse(store.isFinished(photo))
        assertFalse(store.isFinished(starter), "an unopened bundled picture isn't finished")
        val session = store.start(assertIs<CanvasStore.Opened.Ready>(store.open(photo)))
        for (cell in 0 until 12) {
            session.select(pattern.cells[cell])
            session.place(cell)
        }
        session.close()
        assertTrue(store.isFinished(photo))
        store.startOver(photo)
        assertFalse(store.isFinished(photo))
    }

    /** T35 (eng D2): each save stamps the last gem; the save of the last gem stamps the first finished day, once. */
    @Test
    fun thePlayFileFollowsTheSavesAndKeepsTheFirstFinishedDayThroughAStartOver() = runBlocking<Unit> {
        val repo = store.repository
        play(starter, 0, 1)
        assertEquals(PlayTimes(lastGemMillis = wall, firstFinishedDay = null), repo.readPlay(starter))
        val finishedOn = day.today()
        wall += 60_000
        play(starter, *(2 until 12).toList().toIntArray())
        assertEquals(PlayTimes(wall, finishedOn), repo.readPlay(starter))
        store.startOver(starter)
        assertEquals(PlayTimes(null, finishedOn), repo.readPlay(starter))
        wall += 3 * 86_400_000L
        play(starter, *(0 until 12).toList().toIntArray())
        assertEquals(PlayTimes(wall, finishedOn), repo.readPlay(starter), "finished again later: the first day stays")
    }

    /** T35 (eng D12): the reminder's read waits for a closing write and lists only canvases with a gem placed. */
    @Test
    fun reminderCanvasesWaitForTheClosingWriteAndSkipUnstartedOnes() = runBlocking<Unit> {
        createPhoto()
        val session = store.start(assertIs<CanvasStore.Opened.Ready>(store.open(starter)))
        for (cell in 0 until 3) {
            session.select(pattern.cells[cell])
            session.place(cell)
        }
        session.close()
        val read = store.reminderCanvases()
        assertEquals(listOf(ReminderCanvas("BUNDLED/starter", "starter", placed = 3, total = 12, lastGemMillis = wall)), read)
        assertEquals(starter, CanvasStore.refOfReminderKey(read.single().key))
        assertNull(CanvasStore.refOfReminderKey("PHOTO/../x"))
        assertNull(CanvasStore.refOfReminderKey("nope"))
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

    /** The completion screen re-reads on every visit, so a rename and a later play time show up (review on #22). */
    @Test
    fun aRenameAfterFinishingShowsOnTheNextOpen() = runBlocking<Unit> {
        createPhoto()
        play(photo, *IntArray(12) { it }, playedMillis = 60_000)
        assertEquals("My photo · 13 Oct", assertIs<CanvasStore.Opened.Ready>(store.open(photo)).title)
        store.rename(photo, "Beach")
        val reopened = assertIs<CanvasStore.Opened.Ready>(store.open(photo))
        assertEquals("Beach", reopened.title)
        assertTrue(reopened.state.isFinished)
        assertEquals(60_000, reopened.record?.playMillis)
    }

    /** The title is in the video's band, so a rename clears the cached video (review on #4). */
    @Test
    fun renameClearsTheCachedTimelapse() = runBlocking<Unit> {
        createPhoto()
        store.rename(photo, "Grandma")
        assertEquals(listOf(photo), cancelled)
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
        assertEquals(BundledPictures.STARTER.title(XmlStrings.en), opened.title)
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
        assertEquals(BundledPictures.ALL.map { CanvasRef(CanvasKind.BUNDLED, it.id) }, pictures.map { it.ref })
        assertEquals(starter, pictures.first().ref)
        assertEquals(0, assertIs<CanvasSummary.Playable>(pictures[0]).state.placedCount)
        assertEquals(listOf("photo-2", "photo-1"), photos.map { it.ref.id })
        assertIs<CanvasSummary.Damaged>(photos[1])
    }

    /** #89: a finished canvas's summary carries its first finished day for the Finished section; others carry none. */
    @Test
    fun summariesCarryTheFirstFinishedDayOfFinishedCanvasesOnly() = runBlocking<Unit> {
        createPhoto()
        play(photo, 0, 1)
        play(starter, *(0 until 12).toList().toIntArray())
        val finishedOn = day.today()
        val (pictures, photos) = store.summaries()
        assertEquals(finishedOn, assertIs<CanvasSummary.Playable>(pictures.first()).firstFinishedDay)
        assertEquals(null, assertIs<CanvasSummary.Playable>(photos.single()).firstFinishedDay, "started, not finished")
        store.startOver(starter)
        assertEquals(null, assertIs<CanvasSummary.Playable>(store.summaries().first.first()).firstFinishedDay, "Play again: unfinished")
        // Finished before T35 (no play file): no date.
        play(starter, *(0 until 12).toList().toIntArray())
        File(store.repository.dirOf(starter), "play").delete()
        assertEquals(null, assertIs<CanvasSummary.Playable>(store.summaries().first.first()).firstFinishedDay)
    }

    /** #31: the whole catalog shows on a fresh install, never-opened pictures empty and titled, with nothing copied in. */
    @Test
    fun summariesListEveryBundledPictureUnopenedOnAFreshInstall() = runBlocking<Unit> {
        val (pictures, photos) = store.summaries()
        assertEquals(BundledPictures.ALL.map { it.title(XmlStrings.en) }, pictures.map { assertIs<CanvasSummary.Playable>(it).title })
        assertTrue(pictures.all { (it as CanvasSummary.Playable).state.placedCount == 0 })
        assertTrue(store.list(CanvasKind.BUNDLED).isEmpty())
        assertTrue(photos.isEmpty())
    }
}
