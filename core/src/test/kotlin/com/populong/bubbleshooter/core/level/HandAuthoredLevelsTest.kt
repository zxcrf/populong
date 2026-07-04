package com.populong.bubbleshooter.core.level

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HandAuthoredLevelsTest {

    @Test
    fun `ids are exactly 1 to 50 and all specs validate`() {
        // Forcing the lazy list runs full DSL validation for every level.
        assertEquals((1..50).toList(), HandAuthoredLevels.all.map { it.id })
    }

    @Test
    fun `every hand-authored level is clearable by the greedy bot within budget`() {
        val failures = mutableListOf<String>()
        for (spec in HandAuthoredLevels.all) {
            val result = GreedyBot.play(spec, maxShots = spec.shots * 3 / 2)
            if (!result.won) {
                failures += "L${spec.id} (shots=${spec.shots}, used=${result.shotsUsed}, score=${result.finalScore})"
            }
        }
        assertTrue(failures.isEmpty(), "unclearable hand levels: $failures")
    }

    @Test
    fun `catalog dispatches hand-authored below 51 and generated above`() {
        assertEquals(1, LevelCatalog.spec(1).id)
        assertEquals(50, LevelCatalog.spec(50).id)
        assertEquals(51, LevelCatalog.spec(51).id)
        assertEquals(2000, LevelCatalog.spec(2000).id)
        assertEquals(1, LevelCatalog.galaxyOf(1))
        assertEquals(1, LevelCatalog.galaxyOf(20))
        assertEquals(2, LevelCatalog.galaxyOf(21))
        assertEquals(100, LevelCatalog.galaxyOf(2000))
        assertEquals(41..60, LevelCatalog.levelsInGalaxy(3))
    }
}
