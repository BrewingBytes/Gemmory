package com.brewingbytes.gemmory.storage

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.brewingbytes.gemmory.policy.DayKind
import com.brewingbytes.gemmory.policy.HistoryDay
import com.brewingbytes.gemmory.policy.LedgerCodec
import com.brewingbytes.gemmory.policy.LedgerState
import com.brewingbytes.gemmory.policy.StreakHistoryCodec
import com.brewingbytes.gemmory.policy.StreakHistoryState
import com.brewingbytes.gemmory.save.CanvasRecord
import com.brewingbytes.gemmory.save.CanvasRecordCodec
import com.brewingbytes.gemmory.save.Decoded
import com.brewingbytes.gemmory.save.PlayFileCodec
import com.brewingbytes.gemmory.save.PlayTimes
import com.brewingbytes.gemmory.save.ViewState
import com.brewingbytes.gemmory.save.ViewStateCodec
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Android player data is never migrated (eng D7, T64): today's files, written as Android writes them, read back value
 * for value through the shared seams. The prefs are every line of `goldens/storage/prefs.txt`, written as the XML
 * SharedPreferences keeps on disk into device-protected storage (which the app never opens, so nothing is cached and
 * the store parses the file); the saves are a canvas's and the policies' files, written with `java.io.File` as before.
 */
