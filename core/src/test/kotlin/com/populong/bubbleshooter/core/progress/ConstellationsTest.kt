package com.populong.bubbleshooter.core.progress

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConstellationsTest {

    @Test
    fun `catalog has exactly 12 constellations`() {
        assertEquals(12, Constellations.all.size)
    }

    @Test
    fun `all constellation ids are unique and non-blank`() {
        val ids = Constellations.all.map { it.id }
        assertEquals(ids.toSet().size, ids.size, "duplicate constellation id")
        assertTrue(ids.all { it.isNotBlank() })
    }

    @Test
    fun `costs are ascending from 200 to 3000`() {
        val costs = Constellations.all.map { it.cost }
        assertEquals(costs.sorted(), costs)
        assertEquals(200L, costs.first())
        assertEquals(3000L, costs.last())
    }

    @Test
    fun `every constellation has at least 4 stars and at least one connecting line`() {
        for (def in Constellations.all) {
            assertTrue(def.stars.size >= 4, "${def.id} has fewer than 4 stars")
            assertTrue(def.lines.isNotEmpty(), "${def.id} has no connecting lines")
        }
    }

    @Test
    fun `every line index pair references a valid star index`() {
        for (def in Constellations.all) {
            for ((a, b) in def.lines) {
                assertTrue(a in def.stars.indices, "${def.id} line references invalid index $a")
                assertTrue(b in def.stars.indices, "${def.id} line references invalid index $b")
            }
        }
    }

    @Test
    fun `every star coordinate is normalized within the unit range`() {
        for (def in Constellations.all) {
            for ((x, y) in def.stars) {
                assertTrue(x in 0f..1f, "${def.id} star x out of range: $x")
                assertTrue(y in 0f..1f, "${def.id} star y out of range: $y")
            }
        }
    }

    // -- earnStardust --

    @Test
    fun `earnStardust adds to the balance`() {
        val save = SaveData().earnStardust(200).earnStardust(50)
        assertEquals(250L, save.stardust)
    }

    // -- unlockConstellation --

    @Test
    fun `unlockConstellation succeeds and deducts cost when affordable`() {
        val save = SaveData(stardust = 500).unlockConstellation("dipper", 200)
        assertEquals(300L, save?.stardust)
        assertEquals(setOf("dipper"), save?.unlockedConstellations)
    }

    @Test
    fun `unlockConstellation fails when unaffordable`() {
        val save = SaveData(stardust = 100).unlockConstellation("dipper", 200)
        assertNull(save)
    }

    @Test
    fun `unlockConstellation fails when already unlocked, even with enough stardust`() {
        val save = SaveData(stardust = 5000, unlockedConstellations = setOf("dipper"))
            .unlockConstellation("dipper", 200)
        assertNull(save)
    }

    @Test
    fun `unlockConstellation succeeds exactly at the cost boundary`() {
        val save = SaveData(stardust = 200).unlockConstellation("dipper", 200)
        assertEquals(0L, save?.stardust)
    }
}
