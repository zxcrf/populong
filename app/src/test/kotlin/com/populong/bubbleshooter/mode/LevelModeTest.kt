package com.populong.bubbleshooter.mode

import com.populong.bubbleshooter.game.BubbleGrid
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Random

class LevelModeTest {
    private lateinit var mode: LevelMode
    private lateinit var grid: BubbleGrid
    private val rng = Random(42)

    @Before
    fun setup() {
        mode = LevelMode(LevelData.getLevel(1))
        grid = BubbleGrid(columns = 10, maxRows = 16)
    }

    @Test
    fun `initializes grid with level data`() {
        mode.initializeGrid(grid, rng)
        assertTrue(grid.occupiedCount() > 0)
    }

    @Test
    fun `level complete when grid is empty`() {
        mode.initializeGrid(grid, rng)
        assertFalse(mode.isLevelComplete(grid))
        grid.clear()
        assertTrue(mode.isLevelComplete(grid))
    }

    @Test
    fun `push rows down after specified shots`() {
        mode.initializeGrid(grid, rng)
        // Level 1 has shotsPerPush = 12
        for (i in 1..11) {
            val action = mode.onShotResolved(grid, 0, 0, i)
            assertTrue(action is PostShotAction.Nothing)
        }
        val action = mode.onShotResolved(grid, 0, 0, 12)
        assertTrue(action is PostShotAction.PushRowsDown)
    }

    @Test
    fun `stars earned based on score thresholds`() {
        assertEquals(0, mode.starsEarned(50))
        assertEquals(1, mode.starsEarned(100))
        assertEquals(2, mode.starsEarned(250))
        assertEquals(3, mode.starsEarned(500))
    }

    @Test
    fun `calculates score with floating bonus`() {
        val score = mode.calculateScore(3, 2, 1)
        assertEquals(80, score) // 3*10 + 2*25
    }
}
