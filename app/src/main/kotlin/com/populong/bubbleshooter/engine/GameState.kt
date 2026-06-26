package com.populong.bubbleshooter.engine

import com.populong.bubbleshooter.game.BubbleGrid
import com.populong.bubbleshooter.game.Projectile

sealed class GameState {
    object Aiming : GameState()

    data class Shooting(val projectile: Projectile) : GameState()

    data class Resolving(
        val matchedCells: Set<BubbleGrid.GridCell>,
        val floatingCells: Set<BubbleGrid.GridCell>,
        var timer: Float = 0f
    ) : GameState()

    object PushingDown : GameState() {
        var timer: Float = 0f
    }

    object GameOver : GameState()
    object LevelComplete : GameState()
    object Paused : GameState() {
        var previousState: GameState = Aiming
    }
}
