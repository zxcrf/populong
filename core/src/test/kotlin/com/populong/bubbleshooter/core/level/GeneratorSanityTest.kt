package com.populong.bubbleshooter.core.level

import com.populong.bubbleshooter.core.grid.Bubble
import com.populong.bubbleshooter.core.grid.BubbleColor
import kotlin.test.Test
import kotlin.test.assertEquals
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
                    is Bubble.Supernova -> {
                        assertTrue(n >= SUPERNOVA_GATE, "level $n has a supernova before the gate at $pos")
                        val reachable = grid.neighbors(pos).any { nb ->
                            when (val b = grid.bubbleAt(nb)) {
                                is Bubble.Colored -> b.color == bubble.color
                                is Bubble.Supernova -> b.color == bubble.color
                                else -> false
                            }
                        }
                        assertTrue(reachable, "level $n supernova $pos has no same-color neighbor")
                    }
                    is Bubble.Pulsar ->
                        assertTrue(n >= PULSAR_GATE, "level $n has a pulsar before the gate at $pos")
                    is Bubble.GravityWell -> {
                        assertTrue(n >= GRAVITY_WELL_GATE, "level $n has a well before the gate at $pos")
                        assertTrue(pos.row != bottom, "level $n has a well on the bottom row at $pos")
                        assertTrue(pos.row != grid.ceilingRow, "level $n has a well on the ceiling row at $pos")
                    }
                    is Bubble.Wormhole ->
                        assertTrue(n >= WORMHOLE_GATE, "level $n has a wormhole before the gate at $pos")
                    else -> Unit
                }
            }

            // Supernovae are hard-capped per level.
            val supernovae = grid.cells.values.count { it is Bubble.Supernova }
            assertTrue(supernovae <= 2, "level $n has $supernovae supernovae (cap is 2)")

            // Pulsars are hard-capped per level.
            val pulsars = grid.cells.values.count { it is Bubble.Pulsar }
            assertTrue(pulsars <= 4, "level $n has $pulsars pulsars (cap is 4)")

            // Gravity wells: at most 2, never adjacent to each other.
            val wells = grid.cells.filterValues { it is Bubble.GravityWell }.keys
            assertTrue(wells.size <= 2, "level $n has ${wells.size} gravity wells (cap is 2)")
            for (a in wells) for (b in wells) {
                if (a != b) assertTrue(b !in grid.neighbors(a), "level $n has adjacent wells $a,$b")
            }

            // Wormholes: at most one pair, each pairId appearing exactly twice, portals on opposite
            // halves of the field, in mid rows, never adjacent to each other.
            val wormholes = grid.cells.filterValues { it is Bubble.Wormhole }
            val byPair = wormholes.entries.groupBy { (it.value as Bubble.Wormhole).pairId }
            assertTrue(byPair.size <= 1, "level $n has ${byPair.size} wormhole pairs (expected at most 1)")
            for ((pairId, portals) in byPair) {
                assertEquals(2, portals.size, "level $n wormhole pairId $pairId has ${portals.size} portals")
                val cols = portals.map { it.key.col }.sorted()
                assertTrue(cols[0] < 4 && cols[1] >= 4, "level $n wormhole pair not on opposite halves: $cols")
                val rows = portals.map { it.key.row }
                assertTrue(
                    rows.all { it != grid.ceilingRow && it != bottom },
                    "level $n wormhole pair not in mid rows: $rows",
                )
                val (a, b) = portals.map { it.key }
                assertTrue(b !in grid.neighbors(a), "level $n wormhole portals $a,$b are adjacent")
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
        is Bubble.Supernova -> bubble.color
        is Bubble.Pulsar -> bubble.color
        Bubble.Stone, Bubble.GravityWell, is Bubble.Wormhole -> null
    }
}
