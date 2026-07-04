package com.populong.bubbleshooter.core.level

import kotlin.test.Test
import kotlin.test.assertTrue

class GreedyBotPlayabilityTest {

    @Test
    fun `the greedy bot clears the generated campaign at a healthy rate`() {
        var wins = 0
        var count = 0
        val tooEasyBosses = ArrayList<String>()

        println("level | rows | bubbles | shots | used/budget | won | score/star1/star2/star3")
        for (n in GEN_MIN..GEN_MAX step 97) {
            val spec = LevelGenerator.generate(n)
            val budget = spec.shots * 3 / 2
            val result = GreedyBot.play(spec, budget)
            count++
            if (result.won) wins++

            val rows = spec.initialGrid.cells.keys.maxOf { it.row } - spec.initialGrid.ceilingRow + 1
            val bubbles = spec.initialGrid.cells.size
            val boss = n % GALAXY_SIZE == 0
            val flag = if (boss) "B" else " "
            val t = spec.starThresholds
            println(
                "$n$flag | $rows | $bubbles | ${spec.shots} | ${result.shotsUsed}/$budget | " +
                    "${if (result.won) "W" else "."} | ${result.finalScore}/${t[0]}/${t[1]}/${t[2]}",
            )

            // Too-easy guard: a boss cleared in a tiny fraction of its budget signals a soft curve.
            if (boss && result.won) {
                val floor = spec.shots * 2 / 5
                if (result.shotsUsed < floor) {
                    tooEasyBosses.add("level $n cleared in ${result.shotsUsed} < $floor shots")
                }
            }
        }

        val pct = wins * 100 / count
        println("PLAYABILITY: $wins / $count won = $pct%")

        assertTrue(wins * 10 >= count * 9, "bot won only $wins/$count (< 90%) of the sample")
        assertTrue(tooEasyBosses.isEmpty(), "bosses cleared too fast (curve too soft): $tooEasyBosses")
    }
}
