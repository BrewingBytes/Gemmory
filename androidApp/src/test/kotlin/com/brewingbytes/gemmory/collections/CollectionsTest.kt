package com.brewingbytes.gemmory.collections

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

/** Collections on the phone (T37): completion, the keepsake and the token, each once, resumable after a crash. */
class CollectionsTest {
    private val pattern = Pattern(1, 2, listOf(PaletteColor(1, 0xFF336699.toInt(), Lab(40.0, 0.0, 0.0))), intArrayOf(0, 0))
    private val prefsStore = FakePrefs()
    private var catalog: RemoteCatalog? = null
    private var vip = false
    private val unlocked = mutableSetOf<String>()
    private val tokens = mutableSetOf<String>()
    private var keepResult = KeepResult.CREATED
    private val kept = mutableListOf<String>()
    private val events = mutableListOf<AnalyticsEvent>()

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
        today = { DAY },
        analytics = object : Analytics {
            override fun log(event: AnalyticsEvent) {
                events += event
            }
        },
    )

    private fun remote(id: String, rewardOf: String? = null) = RemotePicture(
        id, id, com.brewingbytes.gemmory.converter.AspectRatio.SQUARE, com.brewingbytes.gemmory.converter.CanvasSize.QUICK, 1, 2, 1,
        "grids/$id.gmrg", "0".repeat(64), 0, true, 15, LocalDate(2026, 10, 20), rewardOf = rewardOf,
    )

    private val seaside = RemoteCollection("seaside", "Seaside", listOf("a", "b", "lemons"))
    private val keepsake = remote("harbour-lights", rewardOf = "seaside")

    private fun withCatalog(vararg extra: RemotePicture, collections: List<RemoteCollection> = listOf(seaside)) {
        catalog = RemoteCatalog(1, listOf(remote("a"), remote("b"), keepsake) + extra, collections)
    }

    private fun summary(id: String, finished: Boolean, kind: CanvasKind = if (id == "lemons") CanvasKind.BUNDLED else CanvasKind.REMOTE) =
        CanvasSummary.Playable(CanvasRef(kind, id), id, if (finished) CanvasState.restore(pattern, intArrayOf(0, 1))!! else CanvasState.empty(pattern), 0L)

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
        assertEquals("Complete. 1 unlock token added.", CollectionCopy.line(status, vip = false))
    }

    @Test
    fun theRowLinesFollowTheDesignSystem() = runBlocking {
        withCatalog()
        // b isn't on the gallery yet (its grid isn't stored): it's coming, and counts.
        val open = collections().settle(listOf(summary("a", true), summary("lemons", false))).statuses.single()
        assertEquals(Triple(1, 3, 1), Triple(open.progress.finished, open.progress.size, open.progress.coming))
        assertEquals("Finish all 3 for a keepsake picture.", CollectionCopy.line(open, vip = false))
        val done = collections().settle(all()).statuses.single()
        assertEquals("Keepsake added: harbour-lights. 1 unlock token added.", CollectionCopy.line(done, vip = false))
        assertEquals("1 of 7 finished · 2 more coming", CollectionCopy.count(1, 7, 2, largeFont = true))
        assertEquals("1 of 7 finished", CollectionCopy.count(1, 7, 2, largeFont = false))
        assertEquals("+2", CollectionCopy.comingShort(2))
    }

    private companion object {
        const val DAY = 20_400L
    }
}
