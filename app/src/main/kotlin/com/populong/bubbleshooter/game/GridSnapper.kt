package com.populong.bubbleshooter.game

import kotlin.math.hypot

class GridSnapper {

    fun findSnapCell(
        projectile: Projectile,
        hitCell: BubbleGrid.GridCell,
        grid: BubbleGrid,
        bubbleRadius: Float,
        gridOffsetY: Float
    ): BubbleGrid.GridCell {
        val candidates = grid.neighbors(hitCell).filter { grid.isEmpty(it) }
        if (candidates.isEmpty()) return hitCell

        return candidates.minByOrNull { cell ->
            val center = grid.cellToPixel(cell, bubbleRadius, gridOffsetY)
            hypot(projectile.x - center.x, projectile.y - center.y)
        } ?: hitCell
    }

    fun snapCeiling(
        projectile: Projectile,
        grid: BubbleGrid,
        bubbleRadius: Float,
        gridOffsetY: Float
    ): BubbleGrid.GridCell {
        return grid.pixelToCell(projectile.x, gridOffsetY + bubbleRadius, bubbleRadius, gridOffsetY)
    }
}
