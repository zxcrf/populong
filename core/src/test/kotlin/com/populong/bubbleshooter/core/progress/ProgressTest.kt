package com.populong.bubbleshooter.core.progress

import com.populong.bubbleshooter.core.level.LevelCatalog
import com.populong.bubbleshooter.core.mode.Mutator
import kotlin.test.Test
import kotlin.test.assertEquals

class ProgressTest {

    // -- withLevelResult --

    @Test
    fun `withLevelResult records first completion and bumps levelsCompleted`() {
        val save = SaveData().withLevelResult(level = 5, stars = 2, score = 100)

        assertEquals(LevelResult(2, 100), save.levels[5])
        assertEquals(1, save.stats.levelsCompleted)
        assertEquals(0, save.stats.threeStarLevels)
    }

    @Test
    fun `withLevelResult keeps the maximum stars and score across replays`() {
        val save = SaveData()
            .withLevelResult(level = 5, stars = 2, score = 500)
            .withLevelResult(level = 5, stars = 1, score = 800)
            .withLevelResult(level = 5, stars = 3, score = 200)

        assertEquals(LevelResult(stars = 3, bestScore = 800), save.levels[5])
    }

    @Test
    fun `withLevelResult only counts levelsCompleted on the first completion`() {
        val save = SaveData()
            .withLevelResult(level = 5, stars = 1, score = 100)
            .withLevelResult(level = 5, stars = 2, score = 150)
            .withLevelResult(level = 5, stars = 3, score = 200)

        assertEquals(1, save.stats.levelsCompleted)
    }

    @Test
    fun `withLevelResult counts threeStarLevels only the first time 3 stars is reached`() {
        val save = SaveData()
            .withLevelResult(level = 5, stars = 3, score = 100)
            .withLevelResult(level = 5, stars = 3, score = 150)
            .withLevelResult(level = 6, stars = 3, score = 100)

        assertEquals(2, save.stats.threeStarLevels)
    }

    @Test
    fun `withLevelResult across distinct levels counts each independently`() {
        val save = SaveData()
            .withLevelResult(level = 1, stars = 1, score = 10)
            .withLevelResult(level = 2, stars = 3, score = 20)
            .withLevelResult(level = 3, stars = 2, score = 30)

        assertEquals(3, save.stats.levelsCompleted)
        assertEquals(1, save.stats.threeStarLevels)
    }

    // -- withEndlessScore --

    @Test
    fun `withEndlessScore inserts and sorts descending`() {
        val save = SaveData()
            .withEndlessScore(100, emptySet(), 1)
            .withEndlessScore(500, emptySet(), 2)
            .withEndlessScore(300, emptySet(), 3)

        assertEquals(listOf(500L, 300L, 100L), save.endlessHighs.map { it.score })
    }

    @Test
    fun `withEndlessScore trims to at most 10 entries keeping the highest`() {
        var save = SaveData()
        for (score in 1..15) {
            save = save.withEndlessScore(score.toLong(), emptySet(), score.toLong())
        }

        assertEquals(10, save.endlessHighs.size)
        assertEquals((15 downTo 6).map { it.toLong() }, save.endlessHighs.map { it.score })
    }

    @Test
    fun `withEndlessScore preserves mutators and epochDay on inserted record`() {
        val save = SaveData().withEndlessScore(777, setOf(Mutator.NARROW_FIELD, Mutator.SHORT_AIM), 42)

        val record = save.endlessHighs.single()
        assertEquals(777L, record.score)
        assertEquals(setOf(Mutator.NARROW_FIELD, Mutator.SHORT_AIM), record.mutators)
        assertEquals(42L, record.epochDay)
    }

    // -- withDailyCompleted --

    @Test
    fun `withDailyCompleted starts a streak of 1 from a fresh save`() {
        val save = SaveData().withDailyCompleted(10)

        assertEquals(1, save.daily.streak)
        assertEquals(10L, save.daily.lastCompletedEpochDay)
        assertEquals(setOf(10L), save.daily.completedDays)
    }

    @Test
    fun `withDailyCompleted increments streak on consecutive days`() {
        val save = SaveData()
            .withDailyCompleted(10)
            .withDailyCompleted(11)
            .withDailyCompleted(12)

        assertEquals(3, save.daily.streak)
        assertEquals(12L, save.daily.lastCompletedEpochDay)
    }

    @Test
    fun `withDailyCompleted completing the same day twice is idempotent`() {
        val save = SaveData()
            .withDailyCompleted(10)
            .withDailyCompleted(11)
            .withDailyCompleted(11)

        assertEquals(2, save.daily.streak)
        assertEquals(11L, save.daily.lastCompletedEpochDay)
    }

    @Test
    fun `withDailyCompleted resets streak to 1 after a gap`() {
        val save = SaveData()
            .withDailyCompleted(10)
            .withDailyCompleted(11)
            .withDailyCompleted(20)

        assertEquals(1, save.daily.streak)
        assertEquals(20L, save.daily.lastCompletedEpochDay)
    }

    @Test
    fun `withDailyCompleted replaying an older already-completed day does not disturb the streak`() {
        val save = SaveData()
            .withDailyCompleted(10)
            .withDailyCompleted(11)
            .withDailyCompleted(10) // out-of-order, older, but already recorded

        assertEquals(2, save.daily.streak)
        assertEquals(11L, save.daily.lastCompletedEpochDay)
        assertEquals(setOf(10L, 11L), save.daily.completedDays)
    }

    @Test
    fun `withDailyCompleted always records the day even when it does not advance the streak`() {
        val save = SaveData().withDailyCompleted(10).withDailyCompleted(50)

        assertEquals(setOf(10L, 50L), save.daily.completedDays)
    }

    // -- highestUnlockedLevel --

    @Test
    fun `highestUnlockedLevel is 1 for an empty save`() {
        assertEquals(1, SaveData().highestUnlockedLevel())
    }

    @Test
    fun `highestUnlockedLevel is one past the highest starred level`() {
        val save = SaveData(levels = mapOf(1 to LevelResult(3, 10), 5 to LevelResult(1, 10), 3 to LevelResult(2, 10)))
        assertEquals(6, save.highestUnlockedLevel())
    }

    @Test
    fun `highestUnlockedLevel ignores levels with zero stars`() {
        val save = SaveData(levels = mapOf(1 to LevelResult(1, 10), 2 to LevelResult(0, 999)))
        assertEquals(2, save.highestUnlockedLevel())
    }

    @Test
    fun `highestUnlockedLevel clamps to the catalog total`() {
        val save = SaveData(levels = mapOf(LevelCatalog.TOTAL to LevelResult(3, 10)))
        assertEquals(LevelCatalog.TOTAL, save.highestUnlockedLevel())
    }

    // -- withRun --

    @Test
    fun `withRun keeps the maximum combo across runs`() {
        val stats = CareerStats(maxCombo = 5).withRun(maxComboInRun = 3, runScore = 0)
        assertEquals(5, stats.maxCombo)

        val stats2 = CareerStats(maxCombo = 5).withRun(maxComboInRun = 9, runScore = 0)
        assertEquals(9, stats2.maxCombo)
    }

    @Test
    fun `withRun sums total score across runs`() {
        val stats = CareerStats(totalScore = 100).withRun(maxComboInRun = 0, runScore = 250)
        assertEquals(350L, stats.totalScore)
    }
}
