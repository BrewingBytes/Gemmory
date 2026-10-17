package com.brewingbytes.gemmory.remote

import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.remote.RemoteSchedule.DAY_MILLIS
import com.brewingbytes.gemmory.remote.RemoteSchedule.RETRY_MILLIS
import com.brewingbytes.gemmory.save.Decoded
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteStateTest {
    private val t0 = 1_792_000_000_000L
    private val day1 = LocalDate(2026, 10, 18)
    private fun catalog(vararg ids: String) = RemoteCatalog(
        1,
        ids.map { RemoteFixtures.picture(it, RemoteFixtures.grid()) },
    )

    @Test
    fun checksOnceADayAndRetriesAFailureAfterAnHour() {
        val fresh = RemoteState()
        assertTrue(RemoteSchedule.isDue(fresh, t0))
        val ok = fresh.succeeded(t0, day1, "\"e1\"", catalog("a"))
        assertFalse(RemoteSchedule.isDue(ok, t0 + DAY_MILLIS - 1))
        assertTrue(RemoteSchedule.isDue(ok, t0 + DAY_MILLIS))
        // A failed check a day later: the next try is an hour on, then the day still counts from the last success.
        val failed = ok.attempted(t0 + DAY_MILLIS)
        assertFalse(RemoteSchedule.isDue(failed, t0 + DAY_MILLIS + RETRY_MILLIS - 1))
        assertTrue(RemoteSchedule.isDue(failed, t0 + DAY_MILLIS + RETRY_MILLIS))
        // A clock moved back before the last check doesn't block checks for a day.
        assertTrue(RemoteSchedule.isDue(ok, t0 - 1))
    }

    @Test
    fun theEtagIsSentOnlyWithItsCatalog() {
        val ok = RemoteState().succeeded(t0, day1, "\"e1\"", catalog("a"))
        assertEquals("\"e1\"", RemoteSchedule.ifNoneMatch(ok, hasCachedCatalog = true))
        assertNull(RemoteSchedule.ifNoneMatch(ok, hasCachedCatalog = false))
        assertNull(RemoteSchedule.ifNoneMatch(RemoteState(), hasCachedCatalog = true))
    }

    @Test
    fun newForSevenDaysUntilOpenedButNothingIsNewOnTheFirstSync() {
        val first = RemoteState().succeeded(t0, day1, null, catalog("a"))
        assertFalse(first.isNew("a", day1))
        val second = first.succeeded(t0 + DAY_MILLIS, day1.plus(1, DateTimeUnit.DAY), null, catalog("a", "b"))
        assertFalse(second.isNew("a", day1.plus(1, DateTimeUnit.DAY)))
        assertTrue(second.isNew("b", day1.plus(1, DateTimeUnit.DAY)))
        assertTrue(second.isNew("b", day1.plus(7, DateTimeUnit.DAY)))
        assertFalse(second.isNew("b", day1.plus(8, DateTimeUnit.DAY)))
        assertFalse(second.opened("b").isNew("b", day1.plus(2, DateTimeUnit.DAY)))
        // Withdrawn: forgotten, so a re-publish under the same id is new again.
        val third = second.opened("b").succeeded(t0 + 2 * DAY_MILLIS, day1.plus(2, DateTimeUnit.DAY), null, catalog("a"))
        assertEquals(setOf("a"), third.firstSeen.keys)
        assertEquals(emptySet(), third.opened)
        assertFalse(RemoteState().opened("z").opened.contains("z"))
    }

    @Test
    fun theStateRoundTripsAndGarbageIsRefused() {
        val state = RemoteState().succeeded(t0, day1, "W/\"abc\"", catalog("a")).succeeded(t0 + 5, day1.plus(1, DateTimeUnit.DAY), null, catalog("a", "b")).opened("b")
        assertEquals(state, assertIs<Decoded.Ok<RemoteState>>(RemoteStateCodec.decode(RemoteStateCodec.encode(state))).value)
        assertEquals(RemoteState(), assertIs<Decoded.Ok<RemoteState>>(RemoteStateCodec.decode(RemoteStateCodec.encode(RemoteState()))).value)
        val bytes = RemoteStateCodec.encode(state)
        assertIs<Decoded.Refused>(RemoteStateCodec.decode(bytes.copyOf(bytes.size - 1)))
    }

    @Test
    fun withdrawKeepsStartedPictures() {
        assertTrue(RemotePictureRules.shows(inCatalog = true, gridReady = true, started = false))
        assertFalse(RemotePictureRules.shows(inCatalog = true, gridReady = false, started = false))
        assertFalse(RemotePictureRules.shows(inCatalog = false, gridReady = true, started = false))
        assertTrue(RemotePictureRules.shows(inCatalog = false, gridReady = false, started = true))
        assertEquals(setOf("gone"), RemotePictureRules.withdrawn(setOf("a", "gone"), catalog("a", "b")))
    }

    @Test
    fun todaysPicturePrefersNewOnes() {
        assertEquals(listOf("b"), RemotePictureRules.todayCandidates(listOf("a", "b", "c")) { it == "b" })
        assertEquals(listOf("a", "c"), RemotePictureRules.todayCandidates(listOf("a", "c")) { it == "b" })
    }

    @Test
    fun remotePicturesJoinTheirSizeAfterTheBundledOnesOldestFirst() {
        data class P(val id: String, val size: CanvasSize?, val added: LocalDate? = null)
        val bundled = listOf(P("starter", null), P("q1", CanvasSize.QUICK), P("q2", CanvasSize.QUICK), P("m1", CanvasSize.MEDIUM), P("b1", CanvasSize.BIG))
        val remote = listOf(
            P("r-big-new", CanvasSize.BIG, day1.plus(3, DateTimeUnit.DAY)),
            P("r-quick", CanvasSize.QUICK, day1),
            P("r-big-old", CanvasSize.BIG, day1),
            P("r-big-withdrawn", CanvasSize.BIG, null),
            P("r-medium", CanvasSize.MEDIUM, day1),
        )
        val merged = RemotePictureRules.merge(bundled, remote, P::size, P::added, P::id).map { it.id }
        assertEquals(listOf("starter", "q1", "q2", "r-quick", "m1", "r-medium", "b1", "r-big-old", "r-big-new", "r-big-withdrawn"), merged)
        // A size with no bundled picture still shows, at the end.
        assertEquals(listOf("starter", "r-medium"), RemotePictureRules.merge(listOf(P("starter", null)), listOf(remote[4]), P::size, P::added, P::id).map { it.id })
    }
}
