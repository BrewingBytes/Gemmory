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
import org.junit.Test
import java.io.File
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
     * locale" fails the build), and every plural form within a language takes the same ones. Until T49 adds the
     * languages, English is checked against itself.
     */
    @Test
    fun everyLanguageTakesTheSameArguments() {
        val english = XmlStrings.en
        val folders = File("src/main/res").listFiles { f -> f.isDirectory && f.name.startsWith("values") && File(f, "strings.xml").isFile }.orEmpty()
        assertTrue(folders.any { it.name == "values" })
        for (folder in folders) {
            val lang = XmlStrings(folder.name)
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

    private fun args(text: String): List<String> = ARG.findAll(text).map { it.value }.sorted().toList()

    companion object {
        private val ARG = Regex("""%\d+\$[sd]""")
        private val UNNUMBERED = Regex("""%[sd]""")

        private val sources: List<String> by lazy {
            listOf("src/main/kotlin", "src/debug/kotlin", "src/release/kotlin").flatMap { dir ->
                File(dir).walkTopDown().filter { it.extension == "kt" }.map { it.readText() }.toList()
            }
        }
    }
}
