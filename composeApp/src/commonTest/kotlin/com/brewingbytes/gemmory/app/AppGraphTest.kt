package com.brewingbytes.gemmory.app

import com.brewingbytes.gemmory.play.HighlightStyle
import com.brewingbytes.gemmory.storage.FileStore
import com.brewingbytes.gemmory.storage.KeyValueStore
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

        override val reporter = NonFatalReporter { }

        override fun nowMillis(): Long = now

        override fun timeZone(): TimeZone = zone
    }

    /** A [KeyValueStore] in memory, written at once. */
    private class MemoryStore(override val name: String) : KeyValueStore {
        private val values = mutableMapOf<String, Any>()

        override val keys: Set<String> get() = values.keys.toSet()

        override fun contains(key: String) = key in values

        override fun getBoolean(key: String, default: Boolean) = values[key] as? Boolean ?: default

        override fun getInt(key: String, default: Int) = values[key] as? Int ?: default

        override fun getLong(key: String, default: Long) = values[key] as? Long ?: default

        override fun getString(key: String, default: String?) = values[key] as? String ?: default

        @Suppress("UNCHECKED_CAST") // Only sets of strings are put under a key read this way.
        override fun getStringSet(key: String, default: Set<String>?) = values[key] as? Set<String> ?: default

        override fun edit(commit: Boolean, block: KeyValueStore.Editor.() -> Unit) {
            object : KeyValueStore.Editor {
                override fun putBoolean(key: String, value: Boolean) = apply { values[key] = value }

                override fun putInt(key: String, value: Int) = apply { values[key] = value }

                override fun putLong(key: String, value: Long) = apply { values[key] = value }

                override fun putString(key: String, value: String?) = apply { if (value == null) values.remove(key) else values[key] = value }

                override fun putStringSet(key: String, value: Set<String>?) = apply { if (value == null) values.remove(key) else values[key] = value.toSet() }

                override fun remove(key: String) = apply { values.remove(key) }

                override fun clear() = apply { values.clear() }
            }.block()
        }
    }

    private companion object {
        const val HOUR = 3_600_000L
    }
}
