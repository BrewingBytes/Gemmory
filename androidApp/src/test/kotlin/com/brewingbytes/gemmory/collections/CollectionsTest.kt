package com.brewingbytes.gemmory.collections

import com.brewingbytes.gemmory.copy.XmlStrings
import com.brewingbytes.gemmory.ads.FakePrefs
import com.brewingbytes.gemmory.converter.Lab
import com.brewingbytes.gemmory.converter.PaletteColor
import com.brewingbytes.gemmory.converter.Pattern
import com.brewingbytes.gemmory.game.CanvasState
import com.brewingbytes.gemmory.play.CanvasSummary
import com.brewingbytes.gemmory.play.KeepResult
import com.brewingbytes.gemmory.remote.RemoteCatalog
import com.brewingbytes.gemmory.remote.RemoteCollection
import com.brewingbytes.gemmory.remote.RemotePicture
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.telemetry.Analytics
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Collections on the phone (T37): completion, the keepsake and the token, each once, resumable after a crash. Weekly
 * events (T38): 3 finishes in the window earn the exclusive, for VIP too; all 7 earn the token only.
 */
class CollectionsTest {
    /** The real English resources (T48): these lines are the copy, word for word. */
    private val copy = CollectionCopy(XmlStrings.en)

    private val pattern = Pattern(1, 2, listOf(PaletteColor(1, 0xFF336699.toInt(), Lab(40.0, 0.0, 0.0))), intArrayOf(0, 0))
    private val prefsStore = FakePrefs()
    private var catalog: RemoteCatalog? = null
    private var vip = false
    private val unlocked = mutableSetOf<String>()
    private val tokens = mutableSetOf<String>()
    private var keepResult = KeepResult.CREATED
    private val kept = mutableListOf<String>()
    private val events = mutableListOf<AnalyticsEvent>()
    private var now = DAY

    /** A fresh instance over the same stores, as after a process restart. */
    private fun collections() = Collections(
        prefs = CollectionPrefs(prefsStore),
        catalog = { catalog },
        vip = { vip },
        unlock = { unlocked += it },
        earnToken = { tokens.add(it) },
        // Copied once: after a CREATED, the canvas is there.
        keep = { id ->
            keepResult.also {
                if (it == KeepResult.CREATED) {
                    kept += id
                    keepResult = KeepResult.PRESENT
                }
            }
        },
        keptToday = {},
        today = { now },
        analytics = object : Analytics {
            override fun log(event: AnalyticsEvent) {
                events += event
            }
        },
    )

    private fun remote(id: String, rewardOf: String? = null, availableFrom: LocalDate? = null) = RemotePicture(
        id, id, com.brewingbytes.gemmory.converter.AspectRatio.SQUARE, com.brewingbytes.gemmory.converter.CanvasSize.QUICK, 1, 2, 1,
        "grids/$id.gmrg", "0".repeat(64), 0, true, 15, LocalDate(2026, 10, 20), availableFrom = availableFrom, rewardOf = rewardOf,
    )

    private val seaside = RemoteCollection("seaside", "Seaside", listOf("a", "b", "lemons"))
    private val keepsake = remote("harbour-lights", rewardOf = "seaside")

    private fun withCatalog(vararg extra: RemotePicture, collections: List<RemoteCollection> = listOf(seaside)) {
        catalog = RemoteCatalog(1, listOf(remote("a"), remote("b"), keepsake) + extra, collections)
    }

    private fun summary(
        id: String,
        finished: Boolean,
        kind: CanvasKind = if (id == "lemons") CanvasKind.BUNDLED else CanvasKind.REMOTE,
        finishedDay: Long? = if (finished) DAY else null,
    ) = CanvasSummary.Playable(
        CanvasRef(kind, id), id, if (finished) CanvasState.restore(pattern, intArrayOf(0, 1))!! else CanvasState.empty(pattern), 0L,
        firstFinishedDay = finishedDay,
    )

    private fun all(finished: Boolean = true) = listOf(summary("a", finished), summary("b", finished), summary("lemons", finished))

