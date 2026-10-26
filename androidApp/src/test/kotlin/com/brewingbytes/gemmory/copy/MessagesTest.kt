package com.brewingbytes.gemmory.copy

import com.brewingbytes.gemmory.R
import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.converter.ColorChoice
import com.brewingbytes.gemmory.policy.Awaiting
import com.brewingbytes.gemmory.policy.CoveredDay
import com.brewingbytes.gemmory.policy.DayKind
import com.brewingbytes.gemmory.policy.NudgeHint
import com.brewingbytes.gemmory.policy.NudgeName
import com.brewingbytes.gemmory.policy.NudgeTarget
import com.brewingbytes.gemmory.policy.ReminderCanvas
import com.brewingbytes.gemmory.policy.ReminderCopy
import com.brewingbytes.gemmory.policy.ReminderMsg
import com.brewingbytes.gemmory.policy.ReminderOutcome
import com.brewingbytes.gemmory.policy.ReminderReason
import com.brewingbytes.gemmory.policy.StreakNudge
import com.brewingbytes.gemmory.policy.TodayLabel
import com.brewingbytes.gemmory.remote.MysteryName
import com.brewingbytes.gemmory.timelapse.ProgressLine
import com.brewingbytes.gemmory.timelapse.ProgressShare
import com.brewingbytes.gemmory.timelapse.ShareLink
import com.brewingbytes.gemmory.ui.A11yText
import org.junit.Test
import java.io.File
import java.util.Locale
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * T48 (#135): `commonMain`'s messages turned into words. Every English line `commonMain` used to build is pinned here
 * word for word (the same inputs its tests used before T48), and every language folder's strings must take the same
 * arguments as English, so a translation can't drop or retype one (eng E1).
 */
class MessagesTest {
    private val s = XmlStrings.en

    private fun canvas(key: String, placed: Int, total: Int = 100, photo: Boolean = false) =
        ReminderCanvas(key, if (photo) null else key, placed, total, 1_000L)

    private fun reminder(post: ReminderOutcome.Post, title: String?, size: CanvasSize?, vip: Boolean, mystery: Boolean = false) =
        Messages.reminder(s, ReminderCopy.of(post, title, size, vip, mystery))

    @Test
    fun theRemindersReadAsBefore() {
        val drop = ReminderOutcome.Post(ReminderReason.DROP, pictureId = "lighthouse")
        assertEquals(Notice("Tonight: Lighthouse", "A new picture, free to start today."), reminder(drop, "Lighthouse", null, vip = false))
        val today = ReminderOutcome.Post(ReminderReason.TODAY, pictureId = "huts")
        assertEquals(Notice("Today's picture: Beach Huts", "A Quick picture, free to start today."), reminder(today, "Beach Huts", CanvasSize.QUICK, vip = false))
        assertEquals(Notice("Today's pick: Beach Huts", "A Quick picture, ready when you are."), reminder(today, "Beach Huts", CanvasSize.QUICK, vip = true))
        assertEquals(Notice("Today's picture: Beach Huts", "A picture, free to start today."), reminder(today, "Beach Huts", null, vip = false))
        val venice = ReminderOutcome.Post(ReminderReason.ALMOST_FINISHED, pictureId = "venice", canvas = canvas("venice", placed = 829, total = 1000))
        assertEquals(Notice("You're 82% through Venice", "Pick up where you left off."), reminder(venice, "Venice", null, vip = false))
    }

    @Test
    fun theStreakReminderNamesItsTargetAsBefore() {
        val ends = "Your 6-day streak ends at midnight"
        val quick = ReminderOutcome.Post(ReminderReason.STREAK, pictureId = "lighthouse", streak = 6)
        assertEquals(Notice(ends, "Finish any picture tonight to keep it. Lighthouse is Quick."), reminder(quick, "Lighthouse", CanvasSize.QUICK, vip = false))
        val harbour = ReminderOutcome.Post(ReminderReason.STREAK, pictureId = "harbour", canvas = canvas("harbour", placed = 829, total = 1000), streak = 6)
        assertEquals(Notice(ends, "Finish any picture tonight to keep it. Harbour is 82% done."), reminder(harbour, "Harbour", null, vip = false))
        assertEquals("Finish any picture tonight to keep it. Your mystery picture is 82% done.", reminder(harbour, "Lantern", null, vip = false, mystery = true).text)
        val photo = ReminderOutcome.Post(ReminderReason.STREAK, canvas = canvas("photo/abc", placed = 82, photo = true), streak = 6)
        assertEquals("Finish any picture tonight to keep it. Your photo canvas is 82% done.", reminder(photo, null, null, vip = false).text)
        assertEquals(Notice(ends, "Finish any picture tonight to keep it."), reminder(ReminderOutcome.Post(ReminderReason.STREAK, streak = 6), null, null, vip = false))
    }

    @Test
    fun aMysteryAndAPhotoAreNeverNamed() {
        val drop = ReminderOutcome.Post(ReminderReason.DROP, pictureId = "lantern")
        assertEquals(Notice("Tonight's mystery picture", "A new picture, hidden until you finish it."), reminder(drop, "Lantern", null, vip = false, mystery = true))
        val today = ReminderOutcome.Post(ReminderReason.TODAY, pictureId = "lantern")
        assertEquals("Today's picture: Mystery picture", reminder(today, "Lantern", CanvasSize.QUICK, vip = false, mystery = true).title)
        val almost = ReminderOutcome.Post(ReminderReason.ALMOST_FINISHED, pictureId = "lantern", canvas = canvas("lantern", placed = 829, total = 1000))
        assertEquals("You're 82% through your mystery picture", reminder(almost, "Lantern", null, vip = false, mystery = true).title)
        val photo = ReminderOutcome.Post(ReminderReason.ALMOST_FINISHED, canvas = canvas("photo/abc", placed = 82, photo = true))
        assertEquals("You're 82% through your photo canvas", reminder(photo, "Grandma's garden", null, vip = false).title)
    }

    @Test
    fun theNudgeReadsAsBefore() {
        assertEquals("Finish a picture today to keep your 6 days", Messages.nudgeLine(s, 6))
        fun hint(target: NudgeTarget, title: String?, mystery: Boolean = false, card: TodayLabel? = null) =
            Messages.hint(s, StreakNudge.hint(target, title, mystery, card)!!)
        assertEquals("Today's picture is Quick", hint(NudgeTarget.Today("lighthouse"), "Lighthouse", card = TodayLabel.PICTURE))
        assertEquals("Today's pick is Quick", hint(NudgeTarget.Today("lighthouse"), "Lighthouse", card = TodayLabel.PICK))
        assertEquals("Lighthouse is Quick", hint(NudgeTarget.Today("lighthouse"), "Lighthouse"))
        assertEquals("Beach Huts is Quick", hint(NudgeTarget.Quick("huts"), "Beach Huts"))
        assertEquals("Your picture is Quick", hint(NudgeTarget.Quick("huts"), null))
        assertEquals("Harbour is 82% done", hint(NudgeTarget.Canvas(canvas("harbour", placed = 829, total = 1000)), "Harbour"))
        assertEquals("Your photo canvas is 82% done", hint(NudgeTarget.Canvas(canvas("photo/x", placed = 82, photo = true)), "Grandma"))
        assertEquals("Your mystery picture is 40% done", hint(NudgeTarget.Canvas(canvas("lantern", placed = 40)), "Lantern", mystery = true))
        assertEquals("See your streak", s.text(R.string.nudge_broken_hint))
    }

    @Test
    fun theAwaitingLineNamesTheDayInTheAppsLanguage() {
        val today = LocalDate.of(2026, 10, 22).toEpochDay()
        fun awaiting(vararg days: CoveredDay) = StreakNudge.awaiting(days.toList(), today)?.let { Messages.awaiting(s, it) }
        assertEquals("Yesterday is covered when you finish", awaiting(CoveredDay(today - 1, DayKind.DAY_OFF)))
        assertEquals("Tuesday is covered when you finish", awaiting(CoveredDay(today - 2, DayKind.FREEZE)))
        assertEquals("Your missed days are covered when you finish", awaiting(CoveredDay(today - 2, DayKind.DAY_OFF), CoveredDay(today - 1, DayKind.FREEZE)))
    }

    @Test
    fun theProgressShareTheMysteryNamesAndTheShareText() {
        assertEquals("47% done", Messages.progress(s, ProgressShare.line(564, 1_200)))
        assertEquals("Just started", Messages.progress(s, ProgressShare.line(1, 1_200)))
        assertEquals(listOf("Today's mystery picture", "Tonight's mystery picture", "Mystery picture"), MysteryName.entries.map { Messages.mystery(s, it) })
        assertEquals(listOf("Quick", "Medium", "Big"), CanvasSize.entries.map { Messages.size(s, it) })
        assertEquals(listOf("Fewer", "Standard", "More"), ColorChoice.entries.map { Messages.colors(s, it) })
        assertEquals(
            "Made with Gemmory: https://play.google.com/store/apps/details?id=com.brewingbytes.gemmory" +
                "&referrer=utm_source%3Dshare%26utm_medium%3Dbundled%26utm_campaign%3Dtimelapse%26utm_content%3Dfox-in-snow",
            Messages.shareText(s, ShareLink.url(ShareLink.Medium.BUNDLED, pictureId = "fox-in-snow")),
        )
        assertEquals(
            "Made with Gemmory: https://play.google.com/store/apps/details?id=com.brewingbytes.gemmory" +
                "&referrer=utm_source%3Dshare%26utm_medium%3Dstreak%26utm_campaign%3Dstreak_card",
            Messages.shareText(s, ShareLink.streakUrl()),
        )
    }

    /** One of every message type resolves to words (the `when`s are exhaustive; this checks every string exists). */
    @Test
    fun everyMessageResolves() {
        val reminders = listOf(
            ReminderMsg.MysteryDrop,
            ReminderMsg.Drop("A"),
            ReminderMsg.Today("A", mystery = false, CanvasSize.BIG, vip = false),
            ReminderMsg.Today("A", mystery = true, null, vip = true),
            ReminderMsg.Streak(2, NudgeHint.Quick(NudgeName.Generic)),
            ReminderMsg.Streak(2, null),
            ReminderMsg.AlmostFinished(50, ReminderMsg.Subject.Photo),
            ReminderMsg.AlmostFinished(50, ReminderMsg.Subject.Mystery),
            ReminderMsg.AlmostFinished(50, ReminderMsg.Subject.Titled("A")),
        )
        reminders.forEach { val n = Messages.reminder(s, it); assertTrue(n.title.isNotBlank() && n.text.isNotBlank(), "$it") }
        val names = listOf(NudgeName.Photo, NudgeName.Mystery, NudgeName.Generic, NudgeName.Titled("A")) + TodayLabel.entries.map { NudgeName.TodayCard(it) }
        names.forEach { assertTrue(Messages.hint(s, NudgeHint.Done(it, 10)).isNotBlank()) }
        listOf(Awaiting.Several, Awaiting.Yesterday, Awaiting.Weekday(0)).forEach { assertTrue(Messages.awaiting(s, it).isNotBlank()) }
        listOf(ProgressLine.JustStarted, ProgressLine.Done(5)).forEach { assertTrue(Messages.progress(s, it).isNotBlank()) }
    }

    /** Every string resource the code names exists in English (a missing one would only fail at run time). */
    @Test
    fun everyResourceTheCodeNamesExists() {
        // Ids that aren't copy (games-ids.xml) live in other files of values/.
        val other = File("src/main/res/values").listFiles { f -> f.extension == "xml" && f.name != "strings.xml" }.orEmpty()
            .flatMap { XmlStrings.parse(it).getElementsByTagName("string").let { n -> (0 until n.length).map { i -> (n.item(i) as org.w3c.dom.Element).getAttribute("name") } } }
        for (field in R.string::class.java.fields) {
            val name = field.name
            if (name in s.strings || name in other) continue
            // Library resources share the R class; ours are the ones the app's code names.
            assertTrue(sources.none { "R.string.$name" in it }, "R.string.$name has no English string")
        }
        for (field in R.plurals::class.java.fields) {
            if (field.name in s.plurals) continue
            assertTrue(sources.none { "R.plurals.${field.name}" in it }, "R.plurals.${field.name} has no English plurals")
        }
    }

    /** Every English string is used: a leftover would be translated for nothing in T49. */
    @Test
    fun everyEnglishStringIsUsed() {
        val xml = listOf(File("src/main/AndroidManifest.xml")) + File("src/main/res/xml").listFiles().orEmpty()
        val text = sources + xml.map { it.readText() }
        val unused = s.strings.keys.filter { name -> text.none { "R.string.$name" in it || "@string/$name" in it } } +
            s.plurals.keys.filter { name -> text.none { "R.plurals.$name" in it } }
        assertEquals(emptyList(), unused, "unused strings")
    }

    /**
     * Every language folder's strings take the same arguments as English (eng E1: "a format argument missing in one
     * locale" fails the build), and every plural form within a language takes the same ones.
     */
    @Test
    fun everyLanguageTakesTheSameArguments() {
        val english = XmlStrings.en
        assertTrue(folders.any { it.name == "values" })
        for (folder in folders) {
            val lang = XmlStrings(folder.name)
            for ((name, text) in lang.strings) assertTrue(UNNUMBERED.find(text.replace("%%", "")) == null, "${folder.name}/$name: use %1\$s, not %s")
            for ((name, text) in lang.strings) {
                val base = english.strings[name]
                assertTrue(base != null, "${folder.name}/$name has no English string")
                assertEquals(args(base), args(text), "${folder.name}/$name")
            }
            for ((name, items) in lang.plurals) {
                val base = english.plurals[name]
                assertTrue(base != null, "${folder.name}/$name has no English plurals")
                val expected = args(base.getValue("other"))
                for ((quantity, text) in items) assertEquals(expected, args(text), "${folder.name}/$name[$quantity]")
            }
        }
        // Positional arguments only, so a translation can reorder them.
        for ((name, text) in english.strings) assertTrue(UNNUMBERED.find(text) == null, "$name: use %1\$s, not %s")
    }

    /**
     * T49: every language has every English string and plural, writes its own language tag in `app_locale`, and gives
     * exactly the plural quantities its language uses (CLDR), so no form is lost and none is dead.
     */
    @Test
    fun everyLanguageIsCompleteWithItsOwnPluralForms() {
        val english = XmlStrings.en
        val languages = folders.filter { it.name != "values" }
        assertEquals(TAGS.keys, languages.map { it.name }.toSet())
        for (folder in folders) {
            val lang = XmlStrings(folder.name)
            val tag = TAGS[folder.name] ?: "en"
            assertEquals(emptySet(), english.strings.keys - lang.strings.keys, "${folder.name}: missing strings")
            assertEquals(emptySet(), english.plurals.keys - lang.plurals.keys, "${folder.name}: missing plurals")
            assertEquals(tag, lang.strings["app_locale"], "${folder.name}/app_locale")
            val quantities = PluralRules.QUANTITIES.getValue(tag.substringBefore('-'))
            for ((name, items) in lang.plurals) assertEquals(quantities, items.keys, "${folder.name}/$name quantities")
        }
    }

    @Test
    fun pluralsArePickedByTheLanguagesRules() {
        val ro = XmlStrings("values-ro", Locale.forLanguageTag("ro"))
        assertEquals("1 zi", ro.plural(R.plurals.streak_days, 1, 1))
        assertEquals("5 zile", ro.plural(R.plurals.streak_days, 5, 5))
        assertEquals("20 de zile", ro.plural(R.plurals.streak_days, 20, 20))
        val pl = XmlStrings("values-pl", Locale.forLanguageTag("pl"))
        assertEquals("1 dzień", pl.plural(R.plurals.streak_days, 1, 1))
        assertEquals("3 dni", pl.plural(R.plurals.streak_days, 3, 3))
        assertEquals("12 dni", pl.plural(R.plurals.streak_days, 12, 12))
        val ja = XmlStrings("values-ja", Locale.JAPANESE)
        assertEquals("1日", ja.plural(R.plurals.streak_days, 1, 1))
    }

    /** #163: the counted words that were strings (lint's PluralsCandidate) follow the number. */
    @Test
    fun countedWordsArePlurals() {
        val en = XmlStrings("values", Locale.ENGLISH)
        assertEquals("8,112 gems · 1 color", en.plural(R.plurals.photo_summary, 1, "8,112", 1))
        assertEquals("8,112 gems · 24 colors", en.plural(R.plurals.photo_summary, 24, "8,112", 24))
        val pl = XmlStrings("values-pl", Locale.forLanguageTag("pl"))
        assertEquals("Obejrzyj film: +1 wypełnienie", pl.plural(R.plurals.inventory_watch, 1, 1))
        assertEquals("Obejrzyj film: +3 wypełnienia", pl.plural(R.plurals.inventory_watch, 3, 3))
        assertEquals("Obejrzyj film: +5 wypełnień", pl.plural(R.plurals.inventory_watch, 5, 5))
        assertEquals("Obejrzyj film: +22 wypełnienia", pl.plural(R.plurals.inventory_watch, 22, 22))
        assertEquals("Zostały 2.", pl.plural(R.plurals.streak_left, 2, 2))
        val ro = XmlStrings("values-ro", Locale.forLanguageTag("ro"))
        assertEquals("Mai, 1 culoare", ro.plural(R.plurals.photo_colors_trough, 1, "Mai", 1))
        assertEquals("Mai, 5 culori", ro.plural(R.plurals.photo_colors_trough, 5, "Mai", 5))
        assertEquals("Mai, 60 de culori", ro.plural(R.plurals.photo_colors_trough, 60, "Mai", 60))
        val bg = XmlStrings("values-bg", Locale.forLanguageTag("bg"))
        assertEquals("Остава 1.", bg.plural(R.plurals.streak_left, 1, 1))
        assertEquals("Остават 2.", bg.plural(R.plurals.streak_left, 2, 2))
    }

    /** #163 review: a collection heading's two numbers each pick their own words; Portuguese's "one" includes 0. */
    @Test
    fun collectionHeadingsAgreeWithEachNumber() {
        val es = A11yText(XmlStrings("values-es", Locale.forLanguageTag("es")))
        assertEquals("Mar, 1 de 7 terminado, 4 más en camino", es.collection("Mar", 1, 7, 4))
        assertEquals("Mar, 3 de 7 terminados", es.collection("Mar", 3, 7, 0))
        val de = A11yText(XmlStrings("values-de", Locale.GERMAN))
        assertEquals("See, 2 von 7 fertig, 1 weiteres kommt", de.collection("See", 2, 7, 1))
        val pt = XmlStrings("values-pt-rBR", Locale.forLanguageTag("pt-BR"))
        assertEquals("Mar, concluídos: 0 de 3", A11yText(pt).collection("Mar", 0, 3, 0))
        assertEquals("Concluídos: 0 de 3", pt.plural(R.plurals.collection_count, 0, 0, 3))
        assertEquals("Seaside, 0 of 3 finished, 1 more coming", A11yText(XmlStrings()).collection("Seaside", 0, 3, 1))
    }

    private val folders: List<File>
        get() = File("src/main/res").listFiles { f -> f.isDirectory && f.name.startsWith("values") && File(f, "strings.xml").isFile }.orEmpty().toList()

    private fun args(text: String): List<String> = ARG.findAll(text).map { it.value }.sorted().toList()

    companion object {
        private val ARG = Regex("""%\d+\$[sd]""")
        private val UNNUMBERED = Regex("""%[sd]""")

        /** The eleven language folders (T49) and the tag each writes in `app_locale`. */
        private val TAGS = mapOf(
            "values-de" to "de", "values-es" to "es", "values-pt-rBR" to "pt-BR", "values-fr" to "fr", "values-it" to "it",
            "values-ro" to "ro", "values-pl" to "pl", "values-tr" to "tr", "values-hu" to "hu", "values-bg" to "bg", "values-ja" to "ja",
        )

        private val sources: List<String> by lazy {
            listOf("src/main/kotlin", "src/debug/kotlin", "src/release/kotlin").flatMap { dir ->
                File(dir).walkTopDown().filter { it.extension == "kt" }.map { it.readText() }.toList()
            }
        }
    }
}
