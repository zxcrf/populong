package com.populong.bubbleshooter.core.level

import com.populong.bubbleshooter.core.mode.Mutator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AimGuideTest {

    @Test
    fun `the tutorial band below the cap start is unbounded`() {
        assertEquals(AimGuide.FULL, AimGuide.lengthForLevel(1))
        assertEquals(AimGuide.FULL, AimGuide.lengthForLevel(20))
    }

    @Test
    fun `the shrinking band starts at the full start length and ends at the floor`() {
        // Level 21 is the first capped level; it opens at the start length (24) and the guide has
        // shrunk fully to the floor (10) by level 200, holding there afterward.
        assertEquals(24f, AimGuide.lengthForLevel(21))
        assertEquals(AimGuide.FINAL_LENGTH, AimGuide.lengthForLevel(200))
        assertEquals(AimGuide.FINAL_LENGTH, AimGuide.lengthForLevel(201))
    }

    @Test
    fun `mid-band the guide interpolates strictly between the start length and the floor`() {
        val mid = AimGuide.lengthForLevel(110)
        assertTrue(mid > AimGuide.FINAL_LENGTH, "level 110 guide $mid should exceed the floor")
        assertTrue(mid < 24f, "level 110 guide $mid should be below the start length")
        // The band is monotonically non-increasing.
        assertTrue(AimGuide.lengthForLevel(110) >= AimGuide.lengthForLevel(150))
        assertTrue(AimGuide.lengthForLevel(150) >= AimGuide.lengthForLevel(200))
    }

    @Test
    fun `endless guide is fourteen, tightened to nine under the short-aim mutator`() {
        assertEquals(14f, AimGuide.lengthForEndless(emptySet()))
        assertEquals(14f, AimGuide.lengthForEndless(setOf(Mutator.NARROW_FIELD)))
        assertEquals(9f, AimGuide.lengthForEndless(setOf(Mutator.SHORT_AIM)))
        assertEquals(9f, AimGuide.lengthForEndless(setOf(Mutator.SHORT_AIM, Mutator.FASTER_DESCENT)))
    }
}
