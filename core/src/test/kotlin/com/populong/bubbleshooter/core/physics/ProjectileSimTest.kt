package com.populong.bubbleshooter.core.physics

import com.populong.bubbleshooter.core.engine.Ammo
import com.populong.bubbleshooter.core.grid.Bubble
import com.populong.bubbleshooter.core.grid.BubbleColor
import com.populong.bubbleshooter.core.grid.BubbleGrid
import com.populong.bubbleshooter.core.grid.GridGeometry
import com.populong.bubbleshooter.core.grid.GridPos
import com.populong.bubbleshooter.core.grid.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProjectileSimTest {

    private val evenCols = 9
    private val fieldWidth = 2f * evenCols
    private val ceilingY = 0f
    private val origin = Vec2(evenCols.toFloat(), ceilingY + 12 * GridGeometry.ROW_HEIGHT + 1f)
    private val red = Ammo.ColorAmmo(BubbleColor.RED)

    private fun simulate(grid: BubbleGrid, dir: Vec2, speed: Float, dt: Float): SimOutcome.Landed {
        var p = Projectile(origin, dir.normalized() * speed, red, 0)
        repeat(1_000_000) {
            when (val outcome = ProjectileSim.step(grid, ceilingY, fieldWidth, p, dt)) {
                is SimOutcome.Moving -> p = outcome.projectile
                is SimOutcome.Landed -> return outcome
            }
        }
        error("projectile never landed")
    }

    @Test
    fun `no tunneling even at high speed`() {
        // A bubble sitting on the straight-up axis (centerX 9) at row 5.
        val grid = BubbleGrid(mapOf(GridPos(5, 4) to Bubble.Colored(BubbleColor.BLUE)), evenCols, 0)
        // 200 units/s with dt 1/120 would move ~1.67 units/tick; without substepping it would tunnel.
        val landed = simulate(grid, Vec2(0f, -1f), speed = 200f, dt = 1f / 120f)
        assertEquals(GridPos(5, 4), landed.contact, "should contact the on-axis bubble, not fly past it")
        assertFalse(grid.isOccupied(landed.cell), "snap cell must be empty")
        assertTrue(landed.cell in grid.neighbors(GridPos(5, 4)), "snaps into a neighbor of the contacted bubble")
    }

    @Test
    fun `ceiling snap lands in a valid empty ceiling cell`() {
        val grid = BubbleGrid(emptyMap(), evenCols, 0)
        val landed = simulate(grid, Vec2(0f, -1f), speed = 60f, dt = 1f / 120f)
        assertEquals(0, landed.cell.row, "lands in the ceiling row")
        assertEquals(GridPos(0, 4), landed.cell, "straight up the center snaps to column 4")
        assertTrue(GridGeometry.isValidCell(landed.cell, evenCols))
        assertEquals(null, landed.contact, "ceiling landing has no bubble contact")
    }

    @Test
    fun `wall bounce increments the bounce count`() {
        val grid = BubbleGrid(emptyMap(), evenCols, 0)
        val landed = simulate(grid, Vec2(-1.5f, -0.4f), speed = 60f, dt = 1f / 120f)
        assertTrue(landed.bounces >= 1, "a steep sideways shot must reflect off a wall")
    }

    @Test
    fun `snap never returns an occupied cell across many angles`() {
        val grid = BubbleGrid(
            mapOf(
                GridPos(0, 3) to Bubble.Colored(BubbleColor.RED),
                GridPos(0, 4) to Bubble.Colored(BubbleColor.BLUE),
                GridPos(0, 5) to Bubble.Colored(BubbleColor.GREEN),
                GridPos(1, 3) to Bubble.Colored(BubbleColor.RED),
                GridPos(1, 4) to Bubble.Colored(BubbleColor.YELLOW),
                GridPos(2, 4) to Bubble.Colored(BubbleColor.PURPLE),
            ),
            evenCols,
            0,
        )
        var k = -1.4f
        while (k <= 1.4f) {
            val landed = simulate(grid, Vec2(k, -1f), speed = 60f, dt = 1f / 120f)
            assertFalse(grid.isOccupied(landed.cell), "occupied cell returned for slope $k: ${landed.cell}")
            assertTrue(GridGeometry.isValidCell(landed.cell, evenCols), "invalid cell for slope $k: ${landed.cell}")
            k += 0.2f
        }
    }

    @Test
    fun `a moving projectile keeps advancing`() {
        val grid = BubbleGrid(emptyMap(), evenCols, 0)
        val p = Projectile(origin, Vec2(0f, -1f) * 60f, red, 0)
        val outcome = ProjectileSim.step(grid, ceilingY, fieldWidth, p, 1f / 120f)
        assertNotNull(outcome as? SimOutcome.Moving)
        val moved = (outcome as SimOutcome.Moving).projectile
        assertTrue(moved.pos.y < origin.y, "should have moved upward")
    }
}