@RunWith(AndroidJUnit4::class)
class KeyValueSeedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val seeded: Context = context.createDeviceProtectedStorageContext()
    private val root = File(context.cacheDir, "seed-test").also { it.deleteRecursively() }

    private data class Line(val file: String, val key: String, val type: String)

    private val golden: List<Line> = javaClass.classLoader!!.getResourceAsStream("storage/prefs.txt")!!.bufferedReader().readLines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { it.split(' ').let { (f, k, t) -> Line(f, k.removeSuffix("*") + if (k.endsWith("*")) "wave" else "", t) } }

    @After
    fun cleanUp() {
        StorageLayout.Prefs.ALL.forEach { seeded.deleteSharedPreferences(it) }
        root.deleteRecursively()
    }

    /** A value for each line that is no default: true, 7, a large long, a string with XML in it, two names. */
    private fun sample(line: Line, i: Int): Any = when (line.type) {
        "boolean" -> true
        "int" -> 7 + i
        "long" -> 1_731_600_000_000L + i
        "string" -> "v<$i> & \"${line.key}\""
        "stringset" -> setOf("wave", "sunrise")
        else -> error(line.type)
    }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    /** The XML SharedPreferences writes (`XmlUtils.writeMapXml`). */
    private fun xml(entries: List<Pair<Line, Any>>): String = buildString {
        append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n")
        for ((line, value) in entries) {
            val name = escape(line.key)
            when (value) {
                is Boolean -> append("    <boolean name=\"$name\" value=\"$value\" />\n")
                is Int -> append("    <int name=\"$name\" value=\"$value\" />\n")
                is Long -> append("    <long name=\"$name\" value=\"$value\" />\n")
                is String -> append("    <string name=\"$name\">${escape(value)}</string>\n")
                is Set<*> -> {
                    append("    <set name=\"$name\">\n")
                    value.forEach { append("        <string>${escape(it.toString())}</string>\n") }
                    append("    </set>\n")
                }
            }
        }
        append("</map>\n")
    }

    @Test
    fun everyPrefsKeyReadsBackThroughKeyValueStore() {
        val values = golden.mapIndexed { i, line -> line to sample(line, i) }
        val dir = File(seeded.dataDir, "shared_prefs").apply { mkdirs() }
        for ((file, entries) in values.groupBy { it.first.file }) File(dir, "$file.xml").writeText(xml(entries))

        for ((file, entries) in values.groupBy { it.first.file }) {
            val store: KeyValueStore = SharedPrefsStore.of(seeded, file)
            assertEquals(entries.map { it.first.key }.toSet(), store.keys, file)
            for ((line, value) in entries) {
                val read: Any? = when (line.type) {
                    "boolean" -> store.getBoolean(line.key, false)
                    "int" -> store.getInt(line.key, 0)
                    "long" -> store.getLong(line.key, 0)
                    "string" -> store.getString(line.key, null)
                    else -> store.getStringSet(line.key, null)
                }
                assertEquals(value, read, "$file ${line.key}")
            }
        }
        assertEquals(StorageLayout.Prefs.ALL.toSet(), values.map { it.first.file }.toSet())
    }

    @Test
    fun aWriteThroughTheStoreIsWhatSharedPreferencesReads() {
        val store = SharedPrefsStore.of(seeded, StorageLayout.Prefs.SETTINGS)
        store.edit(commit = true) { putBoolean("sound", false).putInt("music_volume", 40).putString("highlight", "gold") }
        val prefs = seeded.getSharedPreferences(StorageLayout.Prefs.SETTINGS, Context.MODE_PRIVATE)
        assertEquals(false, prefs.getBoolean("sound", true))
        assertEquals(40, prefs.getInt("music_volume", 0))
        assertEquals("gold", prefs.getString("highlight", null))
        store.edit(commit = true) { putString("highlight", null) }
        assertEquals(false, prefs.contains("highlight"))
    }

    @Test
    fun todaysSaveFilesReadBackThroughFileStore() {
        val canvas = "${StorageLayout.BUNDLED_CANVASES}/wave"
        val record = CanvasRecord("Wave", 1_731_600_000_000L, 754_000L)
        val view = ViewState(zoom = 2.5f, panX = -120f, panY = 48.5f, slot = 3)
        val times = PlayTimes(lastGemMillis = 1_731_600_123_000L, firstFinishedDay = 20_040)
        val ledger = LedgerState(day = 20_407, freeUsed = true, extrasEarned = 2)
        val history = StreakHistoryState(listOf(HistoryDay(20_405, DayKind.FINISHED, "bundled/wave"), HistoryDay(20_406, DayKind.FREEZE)))

        // As the app wrote them before T64.
        File(root, canvas).mkdirs()
        File(root, "$canvas/record").writeBytes(CanvasRecordCodec.encode(record))
        File(root, "$canvas/view").writeBytes(ViewStateCodec.encode(view))
        File(root, "$canvas/play").writeBytes(PlayFileCodec.encode(times))
        File(root, StorageLayout.LEDGER).mkdirs()
        File(root, StorageLayout.LEDGER_DAILY).writeBytes(LedgerCodec.encode(ledger))
        File(root, "streak").mkdirs()
        File(root, StorageLayout.STREAK_HISTORY).writeBytes(StreakHistoryCodec.encode(history))

        val store = AndroidFileStore(root)
        assertEquals(record, (CanvasRecordCodec.decode(store.read("$canvas/record")!!) as Decoded.Ok).value)
        assertEquals(view, ViewStateCodec.decode(store.read("$canvas/view"), paletteSize = 8))
        assertEquals(times, (PlayFileCodec.decode(store.read("$canvas/play")!!) as Decoded.Ok).value)
        assertEquals(ledger, FileStateStore(store, StorageLayout.LEDGER_DAILY, LedgerCodec::encode, LedgerCodec::decode).load())
        assertEquals(history, FileStateStore(store, StorageLayout.STREAK_HISTORY, StreakHistoryCodec::encode, StreakHistoryCodec::decode).load())
        assertEquals(listOf("play", "record", "view"), store.list(canvas))

        // And a write through the seam is byte for byte what the old code wrote.
        FileStateStore(store, StorageLayout.LEDGER_DAILY, LedgerCodec::encode, LedgerCodec::decode).save(ledger)
        assertContentEquals(LedgerCodec.encode(ledger), File(root, StorageLayout.LEDGER_DAILY).readBytes())
    }
}
