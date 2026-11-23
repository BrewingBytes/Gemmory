package com.brewingbytes.gemmory.completion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.brewingbytes.gemmory.policy.EarnSource
import com.brewingbytes.gemmory.policy.Item
import com.brewingbytes.gemmory.policy.WhereFoundAnswer
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * "Where did you find Gemmory?" (T56) on one completion, after the first-run rule said to ask: the row, its sheet, the
 * tapped answer's outline and the thanks line (design D5, eng R3). [answered] is the funnel's once-only record (true the
 * first time), [earn] pays the photo canvas (false when it didn't, and never for VIP), [log] sends analytics.
 * [onChange] hears every change of [ui], so its [saved] form can outlive the process (a Share round-trip, #160 review).
 */
class WhereFoundFlow(
    private val scope: CoroutineScope,
    private val answered: (WhereFoundAnswer) -> Boolean,
    private val earn: suspend () -> Boolean,
    private val log: (AnalyticsEvent) -> Unit,
    private val now: () -> Long,
    private val onChange: () -> Unit = {},
) {
    /** The row, then the thanks line; null when not asked, or after Skip. */
    var ui by mutableStateOf<WhereFoundUi?>(null)
        private set

    private fun show(value: WhereFoundUi?) {
        ui = value
        onChange()
    }

    /** The answer sheet is open. */
    var sheet by mutableStateOf(false)
        private set

    /** The answer tapped, outlined while its reward lands (D5); never under reduced motion. */
    var selected by mutableStateOf<WhereFoundAnswer?>(null)
        private set

    /** An answer was tapped and its thanks line is on the way: where TalkBack focus goes when the sheet closes. */
    var answering by mutableStateOf(false)
        private set

    private var done = false
    private var landing: Job? = null

    /** Ask: the row, its answers in [order]; no reward line, and nothing earned, for [vip]. */
    fun ask(order: List<WhereFoundAnswer>, vip: Boolean) {
        show(WhereFoundUi.Row(order, vip))
    }

    fun open() {
        if (ui is WhereFoundUi.Row && !done) sheet = true
    }

    /**
     * An answer, or Skip, was tapped: counted and logged once (later taps do nothing). For an answer the reward is
     * added, and once it has and the outline has shown for [HOLD_MS] (not under [reducedMotion], D5), the row becomes
     * the thanks line.
     */
    fun answer(answer: WhereFoundAnswer, reducedMotion: Boolean) {
        val row = ui as? WhereFoundUi.Row ?: return
        if (done || !answered(answer)) return
        done = true
        log(AnalyticsEvent.whereFound(answer))
        if (answer == WhereFoundAnswer.SKIP) {
            sheet = false
            show(null)
            return
        }
        answering = true
        if (!reducedMotion) selected = answer
        landing = scope.launch {
            val start = now()
            val paid = !row.vip && earn()
            if (paid) log(AnalyticsEvent.inventoryEarned(Item.PHOTO_CANVAS, EarnSource.WHERE_FOUND, 1))
            if (!reducedMotion) delay((HOLD_MS - (now() - start)).coerceAtLeast(0))
            sheet = false
            selected = null
            show(WhereFoundUi.Thanks(paid))
        }
    }

    /** A canvas was just finished, or its completion was left: whatever this flow showed goes, and isn't saved. */
    fun reset() {
        landing?.cancel()
        landing = null
        show(null)
        sheet = false
        selected = null
        answering = false
        done = false
    }

    /** Back, a swipe or the scrim closed the sheet: a skip with no event; after an answer it only closes it. */
    fun dismiss() {
        sheet = false
        if (done || ui !is WhereFoundUi.Row) return
        if (answered(WhereFoundAnswer.SKIP)) {
            done = true
            show(null)
        }
    }

    /** [ui] in a form that survives the process: `row:<vip>:<answers>` or `thanks:<paid>`; null when nothing shows. */
    fun saved(): String? = when (val u = ui) {
        is WhereFoundUi.Row -> "row:${u.vip}:${u.order.joinToString(",") { it.name }}"
        is WhereFoundUi.Thanks -> "thanks:${u.paid}"
        null -> null
    }

    /**
     * Back after the process died (#160 review): [saved] shows again. A row comes back only while [open] says the
     * question is still unanswered (it may have been answered just before the process died); the thanks line always.
     */
    fun restore(saved: String, open: Boolean) {
        val parts = saved.split(':')
        val restored = when (parts.getOrNull(0)) {
            "row" -> parts.getOrNull(1)?.toBooleanStrictOrNull()?.let { vip ->
                val order = parts.getOrNull(2)?.split(',')?.mapNotNull { n -> WhereFoundAnswer.entries.firstOrNull { it.name == n } }
                order?.takeIf { open && it.size == WhereFoundAnswer.entries.size - 1 }?.let { WhereFoundUi.Row(it, vip) }
            }
            "thanks" -> parts.getOrNull(1)?.toBooleanStrictOrNull()?.let { WhereFoundUi.Thanks(it) }
            else -> null
        }
        done = restored is WhereFoundUi.Thanks
        show(restored)
    }

    companion object {
        /** The tapped answer's outline holds at least this long (design D5). */
        const val HOLD_MS = 400L
    }
}

/** T56 on the completion screen. */
sealed interface WhereFoundUi {
    /** The row under Done; [order] is the sheet's answers; no reward line for [vip]. */
    data class Row(val order: List<WhereFoundAnswer>, val vip: Boolean) : WhereFoundUi

    /** The thanks line; it names the photo canvas only when the reward was really added ([paid]). */
    data class Thanks(val paid: Boolean) : WhereFoundUi
}
