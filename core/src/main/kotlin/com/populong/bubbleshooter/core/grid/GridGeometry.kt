package com.populong.bubbleshooter.core.grid

import kotlin.math.sqrt

/**
 * Pixel-space geometry for the odd-r offset hex grid, in unit-radius space (bubble radius = 1).
 *
 * Even-parity rows hold [colsInRow] = `evenCols` bubbles at x-centers `2*col + 1`; odd-parity rows
 * are shifted right by one radius and hold `evenCols - 1` bubbles at x-centers `2*col + 2`. Row
 * parity uses floor-mod so negative rows (inserted above row 0 as the ceiling descends) stay
 * consistent with positive rows.
 */
object GridGeometry {

    /** Vertical distance between adjacent rows: `sqrt(3)` in unit-radius space. */
    const val ROW_HEIGHT: Float = 1.7320508f

    /** Number of bubble slots in [row] for a field [evenCols] wide, by row parity. */
    fun colsInRow(row: Int, evenCols: Int): Int =
        if (row.mod(2) == 0) evenCols else evenCols - 1

    /** x-center of [pos] in unit-radius space. */
    fun centerX(pos: GridPos): Float =
        if (pos.row.mod(2) == 0) (2 * pos.col + 1).toFloat() else (2 * pos.col + 2).toFloat()

    /** y-center of [row] in unit-radius space. */
    fun centerY(row: Int): Float = row * ROW_HEIGHT + 1f

    /** Whether [pos] names a real slot in a field [evenCols] wide. */
    fun isValidCell(pos: GridPos, evenCols: Int): Boolean =
        pos.col in 0 until colsInRow(pos.row, evenCols)
}

/** A 2D vector in unit-radius pixel space. */
data class Vec2(val x: Float, val y: Float) {
    operator fun plus(other: Vec2): Vec2 = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2): Vec2 = Vec2(x - other.x, y - other.y)
    operator fun times(scalar: Float): Vec2 = Vec2(x * scalar, y * scalar)

    fun length(): Float = sqrt(x * x + y * y)

    /** This vector scaled to unit length, or the zero vector if this vector is zero. */
    fun normalized(): Vec2 {
        val len = length()
        return if (len == 0f) this else Vec2(x / len, y / len)
    }
}