    @Test
    fun completingGrantsTheKeepsakeAndOneTokenOnce() = runBlocking {
        withCatalog()
        val c = collections()
        val first = c.settle(all()).statuses.single()
        assertTrue(first.complete)
        assertEquals(Keepsake.ADDED, first.keepsakeState)
        assertTrue(first.glintPending)
        assertEquals(setOf("harbour-lights"), unlocked)
        assertEquals(setOf("collection-seaside"), tokens)
        assertEquals(listOf("harbour-lights"), kept)
        assertEquals(1, events.count { it.name == "collection_completed" && it.params["collection_id"] == "seaside" })
        assertEquals(1, events.count { it.name == "inventory_earned" && it.params["source"] == "collection" })
        // Again, and after a restart: nothing more is given or logged.
        c.settle(all())
        collections().settle(all())
        assertEquals(1, events.count { it.name == "collection_completed" })
        assertEquals(1, events.count { it.name == "inventory_earned" })
        assertEquals(listOf("harbour-lights"), kept)
    }

    @Test
    fun vipEarnsThePictureOnlyAndNoTokenAfterALapse() = runBlocking {
        withCatalog()
        vip = true
        val status = collections().settle(all()).statuses.single()
        assertEquals(Keepsake.ADDED, status.keepsakeState)
        assertTrue(status.vipAtCompletion)
        assertTrue(tokens.isEmpty())
        vip = false
        collections().settle(all())
        assertTrue(tokens.isEmpty(), "completed with VIP: no token later")
    }

    @Test
    fun anUnfinishedMemberKeepsItOpenAndAFinishBeforeTheCollectionExistedCounts() = runBlocking {
        withCatalog(collections = emptyList())
        val c = collections()
        // Every member was finished before the collection was published (a bundled one too, maybe before T35).
        assertTrue(c.settle(all()).statuses.isEmpty())
        withCatalog()
        assertFalse(collections().settle(listOf(summary("a", true), summary("b", false), summary("lemons", true))).statuses.single().complete)
        assertTrue(collections().settle(all()).statuses.single().complete)
    }

    /** A bundled-only phone (never fetched a catalog) has no collections and never completes one. */
    @Test
    fun noCatalogMeansNoCollections() = runBlocking {
        catalog = null
        assertEquals(Settled.NONE, collections().settle(all()))
        assertTrue(tokens.isEmpty() && unlocked.isEmpty())
    }

    @Test
    fun aKeepsakeWhoseGridIsntHereIsPendingThenArrives() = runBlocking {
        withCatalog()
        keepResult = KeepResult.MISSING
        val c = collections()
        val pending = c.settle(all()).statuses.single()
        assertEquals(Keepsake.PENDING_OFFLINE, pending.keepsakeState)
        assertFalse(pending.glintPending, "the glint waits for the picture")
        assertEquals(setOf("collection-seaside"), tokens, "the token at once")
        keepResult = KeepResult.CREATED
        val arrived = c.settle(all()).statuses.single()
        assertEquals(Keepsake.ADDED, arrived.keepsakeState)
        assertTrue(arrived.glintPending)
        assertEquals(1, events.count { it.name == "collection_completed" })
    }

    @Test
    fun aFullPhoneSaysSoAndRetries() = runBlocking {
        withCatalog()
        keepResult = KeepResult.NO_SPACE
        assertEquals(Keepsake.PENDING_SPACE, collections().settle(all()).statuses.single().keepsakeState)
        keepResult = KeepResult.CREATED
        assertEquals(Keepsake.ADDED, collections().settle(all()).statuses.single().keepsakeState)
    }

    /** A crash after the completion was written, before the keepsake or token: the next settle finishes the job. */
    @Test
    fun aCrashBetweenStepsResumes() = runBlocking {
        withCatalog()
        CollectionPrefs(prefsStore).markComplete("seaside", DAY, vip = false)
        val status = collections().settle(all()).statuses.single()
        assertEquals(Keepsake.ADDED, status.keepsakeState)
        assertEquals(setOf("collection-seaside"), tokens)
        assertEquals(setOf("harbour-lights"), unlocked)
        assertEquals(0, events.count { it.name == "collection_completed" }, "logged once, before the crash")
    }

    /** D7: only the return from the finish that completed it is a reward return, and it's read once. */
    @Test
    fun theRewardReturnIsTheCompletingPicturesAndReadOnce() = runBlocking {
        withCatalog()
        val c = collections()
        c.settle(all())
        assertFalse(c.takeRewardReturn("not-a-member"))
        assertTrue(c.takeRewardReturn("b"))
        assertFalse(c.takeRewardReturn("b"))
        // Settling again (the gallery's refresh) doesn't make a new one.
        c.settle(all())
        assertFalse(c.takeRewardReturn("b"))
    }

