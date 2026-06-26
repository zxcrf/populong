package com.populong.bubbleshooter.mode

import com.populong.bubbleshooter.game.BubbleColor
import com.populong.bubbleshooter.game.BubbleGrid
import java.util.Random

sealed class PostShotAction {
    object Nothing : PostShotAction()
    object PushRowsDown : PostShotAction()
    data class AddNewRow(val colors: List<BubbleColor?>) : PostShotAction()
}

interface GameMode {
    fun initializeGrid(grid: BubbleGrid, rng: Random)
    fun chooseNextColor(grid: BubbleGrid, rng: Random): BubbleColor
    fun onShotResolved(
        grid: BubbleGrid,
        matchedCount: Int,
        floatingCount: Int,
        shotNumber: Int
    ): PostShotAction
    fun calculateScore(matchedCount: Int, floatingCount: Int, comboMultiplier: Int): Int
    fun isGameOver(grid: BubbleGrid): Boolean
    fun isLevelComplete(grid: BubbleGrid): Boolean = false
    fun shouldSpawnRainbow(consecutiveMisses: Int): Boolean =
        consecutiveMisses >= 3
}
