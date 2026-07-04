package com.populong.bubbleshooter.core.match

import com.populong.bubbleshooter.core.grid.BubbleColor
import com.populong.bubbleshooter.core.grid.BubbleGrid
import com.populong.bubbleshooter.core.grid.GridPos
import com.populong.bubbleshooter.core.grid.matchableColor
import java.util.ArrayDeque

/** Finds connected clusters of same-colored, currently-matchable bubbles. */
object MatchFinder {

    /**
     * The connected cluster (including [start]) whose bubbles' [matchableColor] equals [color],
     * found by BFS over occupied grid neighbors. Returns an empty set if [start] itself is not
     * occupied or is not currently matchable as [color].
     */
    fun findMatch(grid: BubbleGrid, start: GridPos, color: BubbleColor): Set<GridPos> {
        if (grid.bubbleAt(start)?.matchableColor() != color) return emptySet()

        val visited = LinkedHashSet<GridPos>()
        val queue = ArrayDeque<GridPos>()
        visited.add(start)
        queue.add(start)

        while (queue.isNotEmpty()) {
            val pos = queue.poll()
            for (neighbor in grid.occupiedNeighbors(pos)) {
                if (neighbor !in visited && grid.bubbleAt(neighbor)?.matchableColor() == color) {
                    visited.add(neighbor)
                    queue.add(neighbor)
                }
            }
        }
        return visited
    }
}
