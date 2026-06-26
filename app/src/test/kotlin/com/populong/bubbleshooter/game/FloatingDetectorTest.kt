package com.populong.bubbleshooter.game

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FloatingDetectorTest {
    private lateinit var grid: BubbleGrid
    private lateinit var detector: FloatingDetector

    @Before
    fun setup() {
        grid = BubbleGrid(columns = 10, maxRows = 16)
        detector = FloatingDetector()
    }

    @Test
    fun `all connected to ceiling returns empty`() {
        grid.set(BubbleGrid.GridCell(0, 0), Bubble(BubbleColor.RED))
        grid.set(BubbleGrid.GridCell(1, 0), Bubble(BubbleColor.BLUE))

        val floating = detector.findFloating(grid)
        assertTrue(floating.isEmpty())
    }

    @Test
    fun `disconnected bubble is floating`() {
        grid.set(BubbleGrid.GridCell(0, 0), Bubble(BubbleColor.RED))
        grid.set(BubbleGrid.GridCell(5, 5), Bubble(BubbleColor.BLUE))

        val floating = detector.findFloating(grid)
        assertEquals(1, floating.size)
        assertTrue(floating.contains(BubbleGrid.GridCell(5, 5)))
    }

    @Test
    fun `empty grid returns empty`() {
        val floating = detector.findFloating(grid)
        assertTrue(floating.isEmpty())
    }

    @Test
    fun `chain connected to ceiling is not floating`() {
        grid.set(BubbleGrid.GridCell(0, 0), Bubble(BubbleColor.RED))
        grid.set(BubbleGrid.GridCell(1, 0), Bubble(BubbleColor.BLUE))
        grid.set(BubbleGrid.GridCell(2, 0), Bubble(BubbleColor.GREEN))
        grid.set(BubbleGrid.GridCell(3, 0), Bubble(BubbleColor.YELLOW))

        val floating = detector.findFloating(grid)
        assertTrue(floating.isEmpty())
    }

    @Test
    fun `island structure detected as floating after bridge removed`() {
        grid.set(BubbleGrid.GridCell(0, 0), Bubble(BubbleColor.RED))
        grid.set(BubbleGrid.GridCell(0, 1), Bubble(BubbleColor.RED))
        // Bridge at (1, 0) is removed
        grid.set(BubbleGrid.GridCell(2, 0), Bubble(BubbleColor.BLUE))
        grid.set(BubbleGrid.GridCell(3, 0), Bubble(BubbleColor.BLUE))

        val floating = detector.findFloating(grid)
        assertEquals(2, floating.size)
    }
}
