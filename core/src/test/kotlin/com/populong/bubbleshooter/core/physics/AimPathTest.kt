package com.populong.bubbleshooter.core.physics

import com.populong.bubbleshooter.core.engine.Ammo
import com.populong.bubbleshooter.core.grid.Bubble
import com.populong.bubbleshooter.core.grid.BubbleColor
import com.populong.bubbleshooter.core.grid.BubbleGrid
import com.populong.bubbleshooter.core.grid.GridGeometry
import com.populong.bubbleshooter.core.grid.GridPos
import com.populong.bubbleshooter.core.grid.Vec2
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AimPathTest {

    private val evenCols = 9
    private val fieldWidth = 2f * evenCols
    private val ceilingY = 0f
    private val origin = Vec2(evenCols.toFloat(), ceilingY + 12 * GridGeometry.ROW_HEIGHT + 1f)

    private fun simLanding(grid: BubbleGrid, dir: Vec2): GridPos {
        var p = Projectile(origin, dir.normalized() * 60f, Ammo.ColorAmmo(BubbleColor.RED), 0)
        repeat(1_000_000) {
            when (val o = ProjectileSim.step(grid, ceilingY, fieldWidth, p, 1f / 120f)) {
                is SimOutcome.Moving -> p = o.projectile
                is SimOutcome.Landed -> return o.cell
            }
        }
        error("never landed")
    }

    @Test
    fun `straight shot lands in the center ceiling cell`() {
        val grid = BubbleGrid(emptyMap(), evenCols, 0)
        val result = AimPath.compute(grid, ceilingY, origin, Vec2(0f, -1f), maxBounces = 1)
        assertEquals(GridPos(0, 4), result.landingCell)
        assertEquals(0, result.bounces)
        assertEquals(origin, result.points.first())
    }

    @Test
    fun `one-bounce path reflects with equal incident and reflected slope`() {
        val grid = BubbleGrid(emptyMap(), evenCols, 0)
        val result = AimPath.compute(grid, ceilingY, origin, Vec2(-0.6f, -0.8f), maxBounces = 3)
        assertTrue(result.bounces >= 1, "expected at least one wall reflection")

        // Find a bounce vertex (an interior polyline point that sits on a wall).
        val idx = (1 until result.points.size - 1).first { i ->
            val x = result.points[i].x
            abs(x - 1f) < 1e-2f || abs(x - (fieldWidth - 1f)) < 1e-2f
        }
        val before = result.points[idx] - result.points[idx - 1]
        val after = result.points[idx + 1] - result.points[idx]
        val slopeBefore = abs(before.y / before.x)
        val slopeAfter = abs(after.y / after.x)
        // Vertices are placed at 0.5-unit substep granularity (clamp reflection), so allow a small margin.
        assertTrue(abs(slopeBefore - slopeAfter) / slopeAfter < 0.05f, "slopes $slopeBefore vs $slopeAfter")
        // Horizontal direction flips at the wall.
        assertTrue(before.x * after.x < 0f, "x-direction should reverse at the wall")
    }

    @Test
    fun `preview landing agrees with the projectile simulation across many angles and grids`() {
        val grids = listOf(
            BubbleGrid(emptyMap(), evenCols, 0),
            BubbleGrid(
                mapOf(
                    GridPos(0, 2) to Bubble.Colored(BubbleColor.RED),
                    GridPos(0, 6) to Bubble.Colored(BubbleColor.BLUE),
                    GridPos(3, 4) to Bubble.Colored(BubbleColor.GREEN),
                ),
                evenCols,
                0,
            ),
            BubbleGrid(
                (0 until evenCols).associate { c -> GridPos(0, c) to Bubble.Colored(BubbleColor.PURPLE) } +
                    mapOf(
                        GridPos(1, 2) to Bubble.Colored(BubbleColor.RED),
                        GridPos(1, 5) to Bubble.Colored(BubbleColor.YELLOW),
                    ),
                evenCols,
                0,
            ),
        )

        var checked = 0
        for (grid in grids) {
            var k = -1.5f
            while (k <= 1.5f) {
                val dir = Vec2(k, -1f)
                val preview = AimPath.compute(grid, ceilingY, origin, dir, maxBounces = 3)
                val simCell = simLanding(grid, dir)
                assertEquals(simCell, preview.landingCell, "mismatch at slope $k")
                checked++
                k += 0.15f
            }
        }
        assertTrue(checked >= 60, "expected a broad table of angles, got $checked")
    }
}
