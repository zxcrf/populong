package com.populong.bubbleshooter.game

import android.graphics.PointF

class BubbleGrid(
    val columns: Int = 10,
    val maxRows: Int = 16
) {
    data class GridCell(val row: Int, val col: Int)

    private val cells = mutableMapOf<GridCell, Bubble>()

    fun get(cell: GridCell): Bubble? = cells[cell]

    fun set(cell: GridCell, bubble: Bubble) {
        cells[cell] = bubble
    }

    fun remove(cell: GridCell) {
        cells.remove(cell)
    }

    fun removeAll(toRemove: Set<GridCell>) {
        for (cell in toRemove) cells.remove(cell)
    }

    fun isEmpty(cell: GridCell): Boolean = !cells.containsKey(cell)

    fun allOccupied(): List<Pair<GridCell, Bubble>> =
        cells.entries.map { it.key to it.value }

    fun occupiedCount(): Int = cells.size

    fun colorsOnScreen(): Set<BubbleColor> =
        cells.values.filter { !it.isRainbow }.map { it.color }.toSet()

    fun clear() = cells.clear()

    fun neighbors(cell: GridCell): List<GridCell> {
        val (row, col) = cell
        val isOddRow = row % 2 == 1
        val offsets = if (isOddRow) {
            listOf(
                -1 to 0, -1 to 1,
                0 to -1, 0 to 1,
                1 to 0, 1 to 1
            )
        } else {
            listOf(
                -1 to -1, -1 to 0,
                0 to -1, 0 to 1,
                1 to -1, 1 to 0
            )
        }
        return offsets
            .map { GridCell(row + it.first, col + it.second) }
            .filter { it.row in 0 until maxRows && it.col in 0 until columns }
    }

    fun cellToPixel(cell: GridCell, bubbleRadius: Float, offsetY: Float = 0f): PointF {
        val diameter = bubbleRadius * 2f
        val rowHeight = bubbleRadius * 1.732f
        val xOffset = if (cell.row % 2 == 1) bubbleRadius else 0f
        return PointF(
            cell.col * diameter + bubbleRadius + xOffset,
            cell.row * rowHeight + bubbleRadius + offsetY
        )
    }

    fun pixelToCell(x: Float, y: Float, bubbleRadius: Float, offsetY: Float = 0f): GridCell {
        val rowHeight = bubbleRadius * 1.732f
        val adjustedY = y - offsetY
        val row = ((adjustedY - bubbleRadius) / rowHeight + 0.5f).toInt().coerceIn(0, maxRows - 1)
        val xOffset = if (row % 2 == 1) bubbleRadius else 0f
        val col = ((x - bubbleRadius - xOffset) / (bubbleRadius * 2f) + 0.5f).toInt()
            .coerceIn(0, columns - 1)
        return GridCell(row, col)
    }

    fun shiftDown(): Boolean {
        val newCells = mutableMapOf<GridCell, Bubble>()
        for ((cell, bubble) in cells) {
            val newCell = GridCell(cell.row + 1, cell.col)
            if (newCell.row >= maxRows) return false
            newCells[newCell] = bubble
        }
        cells.clear()
        cells.putAll(newCells)
        return true
    }

    fun addRow(topRow: List<Bubble?>) {
        shiftDown()
        for (col in topRow.indices) {
            val bubble = topRow[col] ?: continue
            cells[GridCell(0, col)] = bubble
        }
    }

    fun lowestOccupiedRow(): Int =
        cells.keys.maxOfOrNull { it.row } ?: -1
}
