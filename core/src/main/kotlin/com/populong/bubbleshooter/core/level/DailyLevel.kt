package com.populong.bubbleshooter.core.level

import com.populong.bubbleshooter.core.grid.Bubble
import com.populong.bubbleshooter.core.grid.BubbleColor
import com.populong.bubbleshooter.core.grid.BubbleGrid
import com.populong.bubbleshooter.core.grid.GridGeometry
import com.populong.bubbleshooter.core.grid.GridPos
import com.populong.bubbleshooter.core.match.FloatDetector
import java.util.ArrayDeque
import kotlin.math.ceil

/**
 * The once-a-day challenge level. Every calendar day maps to one deterministic mid-to-high
 * difficulty board — five colors, a mix of obstacles, no boss and no ceiling descent — so all
 * players on the same day face the exact same puzzle. It reuses the generator's blob-growing and
 * obstacle logic through a fixed [GenParams] rather than the campaign difficulty curve.
 */
object DailyLevel {

    private const val EVEN_COLS = 8
    private const val ROWS = 10
    private const val COLORS = 5

    /** SplitMix golden gamma, folded into the daily seed. */
    private const val GOLDEN = -0x61c8864680b583ebL

    /** The [LevelSpec] for the day numbered [epochDay] (days since the Unix epoch). */
    fun forEpochDay(epochDay: Long): LevelSpec {
        val id = DAILY_ID_BASE + (Math.floorMod(epochDay, 1000L)).toInt()
        val rng = Rng(Rng.mix(epochDay xor (epochDay * GOLDEN)))
        val colors = BubbleColor.palette(COLORS)

        val kept = pruneFloating(carveHoles(fullMask(), 0.16f, rng))
        val colorOf = growBlobs(kept, colors, rng)

        val bubbles = HashMap<GridPos, Bubble>(kept.size * 2)
        for (pos in kept) bubbles[pos] = Bubble.Colored(colorOf.getValue(pos))
        sprinkle(bubbles, rng)

        val clearable = bubbles.values.count { it !is Bubble.Stone }
        val shots = ceil(clearable / 2.2).toInt() + 4
        val stars = listOf(clearable * 12L, clearable * 26L, clearable * 42L)

        return LevelSpec(
            id = id,
            evenCols = EVEN_COLS,
            paletteSize = COLORS,
            initialGrid = BubbleGrid(bubbles, EVEN_COLS, ceilingRow = 0),
            shots = shots.coerceAtLeast(8),
            starThresholds = stars,
            bombEvery = 11,
            rainbowEvery = 9,
            descentEveryShots = 0,
        )
    }

    private fun fullMask(): LinkedHashSet<GridPos> {
        val mask = LinkedHashSet<GridPos>()
        for (row in 0 until ROWS) {
            val cols = GridGeometry.colsInRow(row, EVEN_COLS)
            for (col in 0 until cols) mask.add(GridPos(row, col))
        }
        return mask
    }

    private fun carveHoles(mask: LinkedHashSet<GridPos>, density: Float, rng: Rng): LinkedHashSet<GridPos> {
        val result = LinkedHashSet<GridPos>(mask.size * 2)
        for (pos in mask) {
            if (pos.row != 0 && rng.nextFloat() < density) continue
            result.add(pos)
        }
        return result
    }

    private fun pruneFloating(kept: LinkedHashSet<GridPos>): LinkedHashSet<GridPos> {
        if (kept.isEmpty()) return kept
        val probe = HashMap<GridPos, Bubble>(kept.size * 2)
        for (pos in kept) probe[pos] = ANCHOR_PROBE
        val floating = FloatDetector.floating(BubbleGrid(probe, EVEN_COLS, ceilingRow = 0))
        if (floating.isEmpty()) return kept
        val result = LinkedHashSet<GridPos>(kept.size * 2)
        for (pos in kept) if (pos !in floating) result.add(pos)
        return result
    }

    private fun growBlobs(
        kept: Set<GridPos>,
        colors: List<BubbleColor>,
        rng: Rng,
    ): HashMap<GridPos, BubbleColor> {
        val colorOf = HashMap<GridPos, BubbleColor>(kept.size * 2)
        val remaining = kept.sortedBy { it.packed }.toMutableList()
        val queue = ArrayDeque<GridPos>()
        while (remaining.isNotEmpty()) {
            val seed = remaining.removeAt(rng.nextInt(remaining.size))
            if (seed in colorOf) continue
            val color = colors[rng.nextInt(colors.size)]
            val target = 2 + rng.nextInt(4)
            var grown = 0
            queue.clear()
            queue.add(seed)
            colorOf[seed] = color
            grown++
            while (queue.isNotEmpty() && grown < target) {
                val cur = queue.poll()
                for (nb in neighborsOf(cur)) {
                    if (grown >= target) break
                    if (nb in kept && nb !in colorOf) {
                        colorOf[nb] = color
                        queue.add(nb)
                        grown++
                    }
                }
            }
        }
        return colorOf
    }

    private fun sprinkle(bubbles: HashMap<GridPos, Bubble>, rng: Rng) {
        val total = bubbles.size
        val order = bubbles.keys.sortedBy { it.packed }.toMutableList()
        for (i in order.size - 1 downTo 1) {
            val j = rng.nextInt(i + 1)
            val tmp = order[i]; order[i] = order[j]; order[j] = tmp
        }
        val bottomRow = bubbles.keys.maxOf { it.row }

        var ice = (total * 0.06f).toInt()
        var fog = (total * 0.05f).toInt()
        var stone = (total * 0.04f).toInt()
        for (pos in order) {
            val color = (bubbles[pos] as? Bubble.Colored)?.color ?: continue
            when {
                ice > 0 -> {
                    bubbles[pos] = Bubble.Ice(color, hitsLeft = 2); ice--
                }
                fog > 0 && pos.row >= 2 -> {
                    bubbles[pos] = Bubble.Fog(color, revealed = false); fog--
                }
                stone > 0 && pos.row != bottomRow -> {
                    bubbles[pos] = Bubble.Stone; stone--
                }
            }
        }
    }

    private fun neighborsOf(pos: GridPos): List<GridPos> {
        val deltas = if (pos.row.mod(2) == 0) EVEN_ROW_DELTAS else ODD_ROW_DELTAS
        val out = ArrayList<GridPos>(6)
        for ((dr, dc) in deltas) {
            val candidate = GridPos(pos.row + dr, pos.col + dc)
            if (GridGeometry.isValidCell(candidate, EVEN_COLS)) out.add(candidate)
        }
        return out
    }

    /** Daily levels live in their own id space so they never collide with campaign ids. */
    const val DAILY_ID_BASE = 1_000_000

    private val ANCHOR_PROBE = Bubble.Colored(BubbleColor.RED)
    private val EVEN_ROW_DELTAS = listOf(0 to -1, 0 to 1, -1 to -1, -1 to 0, 1 to -1, 1 to 0)
    private val ODD_ROW_DELTAS = listOf(0 to -1, 0 to 1, -1 to 0, -1 to 1, 1 to 0, 1 to 1)
}
