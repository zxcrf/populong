package com.populong.bubbleshooter.game

import java.util.LinkedList

class FloatingDetector {

    fun findFloating(grid: BubbleGrid): Set<BubbleGrid.GridCell> {
        val connected = mutableSetOf<BubbleGrid.GridCell>()
        val queue = LinkedList<BubbleGrid.GridCell>()

        for ((cell, _) in grid.allOccupied()) {
            if (cell.row == 0) {
                connected.add(cell)
                queue.add(cell)
            }
        }

        while (queue.isNotEmpty()) {
            val current = queue.poll()
            for (neighbor in grid.neighbors(current)) {
                if (neighbor in connected) continue
                if (grid.get(neighbor) != null) {
                    connected.add(neighbor)
                    queue.add(neighbor)
                }
            }
        }

        return grid.allOccupied()
            .map { it.first }
            .filter { it !in connected }
            .toSet()
    }
}
