package com.populong.bubbleshooter.mode

import com.populong.bubbleshooter.game.BubbleColor
import com.populong.bubbleshooter.game.BubbleGrid
import java.util.Random

class LevelMode(private val levelData: LevelData) : GameMode {
    private var shotsSinceLastPush = 0

    override fun initializeGrid(grid: BubbleGrid, rng: Random) {
        levelData.populateGrid(grid)
    }

    override fun chooseNextColor(grid: BubbleGrid, rng: Random): BubbleColor {
        val onScreen = grid.colorsOnScreen()
        return if (onScreen.isNotEmpty()) {
            onScreen.toList()[rng.nextInt(onScreen.size)]
        } else {
            BubbleColor.values()[rng.nextInt(BubbleColor.values().size)]
        }
    }

    override fun onShotResolved(
        grid: BubbleGrid,
        matchedCount: Int,
        floatingCount: Int,
        shotNumber: Int
    ): PostShotAction {
        shotsSinceLastPush++
        if (shotsSinceLastPush >= levelData.shotsPerPush) {
            shotsSinceLastPush = 0
            return PostShotAction.PushRowsDown
        }
        return PostShotAction.Nothing
    }

    override fun calculateScore(matchedCount: Int, floatingCount: Int, comboMultiplier: Int): Int {
        return (matchedCount * 10 + floatingCount * 25) * comboMultiplier.coerceAtLeast(1)
    }

    override fun isGameOver(grid: BubbleGrid): Boolean {
        return grid.lowestOccupiedRow() >= grid.maxRows - 2
    }

    override fun isLevelComplete(grid: BubbleGrid): Boolean {
        return grid.occupiedCount() == 0
    }

    fun starsEarned(score: Int): Int {
        return levelData.starThresholds.count { score >= it }
    }

    fun levelNumber(): Int = levelData.levelNumber
}
