package com.populong.bubbleshooter.core.level

import com.populong.bubbleshooter.core.grid.Bubble
import com.populong.bubbleshooter.core.grid.BubbleColor
import kotlin.test.Test
import kotlin.test.assertTrue

class GeneratorSanityTest {

    /** A representative sample plus every boss falling in the sampled range. */
    private fun sampleLevels(): List<Int> {
        val levels = sortedSetOf<Int>()
        levels += (GEN_MIN..GEN_MAX step 61)
        levels += (60..GEN_MAX step GALAXY_SIZE) // all bosses
        return levels.toList()
    }

    @Test
    fun `every sampled level obeys the structural invariants`() {
        for (n in sampleLevels()) {
            val spec = LevelGenerator.generate(n)
            val grid = spec.initialGrid
            val params = paramsFor(n)

            assertTrue(grid.cells.isNotEmpty(), "level $n grid is empty")

            val bottom = grid.cells.keys.maxOf { it.row }
            assertTrue(
                grid.cells.keys.all { it.row <= params.rows + 2 },
                "level $n has a cell below row ${params.rows + 2}",
            )

            // At least 3 cells of every palette color (obstacles keep their inherent color).
            val palette = BubbleColor.palette(spec.paletteSize)
            for (color in palette) {
                val count = grid.cells.values.count { inherentColor(it) == color }
                assertTrue(count >= 3, "level $n has only $count of color $color")
            }

            // Obstacle placement rules.
            for ((pos, bubble) in grid.cells) {
                when (bubble) {
                    is Bubble.Stone ->
                        assertTrue(pos.row != bottom, "level $n has a stone on the bottom row at $pos")
                    is Bubble.Fog ->
                        assertTrue(pos.row >= 2, "level $n has fog too near the ceiling at $pos")
                    is Bubble.Chained -> {
                        val free = grid.neighbors(pos).any { nb ->
                            val b = grid.bubbleAt(nb)
                            b == null || (b !is Bubble.Stone && b !is Bubble.Chained)
                        }
                        assertTrue(free, "level $n chained cell $pos has no free neighbor")
                    }
                    else -> Unit
                }
            }

            // Scoring and budget.
            val stars = spec.starThresholds
            assertTrue(stars[0] < stars[1] && stars[1] < stars[2], "level $n stars not ascending: $stars")
            assertTrue(spec.shots > 0, "level $n has non-positive shots")
        }
    }

    private fun inherentColor(bubble: Bubble): BubbleColor? = when (bubble) {
        is Bubble.Colored -> bubble.color
        is Bubble.Ice -> bubble.color
        is Bubble.Fog -> bubble.color
        is Bubble.Chained -> bubble.color
        Bubble.Stone -> null
    }
}
