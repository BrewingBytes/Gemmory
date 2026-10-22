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

    /** "Finish a picture today to keep your 6 days". */
    fun line(days: Int): String = "Finish a picture today to keep your $days days"

    /** The broken variant's hint (G4); its line is the celebration's "Your 12 days can still be restored". */
    const val BROKEN_HINT = "See your streak"

    /**
     * The target as a fact: "Today's picture is Quick" (in the gallery, next to its card; the reminder names it),
     * "Harbour is 82% done", "Your photo canvas is 82% done", "Your mystery picture is 82% done", "Beach Huts is
     * Quick". [title] is the picture's; [mystery] an unfinished mystery picture (T43), never named. Null without one.
     */
    fun hint(target: NudgeTarget?, title: String?, mystery: Boolean = false, inGallery: Boolean = true): String? = when (target) {
        null -> null
        is NudgeTarget.Today -> if (inGallery) "Today's picture is Quick" else "${name(title, mystery)} is Quick"
        is NudgeTarget.Quick -> "${name(title, mystery)} is Quick"
        is NudgeTarget.Canvas -> {
            val what = when {
                target.canvas.photo -> "Your photo canvas"
                else -> name(title, mystery)
            }
            "$what is ${target.canvas.percent}% done"
        }
    }

    private fun name(title: String?, mystery: Boolean): String = if (mystery) "Your mystery picture" else title ?: "Your picture"

    /**
     * The muted line under the nudge when a finish today covers missed days (G13), short form: "Yesterday is covered
     * when you finish", "Tuesday is covered when you finish" ([weekday] names it), or for several days "Your missed days
     * are covered when you finish". Null when none is awaiting.
     */
    fun awaiting(days: List<CoveredDay>, today: Long, weekday: (Long) -> String): String? {
        val day = days.singleOrNull() ?: return "Your missed days are covered when you finish".takeIf { days.isNotEmpty() }
        val name = if (day.day == today - 1) "Yesterday" else weekday(day.day)
        return "$name is covered when you finish"
    }
}
