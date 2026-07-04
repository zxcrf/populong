package com.populong.bubbleshooter.core.progress

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AchievementsTest {

    @Test
    fun `all achievement ids are unique and non-blank`() {
        val ids = Achievements.all.map { it.id }
        assertEquals(ids.toSet().size, ids.size, "duplicate achievement id")
        assertTrue(ids.all { it.isNotBlank() && it == it.lowercase() })
    }

    @Test
    fun `evaluate on a fresh save reports nothing`() {
        assertEquals(emptySet(), Achievements.evaluate(SaveData()))
    }

    @Test
    fun `evaluate reports first level cleared`() {
        val save = SaveData().withLevelResult(level = 1, stars = 1, score = 10)
        assertEquals(setOf("level_1_cleared"), Achievements.evaluate(save))
    }

    @Test
    fun `evaluate reports level 20, 50, 100, 500 milestones`() {
        var save = SaveData()
        save = save.withLevelResult(level = 20, stars = 1, score = 10)
        save = save.withLevelResult(level = 50, stars = 1, score = 10)
        save = save.withLevelResult(level = 100, stars = 1, score = 10)
        save = save.withLevelResult(level = 500, stars = 1, score = 10)

        val reported = Achievements.evaluate(save)
        assertEquals(
            // Completing level 500 also unlocks (i.e. makes playable) level 501, which lies
            // in galaxy 10, so that achievement fires too.
            setOf("level_20_cleared", "level_50_cleared", "level_100_cleared", "level_500_cleared", "galaxy_10_unlocked"),
            reported,
        )
    }

    @Test
    fun `evaluate reports galaxy 10 unlocked once level 180 is starred`() {
        // Completing level 180 makes level 181 (the first level of galaxy 10) playable.
        val save = SaveData().withLevelResult(level = 180, stars = 1, score = 10)
        assertTrue("galaxy_10_unlocked" in Achievements.evaluate(save))
    }

    @Test
    fun `evaluate does not report galaxy 10 unlocked before level 180`() {
        val save = SaveData().withLevelResult(level = 179, stars = 3, score = 10)
        assertTrue("galaxy_10_unlocked" !in Achievements.evaluate(save))
    }

    @Test
    fun `evaluate reports combo achievements at their thresholds`() {
        val at5 = SaveData(stats = CareerStats(maxCombo = 5))
        assertTrue("combo_5" in Achievements.evaluate(at5))
        assertTrue("combo_10" !in Achievements.evaluate(at5))

        val at10 = SaveData(stats = CareerStats(maxCombo = 10))
        assertTrue("combo_5" in Achievements.evaluate(at10))
        assertTrue("combo_10" in Achievements.evaluate(at10))
    }

    @Test
    fun `evaluate reports bank shots, bubbles dropped, bombs, fevers thresholds`() {
        val save = SaveData(
            stats = CareerStats(bankShots = 50, bubblesDropped = 1000, bombsDetonated = 25, feversTriggered = 10),
        )
        val reported = Achievements.evaluate(save)
        assertEquals(
            setOf("bank_shots_50", "bubbles_dropped_1000", "bombs_detonated_25", "fevers_triggered_10"),
            reported,
        )
    }

    @Test
    fun `evaluate reports three-star and shots-fired collection milestones`() {
        val save = SaveData(stats = CareerStats(threeStarLevels = 50, shotsFired = 10_000))
        val reported = Achievements.evaluate(save)
        assertEquals(
            setOf("three_star_10", "three_star_50", "shots_fired_1000", "shots_fired_10000"),
            reported,
        )
    }

    @Test
    fun `evaluate reports daily streak and completed-days milestones`() {
        var save = SaveData()
        for (day in 1L..7L) save = save.withDailyCompleted(day)
        assertTrue("daily_streak_3" in Achievements.evaluate(save))
        assertTrue("daily_streak_7" in Achievements.evaluate(save))
        assertTrue("daily_completed_30" !in Achievements.evaluate(save))

        for (day in 8L..30L) save = save.withDailyCompleted(day)
        assertTrue("daily_completed_30" in Achievements.evaluate(save))
    }

    @Test
    fun `evaluate excludes achievements already recorded as earned`() {
        val save = SaveData(
            stats = CareerStats(maxCombo = 5),
            achievements = setOf("combo_5"),
        )
        assertEquals(emptySet(), Achievements.evaluate(save))
    }

    @Test
    fun `evaluate reports newly satisfied achievements exactly once even if condition remains true`() {
        val save = SaveData(stats = CareerStats(maxCombo = 5))
        val firstPass = Achievements.evaluate(save)
        assertEquals(setOf("combo_5"), firstPass)

        val updated = save.copy(achievements = save.achievements + firstPass)
        val secondPass = Achievements.evaluate(updated)
        assertEquals(emptySet(), secondPass)
    }

    @Test
    fun `every achievement is reachable and none accidentally overlap in unexpected ways`() {
        // Sanity: the highest-tier save should satisfy every achievement.
        var save = SaveData(
            stats = CareerStats(
                shotsFired = 20_000, shotsPopped = 5000, bubblesPopped = 20_000,
                bubblesDropped = 5000, maxCombo = 20, bankShots = 200,
                bombsDetonated = 100, feversTriggered = 50,
                levelsCompleted = 500, threeStarLevels = 500, totalScore = 1_000_000,
            ),
        )
        for (level in listOf(1, 20, 50, 100, 500, 181)) {
            save = save.withLevelResult(level = level, stars = 3, score = 100)
        }
        var withDaily = save
        for (day in 1L..30L) withDaily = withDaily.withDailyCompleted(day)

        assertEquals(Achievements.all.map { it.id }.toSet(), Achievements.evaluate(withDaily))
    }
}