    @Test
    fun theGlintPlaysOnce() = runBlocking {
        withCatalog()
        val c = collections()
        assertTrue(c.settle(all()).statuses.single().glintPending)
        c.glintShown("seaside")
        assertFalse(collections().settle(all()).statuses.single().glintPending)
    }

    @Test
    fun aCollectionWithoutAKeepsakeStillGivesItsToken() = runBlocking {
        catalog = RemoteCatalog(1, listOf(remote("a"), remote("b")), listOf(seaside))
        val status = collections().settle(all()).statuses.single()
        assertNull(status.keepsake)
        assertEquals(Keepsake.NONE, status.keepsakeState)
        assertTrue(status.glintPending)
        assertEquals(setOf("collection-seaside"), tokens)
        assertEquals("Complete. 1 unlock token added.", copy.line(status, vip = false))
    }

    @Test
    fun theRowLinesFollowTheDesignSystem() = runBlocking {
        withCatalog()
        // b isn't on the gallery yet (its grid isn't stored): it's coming, and counts.
        val open = collections().settle(listOf(summary("a", true), summary("lemons", false))).statuses.single()
        assertEquals(Triple(1, 3, 1), Triple(open.progress.finished, open.progress.size, open.progress.coming))
        assertEquals("Finish all 3 for a keepsake picture.", copy.line(open, vip = false))
        val done = collections().settle(all()).statuses.single()
        assertEquals("Keepsake added: harbour-lights. 1 unlock token added.", copy.line(done, vip = false))
        assertEquals("1 of 7 finished · 2 more coming", copy.count(1, 7, 2, largeFont = true))
        assertEquals("1 of 7 finished", copy.count(1, 7, 2, largeFont = false))
        assertEquals("+2", CollectionCopy.comingShort(2))
    }

    /** A week whose window is START..START + 6; today ([now]) is its 3rd day unless a test moves it. */
    private val week = RemoteCollection("golden-week", "Golden Week", (1..7).map { "w$it" }, event = true)
    private val exclusive = remote("golden-field", rewardOf = "golden-week")

    private fun withWeek() {
        catalog = RemoteCatalog(1, (1..7).map { remote("w$it", availableFrom = LocalDate.fromEpochDays(START + it - 1)) } + exclusive, listOf(week))
    }

    /** Members finished on [days] (null: finished before T35, no date); the rest shown and unfinished. */
    private fun weekPlayed(vararg days: Long?) =
        week.members.mapIndexed { i, id -> if (i < days.size) summary(id, true, finishedDay = days[i]) else summary(id, false) }

    @Test
    fun threeFinishedInTheWindowEarnTheExclusiveOnce() = runBlocking {
        withWeek()
        val c = collections()
        val two = c.settle(weekPlayed(START, START + 1)).statuses.single()
        assertTrue(two.event && two.inWindow && !two.earned)
        assertEquals(Keepsake.NONE, two.keepsakeState)
        assertTrue(unlocked.isEmpty())
        assertEquals("One new picture a day. Finish any 3 this week for a keepsake picture.", copy.line(two, vip = false))

        val three = c.settle(weekPlayed(START, START + 1, START + 2)).statuses.single()
        assertTrue(three.earned)
        assertFalse(three.complete)
        assertEquals(Keepsake.ADDED, three.keepsakeState)
        assertTrue(three.glintPending, "the glint plays at the 3rd")
        assertEquals(setOf("golden-field"), unlocked)
        assertEquals(listOf("golden-field"), kept)
        assertTrue(tokens.isEmpty(), "the token is for all 7")
        assertEquals("Keepsake earned: golden-field.", copy.line(three, vip = false))
        val logged = events.single { it.name == "event_reward_unlocked" }
        assertEquals(mapOf<String, Any>("event_id" to "golden-week", "finished_in_window" to 3L), logged.params)
        // D7: the return from the 3rd finish is a reward return, read once.
        assertTrue(c.takeRewardReturn("w3"))
        assertFalse(c.takeRewardReturn("w3"))
        // Again, and after a restart: nothing more.
        c.settle(weekPlayed(START, START + 1, START + 2, START + 3))
        collections().settle(weekPlayed(START, START + 1, START + 2, START + 3))
        assertEquals(1, events.count { it.name == "event_reward_unlocked" })
        assertEquals(listOf("golden-field"), kept)
    }

