package com.brewingbytes.gemmory.gallery

import androidx.lifecycle.SavedStateHandle
import com.brewingbytes.gemmory.ads.PlayerAccess
import com.brewingbytes.gemmory.app.AppGraph
import com.brewingbytes.gemmory.app.PlatformServices
import com.brewingbytes.gemmory.app.PlayerProgress
import com.brewingbytes.gemmory.bundled.TodaysPicks
import com.brewingbytes.gemmory.collections.CollectionBook
import com.brewingbytes.gemmory.collections.Settled
import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.converter.ColorChoice
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.copy.Words
import com.brewingbytes.gemmory.play.CanvasProgress
import com.brewingbytes.gemmory.play.CanvasSummary
import com.brewingbytes.gemmory.play.Canvases
import com.brewingbytes.gemmory.policy.Allowance
import com.brewingbytes.gemmory.policy.Cover
import com.brewingbytes.gemmory.policy.Entitlements
import com.brewingbytes.gemmory.policy.Item
import com.brewingbytes.gemmory.policy.ReminderCanvas
import com.brewingbytes.gemmory.policy.StreakState
import com.brewingbytes.gemmory.remote.RemotePicture
import com.brewingbytes.gemmory.remote.RemoteShelf
import com.brewingbytes.gemmory.save.CanvasRecord
import com.brewingbytes.gemmory.share.ProgressShareSession
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.storage.FileStore
import com.brewingbytes.gemmory.storage.KeyValueStore
import com.brewingbytes.gemmory.storage.MemoryStore
import com.brewingbytes.gemmory.storage.WriteResult
import com.brewingbytes.gemmory.streak.StreakSnapshot
import com.brewingbytes.gemmory.streak.StreakSource
import com.brewingbytes.gemmory.telemetry.Analytics
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent
import com.brewingbytes.gemmory.telemetry.NonFatalReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The gallery's ViewModel in shared code (iOS Stage 1c, T64): it reads every service from the [AppGraph], so the same
 * code runs on Android and iOS. The fixtures are catalog entries and damaged canvases only, which draw nothing, so the
 * test runs on the JVM too.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GalleryViewModelTest {
    private val main = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(main)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun picturesThatOpenComeBeforeLockedOnesWithTheirLabels() = runTest(main) {
        val services = FakeServices(
            pictures = listOf(listed("fox", locked = true), listed("owl", locked = false), listed("elk", locked = true)),
            unlocked = setOf("elk"),
            new = setOf("owl"),
        )
        val vm = GalleryViewModel(AppGraph(services), SavedStateHandle())
        vm.refresh()
        val state = vm.state.first { it.loaded }

        assertEquals(listOf("owl", "elk", "fox"), state.pictures.map { it.ref.id }, "open first (free, then unlocked), locked last (#58)")
        assertEquals(listOf(true, false, false), state.pictures.map { it.isNew })
        assertTrue(state.pictures.all { it.lazyImage && it.image == null }, "unstarted remote pictures draw when they show (T36)")
        assertEquals(null, state.today, "no Today card before the first finish (T45)")
        assertEquals(emptyList(), state.collections.rows)
    }

    @Test
    fun aDamagedCanvasWithoutAThumbnailStillShows() = runTest(main) {
        val damaged = CanvasSummary.Damaged(CanvasRef(CanvasKind.PHOTO, "p1"), "My photo", 5L, thumb = null)
        val vm = GalleryViewModel(AppGraph(FakeServices(photoTiles = listOf(damaged))), SavedStateHandle())
        vm.refresh()
        val tile = vm.state.first { it.loaded }.photos.single()

        assertTrue(tile.damaged)
        assertEquals(3f / 4f, tile.aspect)
    }

    @Test
    fun theTabIsKeptInTheSavedState() {
        val handle = SavedStateHandle()
        val graph = AppGraph(FakeServices())
        GalleryViewModel(graph, handle).selectTab(1)

        assertEquals(1, GalleryViewModel(graph, handle).tab, "restored after process death from the handle")
    }

    @Test
    fun tappingANewPictureClearsItsLabelAndTheTeaserIsCounted() = runTest(main) {
        val services = FakeServices(pictures = listOf(listed("owl", locked = false)), new = setOf("owl"))
        val vm = GalleryViewModel(AppGraph(services), SavedStateHandle())
        vm.refresh()
        vm.opened(vm.state.first { it.loaded }.pictures.single())
        vm.teaserTapped()

        assertEquals(listOf("owl"), services.openedIds)
        assertEquals(listOf("teaser_tapped"), services.events.map { it.name })
    }

    @Test
    fun aRenameIsCleanedBeforeItsWritten() = runTest(main) {
        val services = FakeServices()
        val vm = GalleryViewModel(AppGraph(services), SavedStateHandle())
        val ref = CanvasRef(CanvasKind.PHOTO, "p1")
        vm.rename(ref, "  Sunday   at  the lake ")
        vm.rename(ref, "   ")
        vm.state.first { it.loaded }

        assertEquals(listOf("Sunday at the lake"), services.renamed, "a blank rename keeps the old title")
    }

    @Test
    fun theFinishedSectionIsRememberedInSettings() {
        val graph = AppGraph(FakeServices())
        val vm = GalleryViewModel(graph, SavedStateHandle())
        vm.setFinishedOpen(1, true)

        assertTrue(GalleryViewModel(graph, SavedStateHandle()).finishedOpen(1))
        assertFalse(vm.finishedOpen(0))
    }

    private fun listed(id: String, locked: Boolean) =
        CanvasSummary.Listed(CanvasRef(CanvasKind.REMOTE, id), id, 30, 40, CanvasSize.QUICK, locked)

    /** The platform as the gallery sees it: canvases, catalog, today's pick and the player, all in memory. */
    private class FakeServices(
        private val pictures: List<CanvasSummary> = emptyList(),
        private val photoTiles: List<CanvasSummary> = emptyList(),
        unlocked: Set<String> = emptySet(),
        private val new: Set<String> = emptySet(),
    ) : PlatformServices {
        val openedIds = mutableListOf<String>()
        val events = mutableListOf<AnalyticsEvent>()
        val renamed = mutableListOf<String>()
        private val stores = mutableMapOf<String, MemoryStore>()

        override fun keyValue(name: String): KeyValueStore = stores.getOrPut(name) { MemoryStore(name) }

        override val files: FileStore get() = error("not used")

        override val cache: FileStore get() = error("not used")

        override val photos get() = error("not used")

        override val faces get() = error("not used")

        override val reporter = NonFatalReporter { }

        override fun nowMillis(): Long = 0L

        override fun words(): Words = Words.load("en") { "<resources></resources>" }

        override val analytics = object : Analytics {
            override fun log(event: AnalyticsEvent) {
                events += event
            }
        }

        override val store get() = error("not used")

        override val play get() = error("not used")

        override val appScope get() = error("not used")

        override val canvases = object : Canvases {
            override suspend fun summaries() = pictures to photoTiles

            override suspend fun reminderCanvases(): List<ReminderCanvas> = emptyList()

            override fun pattern(ref: CanvasRef): Pattern? = null

            override suspend fun writeThumb(ref: CanvasRef, png: ByteArray) = Unit

            override suspend fun progress(ref: CanvasRef): CanvasProgress? = null

            override suspend fun createPhoto(ref: CanvasRef, pattern: Pattern, record: CanvasRecord, sourceJpeg: ByteArray, colors: ColorChoice) =
                error("not used")

            override suspend fun rename(ref: CanvasRef, title: String): WriteResult = WriteResult.Written.also { renamed += title }

            override suspend fun startOver(ref: CanvasRef) = true

            override suspend fun delete(ref: CanvasRef) = true
        }

        override val remote = object : RemoteShelf {
            override val updates: StateFlow<Int> = MutableStateFlow(0)

            override fun checkIfDue() = Unit

            override fun listed(): List<RemotePicture> = emptyList()

            override fun drops(): Set<String> = emptySet()

            override fun todaysDrops(): Set<String> = emptySet()

            override fun isNew(id: String) = id in new

            override fun tomorrowGrid(): ByteArray? = null

            override fun opened(id: String) {
                openedIds += id
            }
        }

        override val todays = object : TodaysPicks {
            override fun pick(started: Set<String>, vip: Boolean, listed: List<RemotePicture>, drops: Set<String>, isNew: (String) -> Boolean): String? = null

            override fun freeToday(): Set<String> = emptySet()

            override fun isFreeToday(ref: CanvasRef) = false

            override fun opened(ref: CanvasRef, vip: Boolean) = null
        }

        override val collections = object : CollectionBook {
            override suspend fun settle(pictures: List<CanvasSummary>) = Settled.NONE

            override fun glintShown(id: String) = Unit
        }

        override val streak = object : StreakSource {
            override val snapshot = MutableStateFlow(StreakSnapshot(StreakState(), 0L, Cover.NONE))

            override fun refresh() = snapshot.value

            override fun nudgeShown(day: Long) = false
        }

        override val access = object : PlayerAccess {
            override val entitlements: StateFlow<Entitlements> = MutableStateFlow(Entitlements.FREE)

            override val unlocked: StateFlow<Set<String>> = MutableStateFlow(unlocked)

            override val adsOn = true

            override val allowance: StateFlow<Allowance> = MutableStateFlow(Allowance.Free)

            override suspend fun consume(draftId: String) = error("not used")

            override suspend fun release(draftId: String) = error("not used")

            override suspend fun spend(item: Item, useId: String) = error("not used")
        }

        override val progress = object : PlayerProgress {
            override val finishes = 0

            override val landed = false

            override val nudgeOn = false

            override fun quickOpenable(started: List<ReminderCanvas>): List<String> = emptyList()
        }

        override fun progressSharing() = object : ProgressShareSession {
            override val sharingRef: CanvasRef? = null

            override fun share(ref: CanvasRef, pattern: Pattern, log: IntArray, title: String) = Unit

            override fun clear() = Unit
        }
    }
}
