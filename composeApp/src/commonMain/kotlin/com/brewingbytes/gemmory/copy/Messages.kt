package com.brewingbytes.gemmory.copy

import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.converter.ColorChoice
import com.brewingbytes.gemmory.policy.Awaiting
import com.brewingbytes.gemmory.policy.NudgeHint
import com.brewingbytes.gemmory.policy.NudgeName
import com.brewingbytes.gemmory.policy.ReminderMsg
import com.brewingbytes.gemmory.policy.TodayLabel
import com.brewingbytes.gemmory.remote.MysteryName
import com.brewingbytes.gemmory.timelapse.ProgressLine

/** A notification's title and text. */
data class Notice(val title: String, val text: String)

/**
 * Turns `commonMain`'s messages into the app's words (T48, eng E1). Every `when` is exhaustive, so a new message type
 * without its words doesn't compile. Shared since iOS Stage 1c (T64): it reads [Words] by name, and `MessagesTest`
 * resolves every name it uses in every language.
 */
object Messages {
    fun reminder(s: Words, msg: ReminderMsg): Notice = when (msg) {
        ReminderMsg.MysteryDrop -> Notice(mystery(s, MysteryName.TONIGHT), s.text("reminder_mystery_drop_text"))
        is ReminderMsg.Drop -> Notice(s.text("reminder_drop_title", msg.title.toString()), s.text("reminder_drop_text"))
        is ReminderMsg.Today -> {
            val title = if (msg.mystery) mystery(s, MysteryName.LATER) else msg.title.toString()
            val sized = msg.size?.let { s.text("reminder_today_sized", size(s, it)) } ?: s.text("reminder_today_unsized")
            if (msg.vip) {
                Notice(s.text("reminder_today_pick_title", title), s.text("reminder_today_pick_text", sized))
            } else {
                Notice(s.text("reminder_today_title", title), s.text("reminder_today_text", sized))
            }
        }
        is ReminderMsg.Streak -> Notice(
            s.text("reminder_streak_title", msg.days),
            msg.hint?.let { s.text("reminder_streak_text_hint", hint(s, it)) } ?: s.text("reminder_streak_text"),
        )
        is ReminderMsg.AlmostFinished -> {
            val what = when (val subject = msg.subject) {
                ReminderMsg.Subject.Photo -> s.text("reminder_almost_photo")
                ReminderMsg.Subject.Mystery -> s.text("reminder_almost_mystery")
                is ReminderMsg.Subject.Titled -> subject.title.toString()
            }
            Notice(s.text("reminder_almost_title", msg.percent, what), s.text("reminder_almost_text"))
        }
    }

    /** "Lighthouse is Quick", "Your photo canvas is 82% done". */
    fun hint(s: Words, hint: NudgeHint): String = when (hint) {
        is NudgeHint.Quick -> s.text("nudge_hint_quick", name(s, hint.name))
        is NudgeHint.Done -> s.text("nudge_hint_done", name(s, hint.name), hint.percent)
    }

    private fun name(s: Words, name: NudgeName): String = when (name) {
        NudgeName.Photo -> s.text("nudge_name_photo")
        NudgeName.Mystery -> s.text("nudge_name_mystery")
        NudgeName.Generic -> s.text("nudge_name_generic")
        is NudgeName.Titled -> name.title
        is NudgeName.TodayCard -> todayLabel(s, name.label)
    }

    /** "Today's picture" / VIP's "Today's pick". */
    fun todayLabel(s: Words, label: TodayLabel): String = when (label) {
        TodayLabel.PICTURE -> s.text("today_label_picture")
        TodayLabel.PICK -> s.text("today_label_pick")
    }

    /** "Finish a picture today to keep your 6 days". */
    fun nudgeLine(s: Words, days: Int): String = s.plural("nudge_line", days, days)

    /** "Yesterday is covered when you finish". */
    fun awaiting(s: Words, awaiting: Awaiting): String = when (awaiting) {
        Awaiting.Several -> s.text("nudge_awaiting_several")
        Awaiting.Yesterday -> s.text("nudge_awaiting_one", s.text("day_yesterday"))
        is Awaiting.Weekday -> s.text("nudge_awaiting_one", weekday(s, awaiting.day))
    }

    /** "Tuesday": an epoch day's weekday in the app's language. */
    fun weekday(s: Words, epochDay: Long): String = s.weekday(epochDay)

    /** The progress share's band line: "47% done" / "Just started". */
    fun progress(s: Words, line: ProgressLine): String = when (line) {
        ProgressLine.JustStarted -> s.text("progress_just_started")
        is ProgressLine.Done -> s.text("progress_done", line.percent)
    }

    fun mystery(s: Words, name: MysteryName): String = when (name) {
        MysteryName.TODAY -> s.text("mystery_today")
        MysteryName.TONIGHT -> s.text("mystery_tonight")
        MysteryName.LATER -> s.text("mystery_later")
    }

    /** "Quick" / "Medium" / "Big". */
    fun size(s: Words, size: CanvasSize): String = when (size) {
        CanvasSize.QUICK -> s.text("size_quick")
        CanvasSize.MEDIUM -> s.text("size_medium")
        CanvasSize.BIG -> s.text("size_big")
    }

    /** "Fewer" / "Standard" / "More". */
    fun colors(s: Words, choice: ColorChoice): String = when (choice) {
        ColorChoice.FEWER -> s.text("colors_fewer")
        ColorChoice.STANDARD -> s.text("colors_standard")
        ColorChoice.MORE -> s.text("colors_more")
    }

    /** The share sheet's text: "Made with Gemmory: <link>". */
    fun shareText(s: Words, url: String): String = s.text("share_text", url)
}
