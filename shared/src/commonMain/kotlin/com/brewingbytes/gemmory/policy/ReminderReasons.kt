package com.brewingbytes.gemmory.policy

/** Why the evening reminder (T35) speaks, by its Analytics name. */
enum class ReminderReason(val param: String) {
    DROP("drop"),
    TODAY("today"),
    STREAK("streak"),
    ALMOST_FINISHED("almost_finished"),
}

/**
 * A started canvas as the reminder sees it (eng D12: its save and play file, nothing rendered). [key] is how the app
 * finds it again; [pictureId] is the bundled or remote picture's id, null for a photo canvas.
 */
data class ReminderCanvas(
    val key: String,
    val pictureId: String?,
    val placed: Int,
    val total: Int,
    val lastGemMillis: Long?,
) {
    val finished: Boolean get() = placed >= total
    val photo: Boolean get() = pictureId == null

    /** Rounded down, so the title never says 100% before the finish. */
    val percent: Int get() = if (total == 0) 0 else (placed.toLong() * 100 / total).toInt()
}

/** What the evening's run found, read once (eng D3, D12). Days are local epoch days. */
data class ReminderInputs(
    /** When the alarm was set for, and the local date that time falls on. */
    val scheduledMillis: Long,
    val scheduledDay: Long,
    val nowMillis: Long,
    val today: Long,
    /** The app is on screen. */
    val foreground: Boolean,
    /** Remote pictures whose drop date is today, stored and verified (T36), in catalog order. */
    val todaysDrops: List<String>,
    /** Today's picture (or VIP's pick), as stored by the shared pick (eng D3). */
    val todaysPick: String?,
    /** Every canvas with at least one gem placed. */
    val started: List<ReminderCanvas>,
    val streak: StreakState,
)

/** The run's result. */
sealed interface ReminderOutcome {
    /**
     * Post a reminder for [reason]: [pictureId] for a drop or Today's picture; [canvas] for almost finished, and for a
     * streak the most recently played unfinished canvas, if any (the tap's target); [streak] the days in a row.
     */
    data class Post(
        val reason: ReminderReason,
        val pictureId: String? = null,
        val canvas: ReminderCanvas? = null,
        val streak: Int = 0,
    ) : ReminderOutcome

    /** The run came too late for its evening; nothing is posted (`reminder_late`). */
    data class Late(val minutes: Long) : ReminderOutcome

    /** Nothing real to say tonight, or the player is already playing ([skipped]). */
    data class Quiet(val skipped: Boolean) : ReminderOutcome
}

/**
 * Picks tonight's reminder (T35): the first reason that applies wins, in [RULES] order: tonight's drop, Today's
 * picture, a streak that really breaks at midnight, a canvas almost finished. Nothing when none applies. Copy states
 * only true facts (design system, Evening reminder).
 *
 * Skipped while the app is in the foreground or a gem was placed in the [SKIP_AFTER_GEM_MS] before the run: the
 * player is playing already. A skip leaves only the streak, since that one is still true until something is finished.
 */
object ReminderReasons {
    /** A run more than this after the chosen time posts nothing. */
    const val LATE_AFTER_MS = 2 * 60 * 60 * 1000L
    const val SKIP_AFTER_GEM_MS = 3 * 60 * 60 * 1000L

    /** "Almost finished" is a canvas played in this window. */
    const val ALMOST_PLAYED_WITHIN_MS = 7 * 24 * 60 * 60 * 1000L

    private class Run(val inputs: ReminderInputs, val skipped: Boolean) {
        val startedIds: Set<String> = inputs.started.mapNotNullTo(HashSet()) { it.pictureId }

        /** The unfinished canvases, most recently played first. */
        val unfinished: List<ReminderCanvas> = inputs.started.filter { !it.finished && it.lastGemMillis != null }
            .sortedByDescending { it.lastGemMillis }
    }

    private val drop: (Run) -> ReminderOutcome.Post? = { run ->
        run.inputs.todaysDrops.firstOrNull { it !in run.startedIds }
            ?.takeIf { !run.skipped }
            ?.let { ReminderOutcome.Post(ReminderReason.DROP, pictureId = it) }
    }

    private val today: (Run) -> ReminderOutcome.Post? = { run ->
        run.inputs.todaysPick?.takeIf { !run.skipped && it !in run.startedIds }
            ?.let { ReminderOutcome.Post(ReminderReason.TODAY, pictureId = it) }
    }

    private val streak: (Run) -> ReminderOutcome.Post? = { run ->
        val state = run.inputs.streak
        val days = DailyStreak.shown(state, run.inputs.today)
        val breaksAtMidnight = DailyStreak.shown(state, run.inputs.today + 1) == 0
        if (days >= DailyStreak.SHOWN_FROM && state.lastDay != run.inputs.today && breaksAtMidnight) {
            ReminderOutcome.Post(ReminderReason.STREAK, canvas = run.unfinished.firstOrNull(), streak = days)
        } else {
            null
        }
    }

    private val almostFinished: (Run) -> ReminderOutcome.Post? = { run ->
        run.unfinished.takeIf { !run.skipped }
            ?.firstOrNull { it.placed.toLong() * 2 > it.total && run.inputs.nowMillis - it.lastGemMillis!! <= ALMOST_PLAYED_WITHIN_MS }
            ?.let { ReminderOutcome.Post(ReminderReason.ALMOST_FINISHED, pictureId = it.pictureId, canvas = it) }
    }

    /** In priority order. */
    private val RULES = listOf(drop, today, streak, almostFinished)

    /**
     * Minutes late when a run at [nowMillis] (local date [today]) is too late for its evening: more than
     * [LATE_AFTER_MS] after [scheduledMillis], or past its local date [scheduledDay]. Null when it's on time.
     */
    fun lateMinutes(scheduledMillis: Long, scheduledDay: Long, nowMillis: Long, today: Long): Long? {
        val lateBy = nowMillis - scheduledMillis
        return if (lateBy > LATE_AFTER_MS || today != scheduledDay) lateBy.coerceAtLeast(0) / 60_000 else null
    }

    fun decide(inputs: ReminderInputs): ReminderOutcome {
        lateMinutes(inputs.scheduledMillis, inputs.scheduledDay, inputs.nowMillis, inputs.today)?.let { return ReminderOutcome.Late(it) }
        val skipped = inputs.foreground ||
            inputs.started.any { it.lastGemMillis != null && inputs.nowMillis - it.lastGemMillis in 0..SKIP_AFTER_GEM_MS }
        val run = Run(inputs, skipped)
        return RULES.firstNotNullOfOrNull { it(run) } ?: ReminderOutcome.Quiet(skipped)
    }
}
