package com.populong.bubbleshooter.core.engine

import com.populong.bubbleshooter.core.grid.Bubble
import com.populong.bubbleshooter.core.grid.BubbleGrid
import com.populong.bubbleshooter.core.grid.GridPos
import com.populong.bubbleshooter.core.grid.Vec2
import com.populong.bubbleshooter.core.level.LevelSpec
import com.populong.bubbleshooter.core.mode.GameMode
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AimResultTest {

    private fun polylineLength(points: List<Vec2>): Float {
        var total = 0f
        for (i in 1 until points.size) total += (points[i] - points[i - 1]).length()
        return total
    }

    private fun emptyLevel(): GameMode.Level = GameMode.Level(
        LevelSpec(
            id = 1,
            evenCols = 9,
            paletteSize = 4,
            initialGrid = BubbleGrid(emptyMap<GridPos, Bubble>(), 9, ceilingRow = 0),
            shots = 10,
            starThresholds = listOf(100L, 300L, 1000L),
            bombEvery = 0,
            rainbowEvery = 0,
            descentEveryShots = 0,
        ),
    )

    @Test
    fun `precision aim extends the visible guide length by fifty percent`() {
        // A finite cap over an empty field: the ceiling landing sits far past both the normal (6) and
        // the precision (9) caps, so both previews truncate and the precision polyline is exactly 1.5x.
        val engine = GameEngine(GameConfig(aimLength = 6f))
        val base = engine.initialState(emptyLevel(), seed = 1L)
        val up = Vec2(0f, -1f)

        val normal = base.copy(aimDir = up, precision = false).aimResult()!!
        val precise = base.copy(aimDir = up, precision = true).aimResult()!!

        assertNull(normal.landingCell, "normal preview should be truncated (ghost hidden)")
        assertNull(precise.landingCell, "precision preview should be truncated (ghost hidden)")

        val normalLen = polylineLength(normal.points)
        val preciseLen = polylineLength(precise.points)
        assertTrue(normalLen in (6f - 0.2f)..(6f + 0.2f), "normal length $normalLen ~ 6")
        assertTrue(preciseLen in (9f - 0.2f)..(9f + 0.2f), "precision length $preciseLen ~ 9")
        assertTrue(
            abs(preciseLen - 1.5f * normalLen) < 0.3f,
            "precision length $preciseLen should be ~1.5x normal $normalLen",
        )
    }

    private fun abs(x: Float) = if (x < 0f) -x else x
}
