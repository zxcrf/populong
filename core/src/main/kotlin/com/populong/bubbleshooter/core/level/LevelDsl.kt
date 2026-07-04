package com.populong.bubbleshooter.core.level

import com.populong.bubbleshooter.core.grid.Bubble
import com.populong.bubbleshooter.core.grid.BubbleColor
import com.populong.bubbleshooter.core.grid.BubbleGrid
import com.populong.bubbleshooter.core.grid.GridPos

/**
 * The compact text grammar hand authors (and the generator's debug dumps) write levels in.
 *
 * The [grid] string is a `trimIndent`-style block: each non-blank line is one row starting at row 0
 * (the ceiling row), and whitespace-separated tokens fill the row left to right. Odd-r parity is
 * enforced per row — even (0-based) rows carry exactly [evenCols] tokens, odd rows [evenCols] `- 1`.
 *
 * Tokens:
 * - `.` an empty cell.
 * - `R B G Y P O` a [Bubble.Colored] in [BubbleColor] declaration order (RED, BLUE, … ORANGE).
 * - `S` a [Bubble.Stone].
 * - `I`+color (e.g. `IR`) a [Bubble.Ice] with `hitsLeft = 2`.
 * - `F`+color a hidden [Bubble.Fog] (`revealed = false`).
 * - `C`+color a [Bubble.Chained] cell.
 *
 * Every color letter must lie inside the first [palette] entries; `palette = 4` admits `R B G Y`
 * only. Malformed input throws [IllegalArgumentException] naming the level [id], the offending row
 * index and token.
 */
fun level(
    id: Int,
    palette: Int,
    shots: Int,
    star1: Long, star2: Long, star3: Long,
    bombEvery: Int = 0,
    rainbowEvery: Int = 0,
    descentEveryShots: Int = 0,
    evenCols: Int = 8,
    grid: String,
): LevelSpec {
    require(palette in 2..6) { "level $id: palette must be in 2..6, was $palette" }
    require(shots > 0) { "level $id: shots must be > 0, was $shots" }
    require(star1 < star2 && star2 < star3) {
        "level $id: star thresholds must ascend, were $star1 < $star2 < $star3"
    }

    val colors = BubbleColor.palette(palette)
    val rows = grid.trimIndent().lines().filter { it.isNotBlank() }
    require(rows.isNotEmpty()) { "level $id: grid is empty" }

    val cells = HashMap<GridPos, Bubble>()
    for ((row, line) in rows.withIndex()) {
        val tokens = line.trim().split(Regex("\\s+"))
        val expected = if (row % 2 == 0) evenCols else evenCols - 1
        require(tokens.size == expected) {
            "level $id: row $row has ${tokens.size} tokens, expected $expected for its parity"
        }
        for ((col, token) in tokens.withIndex()) {
            val bubble = parseToken(id, row, token, palette, colors) ?: continue
            cells[GridPos(row, col)] = bubble
        }
    }

    return LevelSpec(
        id = id,
        evenCols = evenCols,
        paletteSize = palette,
        initialGrid = BubbleGrid(cells, evenCols, ceilingRow = 0),
        shots = shots,
        starThresholds = listOf(star1, star2, star3),
        bombEvery = bombEvery,
        rainbowEvery = rainbowEvery,
        descentEveryShots = descentEveryShots,
    )
}

/** Parses one grid [token] into a bubble, or null for an empty cell. */
private fun parseToken(
    id: Int,
    row: Int,
    token: String,
    palette: Int,
    colors: List<BubbleColor>,
): Bubble? {
    if (token == ".") return null
    if (token == "S") return Bubble.Stone

    if (token.length == 1) {
        return Bubble.Colored(colorOf(id, row, token, token[0], palette, colors))
    }
    if (token.length == 2) {
        val color = colorOf(id, row, token, token[1], palette, colors)
        return when (token[0]) {
            'I' -> Bubble.Ice(color, hitsLeft = 2)
            'F' -> Bubble.Fog(color, revealed = false)
            'C' -> Bubble.Chained(color)
            else -> throw IllegalArgumentException("level $id: row $row: unknown token '$token'")
        }
    }
    throw IllegalArgumentException("level $id: row $row: unknown token '$token'")
}

/** Resolves a color [letter] to a palette color, rejecting letters outside the first [palette] entries. */
private fun colorOf(
    id: Int,
    row: Int,
    token: String,
    letter: Char,
    palette: Int,
    colors: List<BubbleColor>,
): BubbleColor {
    val index = COLOR_LETTERS.indexOf(letter)
    if (index < 0) {
        throw IllegalArgumentException("level $id: row $row: unknown token '$token'")
    }
    if (index >= palette) {
        throw IllegalArgumentException(
            "level $id: row $row: color '$letter' in token '$token' is outside the palette of $palette",
        )
    }
    return colors[index]
}

/** Color letters in [BubbleColor] declaration order: RED, BLUE, GREEN, YELLOW, PURPLE, ORANGE. */
private const val COLOR_LETTERS = "RBGYPO"
