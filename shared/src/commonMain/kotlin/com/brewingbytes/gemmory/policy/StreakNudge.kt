package com.brewingbytes.gemmory.policy

/** Where the keep-it-alive nudge (#102, G2/G3) and the streak reminder (G5) point. */
sealed interface NudgeTarget {
    /** The Analytics name of `streak_nudge_tapped`'s target. */
    val param: String

    /** Today's picture (or VIP's pick), Quick and unfinished. */
    data class Today(val pictureId: String) : NudgeTarget {
        override val param: String get() = "today"
    }

    /** The unfinished canvas closest to done, any age, a photo canvas included. */
    data class Canvas(val canvas: ReminderCanvas) : NudgeTarget {
        override val param: String get() = "canvas"
    }

    /** A Quick picture a tap opens now, when nothing is started. */
    data class Quick(val pictureId: String) : NudgeTarget {
        override val param: String get() = "quick"
    }
}

/**
 * The gallery's keep-it-alive nudge (#102, G2-G4): for a streak of [DailyStreak.SHOWN_FROM] days or more with nothing
 * finished today, a line that points at the quickest way to keep it. While a repair is offered, the broken variant
 * points at the streak screen instead. Copy states true facts only, no clocks (G7).
 */
object StreakNudge {
    /**
     * Today's picture when it's Quick and unfinished ([todaysPick] in [quick]), else the unfinished canvas with the
     * highest percent (the most recently played on a tie), else the first of [quick]. [quick]: the Quick pictures a tap
     * opens now without a video, unfinished, in catalog order. Null when there's nothing to point at.
     */
    fun target(todaysPick: String?, quick: List<String>, started: List<ReminderCanvas>): NudgeTarget? {
        if (todaysPick != null && todaysPick in quick) return NudgeTarget.Today(todaysPick)
        started.filter { !it.finished && it.placed > 0 }
            .maxWithOrNull(compareBy<ReminderCanvas> { it.percent }.thenBy { it.lastGemMillis ?: Long.MIN_VALUE })
            ?.let { return NudgeTarget.Canvas(it) }
        return quick.firstOrNull()?.let { NudgeTarget.Quick(it) }
    }

    /** The streak of 2 or more days stands today and nothing is finished yet: the nudge shows. */
    fun show(state: StreakState, today: Long, cover: Cover): Boolean =
        DailyStreak.shown(state, today, cover) >= DailyStreak.SHOWN_FROM && state.lastDay != today

    /** The broken variant (G4): a repair is offered, and it wins over a new streak's line. */
    fun broken(state: StreakState, today: Long, cover: Cover): BrokenStreak? = DailyStreak.repairOffer(state, today, cover)

    /**
     * The target as a fact: "Today's picture is Quick" ([todayCard], when the Today card next to it shows that
     * picture under that label: "Today's picture" or VIP's "Today's pick"; else, and in the reminder, it's named),
     * "Harbour is 82% done", "Your photo canvas is 82% done", "Your mystery picture is 82% done", "Beach Huts is
     * Quick". [title] is the picture's; [mystery] an unfinished mystery picture (T43), never named. Null without one.
     */
    fun hint(target: NudgeTarget?, title: String?, mystery: Boolean = false, todayCard: TodayLabel? = null): NudgeHint? = when (target) {
        null -> null
        is NudgeTarget.Today -> NudgeHint.Quick(todayCard?.let { NudgeName.TodayCard(it) } ?: name(title, mystery))
        is NudgeTarget.Quick -> NudgeHint.Quick(name(title, mystery))
        is NudgeTarget.Canvas -> NudgeHint.Done(if (target.canvas.photo) NudgeName.Photo else name(title, mystery), target.canvas.percent)
    }

    private fun name(title: String?, mystery: Boolean): NudgeName = when {
        mystery -> NudgeName.Mystery
        title != null -> NudgeName.Titled(title)
        else -> NudgeName.Generic
    }

    /**
     * The muted line under the nudge when a finish today covers missed days (G13), short form: "Yesterday is covered
     * when you finish", "Tuesday is covered when you finish", or for several days "Your missed days are covered when
     * you finish". Null when none is awaiting.
     */
    fun awaiting(days: List<CoveredDay>, today: Long): Awaiting? {
        val day = days.singleOrNull() ?: return Awaiting.Several.takeIf { days.isNotEmpty() }
        return if (day.day == today - 1) Awaiting.Yesterday else Awaiting.Weekday(day.day)
    }
}

/** The Today card's label: "Today's picture", or VIP's "Today's pick". */
enum class TodayLabel { PICTURE, PICK }

/** What the nudge and the streak reminder name (T48: the words are string resources). */
sealed interface NudgeName {
    /** "Your photo canvas". */
    data object Photo : NudgeName

    /** "Your mystery picture". */
    data object Mystery : NudgeName

    /** "Your picture": a target without a title. */
    data object Generic : NudgeName

    data class Titled(val title: String) : NudgeName

    /** The Today card's own label beside the nudge. */
    data class TodayCard(val label: TodayLabel) : NudgeName
}

/** The nudge's hint: "[name] is Quick" or "[name] is 82% done". */
sealed interface NudgeHint {
    data class Quick(val name: NudgeName) : NudgeHint

    data class Done(val name: NudgeName, val percent: Int) : NudgeHint
}

/** The muted "covered when you finish" line (G13). */
sealed interface Awaiting {
    /** "Your missed days are covered when you finish". */
    data object Several : Awaiting

    /** "Yesterday is covered when you finish". */
    data object Yesterday : Awaiting

    /** "Tuesday is covered when you finish": [day] is the epoch day, named in the app's language. */
    data class Weekday(val day: Long) : Awaiting
}
