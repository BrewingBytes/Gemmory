package com.brewingbytes.gemmory.policy

/** What one day of the milestone share card shows (#104, G14). */
sealed interface CardDay {
    /** The bundled or remote picture finished that day, by its canvas key; never a photo canvas. */
    data class Picture(val canvas: String) : CardDay

    /** The lit flame: a day finished only on photo canvases, or kept by the day off, a freeze or a repair. */
    data object Flame : CardDay

    /** An empty outline: a day after the milestone. */
    data object Empty : CardDay
}

/** The milestone share card's week (#104, G14): a public picture, so nothing from a photo canvas is ever on it. */
object StreakShareCard {
    /**
     * The card's days for the milestone [week] (the celebration's week row) reached on [milestoneDay]. A finished day
     * shows its first picture finished that day: the logged canvas when [isPicture] says it's a bundled or remote
     * picture, else the first picture in [finishes] (canvases by their first finished day). Photo-only and kept days
     * show the flame; days after the milestone are empty.
     */
    fun days(week: List<CalendarCell>, milestoneDay: Long, finishes: Map<Long, List<String>>, isPicture: (String) -> Boolean): List<CardDay> =
        week.map { cell ->
            when {
                cell.day > milestoneDay -> CardDay.Empty
                cell.kind == CellKind.FINISHED -> {
                    val picture = cell.canvas?.takeIf(isPicture) ?: finishes[cell.day].orEmpty().firstOrNull(isPicture)
                    picture?.let(CardDay::Picture) ?: CardDay.Flame
                }
                cell.kind == CellKind.EMPTY || cell.kind == CellKind.FUTURE -> CardDay.Empty
                else -> CardDay.Flame
            }
        }
}
