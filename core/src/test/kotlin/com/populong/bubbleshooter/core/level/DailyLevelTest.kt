package com.populong.bubbleshooter.core.level

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DailyLevelTest {

    @Test
    fun `the daily level is deterministic for a fixed day`() {
        for (day in listOf(0L, 1L, 19_800L, 20_275L, 100_000L)) {
            val a = DailyLevel.forEpochDay(day)
            val b = DailyLevel.forEpochDay(day)
            assertEquals(a.id, b.id, "id differ at day $day")
            assertEquals(a.initialGrid.cells, b.initialGrid.cells, "cells differ at day $day")
            assertEquals(a.shots, b.shots, "shots differ at day $day")
            assertEquals(a.starThresholds, b.starThresholds, "stars differ at day $day")
        }
    }

    @Test
    fun `different days produce different boards`() {
        val a = DailyLevel.forEpochDay(20_275L)
        val b = DailyLevel.forEpochDay(20_276L)
        assertTrue(a.initialGrid.cells != b.initialGrid.cells, "consecutive days should differ")
    }

    @Test
    fun `the daily level has its mid-high difficulty profile`() {
        val spec = DailyLevel.forEpochDay(20_275L)
        assertEquals(5, spec.paletteSize, "daily uses five colors")
        assertTrue(spec.id >= DailyLevel.DAILY_ID_BASE, "daily ids live in their own space")
        val rows = spec.initialGrid.cells.keys.maxOf { it.row } - spec.initialGrid.ceilingRow + 1
        assertTrue(rows in 9..11, "daily rows should sit in the 9..11 band, was $rows")
        assertEquals(0, spec.descentEveryShots, "daily never compresses the ceiling")
        assertTrue(spec.shots > 0)
    }

    @Test
    fun `the daily board is clearable by the greedy bot`() {
        val spec = DailyLevel.forEpochDay(20_275L)
        val result = GreedyBot.play(spec, spec.shots * 3 / 2)
        assertTrue(result.won, "greedy bot could not clear the daily within 1.5x shots")
    }
}
