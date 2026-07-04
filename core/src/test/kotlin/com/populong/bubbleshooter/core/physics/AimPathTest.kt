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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AimPathTest {

    private val evenCols = 9
    private val fieldWidth = 2f * evenCols
    private val ceilingY = 0f
    private val origin = Vec2(evenCols.toFloat(), ceilingY + 12 * GridGeometry.ROW_HEIGHT + 1f)

    private fun simLanding(grid: BubbleGrid, dir: Vec2, gravityStrength: Float = 0f): GridPos {
        var p = Projectile(origin, dir.normalized() * 60f, Ammo.ColorAmmo(BubbleColor.RED), 0)
        repeat(1_000_000) {
            when (val o = ProjectileSim.step(grid, ceilingY, fieldWidth, p, 1f / 120f, gravityStrength, 60f)) {
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

    @Test
    fun `preview landing agrees with the simulation across angles with gravity wells`() {
        val g = 40f
        val wall = (0 until evenCols).associate { c -> GridPos(0, c) to Bubble.Colored(BubbleColor.PURPLE) }
        val grids = listOf(
            // One well.
            BubbleGrid(
                wall + mapOf(GridPos(4, 4) to Bubble.GravityWell),
                evenCols,
                0,
            ),
            // Two wells on opposite halves.
            BubbleGrid(
                wall + mapOf(
                    GridPos(4, 2) to Bubble.GravityWell,
                    GridPos(5, 6) to Bubble.GravityWell,
                ),
                evenCols,
                0,
            ),
        )

        var checked = 0
        for (grid in grids) {
            var k = -1.2f
            while (k <= 1.2f) {
                val dir = Vec2(k, -1f)
                val preview = AimPath.compute(
                    grid, ceilingY, origin, dir, maxBounces = 3, speed = 60f, gravityStrength = g,
                )
                val simCell = simLanding(grid, dir, gravityStrength = g)
                assertEquals(simCell, preview.landingCell, "gravity mismatch at slope $k")
                checked++
                k += 0.15f
            }
        }
        assertTrue(checked >= 30, "expected a broad table of angles, got $checked")
    }

    @Test
    fun `preview landing agrees with the simulation through a wormhole pair`() {
        val wall = (0 until evenCols).associate { c -> GridPos(0, c) to Bubble.Colored(BubbleColor.PURPLE) }
        val grid = BubbleGrid(
            wall + mapOf(
                GridPos(6, 2) to Bubble.Wormhole(1),
                GridPos(6, 6) to Bubble.Wormhole(1),
            ),
            evenCols,
            0,
        )
        var checked = 0
        var k = -1.0f
        while (k <= 1.0f) {
            val dir = Vec2(k, -1f)
            val preview = AimPath.compute(grid, ceilingY, origin, dir, maxBounces = 3, speed = 60f)
            val simCell = simLanding(grid, dir)
            assertEquals(simCell, preview.landingCell, "wormhole mismatch at slope $k")
            checked++
            k += 0.1f
        }
        assertTrue(checked >= 15, "expected a table of angles, got $checked")
    }

    // --- visible-length truncation ------------------------------------------------------------

    private fun polylineLength(points: List<Vec2>): Float {
        var total = 0f
        for (i in 1 until points.size) total += (points[i] - points[i - 1]).length()
        return total
    }

    @Test
    fun `a preview whose cap exceeds the path length is identical to the uncapped preview`() {
        // A full ceiling row so every angle lands quickly; giving a cap comfortably beyond the true
        // path length must leave the result byte-for-byte identical to the uncapped computation.
        val grid = BubbleGrid(
            (0 until evenCols).associate { c -> GridPos(0, c) to Bubble.Colored(BubbleColor.RED) },
            evenCols,
            0,
        )
        var checked = 0
        var k = -1.2f
        while (k <= 1.2f) {
            val dir = Vec2(k, -1f)
            val uncapped = AimPath.compute(grid, ceilingY, origin, dir, maxBounces = 3)
            val slack = polylineLength(uncapped.points) + 5f
            val capped = AimPath.compute(grid, ceilingY, origin, dir, maxBounces = 3, maxLength = slack)
            assertEquals(uncapped.landingCell, capped.landingCell, "landing differs at slope $k")
            assertEquals(uncapped.bounces, capped.bounces, "bounces differ at slope $k")
            assertEquals(uncapped.points, capped.points, "points differ at slope $k")
            checked++
            k += 0.2f
        }
        assertTrue(checked >= 10, "expected a table of angles, got $checked")
    }

    @Test
    fun `a landing beyond the cap hides the ghost but keeps a truncated polyline within the cap`() {
        // Straight up over an empty field: the ceiling is ~12 rows away, far past a 5-unit cap.
        val grid = BubbleGrid(emptyMap(), evenCols, 0)
        val cap = 5f
        val result = AimPath.compute(grid, ceilingY, origin, Vec2(0f, -1f), maxBounces = 1, maxLength = cap)

        assertNull(result.landingCell, "a landing past the cap must hide the ghost")
        assertTrue(result.points.size >= 2, "the truncated polyline must keep its points")
        assertTrue(polylineLength(result.points) <= cap + 1e-3f, "polyline overran the cap")
    }

    @Test
    fun `the truncated polyline never exceeds the cap across a table of angles`() {
        val grid = BubbleGrid(emptyMap(), evenCols, 0)
        val cap = 6f
        var checked = 0
        var k = -1.5f
        while (k <= 1.5f) {
            val r = AimPath.compute(grid, ceilingY, origin, Vec2(k, -1f), maxBounces = 3, maxLength = cap)
            assertTrue(polylineLength(r.points) <= cap + 1e-3f, "cap exceeded at slope $k")
            checked++
            k += 0.15f
        }
        assertTrue(checked >= 15, "expected a table of angles, got $checked")
    }
}
