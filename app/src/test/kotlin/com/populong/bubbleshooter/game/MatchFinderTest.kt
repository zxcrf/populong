package com.populong.bubbleshooter.game

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MatchFinderTest {
    private lateinit var grid: BubbleGrid
    private lateinit var finder: MatchFinder

    @Before
    fun setup() {
        grid = BubbleGrid(columns = 10, maxRows = 16)
        finder = MatchFinder()
    }

    @Test
    fun `finds 3 connected same-color bubbles`() {
        grid.set(BubbleGrid.GridCell(0, 0), Bubble(BubbleColor.RED))
        grid.set(BubbleGrid.GridCell(0, 1), Bubble(BubbleColor.RED))
        grid.set(BubbleGrid.GridCell(1, 0), Bubble(BubbleColor.RED))

        val matches = finder.findMatches(BubbleGrid.GridCell(0, 0), grid)
        assertEquals(3, matches.size)
    }

    @Test
    fun `does not match fewer than 3`() {
        grid.set(BubbleGrid.GridCell(0, 0), Bubble(BubbleColor.RED))
        grid.set(BubbleGrid.GridCell(0, 1), Bubble(BubbleColor.RED))

        val matches = finder.findMatches(BubbleGrid.GridCell(0, 0), grid)
        assertTrue(matches.isEmpty())
    }

    @Test
    fun `does not match different colors`() {
        grid.set(BubbleGrid.GridCell(0, 0), Bubble(BubbleColor.RED))
        grid.set(BubbleGrid.GridCell(0, 1), Bubble(BubbleColor.BLUE))
        grid.set(BubbleGrid.GridCell(1, 0), Bubble(BubbleColor.RED))

        val matches = finder.findMatches(BubbleGrid.GridCell(0, 0), grid)
        assertTrue(matches.isEmpty())
    }

    @Test
    fun `rainbow bubble matches neighboring color group`() {
        grid.set(BubbleGrid.GridCell(0, 0), Bubble(BubbleColor.RED))
        grid.set(BubbleGrid.GridCell(0, 1), Bubble(BubbleColor.RED))
        grid.set(BubbleGrid.GridCell(1, 0), Bubble(BubbleColor.RED, isRainbow = true))

        val matches = finder.findMatches(BubbleGrid.GridCell(1, 0), grid)
        assertEquals(3, matches.size)
    }

    @Test
    fun `returns empty for empty grid cell`() {
        val matches = finder.findMatches(BubbleGrid.GridCell(0, 0), grid)
        assertTrue(matches.isEmpty())
    }

    @Test
    fun `finds large cluster`() {
        for (col in 0 until 5) {
            grid.set(BubbleGrid.GridCell(0, col), Bubble(BubbleColor.GREEN))
        }
        val matches = finder.findMatches(BubbleGrid.GridCell(0, 2), grid)
        assertEquals(5, matches.size)
    }
}
