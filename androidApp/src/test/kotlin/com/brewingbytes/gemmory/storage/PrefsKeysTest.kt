package com.brewingbytes.gemmory.storage

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every key-value file and key the app writes, pinned (eng D7, T64): Android player data is never migrated, so a
 * renamed, removed or added key fails here until `goldens/storage/prefs.txt` says so on purpose. The code side is read
 * from the classes that own each file, in androidApp or the shared composeApp: their string constants (a `+` after one makes it a prefix) and the coach
 * hints' keys.
 */
class PrefsKeysTest {
    private val golden: Map<String, Set<String>> = File("../goldens/storage/prefs.txt").readLines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { it.split(' ') }
        .onEach { require(it.size == 3 && it[2] in TYPES) { "bad golden line: $it" } }
        .groupBy({ it[0] }, { it[1] })
        .mapValues { it.value.toSet() }

    private val sources = File("src/main/kotlin/com/brewingbytes/gemmory")

    /** The shared code's sources (T64): owners move here as their screens move to shared UI. */
    private val shared = File("../composeApp/src/commonMain/kotlin/com/brewingbytes/gemmory")

    /** [path] in whichever module owns it now. */
    private fun source(path: String): File = File(sources, path).takeIf { it.isFile } ?: File(shared, path)

    /** Which class keeps which files. */
    private val owners = mapOf(
        "collections/CollectionPrefs.kt" to listOf(StorageLayout.Prefs.COLLECTIONS),
        "ads/ConsentManager.kt" to listOf(StorageLayout.Prefs.CONSENT),
        "telemetry/FirstRun.kt" to listOf(StorageLayout.Prefs.FIRST_RUN, StorageLayout.Prefs.NEW_PLAYER),
        "play/CoachHints.kt" to listOf(StorageLayout.Prefs.HINTS),
        "telemetry/PrefsDayStore.kt" to listOf(StorageLayout.Prefs.PLAYED),
        "reminder/ReminderPrefs.kt" to listOf(StorageLayout.Prefs.REMINDER),
        "review/ReviewPrefs.kt" to listOf(StorageLayout.Prefs.REVIEW),
        "settings/AppSettings.kt" to listOf(StorageLayout.Prefs.SETTINGS),
        "play/PlayStreak.kt" to listOf(StorageLayout.Prefs.STREAK),
        "bundled/TodaysPictures.kt" to listOf(StorageLayout.Prefs.TODAY),
        "ads/Monetization.kt" to listOf(StorageLayout.Prefs.UNLOCKS),
    )

    @Test
    fun theGoldenListsEveryFileOfStorageLayout() {
        assertEquals(StorageLayout.Prefs.ALL.toSet(), golden.keys)
        assertEquals(StorageLayout.Prefs.ALL.toSet(), owners.values.flatten().toSet())
    }

    @Test
    fun everyKeyInTheCodeIsInTheGoldenAndBack() {
        for ((path, files) in owners) {
            val text = source(path).readText()
            val consts = CONST.findAll(text).map { it.groupValues[1] to it.groupValues[2] }.filter { it.first !in NOT_KEYS }.toList()
            val keys = consts.map { (name, value) -> if (Regex("""\b$name\s*\+""").containsMatchIn(text)) "$value*" else value }.toSet() +
                HINT.findAll(text).map { it.groupValues[1] }
            assertEquals(files.flatMap { golden.getValue(it) }.toSet(), keys, path)
        }
    }

    /** Each file is opened by its [StorageLayout.Prefs] name; the one literal is the consent SDK's own file, read only. */
    @Test
    fun noPrefsFileIsOpenedByAnotherName() {
        val opened = (sources.walkTopDown() + shared.walkTopDown()).filter { it.extension == "kt" }
            .flatMap { f -> OPEN.findAll(f.readText()).map { it.groupValues[1] } }
            .filter { it.startsWith("\"") }
            .toSet()
        assertEquals(setOf("\"\${app.packageName}_preferences\""), opened)
        assertTrue(StorageLayout.Prefs.ALL.all { it.matches(Regex("[a-z_]+")) })
    }

    private companion object {
        val TYPES = setOf("boolean", "int", "long", "string", "stringset")
        val CONST = Regex("""const val ([A-Z_0-9]+) = "([^"]*)"""")
        /** A coach hint's key, before its copy's string name (`ZOOM_IN("zoomIn", "hint_zoom_in")`). */
        val HINT = Regex("""^\s+[A-Z_]+\("(\w+)", "hint_""", RegexOption.MULTILINE)
        val OPEN = Regex("""getSharedPreferences\(([^,]+),""")

        /** String constants in those files that aren't keys. */
        val NOT_KEYS = setOf("TAG")
    }
}
