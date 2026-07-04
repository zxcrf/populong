package com.populong.bubbleshooter.core.match

import com.populong.bubbleshooter.core.grid.Bubble
import com.populong.bubbleshooter.core.grid.BubbleColor
import com.populong.bubbleshooter.core.grid.BubbleGrid
import com.populong.bubbleshooter.core.grid.GridPos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FloatDetectorTest {

    @Test
    fun `popping a bridge cell strands a cluster`() {
        // ceiling (0,0) -- bridge (1,0) -- stranded (2,0)
        val cells = mapOf(
            GridPos(0, 0) to Bubble.Colored(BubbleColor.RED),
            GridPos(1, 0) to Bubble.Colored(BubbleColor.BLUE),
            GridPos(2, 0) to Bubble.Colored(BubbleColor.GREEN),
        )
        val grid = BubbleGrid(cells, evenCols = 8, ceilingRow = 0)
        assertTrue(FloatDetector.floating(grid).isEmpty())

        val afterPop = grid.without(setOf(GridPos(1, 0)))
        assertEquals(setOf(GridPos(2, 0)), FloatDetector.floating(afterPop))
    }

    @Test
    fun `chained cluster is not floating even when detached from the ceiling`() {
        val cells = mapOf(
            GridPos(5, 0) to Bubble.Chained(BubbleColor.GREEN),
            GridPos(6, 0) to Bubble.Colored(BubbleColor.GREEN),
        )
        val grid = BubbleGrid(cells, evenCols = 8, ceilingRow = 0)
        assertTrue(FloatDetector.floating(grid).isEmpty())
    }
}
