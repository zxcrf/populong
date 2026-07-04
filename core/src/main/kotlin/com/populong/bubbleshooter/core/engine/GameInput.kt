package com.populong.bubbleshooter.core.engine

import com.populong.bubbleshooter.core.grid.Vec2

/** A player intent handed to [GameEngine.handleInput]. */
sealed interface GameInput {

    /** Point the shooter along [dir]; ignored if it aims level or downward (`dir.y > -0.05`). */
    data class AimAt(val dir: Vec2) : GameInput

    /** Launch the current ammo along the stored aim direction. */
    data object Fire : GameInput

    /** Swap the current and next ammo (only while aiming). */
    data object Swap : GameInput

    /** Toggle the precision aim assist (more preview bounces). */
    data class Precision(val on: Boolean) : GameInput
}
