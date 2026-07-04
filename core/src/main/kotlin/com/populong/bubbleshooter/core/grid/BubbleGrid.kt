package com.populong.bubbleshooter.core.grid

import java.util.ArrayDeque

/**
 * An immutable snapshot of the occupied cells in the play field.
 *
 * @property cells occupied cells only; positions absent from the map are empty.
 * @property evenCols width of the field: even-parity rows hold this many bubbles.
 * @property ceilingRow topmost playable row; bubbles occupying it anchor to the backdrop.
 */
class BubbleGrid(
    val cells: Map<GridPos, Bubble>,
    val evenCols: Int,
    val ceilingRow: Int,
) {

    fun bubbleAt(pos: GridPos): Bubble? = cells[pos]

    fun isOccupied(pos: GridPos): Boolean = cells.containsKey(pos)

    /** All 6 odd-r neighbors of [pos] that name valid cells in this field, occupied or not. */
    fun neighbors(pos: GridPos): List<GridPos> {
        val row = pos.row
        val col = pos.col
        val deltas = if (row.mod(2) == 0) EVEN_ROW_DELTAS else ODD_ROW_DELTAS
        val result = ArrayList<GridPos>(6)
        for ((dr, dc) in deltas) {
            val candidate = GridPos(row + dr, col + dc)
            if (GridGeometry.isValidCell(candidate, evenCols)) {
                result.add(candidate)
            }
        }
        return result
    }

    /** Neighbors of [pos] that are occupied. */
    fun occupiedNeighbors(pos: GridPos): List<GridPos> = neighbors(pos).filter { isOccupied(it) }

    /** Neighbors of [pos] that are valid and unoccupied. */
    fun emptyNeighbors(pos: GridPos): List<GridPos> = neighbors(pos).filter { !isOccupied(it) }

    /** Returns a new grid with [bubble] placed at [pos], replacing any bubble already there. */
    fun with(pos: GridPos, bubble: Bubble): BubbleGrid {
        require(GridGeometry.isValidCell(pos, evenCols)) { "invalid cell $pos for evenCols=$evenCols" }
        val updated = HashMap(cells)
        updated[pos] = bubble
        return BubbleGrid(updated, evenCols, ceilingRow)
    }

    /** Returns a new grid with [positions] removed; positions not present are ignored. */
    fun without(positions: Collection<GridPos>): BubbleGrid {
        if (positions.isEmpty()) return this
        val updated = HashMap(cells)
        for (pos in positions) {
            updated.remove(pos)
        }
        return BubbleGrid(updated, evenCols, ceilingRow)
    }

    /**
     * Cells structurally attached to the backdrop: reachable from an occupied cell in
     * [ceilingRow] by BFS over occupied neighbors, plus every [Bubble.Chained] and [Bubble.Wormhole]
     * cell and its whole connected component (a locked chain or a wormhole portal bolts its cluster
     * to the backdrop even when it hangs disconnected from the ceiling).
     */
    fun anchored(): Set<GridPos> {
        val anchored = HashSet<GridPos>()
        val queue = ArrayDeque<GridPos>()

        for (pos in cells.keys) {
            if (pos.row == ceilingRow && anchored.add(pos)) {
                queue.add(pos)
            }
        }
        drainBfs(queue, anchored)

        for ((pos, bubble) in cells) {
            if ((bubble is Bubble.Chained || bubble is Bubble.Wormhole) && anchored.add(pos)) {
                queue.add(pos)
            }
        }
        drainBfs(queue, anchored)

        return anchored
    }

    private fun drainBfs(queue: ArrayDeque<GridPos>, visited: MutableSet<GridPos>) {
        while (queue.isNotEmpty()) {
            val pos = queue.poll()
            for (neighbor in occupiedNeighbors(pos)) {
                if (visited.add(neighbor)) {
                    queue.add(neighbor)
                }
            }
        }
    }

    /** Highest-numbered occupied row, or `ceilingRow - 1` if the grid is empty. */
    fun bottomRow(): Int = cells.keys.maxOfOrNull { it.row } ?: (ceilingRow - 1)

    fun isEmpty(): Boolean = cells.isEmpty()

    private companion object {
        // Even row (row.mod(2)==0): (r,c-1) (r,c+1) (r-1,c-1) (r-1,c) (r+1,c-1) (r+1,c)
        val EVEN_ROW_DELTAS = listOf(
            0 to -1, 0 to 1, -1 to -1, -1 to 0, 1 to -1, 1 to 0,
        )

        // Odd row: (r,c-1) (r,c+1) (r-1,c) (r-1,c+1) (r+1,c) (r+1,c+1)
        val ODD_ROW_DELTAS = listOf(
            0 to -1, 0 to 1, -1 to 0, -1 to 1, 1 to 0, 1 to 1,
        )
    }
}
