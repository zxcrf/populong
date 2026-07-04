package com.populong.bubbleshooter.core.grid

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BubbleGridTest {

    private fun emptyGrid(evenCols: Int = 8, ceilingRow: Int = 0) =
        BubbleGrid(emptyMap(), evenCols, ceilingRow)

    @Test
    fun `neighbor relation is symmetric across the field`() {
        val grid = emptyGrid(evenCols = 8, ceilingRow = -3)
        for (row in -3..4) {
            val cols = GridGeometry.colsInRow(row, grid.evenCols)
            for (col in 0 until cols) {
                val p = GridPos(row, col)
                for (q in grid.neighbors(p)) {
                    assertTrue(
                        p in grid.neighbors(q),
                        "expected $p in neighbors($q)=${grid.neighbors(q)}; neighbors($p)=${grid.neighbors(p)}",
                    )
                }
            }
        }
    }

    @Test
    fun `neighbors near walls are clipped to valid cells`() {
        val grid = emptyGrid(evenCols = 8, ceilingRow = 0)

        val leftEven = grid.neighbors(GridPos(2, 0))
        assertTrue(leftEven.all { GridGeometry.isValidCell(it, grid.evenCols) })
        assertFalse(leftEven.any { it.col < 0 })

        val rightEven = grid.neighbors(GridPos(2, 7))
        assertTrue(rightEven.all { GridGeometry.isValidCell(it, grid.evenCols) })

        val leftOdd = grid.neighbors(GridPos(1, 0))
        assertTrue(leftOdd.all { GridGeometry.isValidCell(it, grid.evenCols) })

        val rightOdd = grid.neighbors(GridPos(1, 6))
        assertTrue(rightOdd.all { GridGeometry.isValidCell(it, grid.evenCols) })
    }

    @Test
    fun `odd rows have evenCols minus one columns`() {
        val grid = emptyGrid(evenCols = 8)
        assertEquals(7, GridGeometry.colsInRow(1, grid.evenCols))
        assertFalse(GridGeometry.isValidCell(GridPos(1, 7), grid.evenCols))
    }

    @Test
    fun `anchored cluster hanging from ceiling stays anchored`() {
        val cells = mapOf(
            GridPos(0, 0) to Bubble.Colored(BubbleColor.RED),
            GridPos(1, 0) to Bubble.Colored(BubbleColor.RED),
            GridPos(2, 0) to Bubble.Colored(BubbleColor.RED),
        )
        val grid = BubbleGrid(cells, evenCols = 8, ceilingRow = 0)
        assertEquals(cells.keys, grid.anchored())
    }

    @Test
    fun `detached cluster is not anchored`() {
        val cells = mapOf(
            GridPos(0, 0) to Bubble.Colored(BubbleColor.RED),
            GridPos(5, 0) to Bubble.Colored(BubbleColor.BLUE),
            GridPos(6, 0) to Bubble.Colored(BubbleColor.BLUE),
        )
        val grid = BubbleGrid(cells, evenCols = 8, ceilingRow = 0)
        val anchored = grid.anchored()
        assertTrue(GridPos(0, 0) in anchored)
        assertFalse(GridPos(5, 0) in anchored)
        assertFalse(GridPos(6, 0) in anchored)
    }

    @Test
    fun `a locked chained cell keeps its whole connected component anchored`() {
        val cells = mapOf(
            GridPos(5, 0) to Bubble.Chained(BubbleColor.GREEN),
            GridPos(6, 0) to Bubble.Colored(BubbleColor.GREEN),
            // isolated, not connected to the chain -> not anchored
            GridPos(9, 0) to Bubble.Colored(BubbleColor.YELLOW),
        )
        val grid = BubbleGrid(cells, evenCols = 8, ceilingRow = 0)
        val anchored = grid.anchored()
        assertTrue(GridPos(5, 0) in anchored)
        assertTrue(GridPos(6, 0) in anchored)
        assertFalse(GridPos(9, 0) in anchored)
    }

    @Test
    fun `a wormhole portal keeps its whole connected component anchored`() {
        val cells = mapOf(
            GridPos(5, 0) to Bubble.Wormhole(1),
            GridPos(6, 0) to Bubble.Colored(BubbleColor.GREEN),
            // isolated, not connected to the wormhole -> not anchored
            GridPos(9, 0) to Bubble.Colored(BubbleColor.YELLOW),
        )
        val grid = BubbleGrid(cells, evenCols = 8, ceilingRow = 0)
        val anchored = grid.anchored()
        assertTrue(GridPos(5, 0) in anchored)
        assertTrue(GridPos(6, 0) in anchored)
        assertFalse(GridPos(9, 0) in anchored)
    }

    @Test
    fun `a gravity well is not an anchor and falls when detached`() {
        val cells = mapOf(
            GridPos(0, 0) to Bubble.Colored(BubbleColor.RED),
            // a well hanging free, not connected to the ceiling
            GridPos(5, 0) to Bubble.GravityWell,
        )
        val grid = BubbleGrid(cells, evenCols = 8, ceilingRow = 0)
        val anchored = grid.anchored()
        assertTrue(GridPos(0, 0) in anchored)
        assertFalse(GridPos(5, 0) in anchored, "a detached gravity well must not anchor itself")
    }

    @Test
    fun `negative ceilingRow works`() {
        val cells = mapOf(
            GridPos(-3, 0) to Bubble.Colored(BubbleColor.RED),
            GridPos(-2, 0) to Bubble.Colored(BubbleColor.RED),
        )
        val grid = BubbleGrid(cells, evenCols = 8, ceilingRow = -3)
        assertEquals(cells.keys, grid.anchored())
    }

    @Test
    fun `with and without do not mutate the original grid`() {
        val original = emptyGrid(evenCols = 8, ceilingRow = 0)
        val added = original.with(GridPos(0, 0), Bubble.Colored(BubbleColor.RED))

        assertTrue(original.isEmpty())
        assertFalse(added.isEmpty())
        assertTrue(added.isOccupied(GridPos(0, 0)))

        val removed = added.without(listOf(GridPos(0, 0)))
        assertTrue(added.isOccupied(GridPos(0, 0)))
        assertFalse(removed.isOccupied(GridPos(0, 0)))
    }

    @Test
    fun `with replaces an existing bubble at the same position`() {
        val grid = emptyGrid(evenCols = 8).with(GridPos(0, 0), Bubble.Colored(BubbleColor.RED))
        val replaced = grid.with(GridPos(0, 0), Bubble.Stone)
        assertEquals(Bubble.Stone, replaced.bubbleAt(GridPos(0, 0)))
        assertEquals(Bubble.Colored(BubbleColor.RED), grid.bubbleAt(GridPos(0, 0)))
    }

    @Test
    fun `bottomRow reflects the deepest occupied row or ceilingRow minus one when empty`() {
        assertEquals(-1, emptyGrid(evenCols = 8, ceilingRow = 0).bottomRow())
        val grid = BubbleGrid(
            mapOf(GridPos(3, 0) to Bubble.Colored(BubbleColor.RED)),
            evenCols = 8,
            ceilingRow = 0,
        )
        assertEquals(3, grid.bottomRow())
    }
}
