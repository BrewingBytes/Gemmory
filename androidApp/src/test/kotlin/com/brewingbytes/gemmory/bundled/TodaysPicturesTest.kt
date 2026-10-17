package com.brewingbytes.gemmory.bundled

import com.brewingbytes.gemmory.ads.FakePrefs
import com.brewingbytes.gemmory.ads.PictureUnlocks
import com.brewingbytes.gemmory.policy.DayClock
import com.brewingbytes.gemmory.policy.StateStore
import com.brewingbytes.gemmory.policy.TodayState
import com.brewingbytes.gemmory.storage.CanvasKind
import com.brewingbytes.gemmory.storage.CanvasRef
import com.brewingbytes.gemmory.telemetry.Analytics
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** #34: the grant is decided when today's picture opens, so its first gem counts even after midnight. */
class TodaysPicturesTest {
    private val zone = TimeZone.of("Europe/Bucharest")
    private var now = at("2026-10-17T23:58")
    private val unlocks = PictureUnlocks(FakePrefs())
    private val events = mutableListOf<AnalyticsEvent>()
    private val store = object : StateStore<TodayState> {
        var saved: TodayState? = null

        override fun load() = saved

        override fun save(value: TodayState) {
            saved = value
        }
    }
    private val todays = TodaysPictures(DayClock({ now }) { zone }, store, { unlocks }, object : Analytics {
        override fun log(event: AnalyticsEvent) {
            events += event
        }
    })

    private fun at(local: String) = LocalDateTime.parse(local).toInstant(zone).toEpochMilliseconds()

    private fun ref(id: String) = CanvasRef(CanvasKind.BUNDLED, id)

    @Test
    fun aFirstGemAfterMidnightStillUnlocksTodaysPicture() {
        val id = assertNotNull(todays.pick(started = emptySet(), vip = false))
        val gem = assertNotNull(todays.opened(ref(id), vip = false), "opened at 23:58 as today's picture")

        now = at("2026-10-18T00:01")
        assertTrue(!todays.isFreeToday(ref(id)), "a new day: no longer free to open")
        gem.placed(104, 78)

        assertTrue(id in unlocks.unlocked.value, "the grant made at open still writes the unlock")
        assertEquals(listOf(AnalyticsEvent.todaysPictureStarted(ref(id), 104, 78, vip = false)), events)
    }

    @Test
    fun anotherPictureHasNoGrant() {
        val id = assertNotNull(todays.pick(started = emptySet(), vip = false))
        val other = BundledPictures.ALL.first { it.locked && it.id != id }.id
        assertNull(todays.opened(ref(other), vip = false))
        assertNull(todays.opened(CanvasRef(CanvasKind.PHOTO, id), vip = false), "a photo canvas is never today's")
    }

    @Test
    fun vipsFirstGemWritesNoUnlock() {
        val id = assertNotNull(todays.pick(started = emptySet(), vip = true))
        assertNotNull(todays.opened(ref(id), vip = true)).placed(78, 104)
        assertTrue(unlocks.unlocked.value.isEmpty())
        assertEquals("vip", events.single().params["tier"])
    }
}
