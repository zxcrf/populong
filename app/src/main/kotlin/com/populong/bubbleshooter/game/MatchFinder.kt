package com.populong.bubbleshooter.game

import java.util.LinkedList

class MatchFinder {

    fun findMatches(startCell: BubbleGrid.GridCell, grid: BubbleGrid): Set<BubbleGrid.GridCell> {
        val bubble = grid.get(startCell) ?: return emptySet()

        if (bubble.isRainbow) {
            return findRainbowMatches(startCell, grid)
        }

        val targetColor = bubble.color
        val visited = mutableSetOf<BubbleGrid.GridCell>()
        val queue = LinkedList<BubbleGrid.GridCell>()
        queue.add(startCell)
        visited.add(startCell)

        while (queue.isNotEmpty()) {
            val current = queue.poll()
            for (neighbor in grid.neighbors(current)) {
                if (neighbor in visited) continue
                val neighborBubble = grid.get(neighbor) ?: continue
                if (neighborBubble.color == targetColor || neighborBubble.isRainbow) {
                    visited.add(neighbor)
                    queue.add(neighbor)
                }
            }
        }
        return if (visited.size >= 3) visited else emptySet()
    }

    private fun findRainbowMatches(
        startCell: BubbleGrid.GridCell,
        grid: BubbleGrid
    ): Set<BubbleGrid.GridCell> {
        val neighborColors = grid.neighbors(startCell)
            .mapNotNull { grid.get(it) }
            .filter { !it.isRainbow }
            .groupBy { it.color }

        val bestColor = neighborColors.maxByOrNull { it.value.size }?.key
            ?: return setOf(startCell)

        val visited = mutableSetOf<BubbleGrid.GridCell>()
        val queue = LinkedList<BubbleGrid.GridCell>()
        queue.add(startCell)
        visited.add(startCell)

        while (queue.isNotEmpty()) {
            val current = queue.poll()
            for (neighbor in grid.neighbors(current)) {
                if (neighbor in visited) continue
                val neighborBubble = grid.get(neighbor) ?: continue
                if (neighborBubble.color == bestColor || neighborBubble.isRainbow) {
                    visited.add(neighbor)
                    queue.add(neighbor)
                }
            }
        }
        return if (visited.size >= 3) visited else emptySet()
    }
}