    /** CEO C3: VIP earns it the same way, and gets no token for all 7. */
    @Test
    fun vipEarnsTheExclusiveTheSameWay() = runBlocking {
        withWeek()
        vip = true
        assertFalse(collections().settle(weekPlayed(START, START + 1)).statuses.single().earned)
        assertTrue(unlocked.isEmpty(), "hidden until earned, for VIP too")
        assertTrue(collections().settle(weekPlayed(START, START + 1, START + 6)).statuses.single().earned)
        assertEquals(setOf("golden-field"), unlocked)
        now = START + 10
        val all = collections().settle(weekPlayed(START, START + 1, START + 6, START + 8, START + 8, START + 9, START + 10)).statuses.single()
        assertTrue(all.complete)
        assertTrue(tokens.isEmpty())
        assertEquals("Complete.", copy.line(all, vip = true))
    }

    /** The window ends on its 7th day: finishes after it don't count, and all 7 then earn the token only. */
    @Test
    fun afterTheWindowAllSevenGiveTheTokenAndNoExclusive() = runBlocking {
        withWeek()
        now = START + 7
        val after = collections().settle(weekPlayed(START, START + 1, START + 7)).statuses.single()
        assertFalse(after.inWindow || after.earned)
        assertNull(after.keepsake)
        assertEquals("Finish all 7 for an unlock token.", copy.line(after, vip = false))
        assertEquals("Finish all 7 to complete it.", copy.line(after, vip = true))
        val all = collections().settle(weekPlayed(START, START + 1, START + 7, START + 7, START + 7, START + 7, START + 7)).statuses.single()
        assertTrue(all.complete)
        assertTrue(all.glintPending, "completion plays the glint when no keepsake came")
        assertEquals(setOf("collection-golden-week"), tokens)
        assertTrue(unlocked.isEmpty(), "completing never grants the exclusive")
        assertEquals("Complete. 1 unlock token added.", copy.line(all, vip = false))
        assertEquals(0, events.count { it.name == "event_reward_unlocked" })
    }

    /** No double keepsake: completing an earned event gives the token, and the exclusive stays the one picture. */
    @Test
    fun completingAnEarnedEventGivesTheTokenOnly() = runBlocking {
        withWeek()
        collections().settle(weekPlayed(START, START + 1, START + 2))
        collections().glintShown("golden-week")
        now = START + 6
        val all = collections().settle(weekPlayed(START, START + 1, START + 2, START + 3, START + 4, START + 5, START + 6)).statuses.single()
        assertTrue(all.complete && all.earned)
        assertFalse(all.glintPending, "one glint an event")
        assertEquals(setOf("collection-golden-week"), tokens)
        assertEquals(listOf("golden-field"), kept)
        assertEquals(1, events.count { it.name == "collection_completed" })
        assertEquals("Complete. 1 unlock token added.", copy.line(all, vip = false))
    }

    /** Eng outside voice #15: a finish with no `play` date (before T35) doesn't count for an event. */
    @Test
    fun aFinishWithoutADateDoesNotEarnAnEvent() = runBlocking {
        withWeek()
        assertFalse(collections().settle(weekPlayed(START, START + 1, null)).statuses.single().earned)
        assertTrue(unlocked.isEmpty())
    }

    @Test
    fun anEarnedExclusiveWhoseGridIsntHereIsPending() = runBlocking {
        withWeek()
        keepResult = KeepResult.MISSING
        val pending = collections().settle(weekPlayed(START, START + 1, START + 2)).statuses.single()
        assertEquals(Keepsake.PENDING_OFFLINE, pending.keepsakeState)
        assertFalse(pending.glintPending, "the glint waits for the picture")
        assertEquals("Keepsake earned. It arrives next time you're online.", copy.line(pending, vip = false))
        assertEquals("4 more coming, one a day", copy.coming(4, oneADay = true))
        assertEquals("3 of 7 finished · 4 more coming, one a day", copy.count(3, 7, 4, largeFont = true, oneADay = true))
    }

    private companion object {
        const val DAY = 20_400L

        /** The week's first date: today is its 3rd day. */
        const val START = DAY - 2
    }
}
