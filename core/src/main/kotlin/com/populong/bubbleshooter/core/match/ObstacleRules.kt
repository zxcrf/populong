package com.populong.bubbleshooter.core.match

import com.populong.bubbleshooter.core.grid.Bubble
import com.populong.bubbleshooter.core.grid.BubbleGrid
import com.populong.bubbleshooter.core.grid.GridPos
import java.util.ArrayDeque

/** Rules for obstacle bubbles ([Bubble.Ice], [Bubble.Chained], [Bubble.Fog]) reacting to shots and matches. */
object ObstacleRules {

    /**
     * Applies one pop-event's side effects to the neighbors of [popped], on a [grid] that has
     * already had [popped] removed. Each distinct neighboring [Bubble.Ice] loses one hit for this
     * whole pop-event (not once per adjacent popped cell); at 0 hits left it becomes
     * [Bubble.Colored]. Each distinct neighboring [Bubble.Chained] unlocks to [Bubble.Colored].
     */
    fun afterPop(grid: BubbleGrid, popped: Set<GridPos>): BubbleGrid {
        if (popped.isEmpty()) return grid

        val affected = LinkedHashSet<GridPos>()
        for (pos in popped) {
            affected.addAll(grid.neighbors(pos))
        }

        var result = grid
        for (pos in affected) {
            when (val bubble = grid.bubbleAt(pos)) {
                is Bubble.Ice -> {
                    result = if (bubble.hitsLeft <= 1) {
                        result.with(pos, Bubble.Colored(bubble.color))
                    } else {
                        result.with(pos, bubble.copy(hitsLeft = bubble.hitsLeft - 1))
                    }
                }
                is Bubble.Chained -> {
                    result = result.with(pos, Bubble.Colored(bubble.color))
                }
                else -> Unit
            }
        }
        return result
    }

    /**
     * Reveals [Bubble.Fog] cells within hex grid-distance `<=` [radius] of [landing], using BFS
     * depth over [BubbleGrid.neighbors] (both occupied and empty cells are traversable).
     */
    fun revealFogAround(grid: BubbleGrid, landing: GridPos, radius: Int = 2): BubbleGrid {
        val depthOf = HashMap<GridPos, Int>()
        val queue = ArrayDeque<GridPos>()
        depthOf[landing] = 0
        queue.add(landing)

        while (queue.isNotEmpty()) {
            val pos = queue.poll()
            val depth = depthOf.getValue(pos)
            if (depth >= radius) continue
            for (neighbor in grid.neighbors(pos)) {
                if (neighbor !in depthOf) {
                    depthOf[neighbor] = depth + 1
                    queue.add(neighbor)
                }
            }
        }

        var result = grid
        for (pos in depthOf.keys) {
            val bubble = grid.bubbleAt(pos)
            if (bubble is Bubble.Fog && !bubble.revealed) {
                result = result.with(pos, bubble.copy(revealed = true))
            }
        }
        return result
    }
}
