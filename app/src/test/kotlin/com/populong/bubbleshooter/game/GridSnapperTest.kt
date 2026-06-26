package com.populong.bubbleshooter.game

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GridSnapperTest {
    private lateinit var grid: BubbleGrid
    private lateinit var snapper: GridSnapper

    @Before
    fun setup() {
        grid = BubbleGrid(columns = 10, maxRows = 16)
        snapper = GridSnapper()
    }

    @Test
    fun `snaps to empty neighbor closest to projectile`() {
        val radius = 20f
        val hitCell = BubbleGrid.GridCell(2, 3)
        grid.set(hitCell, Bubble(BubbleColor.RED))

        val center = grid.cellToPixel(hitCell, radius, 0f)
        val projectile = Projectile(center.x, center.y + radius * 2f, 0f, -0.5f, BubbleColor.BLUE)

        val snap = snapper.findSnapCell(projectile, hitCell, grid, radius, 0f)
        assertNotEquals(hitCell, snap)
        assertTrue(grid.isEmpty(snap))
    }

    @Test
    fun `ceiling snap returns valid cell`() {
        val radius = 20f
        val projectile = Projectile(100f, radius, 0f, -0.5f, BubbleColor.RED)

        val snap = snapper.snapCeiling(projectile, grid, radius, 0f)
        assertEquals(0, snap.row)
        assertTrue(snap.col in 0 until grid.columns)
    }
}
