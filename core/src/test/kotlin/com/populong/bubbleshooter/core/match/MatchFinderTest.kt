package com.populong.bubbleshooter.core.match

import com.populong.bubbleshooter.core.grid.Bubble
import com.populong.bubbleshooter.core.grid.BubbleColor
import com.populong.bubbleshooter.core.grid.BubbleGrid
import com.populong.bubbleshooter.core.grid.GridPos
import kotlin.test.Test
import kotlin.test.assertEquals

class MatchFinderTest {

    @Test
    fun `finds an L-shaped 3-match`() {
        // (0,0)-(1,0) adjacent via even-row (r+1,c); (1,0)-(1,1) adjacent same row -> an L shape
        val cells = mapOf(
            GridPos(0, 0) to Bubble.Colored(BubbleColor.RED),
            GridPos(1, 0) to Bubble.Colored(BubbleColor.RED),
            GridPos(1, 1) to Bubble.Colored(BubbleColor.RED),
        )
        val grid = BubbleGrid(cells, evenCols = 8, ceilingRow = 0)
        val match = MatchFinder.findMatch(grid, GridPos(0, 0), BubbleColor.RED)
        assertEquals(cells.keys, match)
    }

    @Test
    fun `a different colored neighbor blocks propagation`() {
        val cells = mapOf(
            GridPos(0, 0) to Bubble.Colored(BubbleColor.RED),
            GridPos(1, 0) to Bubble.Colored(BubbleColor.BLUE),
            GridPos(2, 0) to Bubble.Colored(BubbleColor.RED),
        )
        val grid = BubbleGrid(cells, evenCols = 8, ceilingRow = 0)
        val match = MatchFinder.findMatch(grid, GridPos(0, 0), BubbleColor.RED)
        assertEquals(setOf(GridPos(0, 0)), match)
    }

    @Test
    fun `frozen ice, unrevealed fog, stone and chained do not match`() {
        val grid = BubbleGrid(
            mapOf(
                GridPos(0, 0) to Bubble.Colored(BubbleColor.RED),
                GridPos(1, 0) to Bubble.Ice(BubbleColor.RED, hitsLeft = 2),
                GridPos(0, 1) to Bubble.Fog(BubbleColor.RED, revealed = false),
                GridPos(2, 0) to Bubble.Stone,
                GridPos(1, 1) to Bubble.Chained(BubbleColor.RED),
            ),
            evenCols = 8,
            ceilingRow = 0,
        )
        val match = MatchFinder.findMatch(grid, GridPos(0, 0), BubbleColor.RED)
        assertEquals(setOf(GridPos(0, 0)), match)
    }

    @Test
    fun `revealed fog matches its color`() {
        val cells = mapOf(
            GridPos(0, 0) to Bubble.Colored(BubbleColor.RED),
            GridPos(1, 0) to Bubble.Fog(BubbleColor.RED, revealed = true),
        )
        val grid = BubbleGrid(cells, evenCols = 8, ceilingRow = 0)
        val match = MatchFinder.findMatch(grid, GridPos(0, 0), BubbleColor.RED)
        assertEquals(cells.keys, match)
    }

    @Test
    fun `starting from a non-matchable cell returns an empty set`() {
        val grid = BubbleGrid(
            mapOf(GridPos(0, 0) to Bubble.Stone),
            evenCols = 8,
            ceilingRow = 0,
        )
        assertEquals(emptySet(), MatchFinder.findMatch(grid, GridPos(0, 0), BubbleColor.RED))
    }
}
