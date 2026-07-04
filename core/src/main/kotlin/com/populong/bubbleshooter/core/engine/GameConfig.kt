package com.populong.bubbleshooter.core.engine

/**
 * Every tunable constant the engine reads. Defaults define the shipping balance; tests may vary
 * them to isolate behavior. Held inside [GameState] so a snapshot fully determines the simulation.
 */
data class GameConfig(
    val TICK: Float = 1f / 120f,
    val projectileSpeed: Float = 60f,
    val aimBouncesNormal: Int = 1,
    val aimBouncesPrecision: Int = 3,
    val maxRows: Int = 12,
    val bombRadius: Int = 2,
    val popBase: Long = 10L,
    val fallBase: Long = 20L,
    val bankBonusPerBounce: Long = 50L,
    val comboCap: Int = 6,
    val feverGainPerPop: Float = 0.18f,
    val feverGainPerBubble: Float = 0.012f,
    val feverLossOnMiss: Float = 0.15f,
    val feverDurationTicks: Int = 1200,
    val feverMultiplier: Int = 2,
    val clearBonusPerRemainingShot: Long = 100L,
    val endlessRowEveryShots: Int = 6,
    val endlessRowEveryShotsFast: Int = 4,
)
