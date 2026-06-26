package com.populong.bubbleshooter.mode

import com.populong.bubbleshooter.game.BubbleGrid
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Random

class EndlessModeTest {
    private lateinit var mode: EndlessMode
    private lateinit var grid: BubbleGrid
    private val rng = Random(42)

    @Before
    fun setup() {
        mode = EndlessMode()
        grid = BubbleGrid(columns = 10, maxRows = 16)
    }

    @Test
    fun `initializes grid with bubbles`() {
        mode.initializeGrid(grid, rng)
        assertTrue(grid.occupiedCount() > 0)
    }

    @Test
    fun `chooses color from on-screen colors`() {
        mode.initializeGrid(grid, rng)
        val onScreen = grid.colorsOnScreen()
        val chosen = mode.chooseNextColor(grid, rng)
        assertTrue(onScreen.contains(chosen))
    }

    @Test
    fun `returns add new row action periodically`() {
        mode.initializeGrid(grid, rng)
        val action = mode.onShotResolved(grid, 0, 0, 8)
        assertTrue(action is PostShotAction.AddNewRow)
    }

    @Test
    fun `returns nothing for non-interval shots`() {
        mode.initializeGrid(grid, rng)
        val action = mode.onShotResolved(grid, 0, 0, 5)
        assertTrue(action is PostShotAction.Nothing)
    }

    @Test
    fun `game over when bubbles reach bottom`() {
        mode.initializeGrid(grid, rng)
        grid.set(BubbleGrid.GridCell(14, 0), com.populong.bubbleshooter.game.Bubble(com.populong.bubbleshooter.game.BubbleColor.RED))
        assertTrue(mode.isGameOver(grid))
    }

    @Test
    fun `calculates score correctly`() {
        val score = mode.calculateScore(3, 2, 1)
        assertEquals(70, score) // 3*10 + 2*20
    }

    @Test
    fun `should spawn rainbow after 3 misses`() {
        assertTrue(mode.shouldSpawnRainbow(3))
        assertTrue(mode.shouldSpawnRainbow(5))
        assertFalse(mode.shouldSpawnRainbow(2))
    }
}
