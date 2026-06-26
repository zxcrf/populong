package com.populong.bubbleshooter.mode

import com.populong.bubbleshooter.engine.GameConfig
import com.populong.bubbleshooter.game.Bubble
import com.populong.bubbleshooter.game.BubbleColor
import com.populong.bubbleshooter.game.BubbleGrid
import java.util.Random

class EndlessMode : GameMode {
    private var totalShots = 0
    private val colorPool = BubbleColor.values()

    private val activeColorCount: Int
        get() = (GameConfig.ENDLESS_INITIAL_COLORS +
                totalShots / GameConfig.ENDLESS_SHOTS_PER_COLOR_INCREASE)
            .coerceAtMost(GameConfig.ENDLESS_MAX_COLORS)

    override fun initializeGrid(grid: BubbleGrid, rng: Random) {
        val colors = GameConfig.ENDLESS_INITIAL_COLORS
        for (row in 0 until GameConfig.ENDLESS_INITIAL_ROWS) {
            for (col in 0 until grid.columns) {
                if (rng.nextFloat() > 0.12f) {
                    grid.set(
                        BubbleGrid.GridCell(row, col),
                        Bubble(colorPool[rng.nextInt(colors)])
                    )
                }
            }
        }
        createIsland(grid, rng)
    }

    override fun chooseNextColor(grid: BubbleGrid, rng: Random): BubbleColor {
        val onScreen = grid.colorsOnScreen()
        return if (onScreen.isNotEmpty()) {
            onScreen.toList()[rng.nextInt(onScreen.size)]
        } else {
            colorPool[rng.nextInt(activeColorCount)]
        }
    }

    override fun onShotResolved(
        grid: BubbleGrid,
        matchedCount: Int,
        floatingCount: Int,
        shotNumber: Int
    ): PostShotAction {
        totalShots = shotNumber
        if (shotNumber > 0 && shotNumber % GameConfig.ENDLESS_SHOTS_PER_NEW_ROW == 0) {
            return PostShotAction.AddNewRow(generateNewRow(grid))
        }
        return PostShotAction.Nothing
    }

    override fun calculateScore(matchedCount: Int, floatingCount: Int, comboMultiplier: Int): Int {
        return (matchedCount * 10 + floatingCount * 20) * comboMultiplier.coerceAtLeast(1)
    }

    override fun isGameOver(grid: BubbleGrid): Boolean {
        return grid.lowestOccupiedRow() >= grid.maxRows - 2
    }

    private fun generateNewRow(grid: BubbleGrid): List<BubbleColor?> {
        val rng = Random()
        return (0 until grid.columns).map {
            if (rng.nextFloat() > 0.1f) colorPool[rng.nextInt(activeColorCount)]
            else null
        }
    }

    private fun createIsland(grid: BubbleGrid, rng: Random) {
        val bridgeRow = 3
        val bridgeCol = rng.nextInt(grid.columns - 2) + 1
        val islandColor = colorPool[rng.nextInt(GameConfig.ENDLESS_INITIAL_COLORS)]
        val bridgeColor = colorPool.first { it != islandColor }

        grid.set(BubbleGrid.GridCell(bridgeRow, bridgeCol), Bubble(bridgeColor))

        val islandCells = grid.neighbors(BubbleGrid.GridCell(bridgeRow, bridgeCol))
            .filter { it.row > bridgeRow && it.col in 0 until grid.columns }
            .take(3)
        for (cell in islandCells) {
            grid.set(cell, Bubble(islandColor))
        }
    }
}
