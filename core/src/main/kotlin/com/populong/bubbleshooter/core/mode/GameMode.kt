package com.populong.bubbleshooter.core.mode

import com.populong.bubbleshooter.core.level.LevelSpec

/**
 * Which flavor of the game the engine is running. The mode fixes the field width, palette,
 * pacing and win/lose conditions.
 */
sealed interface GameMode {

    /** A finite hand-authored level; won by clearing the grid, lost by running out of shots or space. */
    data class Level(val spec: LevelSpec) : GameMode

    /**
     * An endless survival run. Field width is 8 (7 with [Mutator.NARROW_FIELD]); palette is 4
     * (5 with [Mutator.EXTRA_COLOR]); a new row is inserted every 6 shots (4 with
     * [Mutator.FASTER_DESCENT]). It never wins — it is lost when bubbles reach the bottom line.
     */
    data class Endless(val mutators: Set<Mutator>) : GameMode

    /** A daily challenge; its [spec] is supplied by a later phase, and the engine treats it like [Level]. */
    data class Daily(val spec: LevelSpec) : GameMode
}
