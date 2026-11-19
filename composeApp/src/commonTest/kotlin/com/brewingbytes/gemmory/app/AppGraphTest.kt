package com.brewingbytes.gemmory.app

import com.brewingbytes.gemmory.play.HighlightStyle
import com.brewingbytes.gemmory.storage.FileStore
import com.brewingbytes.gemmory.storage.KeyValueStore
import com.brewingbytes.gemmory.storage.MemoryStore
import com.brewingbytes.gemmory.storage.StorageLayout
import com.brewingbytes.gemmory.telemetry.NonFatalReporter
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame

/** The shared app's wiring (iOS eng D6, T64): what the graph builds from the platform's services, and where it keeps it. */
class AppGraphTest {
    @Test
    fun settingsLiveInTheSettingsFileUnderTodaysKeys() {
        val services = FakeServices()
        val settings = AppGraph(services).settings
        settings.setSound(false)
        settings.setHighlight(HighlightStyle.entries.last())

        assertEquals(listOf(StorageLayout.Prefs.SETTINGS), services.opened, "one file, opened once, by its Android name")
        val file = services.stores.getValue(StorageLayout.Prefs.SETTINGS)
        assertEquals(setOf("sound", "highlight"), file.keys)
        assertFalse(file.getBoolean("sound", true))
        assertFalse(AppGraph(services).settings.sound.value, "a new process reads it back")
    }

    @Test
    fun theDayClockFollowsThePlatformClockAndZone() {
        val services = FakeServices()
        val graph = AppGraph(services)
        val day = LocalDate(2026, 11, 15)
        services.now = day.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds() + 23 * HOUR
        assertEquals(day.toEpochDays(), graph.dayClock.today())
        services.zone = TimeZone.of("UTC+02:00")
        assertEquals(day.toEpochDays() + 1, graph.dayClock.today(), "a zone change turns the day at once")
    }

    @Test
    fun theReporterIsThePlatformsOwn() {
        val services = FakeServices()
        assertSame(services.reporter, AppGraph(services).reporter)
    }

    private class FakeServices : PlatformServices {
        val stores = mutableMapOf<String, MemoryStore>()
        val opened = mutableListOf<String>()
        var now = 0L
        var zone: TimeZone = TimeZone.UTC

        override fun keyValue(name: String): KeyValueStore = stores.getOrPut(name) { opened += name; MemoryStore(name) }

        override val files: FileStore get() = error("not used")

        override val cache: FileStore get() = error("not used")

        override val photos get() = error("not used")

        override val faces get() = error("not used")

        override val reporter = NonFatalReporter { }

        override fun nowMillis(): Long = now

        override fun timeZone(): TimeZone = zone

        override fun words() = error("not used")

        override val analytics get() = error("not used")

        override val store get() = error("not used")

        override val play get() = error("not used")

        override val appScope get() = error("not used")

        override val remote get() = error("not used")

        override val todays get() = error("not used")

        override val collections get() = error("not used")

        override val streak get() = error("not used")

        override val access get() = error("not used")

        override val progress get() = error("not used")

        override fun progressSharing() = error("not used")
    }

    private companion object {
        const val HOUR = 3_600_000L
    }
}
