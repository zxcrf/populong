package com.populong.bubbleshooter.game

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BubbleGridTest {
    private lateinit var grid: BubbleGrid

    @Before
    fun setup() {
        grid = BubbleGrid(columns = 10, maxRows = 16)
    }

    @Test
    fun `set and get bubble`() {
        val cell = BubbleGrid.GridCell(0, 0)
        val bubble = Bubble(BubbleColor.RED)
        grid.set(cell, bubble)
        assertEquals(bubble, grid.get(cell))
    }

    @Test
    fun `get returns null for empty cell`() {
        assertNull(grid.get(BubbleGrid.GridCell(0, 0)))
    }

    @Test
    fun `remove bubble`() {
        val cell = BubbleGrid.GridCell(0, 0)
        grid.set(cell, Bubble(BubbleColor.RED))
        grid.remove(cell)
        assertTrue(grid.isEmpty(cell))
    }

    @Test
    fun `neighbors returns 6 for even row center cell`() {
        val cell = BubbleGrid.GridCell(2, 5)
        val neighbors = grid.neighbors(cell)
        assertEquals(6, neighbors.size)
    }

    @Test
    fun `neighbors returns 6 for odd row center cell`() {
        val cell = BubbleGrid.GridCell(3, 5)
        val neighbors = grid.neighbors(cell)
        assertEquals(6, neighbors.size)
    }

    @Test
    fun `neighbors handles edge cells`() {
        val cell = BubbleGrid.GridCell(0, 0)
        val neighbors = grid.neighbors(cell)
        assertTrue(neighbors.size in 2..3)
        assertTrue(neighbors.all { it.row >= 0 && it.col >= 0 })
    }

    @Test
    fun `shiftDown moves all bubbles one row down`() {
        grid.set(BubbleGrid.GridCell(0, 0), Bubble(BubbleColor.RED))
        grid.set(BubbleGrid.GridCell(1, 1), Bubble(BubbleColor.BLUE))
        assertTrue(grid.shiftDown())
        assertNull(grid.get(BubbleGrid.GridCell(0, 0)))
        assertNotNull(grid.get(BubbleGrid.GridCell(1, 0)))
        assertNotNull(grid.get(BubbleGrid.GridCell(2, 1)))
    }

    @Test
    fun `shiftDown returns false when overflow`() {
        grid.set(BubbleGrid.GridCell(15, 0), Bubble(BubbleColor.RED))
        assertFalse(grid.shiftDown())
    }

    @Test
    fun `colorsOnScreen returns unique colors`() {
        grid.set(BubbleGrid.GridCell(0, 0), Bubble(BubbleColor.RED))
        grid.set(BubbleGrid.GridCell(0, 1), Bubble(BubbleColor.RED))
        grid.set(BubbleGrid.GridCell(0, 2), Bubble(BubbleColor.BLUE))
        val colors = grid.colorsOnScreen()
        assertEquals(2, colors.size)
        assertTrue(colors.contains(BubbleColor.RED))
        assertTrue(colors.contains(BubbleColor.BLUE))
    }

    @Test
    fun `lowestOccupiedRow returns correct row`() {
        grid.set(BubbleGrid.GridCell(0, 0), Bubble(BubbleColor.RED))
        grid.set(BubbleGrid.GridCell(5, 0), Bubble(BubbleColor.BLUE))
        assertEquals(5, grid.lowestOccupiedRow())
    }

    @Test
    fun `lowestOccupiedRow returns -1 for empty grid`() {
        assertEquals(-1, grid.lowestOccupiedRow())
    }
}
