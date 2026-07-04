package com.populong.bubbleshooter.core.engine

import com.populong.bubbleshooter.core.grid.BubbleColor
import com.populong.bubbleshooter.core.grid.GridPos
import com.populong.bubbleshooter.core.grid.Vec2

/**
 * A discrete thing that happened during a step, emitted in resolution order. Events are the engine's
 * only channel for presentation (animations, audio, haptics); state carries the authoritative facts.
 */
sealed interface GameEvent {

    /** A projectile was launched. */
    data object Fired : GameEvent

    /** A color match cleared [cells]; [color] is the matched color, [scoreGained] the points awarded. */
    data class Popped(val cells: Set<GridPos>, val color: BubbleColor?, val scoreGained: Long) : GameEvent

    /** A bomb removed [cells]. */
    data class BombExploded(val cells: Set<GridPos>) : GameEvent

    /**
     * One or more supernovae detonated during a single shot resolution. [origins] are the supernova
     * cells that fired, [removed] every cell cleared by the chained shockwave (including the seeding
     * match and the origins), and [waves] the number of chain generations that detonated.
     */
    data class SupernovaChained(
        val origins: Set<GridPos>,
        val removed: Set<GridPos>,
        val waves: Int,
    ) : GameEvent

    /** [cells] fell after losing support; [scoreGained] the points awarded. */
    data class Fell(val cells: Set<GridPos>, val scoreGained: Long) : GameEvent

    /** The projectile reflected off a wall at [at]. */
    data class Bounced(val at: Vec2) : GameEvent

    /** The projectile came to rest at [cell]. */
    data class Landed(val cell: GridPos) : GameEvent

    /** A shot that first bounced off a wall then popped, earning [bonus] for [bounces] reflections. */
    data class BankShot(val bounces: Int, val bonus: Long) : GameEvent

    /** [cells] of ice lost a hit (and possibly thawed). */
    data class IceCracked(val cells: Set<GridPos>) : GameEvent

    /** [cells] of chained bubbles were unlocked. */
    data class Unchained(val cells: Set<GridPos>) : GameEvent

    /** [cells] of fog were revealed. */
    data class FogRevealed(val cells: Set<GridPos>) : GameEvent

    /** Fever mode began. */
    data object FeverStarted : GameEvent

    /** Fever mode ended. */
    data object FeverEnded : GameEvent

    /** A non-popping shot reset the combo. */
    data object ComboBroken : GameEvent

    /** The current and next ammo were swapped. */
    data object Swapped : GameEvent

    /** A fresh row was inserted at the top (Endless). */
    data object RowInserted : GameEvent

    /** The ceiling compressed one step (Level). */
    data object CeilingStepped : GameEvent

    /** The level was won with [stars] stars at [finalScore]. */
    data class Won(val stars: Int, val finalScore: Long) : GameEvent

    /** The game was lost at [finalScore]. */
    data class Lost(val finalScore: Long) : GameEvent
}
