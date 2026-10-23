package com.brewingbytes.gemmory.widget

import com.brewingbytes.gemmory.R
import com.brewingbytes.gemmory.copy.Strings
import com.brewingbytes.gemmory.streak.StreakSnapshot

/** What the flame shows (G1): the grey ember, or the lit flame in its tier's color. */
enum class WidgetFlame { EMBER, LIT, LIT_30, LIT_100 }

/**
 * The home-screen widget's state (#103, G17), read from the one [StreakSnapshot] (O8), so it agrees with the chip and
 * the streak screen, freezes and the day off included. [number] is the days in a row (0 when never played or broken,
 * the repair window included: the repair lives on the streak screen); [lit] once today's picture is finished.
 */
data class StreakWidgetState(val number: Int, val lit: Boolean) {
    /** The status line under the number. */
    fun line(s: Strings): String = s.text(
        when {
            number == 0 -> R.string.widget_start
            lit -> R.string.widget_done
            else -> R.string.widget_not_yet
        },
    )

    val flame: WidgetFlame
        get() = when {
            !lit -> WidgetFlame.EMBER
            number >= 100 -> WidgetFlame.LIT_100
            number >= 30 -> WidgetFlame.LIT_30
            else -> WidgetFlame.LIT
        }

    /** The widget is one TalkBack node: "Gemmory streak, 6 days, done today". */
    fun description(s: Strings): String = when {
        number == 0 -> s.text(R.string.widget_description_start)
        else -> s.plural(if (lit) R.plurals.widget_description_done else R.plurals.widget_description_not_done, number, number)
    }

    companion object {
        fun of(snapshot: StreakSnapshot): StreakWidgetState {
            val shown = snapshot.shown
            return StreakWidgetState(shown, lit = shown > 0 && snapshot.doneToday)
        }

        /** The widget picker's sample (and the content before the first read): 6 days, lit. */
        val PREVIEW = StreakWidgetState(6, lit = true)
    }
}
