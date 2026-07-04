package com.populong.bubbleshooter.core.level

import com.populong.bubbleshooter.core.mode.Mutator

/**
 * The aim-guide length curve. Early levels grant a full-length preview as a tutorial aid; the guide
 * then shrinks with the campaign so late-game play leans on the player's own trajectory intuition.
 * The values feed [com.populong.bubbleshooter.core.engine.GameConfig.aimLength], which
 * [com.populong.bubbleshooter.core.engine.GameState.aimResult] passes to
 * [com.populong.bubbleshooter.core.physics.AimPath.compute] as its visible-length cap.
 */
object AimGuide {

    /** An unbounded guide: the preview always reaches its landing. */
    const val FULL: Float = Float.MAX_VALUE

    /** The floor the guide length shrinks to for the late campaign, in unit-radius space. */
    const val FINAL_LENGTH: Float = 10f

    /** Guide length at the start of the shrinking band (level 21). */
    private const val START_LENGTH: Float = 24f

    /** First level whose guide is capped. */
    private const val CAP_START: Int = 21

    /** Level by which the guide has shrunk fully to [FINAL_LENGTH]. */
    private const val CAP_END: Int = 200

    /**
     * The visible aim-guide length for campaign [level]:
     * levels `1..20` get [FULL]; `21..200` shrink linearly from [START_LENGTH] to [FINAL_LENGTH];
     * everything past 200 stays at [FINAL_LENGTH].
     */
    fun lengthForLevel(level: Int): Float = when {
        level < CAP_START -> FULL
        level >= CAP_END -> FINAL_LENGTH
        else -> {
            val t = (level - CAP_START).toFloat() / (CAP_END - CAP_START).toFloat()
            START_LENGTH + (FINAL_LENGTH - START_LENGTH) * t
        }
    }

    /** The visible aim-guide length for an endless run: `14`, tightened to `9` under [Mutator.SHORT_AIM]. */
    fun lengthForEndless(mutators: Set<Mutator>): Float =
        if (Mutator.SHORT_AIM in mutators) 9f else 14f
}
