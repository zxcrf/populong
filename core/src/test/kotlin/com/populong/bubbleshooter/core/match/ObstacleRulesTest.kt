package com.populong.bubbleshooter.core.match

import com.populong.bubbleshooter.core.grid.Bubble
import com.populong.bubbleshooter.core.grid.BubbleColor
import com.populong.bubbleshooter.core.grid.BubbleGrid
import com.populong.bubbleshooter.core.grid.GridPos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ObstacleRulesTest {

    @Test
    fun `ice cracks from 2 to 1 and stays Ice`() {
        // grid already has the popped cell removed, per contract
        val grid = BubbleGrid(
            mapOf(GridPos(1, 0) to Bubble.Ice(BubbleColor.BLUE, hitsLeft = 2)),
            evenCols = 8,
            ceilingRow = 0,
        )
        val result = ObstacleRules.afterPop(grid, setOf(GridPos(0, 0)))
        assertEquals(Bubble.Ice(BubbleColor.BLUE, hitsLeft = 1), result.bubbleAt(GridPos(1, 0)))
    }

    @Test
    fun `ice cracks from 1 to 0 and becomes Colored`() {
        val grid = BubbleGrid(
            mapOf(GridPos(1, 0) to Bubble.Ice(BubbleColor.BLUE, hitsLeft = 1)),
            evenCols = 8,
            ceilingRow = 0,
        )
        val result = ObstacleRules.afterPop(grid, setOf(GridPos(0, 0)))
        assertEquals(Bubble.Colored(BubbleColor.BLUE), result.bubbleAt(GridPos(1, 0)))
    }

    @Test
    fun `ice loses only one hit per pop-event even with multiple popped neighbors`() {
        // (1,0) is an odd-row cell; both (0,0) [r-1,c] and (2,0) [r+1,c] are its neighbors
        val grid = BubbleGrid(
            mapOf(GridPos(1, 0) to Bubble.Ice(BubbleColor.BLUE, hitsLeft = 2)),
            evenCols = 8,
            ceilingRow = 0,
        )
        val result = ObstacleRules.afterPop(grid, setOf(GridPos(0, 0), GridPos(2, 0)))
        assertEquals(Bubble.Ice(BubbleColor.BLUE, hitsLeft = 1), result.bubbleAt(GridPos(1, 0)))
    }

    @Test
    fun `chained cell unlocks to Colored`() {
        val grid = BubbleGrid(
            mapOf(GridPos(1, 0) to Bubble.Chained(BubbleColor.PURPLE)),
            evenCols = 8,
            ceilingRow = 0,
        )
        val result = ObstacleRules.afterPop(grid, setOf(GridPos(0, 0)))
        assertEquals(Bubble.Colored(BubbleColor.PURPLE), result.bubbleAt(GridPos(1, 0)))
    }

    @Test
    fun `non-adjacent obstacles are untouched`() {
        val cells = mapOf(
            GridPos(5, 0) to Bubble.Ice(BubbleColor.BLUE, hitsLeft = 2),
            GridPos(5, 1) to Bubble.Chained(BubbleColor.PURPLE),
        )
        val grid = BubbleGrid(cells, evenCols = 8, ceilingRow = 0)
        val result = ObstacleRules.afterPop(grid, setOf(GridPos(0, 0)))
        assertEquals(cells, result.cells)
    }

    @Test
    fun `fog within distance 2 is revealed, distance 3 is not`() {
        // straight run down column 0: (0,0)-(1,0)-(2,0)-(3,0), each step distance 1
        val cells = mapOf(
            GridPos(2, 0) to Bubble.Fog(BubbleColor.YELLOW, revealed = false), // distance 2
            GridPos(3, 0) to Bubble.Fog(BubbleColor.YELLOW, revealed = false), // distance 3
        )
        val grid = BubbleGrid(cells, evenCols = 8, ceilingRow = 0)
        val result = ObstacleRules.revealFogAround(grid, landing = GridPos(0, 0), radius = 2)

        assertEquals(true, (result.bubbleAt(GridPos(2, 0)) as Bubble.Fog).revealed)
        assertFalse((result.bubbleAt(GridPos(3, 0)) as Bubble.Fog).revealed)
    }
}
