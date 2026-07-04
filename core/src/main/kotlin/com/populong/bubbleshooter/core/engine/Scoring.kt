package com.populong.bubbleshooter.core.engine

import kotlin.math.max
import kotlin.math.min

/**
 * Pure scoring formulas, parameterized by [config]. The mode-level Endless mutator multiplier is
 * applied by the engine at award time, not here, so these stay independent of game mode.
 */
class Scoring(private val config: GameConfig) {

    private fun comboMultiplier(combo: Int): Int = min(max(combo, 1), config.comboCap)

    /** Points for popping [n] bubbles at [combo] (>= 1 effective), doubled while [fever] is active. */
    fun popScore(n: Int, combo: Int, fever: Boolean): Long {
        var s = config.popBase * n * comboMultiplier(combo)
        if (fever) s *= config.feverMultiplier
        return s
    }

    /** Points for [n] falling bubbles at [combo] (>= 1 effective), doubled while [fever] is active. */
    fun fallScore(n: Int, combo: Int, fever: Boolean): Long {
        var s = config.fallBase * n * comboMultiplier(combo)
        if (fever) s *= config.feverMultiplier
        return s
    }

    /** Bonus for a bank shot with [bounces] wall reflections. */
    fun bankBonus(bounces: Int): Long = bounces.toLong() * config.bankBonusPerBounce

    /** Number of stars earned by [score] against ascending [thresholds]. */
    fun starsFor(score: Long, thresholds: List<Long>): Int {
        var stars = 0
        for (t in thresholds) if (score >= t) stars++
        return stars
    }
}
