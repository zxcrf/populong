package com.populong.bubbleshooter.game

import kotlin.math.hypot

sealed class CollisionResult {
    data class BubbleHit(val cell: BubbleGrid.GridCell) : CollisionResult()
    data class Ceiling(val cell: BubbleGrid.GridCell) : CollisionResult()
}

class CollisionDetector {

    fun check(
        projectile: Projectile,
        grid: BubbleGrid,
        bubbleRadius: Float,
        gridOffsetY: Float
    ): CollisionResult? {
        if (projectile.y - bubbleRadius <= gridOffsetY) {
            val cell = grid.pixelToCell(projectile.x, gridOffsetY + bubbleRadius, bubbleRadius, gridOffsetY)
            return CollisionResult.Ceiling(cell)
        }

        val collisionDist = bubbleRadius * 2f * 0.9f
        val projectileRow = ((projectile.y - gridOffsetY) / (bubbleRadius * 1.732f)).toInt()
        val checkRange = (projectileRow - 2)..(projectileRow + 2)

        var closest: Pair<BubbleGrid.GridCell, Float>? = null
        for ((cell, _) in grid.allOccupied()) {
            if (cell.row !in checkRange) continue
            val center = grid.cellToPixel(cell, bubbleRadius, gridOffsetY)
            val dist = hypot(projectile.x - center.x, projectile.y - center.y)
            if (dist <= collisionDist) {
                if (closest == null || dist < closest.second) {
                    closest = cell to dist
                }
            }
        }
        return closest?.let { CollisionResult.BubbleHit(it.first) }
    }
}
