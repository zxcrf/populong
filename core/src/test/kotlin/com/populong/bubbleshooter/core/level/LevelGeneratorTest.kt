package com.populong.bubbleshooter.core.level

import com.populong.bubbleshooter.core.grid.Bubble
import kotlin.test.Test
import kotlin.test.assertEquals

class LevelGeneratorTest {

    @Test
    fun `generation is deterministic for the same level`() {
        for (n in listOf(51, 137, 500, 1999)) {
            val a = LevelGenerator.generate(n)
            val b = LevelGenerator.generate(n)
            assertEquals(a.initialGrid.cells, b.initialGrid.cells, "cells differ at $n")
            assertEquals(a.initialGrid.evenCols, b.initialGrid.evenCols, "evenCols differ at $n")
            assertEquals(a.initialGrid.ceilingRow, b.initialGrid.ceilingRow, "ceilingRow differ at $n")
            assertEquals(a.paletteSize, b.paletteSize, "palette differ at $n")
            assertEquals(a.shots, b.shots, "shots differ at $n")
            assertEquals(a.starThresholds, b.starThresholds, "stars differ at $n")
            assertEquals(a.bombEvery, b.bombEvery, "bombEvery differ at $n")
            assertEquals(a.rainbowEvery, b.rainbowEvery, "rainbowEvery differ at $n")
            assertEquals(a.descentEveryShots, b.descentEveryShots, "descent differ at $n")
        }
    }

    /**
     * Golden hashes pin the generator's output so accidental drift (a changed constant, a reordered
     * loop) is caught immediately. Recompute deliberately and update only when a change to the
     * generator is intended.
     */
    @Test
    fun `generated levels match their pinned golden hashes`() {
        val golden = mapOf(
            // Levels 51 and 100 predate every gate (supernova 90, pulsar 120, wormhole 160, well 220),
            // so the cosmic-mechanics rollout leaves their hashes byte-for-byte unchanged — they are the
            // stability witnesses. Levels 500/1000/1500/2000 were re-pinned when the pulsar/wormhole/
            // gravity-well passes began consuming RNG and placing bubbles in them.
            51 to -4157053415450925333L,
            100 to -3843162708054950960L,
            500 to -4089252969565699336L,
            1000 to 7342820734415443411L,
            1500 to -4844168299950760733L,
            2000 to 6585627917580355968L,
        )
        for ((n, expected) in golden) {
            assertEquals(expected, goldenHash(LevelGenerator.generate(n)), "golden hash drift at level $n")
        }
    }

    private companion object {

        /** A stable hash of the layout (cells in packed order), palette and shot budget. */
        fun goldenHash(spec: LevelSpec): Long {
            var h = 1125899906842597L
            h = h * 31 + spec.paletteSize
            h = h * 31 + spec.shots
            for (pos in spec.initialGrid.cells.keys.sortedBy { it.packed }) {
                h = h * 1000003 + pos.packed
                h = h * 1000003 + tokenCode(spec.initialGrid.cells.getValue(pos))
            }
            return h
        }

        fun tokenCode(b: Bubble): Int = when (b) {
            is Bubble.Colored -> 100 + b.color.ordinal
            Bubble.Stone -> 200
            is Bubble.Ice -> 300 + b.color.ordinal * 10 + b.hitsLeft
            is Bubble.Fog -> 400 + b.color.ordinal
            is Bubble.Chained -> 500 + b.color.ordinal
            is Bubble.Supernova -> 600 + b.color.ordinal
            is Bubble.Pulsar -> 700 + b.color.ordinal * 10 + if (b.lit) 1 else 0
            Bubble.GravityWell -> 800
            is Bubble.Wormhole -> 900 + b.pairId
        }
    }
}
