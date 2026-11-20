package com.brewingbytes.gemmory.play

import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import com.brewingbytes.gemmory.ads.PlayerAccess
import com.brewingbytes.gemmory.app.AppGraph
import com.brewingbytes.gemmory.app.PlatformServices
import com.brewingbytes.gemmory.bundled.BundledPictures
import com.brewingbytes.gemmory.bundled.FirstGem
import com.brewingbytes.gemmory.bundled.TodaysPicks
import com.brewingbytes.gemmory.converter.Lab
import com.brewingbytes.gemmory.converter.PaletteColor
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.copy.Words
import com.brewingbytes.gemmory.policy.Allowance
import com.brewingbytes.gemmory.policy.Entitlements
import com.brewingbytes.gemmory.policy.Item
import com.brewingbytes.gemmory.remote.RemotePicture
import com.brewingbytes.gemmory.save.GridCodec
import com.brewingbytes.gemmory.share.EncodeCanceller
import com.brewingbytes.gemmory.share.ProgressShareSession
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.storage.KeyValueStore
import com.brewingbytes.gemmory.storage.MemoryFileStore
import com.brewingbytes.gemmory.storage.MemoryStore
import com.brewingbytes.gemmory.telemetry.Analytics
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent
import com.brewingbytes.gemmory.telemetry.NonFatalReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The canvas's ViewModel in shared code (iOS Stage 1c, T64): it opens a canvas from the common [CanvasStore] (in
 * memory here) and tells the platform's services what happens on it. These paths draw nothing, so the test runs on
 * the JVM and on iOS; the screen itself stays in Android's connected tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayViewModelTest {
    private val main = UnconfinedTestDispatcher()
    private val pattern = Pattern(
        width = 4,
        height = 3,
        palette = listOf(
            PaletteColor(1, 0xFF102030.toInt(), Lab(12.0, 1.0, -9.0)),
            PaletteColor(2, 0xFFE0D0C0.toInt(), Lab(85.0, 1.5, 5.0)),
        ),
        cells = IntArray(12) { it % 2 },
    )
    private val starter = CanvasRef(CanvasKind.BUNDLED, BundledPictures.STARTER.id)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(main)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun theFirstGemTellsTodaysPictureTheFunnelAndDayPlayed() = runTest(main) {
        val services = FakeServices(this)
        services.toasts[starter] = "Shared with you: Starter"
        val vm = PlayViewModel(AppGraph(services), SavedStateHandle())
        vm.open(starter)
        assertEquals("Shared with you: Starter", vm.sharedToast, "the share link's toast, taken once")
        assertNull(services.toasts[starter])
        val session = ready(vm)

        place(session, 0)
        place(session, 1)

        assertEquals(listOf(4 to 3), services.firstGems, "the grant decided at open lands once, with the grid's size")
        assertEquals(listOf("first_gem", "day_played"), services.events.map { it.name }, "each once, on the first gem")
        assertEquals(2, services.gemsSeen)
        vm.close()
    }

    @Test
    fun finishingThePictureCountsTheStreakDayOnce() = runTest(main) {
        val services = FakeServices(this)
        val vm = PlayViewModel(AppGraph(services), SavedStateHandle())
        vm.open(starter)
        val session = ready(vm)

        for (cell in 0 until session.cellCount) place(session, cell)

        assertEquals(listOf("BUNDLED/starter"), services.finished)
        vm.close()
    }

    @Test
    fun aFillIsSpentThroughThePlayersAccessEvenAfterTheCanvasCloses() = runTest(main) {
        val services = FakeServices(this)
        val vm = PlayViewModel(AppGraph(services), SavedStateHandle())
        vm.open(starter)
        val session = ready(vm)
        var done = false

        session.spendFill("fill-1") { done = true }
        vm.close()
        advanceUntilIdle()

        assertEquals(listOf(Item.FILLS to "fill-1"), services.spent)
        assertEquals(listOf("inventory_spent"), services.events.map { it.name })
        assertTrue(done)
    }

    @Test
    fun aCanvasThatCantOpenIsDamaged() = runTest(main) {
        val services = FakeServices(this)
        val photo = CanvasRef(CanvasKind.PHOTO, "photo-1")
        services.files.mkdirs("canvases/photo/photo-1")
        services.files.writeAtomic("canvases/photo/photo-1/grid", byteArrayOf(1, 2, 3))
        val vm = PlayViewModel(AppGraph(services), SavedStateHandle())

        vm.open(photo)
        advanceUntilIdle()

        assertEquals(PlayUi.Damaged, vm.ui)
    }

    @Test
    fun aHintShownIsRememberedInTheHintsFile() = runTest(main) {
        val services = FakeServices(this)
        val vm = PlayViewModel(AppGraph(services), SavedStateHandle())

        vm.hints.onOpened(4f)

        assertEquals(CoachHint.ZOOM_IN, vm.hints.current)
        assertTrue(services.keyValue(CoachHints.FILE).getBoolean(CoachHint.ZOOM_IN.key, false))
    }

    private fun TestScope.place(session: PlaySession, cell: Int) {
        session.select(pattern.cells[cell])
        session.place(cell)
        Snapshot.sendApplyNotifications()
        advanceUntilIdle()
    }

    private fun TestScope.ready(vm: PlayViewModel): PlaySession {
        advanceUntilIdle()
        return assertIs<PlayUi.Ready>(vm.ui).session
    }

    /** The platform as an open canvas sees it: the store in memory, the player free, the hooks recorded. */
    private inner class FakeServices(scope: TestScope) : PlatformServices {
        val toasts = mutableMapOf<CanvasRef, String>()
        val events = mutableListOf<AnalyticsEvent>()
        val firstGems = mutableListOf<Pair<Int, Int>>()
        val spent = mutableListOf<Pair<Item, String>>()
        val finished = mutableListOf<String>()
        var gemsSeen = 0
        private var firstGemTold = false
        private var dayTold = false
        private val stores = mutableMapOf<String, MemoryStore>()
        private val words = Words.load("en") {
            val titles = BundledPictures.ALL.joinToString("") { "<string name=\"${it.titleKey}\">${it.id} title</string>" }
            // What the session announces as the picture fills (TalkBack, milestones #35), each as its own name: the
            // copy is checked on Android.
            val spoken = SPOKEN.joinToString("") { "<string name=\"$it\">$it</string>" } +
                SPOKEN_PLURALS.joinToString("") { "<plurals name=\"$it\"><item quantity=\"other\">$it</item></plurals>" }
            "<resources>$titles$spoken</resources>"
        }

        override fun keyValue(name: String): KeyValueStore = stores.getOrPut(name) { MemoryStore(name) }

        override val files = MemoryFileStore()

        override val cache get() = error("not used")

        override val photos get() = error("not used")

        override val faces get() = error("not used")

        override val reporter = NonFatalReporter { }

        override val appScope: CoroutineScope = scope.backgroundScope

        override fun nowMillis(): Long = 0L

        override fun words(): Words = words

        override val analytics = object : Analytics {
            override fun log(event: AnalyticsEvent) {
                events += event
            }
        }

        override val store = CanvasStore(
            files = files,
            scope = scope.backgroundScope,
            writer = UnconfinedTestDispatcher(scope.testScheduler),
            nowMillis = { 0L },
            bundledGrid = { GridCodec.encode(pattern) },
            encodes = object : EncodeCanceller {
                override suspend fun cancelAndClear(ref: CanvasRef) = Unit
            },
            reporter = NonFatalReporter { },
            words = { words },
            wallMillis = { 0L },
        )

        override val play = object : PlayHooks {
            override fun takeSharedToast(ref: CanvasRef): String? = toasts.remove(ref)

            override fun firstGem(nowMillis: Long): Long? {
                gemsSeen++
                return if (firstGemTold) null else 5L.also { firstGemTold = true }
            }

            override fun dayPlayed(): Boolean = !dayTold.also { dayTold = true }

            override fun finished(canvasKey: String) {
                finished += canvasKey
            }
        }

        override val remote get() = error("not used")

        override val todays = object : TodaysPicks {
            override fun pick(started: Set<String>, vip: Boolean, listed: List<RemotePicture>, drops: Set<String>, isNew: (String) -> Boolean) = null

            override fun freeToday(): Set<String> = emptySet()

            override fun isFreeToday(ref: CanvasRef) = true

            override fun opened(ref: CanvasRef, vip: Boolean) = FirstGem { width, height -> firstGems += width to height }
        }

        override val collections get() = error("not used")

        override val streak get() = error("not used")

        override val access = object : PlayerAccess {
            override val entitlements: StateFlow<Entitlements> = MutableStateFlow(Entitlements.FREE)

            override val unlocked: StateFlow<Set<String>> = MutableStateFlow(emptySet())

            override val adsOn = false

            override val allowance: StateFlow<Allowance> = MutableStateFlow(Allowance.Free)

            override suspend fun consume(draftId: String) = error("not used")

            override suspend fun release(draftId: String) = error("not used")

            override suspend fun spend(item: Item, useId: String): Boolean {
                spent += item to useId
                return true
            }
        }

        override val progress get() = error("not used")

        override fun progressSharing() = object : ProgressShareSession {
            override val sharingRef: CanvasRef? = null

            override fun share(ref: CanvasRef, pattern: Pattern, log: IntArray, title: String) = Unit

            override fun clear() = Unit
        }
    }

    private companion object {
        val SPOKEN = listOf(
            "milestone_quarter", "milestone_half", "milestone_three_quarters", "a11y_canvas_progress", "a11y_not_color",
            "a11y_left", "a11y_batch", "a11y_join", "a11y_with_milestone",
        )
        val SPOKEN_PLURALS = listOf("a11y_canvas_selected", "a11y_color_done", "a11y_color_done_next")
    }
}
