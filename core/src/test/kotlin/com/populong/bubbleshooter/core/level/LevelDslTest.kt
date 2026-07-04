package com.populong.bubbleshooter.core.level

import com.populong.bubbleshooter.core.grid.Bubble
import com.populong.bubbleshooter.core.grid.BubbleColor
import com.populong.bubbleshooter.core.grid.GridPos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LevelDslTest {

    private fun pos(row: Int, col: Int) = GridPos(row, col)

    @Test
    fun `every token kind parses into the right bubble type`() {
        val spec = level(
            id = 7,
            palette = 6,
            shots = 20,
            star1 = 100, star2 = 200, star3 = 300,
            grid = """
                R B G Y P O S .
                IR FB CG . R B G
            """,
        )
        val grid = spec.initialGrid

        assertEquals(Bubble.Colored(BubbleColor.RED), grid.bubbleAt(pos(0, 0)))
        assertEquals(Bubble.Colored(BubbleColor.BLUE), grid.bubbleAt(pos(0, 1)))
        assertEquals(Bubble.Colored(BubbleColor.GREEN), grid.bubbleAt(pos(0, 2)))
        assertEquals(Bubble.Colored(BubbleColor.YELLOW), grid.bubbleAt(pos(0, 3)))
        assertEquals(Bubble.Colored(BubbleColor.PURPLE), grid.bubbleAt(pos(0, 4)))
        assertEquals(Bubble.Colored(BubbleColor.ORANGE), grid.bubbleAt(pos(0, 5)))
        assertEquals(Bubble.Stone, grid.bubbleAt(pos(0, 6)))
        assertNull(grid.bubbleAt(pos(0, 7)))

        assertEquals(Bubble.Ice(BubbleColor.RED, hitsLeft = 2), grid.bubbleAt(pos(1, 0)))
        assertEquals(Bubble.Fog(BubbleColor.BLUE, revealed = false), grid.bubbleAt(pos(1, 1)))
        assertEquals(Bubble.Chained(BubbleColor.GREEN), grid.bubbleAt(pos(1, 2)))
        assertNull(grid.bubbleAt(pos(1, 3)))
        assertEquals(Bubble.Colored(BubbleColor.RED), grid.bubbleAt(pos(1, 4)))

        assertEquals(0, grid.ceilingRow)
        assertEquals(8, spec.evenCols)
        assertEquals(6, spec.paletteSize)
        assertEquals(listOf(100L, 200L, 300L), spec.starThresholds)
    }

    @Test
    fun `parity fixes the token count of each row`() {
        // Even rows need evenCols tokens, odd rows one fewer; a smaller field is honored too.
        val spec = level(
            id = 1, palette = 4, shots = 5, star1 = 1, star2 = 2, star3 = 3,
            evenCols = 4,
            grid = """
                R R R R
                B B B
            """,
        )
        assertEquals(4, spec.evenCols)
        assertEquals(Bubble.Colored(BubbleColor.RED), spec.initialGrid.bubbleAt(pos(0, 3)))
        assertEquals(Bubble.Colored(BubbleColor.BLUE), spec.initialGrid.bubbleAt(pos(1, 2)))
        assertNull(spec.initialGrid.bubbleAt(pos(1, 3))) // odd row has only cols 0..2
    }

    @Test
    fun `wrong token count for a row parity is rejected`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            level(
                id = 42, palette = 4, shots = 5, star1 = 1, star2 = 2, star3 = 3,
                grid = """
                    R R R R R R R
                    B B B B B B B
                """,
            )
        }
        val msg = ex.message!!
        assertTrue("42" in msg, "message names the level: $msg")
        assertTrue("row 0" in msg, "message names the row: $msg")
        assertTrue("expected 8" in msg, "message names the expected count: $msg")
    }

    @Test
    fun `an unknown token is rejected with its location`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            level(
                id = 9, palette = 6, shots = 5, star1 = 1, star2 = 2, star3 = 3,
                grid = """
                    R B G Y P O S Z
                    . . . . . . .
                """,
            )
        }
        val msg = ex.message!!
        assertTrue("9" in msg && "row 0" in msg && "Z" in msg, "message locates the token: $msg")
    }

    @Test
    fun `a color outside the palette prefix is rejected`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            level(
                id = 3, palette = 4, shots = 5, star1 = 1, star2 = 2, star3 = 3,
                grid = """
                    R B G Y P R B G
                    . . . . . . .
                """,
            )
        }
        val msg = ex.message!!
        assertTrue("3" in msg && "P" in msg && "4" in msg, "message explains the palette violation: $msg")
    }

    @Test
    fun `an obstacle wrapping an out-of-palette color is rejected`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            level(
                id = 5, palette = 3, shots = 5, star1 = 1, star2 = 2, star3 = 3,
                grid = """
                    R B G IY . . . .
                    . . . . . . .
                """,
            )
        }
        assertTrue("IY" in ex.message!!, "message names the offending token: ${ex.message}")
    }

    @Test
    fun `non-ascending star thresholds are rejected`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            level(id = 1, palette = 4, shots = 5, star1 = 300, star2 = 200, star3 = 100, grid = "R R R R R R R R")
        }
        assertTrue("ascend" in ex.message!!, ex.message!!)
    }

    @Test
    fun `a non-positive shot budget is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            level(id = 1, palette = 4, shots = 0, star1 = 1, star2 = 2, star3 = 3, grid = "R R R R R R R R")
        }
    }

    @Test
    fun `a palette size outside 2 to 6 is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            level(id = 1, palette = 7, shots = 5, star1 = 1, star2 = 2, star3 = 3, grid = "R R R R R R R R")
        }
    }
}
