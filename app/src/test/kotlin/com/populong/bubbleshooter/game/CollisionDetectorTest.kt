package com.populong.bubbleshooter.game

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CollisionDetectorTest {
    private lateinit var grid: BubbleGrid
    private lateinit var detector: CollisionDetector

    @Before
    fun setup() {
        grid = BubbleGrid(columns = 10, maxRows = 16)
        detector = CollisionDetector()
    }

    @Test
    fun `detects ceiling collision`() {
        val projectile = Projectile(100f, 15f, 0f, -0.5f, BubbleColor.RED)
        val result = detector.check(projectile, grid, 20f, 0f)
        assertTrue(result is CollisionResult.Ceiling)
    }

    @Test
    fun `detects bubble collision`() {
        val radius = 20f
        val cell = BubbleGrid.GridCell(2, 2)
        grid.set(cell, Bubble(BubbleColor.RED))
        val center = grid.cellToPixel(cell, radius, 0f)

        val projectile = Projectile(center.x, center.y + radius * 2.2f, 0f, -0.5f, BubbleColor.BLUE)
        projectile.y = center.y + radius * 1.5f

        val result = detector.check(projectile, grid, radius, 0f)
        assertTrue(result is CollisionResult.BubbleHit)
    }

    @Test
    fun `no collision in empty grid far from ceiling`() {
        val projectile = Projectile(100f, 300f, 0f, -0.5f, BubbleColor.RED)
        val result = detector.check(projectile, grid, 20f, 0f)
        assertNull(result)
    }
}
