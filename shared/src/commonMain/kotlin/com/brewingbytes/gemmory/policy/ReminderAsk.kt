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

/** A reminder's title and text (design system, Evening reminder, notification table; founder approved, Phase 5 D21). */
data class ReminderText(val title: String, val text: String)

/**
 * The notification copy for each reason. Names the picture and states only true facts: never "don't lose", "last
 * chance", "hurry", a countdown or an exclamation mark.
 */
object ReminderCopy {
    /**
     * [title]: the picture's title; [size]: Today's picture's size; [vip]: Today's pick wording. [mystery]: an
     * unfinished mystery picture (T43), never named: "Tonight's mystery picture" on its date, "Mystery picture" after.
     */
    fun of(post: ReminderOutcome.Post, title: String?, size: CanvasSize?, vip: Boolean, mystery: Boolean = false): ReminderText {
        if (mystery && post.reason == ReminderReason.DROP) return ReminderText(Mystery.TONIGHT, "A new picture, hidden until you finish it.")
        return named(post, if (mystery) Mystery.LATER else title, size, vip, mystery)
    }

    private fun named(post: ReminderOutcome.Post, title: String?, size: CanvasSize?, vip: Boolean, mystery: Boolean): ReminderText = when (post.reason) {
        ReminderReason.DROP -> ReminderText("Tonight: $title", "A new picture, free to start today.")
        ReminderReason.TODAY -> {
            val sized = size?.let { "A ${it.label} picture" } ?: "A picture"
            if (vip) {
                ReminderText("Today's pick: $title", "$sized, ready when you are.")
            } else {
                ReminderText("Today's picture: $title", "$sized, free to start today.")
            }
        }
        ReminderReason.STREAK -> ReminderText("${post.streak} days in a row", "Finish any picture today to keep it going.")
        ReminderReason.ALMOST_FINISHED -> {
            val canvas = post.canvas
            val percent = canvas?.percent ?: 0
            val what = when {
                canvas?.photo != false -> "your photo canvas"
                mystery -> "your mystery picture"
                else -> title
            }
            ReminderText("You're $percent% through $what", "Pick up where you left off.")
        }
    }
}
