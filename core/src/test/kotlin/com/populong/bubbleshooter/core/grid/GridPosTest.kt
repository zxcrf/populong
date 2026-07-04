package com.populong.bubbleshooter.core.grid

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GridPosTest {

    @Test
    fun `round-trips row and col across the signed 16-bit range`() {
        val samples = listOf(
            0 to 0, 1 to 1, 11 to 7, -1 to 0, -5 to 12,
            32767 to 32767, -32768 to -32768, 100 to -3,
        )
        for ((row, col) in samples) {
            val pos = GridPos(row, col)
            assertEquals(row, pos.row, "row of ($row,$col)")
            assertEquals(col, pos.col, "col of ($row,$col)")
        }
    }

    @Test
    fun `equal coordinates pack to equal values`() {
        assertEquals(GridPos(3, 4), GridPos(3, 4))
        assertEquals(GridPos(-2, 5).packed, GridPos(-2, 5).packed)
    }

    @Test
    fun `odd row parity uses floor mod for negative rows`() {
        assertFalse(GridPos(0, 0).isOddRow)
        assertTrue(GridPos(1, 0).isOddRow)
        assertTrue(GridPos(-1, 0).isOddRow)
        assertFalse(GridPos(-2, 0).isOddRow)
    }

    @Test
    fun `palette selects a stable prefix`() {
        assertEquals(
            listOf(BubbleColor.RED, BubbleColor.BLUE, BubbleColor.GREEN, BubbleColor.YELLOW),
            BubbleColor.palette(4),
        )
        assertFailsWith<IllegalArgumentException> { BubbleColor.palette(1) }
        assertFailsWith<IllegalArgumentException> { BubbleColor.palette(7) }
    }
}
