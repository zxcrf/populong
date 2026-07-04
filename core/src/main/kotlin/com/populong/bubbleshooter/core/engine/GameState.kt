package com.populong.bubbleshooter.core.engine

import com.populong.bubbleshooter.core.grid.BubbleGrid
import com.populong.bubbleshooter.core.grid.GridGeometry
import com.populong.bubbleshooter.core.grid.Vec2
import com.populong.bubbleshooter.core.mode.GameMode
import com.populong.bubbleshooter.core.physics.AimPath
import com.populong.bubbleshooter.core.physics.AimResult
import com.populong.bubbleshooter.core.physics.Projectile

/** The lifecycle phase of a game. */
enum class Phase { AIMING, FLYING, WON, LOST }

/**
 * A complete, immutable snapshot of a game. Two states produced from the same seed and the same
 * input sequence are always structurally equal — the engine is a pure function of state and input.
 *
 * @property shotsLeft remaining shots, or -1 for unlimited (Endless).
 * @property descentSteps level-mode compression steps applied; each counts as half a row toward the lose line.
 * @property rngState snapshot of the [com.populong.bubbleshooter.core.level.Rng] stream.
 * @property ticks total steps advanced.
 */
data class GameState(
    val mode: GameMode,
    val config: GameConfig,
    val grid: BubbleGrid,
    val phase: Phase,
    val projectile: Projectile?,
    val currentAmmo: Ammo,
    val nextAmmo: Ammo,
    val aimDir: Vec2?,
    val precision: Boolean,
    val shotsFired: Int,
    val shotsLeft: Int,
    val score: Long,
    val combo: Int,
    val feverMeter: Float,
    val feverTicksLeft: Int,
    val descentSteps: Int,
    val rngState: Long,
    val ticks: Long,
) {

    /** True while fever scoring is in effect. */
    val feverActive: Boolean get() = feverTicksLeft > 0

    /** The ceiling plane's y (top edge of ceiling-row bubbles) in unit-radius space. */
    val ceilingY: Float get() = GridGeometry.centerY(grid.ceilingRow) - 1f

    /** The shooter's fixed muzzle position at the bottom-center of the playfield. */
    val shooterOrigin: Vec2
        get() = Vec2(grid.evenCols.toFloat(), ceilingY + config.shooterDistance)

    /**
     * The current aim preview, or null when no valid aim is set. Precision aim previews more wall
     * reflections and, as a skill reward, extends the visible guide length by 50%.
     */
    fun aimResult(): AimResult? {
        val dir = aimDir ?: return null
        val bounces = if (precision) config.aimBouncesPrecision else config.aimBouncesNormal
        val maxLength = config.aimLength * (if (precision) 1.5f else 1f)
        return AimPath.compute(
            grid, ceilingY, shooterOrigin, dir, bounces, maxLength,
            config.projectileSpeed, config.gravityWellStrength,
        )
    }
}
