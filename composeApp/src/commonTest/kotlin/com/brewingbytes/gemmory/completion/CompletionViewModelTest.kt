package com.brewingbytes.gemmory.completion

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.lifecycle.SavedStateHandle
import com.brewingbytes.gemmory.ads.PlayerAccess
import com.brewingbytes.gemmory.app.AppGraph
import com.brewingbytes.gemmory.app.PlatformServices
import com.brewingbytes.gemmory.bundled.BundledPictures
import com.brewingbytes.gemmory.converter.Lab
import com.brewingbytes.gemmory.converter.PaletteColor
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.copy.Words
import com.brewingbytes.gemmory.policy.Allowance
import com.brewingbytes.gemmory.policy.Entitlements
import com.brewingbytes.gemmory.policy.FirstRunAsk
import com.brewingbytes.gemmory.policy.InventoryState
import com.brewingbytes.gemmory.policy.Item
import com.brewingbytes.gemmory.policy.VipPlan
import com.brewingbytes.gemmory.policy.WhereFoundAnswer
import com.brewingbytes.gemmory.save.GridCodec
import com.brewingbytes.gemmory.share.EncodeCanceller
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.storage.KeyValueStore
import com.brewingbytes.gemmory.storage.MemoryFileStore
import com.brewingbytes.gemmory.storage.MemoryStore
import com.brewingbytes.gemmory.telemetry.Analytics
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent
import com.brewingbytes.gemmory.telemetry.NonFatalReporter
import com.brewingbytes.gemmory.play.CanvasStore
import com.brewingbytes.gemmory.timelapse.EncodeState
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The completion's ViewModel in shared code (iOS Stage 1c, T64): it loads the finished canvas from the common
 * [CanvasStore] (in memory here), shares it through the platform's [TimelapseShares] and asks T56 through its
 * [CompletionHooks]. Gems are "drawn" as sized stand-ins, so the test runs on the JVM and on iOS; the screen itself stays
 * in Android's connected tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CompletionViewModelTest {
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
    fun theFinishedPictureLoadsWithItsMetaLineAndTheTintGridAsBefore() = runTest(main) {
        val services = FakeServices(this)
        services.killed += starter
        val vm = CompletionViewModel(AppGraph(services), SavedStateHandle(), main)

        vm.load(starter)
        advanceUntilIdle()

        val finished = assertNotNull(vm.finished)
        assertEquals("starter title · 12 gems · 1 min", finished.meta)
        assertEquals(listOf(Triple(24, true, 4), Triple(24, false, 4)), services.renders, "the picture, then the empty tint grid as Before")
        assertEquals(4 * 24, finished.image.width)
        assertEquals(4f / 3f, finished.aspect)
        assertTrue(vm.killedNotice, "the encode that died with the process is told once")
        assertFalse(vm.damaged)
    }

    @Test
    fun shareEncodesTheLogWithTheWatermarkDecidedAtTheTap() = runTest(main) {
        val services = FakeServices(this)
        val vm = CompletionViewModel(AppGraph(services), SavedStateHandle(), main)
        vm.load(starter)
        advanceUntilIdle()

        vm.share(starter)
        services.entitlements.value = Entitlements(VipPlan.YEARLY)
        vm.share(starter)

        assertEquals(listOf(true, false), services.shared.map { it.second }, "VIP since the last tap: no watermark")
        assertEquals("starter title", services.shared.first().first)
        assertEquals(listOf("share_tapped", "share_tapped"), services.events.map { it.name })
        vm.shareHandled(starter)
        assertEquals(listOf(starter), services.acknowledged)
    }

    @Test
    fun aShareBeforeTheCanvasLoadedDoesNothing() = runTest(main) {
        val services = FakeServices(this)
        val vm = CompletionViewModel(AppGraph(services), SavedStateHandle(), main)

        vm.share(starter)

        assertTrue(services.shared.isEmpty())
        assertTrue(services.events.isEmpty())
    }

    @Test
    fun theFirstFinishAsksWhereTheyFoundGemmoryOncePerCanvas() = runTest(main) {
        val services = FakeServices(this)
        val vm = CompletionViewModel(AppGraph(services), SavedStateHandle(), main)

        vm.decideWhereFound(starter)
        vm.decideWhereFound(starter)

        assertEquals(listOf(true), services.asks, "decided once, as a first finish")
        val row = assertIs<WhereFoundUi.Row>(vm.whereFound.ui)
        assertEquals(ORDER, row.order)
        assertFalse(row.vip)
        assertTrue(vm.whereFoundOn(starter))
    }

    @Test
    fun anInstallWithAnEarlierFinishInTheStreakLogIsNotAFirstFinish() = runTest(main) {
        val services = FakeServices(this)
        services.finishDays += -5L
        val vm = CompletionViewModel(AppGraph(services), SavedStateHandle(), main)

        vm.decideWhereFound(starter)

        assertEquals(listOf(false), services.asks)
    }

    @Test
    fun nothingIsAskedOnceT56IsDecided() = runTest(main) {
        val services = FakeServices(this)
        services.due = false
        val vm = CompletionViewModel(AppGraph(services), SavedStateHandle(), main)

        vm.decideWhereFound(starter)

        assertTrue(services.asks.isEmpty())
        assertNull(vm.whereFound.ui)
    }

    @Test
    fun theRowAndItsThanksComeBackFromSavedState() = runTest(main) {
        val services = FakeServices(this)
        val handle = SavedStateHandle()
        val first = CompletionViewModel(AppGraph(services), handle)
        first.decideWhereFound(starter)

        val restored = CompletionViewModel(AppGraph(services), handle)
        assertEquals(first.whereFound.ui, restored.whereFound.ui)
        assertTrue(restored.whereFoundOn(starter))

        restored.whereFound.answer(WhereFoundAnswer.FRIEND, reducedMotion = true)
        advanceUntilIdle()
        assertEquals(WhereFoundUi.Thanks(paid = true), restored.whereFound.ui)
        assertEquals(listOf(CompletionViewModel.WHERE_FOUND_EARN_ID), services.earned)
        assertEquals(WhereFoundUi.Thanks(paid = true), CompletionViewModel(AppGraph(services), handle).whereFound.ui)

        restored.leftCompletion(starter)
        assertNull(CompletionViewModel(AppGraph(services), handle).whereFound.ui, "nothing once the completion was left")
    }

    @Test
    fun aNewFinishForgetsTheLightsDownAndTheLastRow() = runTest(main) {
        val services = FakeServices(this)
        val vm = CompletionViewModel(AppGraph(services), SavedStateHandle(), main)
        vm.decideWhereFound(starter)
        vm.markPlayed(starter)

        vm.onFinished(starter)

        assertFalse(vm.hasPlayed(starter))
        assertNull(vm.whereFound.ui)
        assertFalse(vm.whereFoundOn(starter))
        assertTrue(vm.fresh.isFresh(starter, routeFresh = false), "fresh across a rotation in this process")
        assertTrue(vm.fresh.left(starter, routeFresh = false))
        assertFalse(vm.fresh.isFresh(starter, routeFresh = false), "a still once left")
    }

    @Test
    fun playTimeRoundsUpToAMinute() {
        val words = Words.load("en") { WORDS }
        assertEquals("1 min", CompletionViewModel.playTime(words, 0))
        assertEquals("5 min", CompletionViewModel.playTime(words, 4 * 60_000 + 1))
        assertEquals("1 h 12 min", CompletionViewModel.playTime(words, 72 * 60_000L))
    }

    /** A drawn picture's size, without drawing: the stand-in for a platform bitmap. */
    private class SizedImage(override val width: Int, override val height: Int) : ImageBitmap {
        override val colorSpace: ColorSpace get() = ColorSpaces.Srgb
        override val hasAlpha: Boolean get() = true
        override val config: ImageBitmapConfig get() = ImageBitmapConfig.Argb8888

        override fun prepareToDraw() = Unit

        override fun readPixels(buffer: IntArray, startX: Int, startY: Int, width: Int, height: Int, bufferOffset: Int, stride: Int) = Unit
    }

    /** The platform as a completion sees it: the store in memory, the shares and T56's funnel recorded. */
    private inner class FakeServices(scope: TestScope) : PlatformServices {
        val events = mutableListOf<AnalyticsEvent>()
        val renders = mutableListOf<Triple<Int, Boolean, Int>>()
        val killed = mutableSetOf<CanvasRef>()
        val shared = mutableListOf<Pair<String, Boolean>>()
        val acknowledged = mutableListOf<CanvasRef>()
        val asks = mutableListOf<Boolean>()
        val earned = mutableListOf<String>()
        val finishDays = mutableListOf<Long>()
        val entitlements = MutableStateFlow(Entitlements.FREE)
        var due = true
        private var open = true
        private val stores = mutableMapOf<String, MemoryStore>()
        private val words = Words.load("en") {
            val titles = BundledPictures.ALL.joinToString("") { "<string name=\"${it.titleKey}\">${it.id} title</string>" }
            "<resources>$titles$WORDS_BODY</resources>"
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

        override val timelapses = object : TimelapseShares {
            override fun state(ref: CanvasRef): StateFlow<EncodeState> = MutableStateFlow(EncodeState.Idle)

            override fun share(ref: CanvasRef, pattern: Pattern, log: IntArray, title: String, watermark: Boolean) {
                shared += title to watermark
            }

            override fun cancel(ref: CanvasRef) = Unit

            override fun acknowledge(ref: CanvasRef) {
                acknowledged += ref
            }

            override suspend fun takeKilledNotice(ref: CanvasRef): Boolean = killed.remove(ref)
        }

        override val completion = object : CompletionHooks {
            override fun whereFoundDue(): Boolean = due

            override fun whereFoundOpen(): Boolean = open

            override fun whereFoundShown() = Unit

            override fun whereFoundAnswered(answer: WhereFoundAnswer): Boolean = open.also { open = false }

            override fun whereFoundAsk(firstFinish: Boolean): FirstRunAsk {
                asks += firstFinish
                return if (firstFinish) FirstRunAsk.Show(ORDER) else FirstRunAsk.No
            }

            override suspend fun earnCanvasOnce(earnId: String): Boolean = true.also { earned += earnId }

            override fun finishDays(): List<Long> = finishDays
        }

        override val play get() = error("not used")

        override val remote get() = error("not used")

        override val todays get() = error("not used")

        override val collections get() = error("not used")

        override val streak get() = error("not used")

        override val access = object : PlayerAccess {
            override val entitlements: StateFlow<Entitlements> = this@FakeServices.entitlements

            override val inventory: StateFlow<InventoryState> = MutableStateFlow(InventoryState())

            override val unlocked: StateFlow<Set<String>> = MutableStateFlow(emptySet())

            override val adsOn = false

            override val allowance: StateFlow<Allowance> = MutableStateFlow(Allowance.Free)

            override suspend fun consume(draftId: String) = error("not used")

            override suspend fun release(draftId: String) = error("not used")

            override suspend fun spend(item: Item, useId: String): Boolean = error("not used")
        }

        override val progress get() = error("not used")

        override fun progressSharing() = error("not used")

        override fun renderGems(pattern: Pattern, cellPx: Int, placed: ((Int) -> Boolean)?): ImageBitmap {
            renders += Triple(cellPx, placed == null, pattern.width)
            return SizedImage(pattern.width * cellPx, pattern.height * cellPx)
        }
    }

    private companion object {
        val ORDER = WhereFoundAnswer.entries.filter { it != WhereFoundAnswer.SKIP }

        /** The completion's copy, as in English. */
        const val WORDS_BODY = "<string name=\"completion_meta\">%1\$s · %2\$s gems · %3\$s</string>" +
            "<string name=\"play_time_minutes\">%1\$d min</string>" +
            "<string name=\"play_time_hours\">%1\$d h %2\$d min</string>"

        const val WORDS = "<resources>$WORDS_BODY</resources>"
    }
}
