package com.populong.bubbleshooter.core.match

import com.populong.bubbleshooter.core.grid.BubbleGrid
import com.populong.bubbleshooter.core.grid.GridPos

/** Detects bubble clusters left without structural support after cells are removed. */
object FloatDetector {

    /** Occupied cells not reachable from the backdrop ([BubbleGrid.anchored]) — they fall. */
    fun floating(grid: BubbleGrid): Set<GridPos> {
        val anchored = grid.anchored()
        return grid.cells.keys.filterNotTo(HashSet()) { it in anchored }
    }
}
