package com.populong.bubbleshooter.core.grid

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GridGeometryTest {

    @Test
    fun `colsInRow follows parity including negative rows`() {
        val cases = listOf(
            0 to 8, 1 to 7, 2 to 8, -1 to 7, -2 to 8, -3 to 7,
        )
        for ((row, expected) in cases) {
            assertEquals(expected, GridGeometry.colsInRow(row, evenCols = 8), "row $row")
        }
    }

    @Test
    fun `centerX for even and odd rows`() {
        assertEquals(1f, GridGeometry.centerX(GridPos(0, 0)))
        assertEquals(3f, GridGeometry.centerX(GridPos(0, 1)))
        assertEquals(2f, GridGeometry.centerX(GridPos(1, 0)))
        assertEquals(4f, GridGeometry.centerX(GridPos(1, 1)))
    }

    @Test
    fun `centerX for negative rows uses floor-mod parity`() {
        // row -1 is odd parity (floor mod), same x-offset scheme as row 1
        assertEquals(2f, GridGeometry.centerX(GridPos(-1, 0)))
        // row -2 is even parity, same x-offset scheme as row 0
        assertEquals(1f, GridGeometry.centerX(GridPos(-2, 0)))
    }

    @Test
    fun `centerY steps by ROW_HEIGHT including negative rows`() {
        assertEquals(1f, GridGeometry.centerY(0))
        assertEquals(1f + GridGeometry.ROW_HEIGHT, GridGeometry.centerY(1))
        assertEquals(1f - GridGeometry.ROW_HEIGHT, GridGeometry.centerY(-1))
    }

    @Test
    fun `isValidCell rejects out-of-range columns per row parity`() {
        assertTrue(GridGeometry.isValidCell(GridPos(0, 0), evenCols = 8))
        assertTrue(GridGeometry.isValidCell(GridPos(0, 7), evenCols = 8))
        assertFalse(GridGeometry.isValidCell(GridPos(0, 8), evenCols = 8))
        assertFalse(GridGeometry.isValidCell(GridPos(0, -1), evenCols = 8))

        assertTrue(GridGeometry.isValidCell(GridPos(1, 0), evenCols = 8))
        assertTrue(GridGeometry.isValidCell(GridPos(1, 6), evenCols = 8))
        assertFalse(GridGeometry.isValidCell(GridPos(1, 7), evenCols = 8))
    }

    @Test
    fun `isValidCell works for negative rows`() {
        assertTrue(GridGeometry.isValidCell(GridPos(-1, 6), evenCols = 8))
        assertFalse(GridGeometry.isValidCell(GridPos(-1, 7), evenCols = 8))
        assertTrue(GridGeometry.isValidCell(GridPos(-2, 7), evenCols = 8))
        assertFalse(GridGeometry.isValidCell(GridPos(-2, 8), evenCols = 8))
    }

    @Test
    fun `Vec2 arithmetic and length`() {
        val a = Vec2(1f, 2f)
        val b = Vec2(3f, -1f)
        assertEquals(Vec2(4f, 1f), a + b)
        assertEquals(Vec2(-2f, 3f), a - b)
        assertEquals(Vec2(2f, 4f), a * 2f)
        assertEquals(5f, Vec2(3f, 4f).length())
    }

    @Test
    fun `Vec2 normalized has unit length and zero vector stays zero`() {
        val n = Vec2(3f, 4f).normalized()
        assertEquals(1f, n.length())
        assertEquals(Vec2(0f, 0f), Vec2(0f, 0f).normalized())
    }
}
