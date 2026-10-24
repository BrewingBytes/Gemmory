package com.brewingbytes.gemmory.policy

import com.brewingbytes.gemmory.converter.CanvasSize
import com.brewingbytes.gemmory.remote.Mystery

/** Where the reminder ask (T35) stands. */
enum class ReminderAskState {
    NOT_ASKED,

    /** "Not now" once: asked again after the 3rd finish. */
    NOT_NOW_ONCE,

    /** Never again: answered Yes, denied by the system, or "Not now" twice. */
    DONE,
}

/**
 * When the gallery shows the reminder ask (T35): after the 1st fresh finish counted since T35 arrived (founder,
 * 2026-10-20: a player who already had finishes is asked at their next one), and once more after the 3rd if the
 * first answer was "Not now". Never while reminders are on. An ask that can't show waits for the next finish.
 */
object ReminderAsk {
    const val FIRST_AT = 1
    const val SECOND_AT = 3

    /** The attempt (1 or 2) due after [finishes] counted fresh finishes, or null. */
    fun attemptDue(state: ReminderAskState, finishes: Int, remindersOn: Boolean): Int? = when {
        remindersOn -> null
        state == ReminderAskState.NOT_ASKED && finishes >= FIRST_AT -> 1
        state == ReminderAskState.NOT_NOW_ONCE && finishes >= SECOND_AT -> 2
        else -> null
    }

    fun afterNotNow(state: ReminderAskState): ReminderAskState =
        if (state == ReminderAskState.NOT_ASKED) ReminderAskState.NOT_NOW_ONCE else ReminderAskState.DONE
}

/**
 * A reminder's title and text (design system, Evening reminder, notification table; founder approved, Phase 5 D21), as
 * a message the app turns into its words (T48): "Tonight: Lighthouse" / "A new picture, free to start today.".
 */
sealed interface ReminderMsg {
    /** "Tonight's mystery picture" / "A new picture, hidden until you finish it.". */
    data object MysteryDrop : ReminderMsg

    /** "Tonight: [title]" / "A new picture, free to start today.". */
    data class Drop(val title: String?) : ReminderMsg

    /**
     * "Today's picture: [title]" / "A Quick picture, free to start today."; VIP: "Today's pick: [title]" / "…, ready
     * when you are.". [mystery]: an unfinished mystery picture, named "Mystery picture" instead of [title].
     */
    data class Today(val title: String?, val mystery: Boolean, val size: CanvasSize?, val vip: Boolean) : ReminderMsg

    /** "Your 6-day streak ends at midnight" / "Finish any picture tonight to keep it. Harbour is 82% done.". */
    data class Streak(val days: Int, val hint: NudgeHint?) : ReminderMsg

    /** "You're 82% through Harbour" / "Pick up where you left off.". */
    data class AlmostFinished(val percent: Int, val subject: Subject) : ReminderMsg

    /** What an almost-finished reminder names: "your photo canvas", "your mystery picture" or the title. */
    sealed interface Subject {
        data object Photo : Subject

        data object Mystery : Subject

        data class Titled(val title: String?) : Subject
    }
}

/**
 * The notification copy for each reason. Names the picture and states only true facts: never "don't lose", "last
 * chance", "hurry", a countdown or an exclamation mark.
 */
object ReminderCopy {
    /**
     * [title]: the picture's title; [size]: Today's picture's size; [vip]: Today's pick wording. [mystery]: an
     * unfinished mystery picture (T43), never named: "Tonight's mystery picture" on its date, "Mystery picture" after.
     */
    fun of(post: ReminderOutcome.Post, title: String?, size: CanvasSize?, vip: Boolean, mystery: Boolean = false): ReminderMsg = when (post.reason) {
        ReminderReason.DROP -> if (mystery) ReminderMsg.MysteryDrop else ReminderMsg.Drop(title)
        ReminderReason.TODAY -> ReminderMsg.Today(title, mystery, size, vip)
        ReminderReason.STREAK -> {
            // The nudge's target (#102, G5), named like the gallery does it; none without one.
            val target = when {
                post.canvas != null -> NudgeTarget.Canvas(post.canvas)
                post.pictureId != null -> NudgeTarget.Quick(post.pictureId)
                else -> null
            }
            ReminderMsg.Streak(post.streak, StreakNudge.hint(target, title, mystery))
        }
        ReminderReason.ALMOST_FINISHED -> {
            val canvas = post.canvas
            val subject = when {
                canvas?.photo != false -> ReminderMsg.Subject.Photo
                mystery -> ReminderMsg.Subject.Mystery
                else -> ReminderMsg.Subject.Titled(title)
            }
            ReminderMsg.AlmostFinished(canvas?.percent ?: 0, subject)
        }
    }
}
