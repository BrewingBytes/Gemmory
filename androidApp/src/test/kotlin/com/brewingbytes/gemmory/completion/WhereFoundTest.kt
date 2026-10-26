package com.brewingbytes.gemmory.completion

import com.brewingbytes.gemmory.copy.XmlStrings
import com.brewingbytes.gemmory.policy.WhereFoundAnswer
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** "Where did you find Gemmory?" (T56, #157) on the completion screen: once, paid once, never for VIP, held 400ms. */
class WhereFoundTest {
    private val order = WhereFoundAnswer.order(1)

    private class Harness(scope: TestScope, private val paid: CompletableDeferred<Boolean>? = null) {
        val answers = ArrayList<WhereFoundAnswer>()
        val events = ArrayList<AnalyticsEvent>()
        var earns = 0
        val flow = WhereFoundFlow(
            scope = scope,
            // The funnel's record: true the first time only.
            answered = { answers.isEmpty().also { first -> if (first) answers += it } },
            earn = { earns++; paid?.await() ?: true },
            log = { events += it },
            now = { scope.currentTime },
        )
    }

    @Test
    fun aDoubleTapPaysAndLogsOnce() = runTest {
        val h = Harness(this)
        h.flow.ask(order, vip = false)
        h.flow.open()
        h.flow.answer(WhereFoundAnswer.FRIEND, reducedMotion = false)
        h.flow.answer(WhereFoundAnswer.VIDEO, reducedMotion = false)
        h.flow.answer(WhereFoundAnswer.SKIP, reducedMotion = false)
        advanceUntilIdle()
        assertEquals(1, h.earns)
        assertEquals(listOf("where_found", "inventory_earned"), h.events.map { it.name })
        assertEquals("friend", h.events.first().params["answer"])
        assertEquals(WhereFoundUi.Thanks(paid = true), h.flow.ui)
        assertFalse(h.flow.sheet)
    }

    @Test
    fun vipNeverEarnsAndHearsOnlyThanks() = runTest {
        val h = Harness(this)
        h.flow.ask(order, vip = true)
        h.flow.answer(WhereFoundAnswer.VIDEO, reducedMotion = false)
        advanceUntilIdle()
        assertEquals(0, h.earns)
        assertEquals(listOf("where_found"), h.events.map { it.name })
        assertEquals(WhereFoundUi.Thanks(paid = false), h.flow.ui)
    }

    @Test
    fun skipLogsSkipEarnsNothingAndRemovesTheRow() = runTest {
        val h = Harness(this)
        h.flow.ask(order, vip = false)
        h.flow.open()
        h.flow.answer(WhereFoundAnswer.SKIP, reducedMotion = false)
        advanceUntilIdle()
        assertEquals(0, h.earns)
        assertEquals(listOf(AnalyticsEvent.whereFound(WhereFoundAnswer.SKIP)), h.events)
        assertNull(h.flow.ui)
        assertFalse(h.flow.sheet)
        assertFalse(h.flow.answering, "focus goes back to Done")
    }

    @Test
    fun aDismissIsASkipThatLogsNothing() = runTest {
        val h = Harness(this)
        h.flow.ask(order, vip = false)
        h.flow.open()
        h.flow.dismiss()
        assertEquals(listOf(WhereFoundAnswer.SKIP), h.answers)
        assertTrue(h.events.isEmpty(), "leaving logs nothing")
        assertNull(h.flow.ui)
        h.flow.answer(WhereFoundAnswer.FRIEND, reducedMotion = false)
        h.flow.open()
        assertTrue(h.events.isEmpty())
        assertFalse(h.flow.sheet)
    }

    @Test
    fun theOutlineHoldsFor400msWhenTheRewardIsQuick() = runTest {
        val h = Harness(this)
        h.flow.ask(order, vip = false)
        h.flow.open()
        h.flow.answer(WhereFoundAnswer.COMMUNITY, reducedMotion = false)
        assertTrue(h.flow.answering, "focus will go to the thanks line")
        runCurrent()
        assertEquals(WhereFoundAnswer.COMMUNITY, h.flow.selected)
        advanceTimeBy(WhereFoundFlow.HOLD_MS - 1)
        assertTrue(h.flow.ui is WhereFoundUi.Row, "still holding")
        assertTrue(h.flow.sheet)
        advanceTimeBy(2)
        assertEquals(WhereFoundUi.Thanks(paid = true), h.flow.ui)
        assertNull(h.flow.selected)
        assertFalse(h.flow.sheet)
    }

    @Test
    fun aSlowRewardHoldsTheOutlineUntilItLands() = runTest {
        val paid = CompletableDeferred<Boolean>()
        val h = Harness(this, paid)
        h.flow.ask(order, vip = false)
        h.flow.answer(WhereFoundAnswer.PLAY_SEARCH, reducedMotion = false)
        advanceTimeBy(1_000)
        assertTrue(h.flow.ui is WhereFoundUi.Row, "the reward hasn't landed")
        assertEquals(WhereFoundAnswer.PLAY_SEARCH, h.flow.selected)
        paid.complete(false)
        runCurrent()
        assertEquals(WhereFoundUi.Thanks(paid = false), h.flow.ui, "a canvas that wasn't added isn't claimed")
    }

    @Test
    fun reducedMotionHasNoOutlineHold() = runTest {
        val h = Harness(this)
        h.flow.ask(order, vip = false)
        h.flow.open()
        h.flow.answer(WhereFoundAnswer.FRIEND, reducedMotion = true)
        assertNull(h.flow.selected)
        assertTrue(h.flow.answering)
        runCurrent()
        assertEquals(0, currentTime)
        assertEquals(WhereFoundUi.Thanks(paid = true), h.flow.ui)
    }

    @Test
    fun answersFitTheirCapsInEveryLanguage() {
        val folders = File("src/main/res").listFiles { f -> f.name.startsWith("values") && File(f, "strings.xml").exists() }!!
        assertEquals(12, folders.size, folders.joinToString { it.name })
        for (folder in folders) {
            val s = XmlStrings(folder.name).strings
            for (key in listOf("community", "friend", "play_search", "other")) {
                val text = s.getValue("where_found_$key")
                assertTrue(text.length <= 24, "${folder.name} $key is ${text.length} characters: $text")
            }
            val video = s.getValue("where_found_video")
            assertTrue(video.length <= 36, "${folder.name} video is ${video.length} characters: $video")
            listOf("TikTok", "Reels", "YouTube").forEach { assertTrue(it in video, "${folder.name} keeps $it in Latin: $video") }
        }
    }
}
