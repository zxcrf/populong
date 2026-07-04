package com.populong.bubbleshooter.core.level

import com.populong.bubbleshooter.core.grid.BubbleGrid

/**
 * The static definition of a hand-authored (or generated) level.
 *
 * @property id stable identifier for the level.
 * @property evenCols field width: even-parity rows hold this many bubbles.
 * @property paletteSize number of colors drawn from [com.populong.bubbleshooter.core.grid.BubbleColor.palette].
 * @property initialGrid the starting layout.
 * @property shots shot budget; the engine loses the level when it is exhausted and the grid is not cleared.
 * @property starThresholds size-3 ascending scores needed for 1, 2 and 3 stars.
 * @property bombEvery every Nth drawn shot serves a Bomb (0 = never).
 * @property rainbowEvery every Nth drawn shot serves a Rainbow (0 = never).
 * @property descentEveryShots level-mode ceiling compression: every N shots one compression step (0 = never).
 */
data class LevelSpec(
    val id: Int,
    val evenCols: Int,
    val paletteSize: Int,
    val initialGrid: BubbleGrid,
    val shots: Int,
    val starThresholds: List<Long>,
    val bombEvery: Int = 0,
    val rainbowEvery: Int = 0,
    val descentEveryShots: Int = 0,
) {
    init {
        require(starThresholds.size == 3) { "starThresholds must have exactly 3 entries" }
    }
}
