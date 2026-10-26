package com.brewingbytes.gemmory.completion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.brewingbytes.gemmory.policy.EarnSource
import com.brewingbytes.gemmory.policy.Item
import com.brewingbytes.gemmory.policy.WhereFoundAnswer
import com.brewingbytes.gemmory.telemetry.AnalyticsEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * "Where did you find Gemmory?" (T56) on one completion, after the first-run rule said to ask: the row, its sheet, the
 * tapped answer's outline and the thanks line (design D5, eng R3). [answered] is the funnel's once-only record (true the
 * first time), [earn] pays the photo canvas (false when it didn't, and never for VIP), [log] sends analytics.
 */
class WhereFoundFlow(
    private val scope: CoroutineScope,
    private val answered: (WhereFoundAnswer) -> Boolean,
    private val earn: suspend () -> Boolean,
    private val log: (AnalyticsEvent) -> Unit,
    private val now: () -> Long,
) {
    /** The row, then the thanks line; null when not asked, or after Skip. */
    var ui by mutableStateOf<WhereFoundUi?>(null)
        private set

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

    /** Ask: the row, its answers in [order]; no reward line, and nothing earned, for [vip]. */
    fun ask(order: List<WhereFoundAnswer>, vip: Boolean) {
        ui = WhereFoundUi.Row(order, vip)
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
            ui = null
            return
        }
        answering = true
        if (!reducedMotion) selected = answer
        scope.launch {
            val start = now()
            val paid = !row.vip && earn()
            if (paid) log(AnalyticsEvent.inventoryEarned(Item.PHOTO_CANVAS, EarnSource.WHERE_FOUND, 1))
            if (!reducedMotion) delay((HOLD_MS - (now() - start)).coerceAtLeast(0))
            sheet = false
            selected = null
            ui = WhereFoundUi.Thanks(paid)
        }
    }

    /** Back, a swipe or the scrim closed the sheet: a skip with no event; after an answer it only closes it. */
    fun dismiss() {
        sheet = false
        if (done || ui !is WhereFoundUi.Row) return
        if (answered(WhereFoundAnswer.SKIP)) {
            done = true
            ui = null
        }
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
