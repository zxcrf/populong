package com.populong.bubbleshooter.core.grid

/**
 * A cell position in the odd-r offset hex grid, packed into a single Int.
 *
 * Rows grow downward. Rows may be negative: when the ceiling descends, new rows
 * are inserted above the current top row without re-indexing existing bubbles.
 * Row parity is therefore taken with floor-mod so it stays consistent across zero.
 *
 * Both row and col live in signed 16-bit ranges [-32768, 32767].
 */
@JvmInline
value class GridPos(val packed: Int) {
    constructor(row: Int, col: Int) : this((row shl 16) or (col and 0xFFFF))

    val row: Int get() = packed shr 16
    val col: Int get() = (packed shl 16) shr 16

    /** True for rows shifted right by half a cell in odd-r layout. */
    val isOddRow: Boolean get() = row.mod(2) == 1

    override fun toString(): String = "($row,$col)"
}
