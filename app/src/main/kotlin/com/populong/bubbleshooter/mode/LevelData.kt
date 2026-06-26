package com.populong.bubbleshooter.mode

import com.populong.bubbleshooter.game.Bubble
import com.populong.bubbleshooter.game.BubbleColor
import com.populong.bubbleshooter.game.BubbleColor.*
import com.populong.bubbleshooter.game.BubbleGrid

data class LevelData(
    val levelNumber: Int,
    val layout: List<List<BubbleColor?>>,
    val shotsPerPush: Int,
    val starThresholds: List<Int>
) {
    fun populateGrid(grid: BubbleGrid) {
        grid.clear()
        for (row in layout.indices) {
            for (col in layout[row].indices) {
                val color = layout[row][col] ?: continue
                grid.set(BubbleGrid.GridCell(row, col), Bubble(color))
            }
        }
    }

    companion object {
        fun totalLevels(): Int = 5

        fun getLevel(number: Int): LevelData = when (number) {
            1 -> level1()
            2 -> level2()
            3 -> level3()
            4 -> level4()
            5 -> level5()
            else -> level1()
        }

        private fun level1(): LevelData {
            val r = RED; val b = BLUE; val n: BubbleColor? = null
            return LevelData(
                levelNumber = 1,
                layout = listOf(
                    listOf(r, b, r, b, r, b, r, b, r, b),
                    listOf(b, r, b, r, b, r, b, r, b, n),
                    listOf(r, b, r, b, r, b, r, b, r, b),
                ),
                shotsPerPush = 12,
                starThresholds = listOf(100, 250, 500)
            )
        }

        private fun level2(): LevelData {
            val r = RED; val b = BLUE; val g = GREEN; val n: BubbleColor? = null
            return LevelData(
                levelNumber = 2,
                layout = listOf(
                    listOf(r, r, b, b, g, g, b, b, r, r),
                    listOf(r, b, b, g, g, b, b, r, r, n),
                    listOf(g, g, r, r, b, b, r, r, g, g),
                    listOf(n, g, r, b, b, r, r, g, n, n),
                ),
                shotsPerPush = 10,
                starThresholds = listOf(200, 400, 700)
            )
        }

        private fun level3(): LevelData {
            val r = RED; val b = BLUE; val g = GREEN; val y = YELLOW; val n: BubbleColor? = null
            return LevelData(
                levelNumber = 3,
                layout = listOf(
                    listOf(y, r, b, g, y, r, b, g, y, r),
                    listOf(r, b, g, y, r, b, g, y, r, n),
                    listOf(b, g, y, r, b, g, y, r, b, g),
                    listOf(g, y, r, b, g, y, r, b, g, n),
                    listOf(n, n, n, y, y, y, n, n, n, n),
                ),
                shotsPerPush = 8,
                starThresholds = listOf(300, 600, 1000)
            )
        }

        private fun level4(): LevelData {
            val r = RED; val b = BLUE; val g = GREEN; val y = YELLOW
            val p = PURPLE; val n: BubbleColor? = null
            return LevelData(
                levelNumber = 4,
                layout = listOf(
                    listOf(p, p, r, r, b, b, g, g, p, p),
                    listOf(p, r, r, b, b, g, g, p, p, n),
                    listOf(r, r, b, b, g, g, p, p, r, r),
                    listOf(n, b, b, g, g, p, p, r, r, n),
                    listOf(n, n, y, n, n, n, y, n, n, n),
                    listOf(n, n, n, y, y, y, n, n, n, n),
                ),
                shotsPerPush = 7,
                starThresholds = listOf(400, 800, 1400)
            )
        }

        private fun level5(): LevelData {
            val r = RED; val b = BLUE; val g = GREEN; val y = YELLOW
            val p = PURPLE; val o = ORANGE; val n: BubbleColor? = null
            return LevelData(
                levelNumber = 5,
                layout = listOf(
                    listOf(o, r, b, g, y, p, o, r, b, g),
                    listOf(r, b, g, y, p, o, r, b, g, n),
                    listOf(b, g, y, p, o, r, b, g, y, p),
                    listOf(g, y, p, o, r, b, g, y, p, n),
                    listOf(n, n, n, r, r, n, n, n, n, n),
                    listOf(n, n, n, n, b, b, n, n, n, n),
                    listOf(n, n, n, b, b, n, n, n, n, n),
                ),
                shotsPerPush = 6,
                starThresholds = listOf(500, 1000, 1800)
            )
        }
    }
}
