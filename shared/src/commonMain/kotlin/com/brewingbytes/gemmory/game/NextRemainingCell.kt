package com.brewingbytes.gemmory.game

import com.brewingbytes.gemmory.converter.Pattern

/**
 * The next unplaced cell of [slot] after cell [after], scanning row-major and wrapping to the start (eng re-check
 * finding 5). [after] itself is checked last, so a single remaining cell is found again; pass -1 to start at cell 0.
 * Null when every cell of [slot] is placed. Shared by Find (design D24) and the TalkBack "Find next gem" action.
 */
fun nextRemainingCell(grid: Pattern, placed: (Int) -> Boolean, slot: Int, after: Int): Int? {
    val cells = grid.cells
    val n = cells.size
    if (n == 0) return null
    val start = if (after in 0 until n) after + 1 else 0
    for (i in 0 until n) {
        val cell = (start + i) % n
        if (cells[cell] == slot && !placed(cell)) return cell
    }
    return null
}
