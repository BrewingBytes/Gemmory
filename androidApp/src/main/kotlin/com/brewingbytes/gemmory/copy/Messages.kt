package com.brewingbytes.gemmory.copy

import com.brewingbytes.gemmory.R
import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.converter.ColorChoice
import com.brewingbytes.gemmory.policy.Awaiting
import com.brewingbytes.gemmory.policy.NudgeHint
import com.brewingbytes.gemmory.policy.NudgeName
import com.brewingbytes.gemmory.policy.ReminderMsg
import com.brewingbytes.gemmory.policy.TodayLabel
import com.brewingbytes.gemmory.remote.MysteryName
import com.brewingbytes.gemmory.timelapse.ProgressLine
import java.time.LocalDate
import java.time.format.TextStyle

/** A notification's title and text. */
data class Notice(val title: String, val text: String)

/**
 * Turns `commonMain`'s messages into the app's words (T48, eng E1). Every `when` is exhaustive, so a new message type
 * without its words doesn't compile.
 */
object Messages {
    fun reminder(s: Strings, msg: ReminderMsg): Notice = when (msg) {
        ReminderMsg.MysteryDrop -> Notice(mystery(s, MysteryName.TONIGHT), s.text(R.string.reminder_mystery_drop_text))
        is ReminderMsg.Drop -> Notice(s.text(R.string.reminder_drop_title, msg.title.toString()), s.text(R.string.reminder_drop_text))
        is ReminderMsg.Today -> {
            val title = if (msg.mystery) mystery(s, MysteryName.LATER) else msg.title.toString()
            val sized = msg.size?.let { s.text(R.string.reminder_today_sized, size(s, it)) } ?: s.text(R.string.reminder_today_unsized)
            if (msg.vip) {
                Notice(s.text(R.string.reminder_today_pick_title, title), s.text(R.string.reminder_today_pick_text, sized))
            } else {
                Notice(s.text(R.string.reminder_today_title, title), s.text(R.string.reminder_today_text, sized))
            }
        }
        is ReminderMsg.Streak -> Notice(
            s.text(R.string.reminder_streak_title, msg.days),
            msg.hint?.let { s.text(R.string.reminder_streak_text_hint, hint(s, it)) } ?: s.text(R.string.reminder_streak_text),
        )
        is ReminderMsg.AlmostFinished -> {
            val what = when (val subject = msg.subject) {
                ReminderMsg.Subject.Photo -> s.text(R.string.reminder_almost_photo)
                ReminderMsg.Subject.Mystery -> s.text(R.string.reminder_almost_mystery)
                is ReminderMsg.Subject.Titled -> subject.title.toString()
            }
            Notice(s.text(R.string.reminder_almost_title, msg.percent, what), s.text(R.string.reminder_almost_text))
        }
    }

    /** "Lighthouse is Quick", "Your photo canvas is 82% done". */
    fun hint(s: Strings, hint: NudgeHint): String = when (hint) {
        is NudgeHint.Quick -> s.text(R.string.nudge_hint_quick, name(s, hint.name))
        is NudgeHint.Done -> s.text(R.string.nudge_hint_done, name(s, hint.name), hint.percent)
    }

    private fun name(s: Strings, name: NudgeName): String = when (name) {
        NudgeName.Photo -> s.text(R.string.nudge_name_photo)
        NudgeName.Mystery -> s.text(R.string.nudge_name_mystery)
        NudgeName.Generic -> s.text(R.string.nudge_name_generic)
        is NudgeName.Titled -> name.title
        is NudgeName.TodayCard -> todayLabel(s, name.label)
    }

    /** "Today's picture" / VIP's "Today's pick". */
    fun todayLabel(s: Strings, label: TodayLabel): String = when (label) {
        TodayLabel.PICTURE -> s.text(R.string.today_label_picture)
        TodayLabel.PICK -> s.text(R.string.today_label_pick)
    }

    /** "Finish a picture today to keep your 6 days". */
    fun nudgeLine(s: Strings, days: Int): String = s.plural(R.plurals.nudge_line, days, days)

    /** "Yesterday is covered when you finish". */
    fun awaiting(s: Strings, awaiting: Awaiting): String = when (awaiting) {
        Awaiting.Several -> s.text(R.string.nudge_awaiting_several)
        Awaiting.Yesterday -> s.text(R.string.nudge_awaiting_one, s.text(R.string.day_yesterday))
        is Awaiting.Weekday -> s.text(R.string.nudge_awaiting_one, weekday(s, awaiting.day))
    }

    /** "Tuesday": an epoch day's weekday in the app's language. */
    fun weekday(s: Strings, epochDay: Long): String = LocalDate.ofEpochDay(epochDay).dayOfWeek.getDisplayName(TextStyle.FULL, s.locale)

    /** The progress share's band line: "47% done" / "Just started". */
    fun progress(s: Strings, line: ProgressLine): String = when (line) {
        ProgressLine.JustStarted -> s.text(R.string.progress_just_started)
        is ProgressLine.Done -> s.text(R.string.progress_done, line.percent)
    }

    fun mystery(s: Strings, name: MysteryName): String = when (name) {
        MysteryName.TODAY -> s.text(R.string.mystery_today)
        MysteryName.TONIGHT -> s.text(R.string.mystery_tonight)
        MysteryName.LATER -> s.text(R.string.mystery_later)
    }

    /** "Quick" / "Medium" / "Big". */
    fun size(s: Strings, size: CanvasSize): String = when (size) {
        CanvasSize.QUICK -> s.text(R.string.size_quick)
        CanvasSize.MEDIUM -> s.text(R.string.size_medium)
        CanvasSize.BIG -> s.text(R.string.size_big)
    }

    /** "Fewer" / "Standard" / "More". */
    fun colors(s: Strings, choice: ColorChoice): String = when (choice) {
        ColorChoice.FEWER -> s.text(R.string.colors_fewer)
        ColorChoice.STANDARD -> s.text(R.string.colors_standard)
        ColorChoice.MORE -> s.text(R.string.colors_more)
    }

    /** The share sheet's text: "Made with Gemmory: <link>". */
    fun shareText(s: Strings, url: String): String = s.text(R.string.share_text, url)
}
