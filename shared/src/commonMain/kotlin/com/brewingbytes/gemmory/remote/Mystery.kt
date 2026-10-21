package com.brewingbytes.gemmory.remote

import kotlinx.datetime.LocalDate

/**
 * The mystery picture's rules (T43; design system, Mystery picture): a dated drop flagged `mystery` keeps its title,
 * art, canvas tints, Peek and Share hidden until it's finished (plan-design-review D23). There's no reveal at 50%: the
 * finish is the reveal, whether the last gem comes from a tap or a batch.
 */
object Mystery {
    const val TODAY = "Today's mystery picture"
    const val TONIGHT = "Tonight's mystery picture"
    const val LATER = "Mystery picture"

    fun hidden(mystery: Boolean, finished: Boolean): Boolean = mystery && !finished

    /**
     * The neutral name in the app (D11): "Today's mystery picture" on its [availableFrom] date, "Mystery picture"
     * after it. "Tonight's mystery picture" is the evening reminder's alone ([TONIGHT]).
     */
    fun name(availableFrom: LocalDate?, today: LocalDate): String = if (availableFrom == today) TODAY else LATER
}
