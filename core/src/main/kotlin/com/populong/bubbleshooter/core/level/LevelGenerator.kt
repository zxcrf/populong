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
 * The procedural level factory for the campaign's generated tail (levels 51..2000).
 *
 * Each level is a pure, deterministic function of its number: the RNG stream is seeded from the
 * level index alone, so `generate(n)` is byte-for-byte reproducible on every platform. The pipeline
 * is: read the [GenParams] curve, stamp an [Archetype] silhouette, carve holes, prune anything that
 * would float, grow contiguous same-color blobs (never i.i.d. per cell — that is what makes the
 * layouts feel authored), then sprinkle obstacles under caps and placement rules, and finally derive
 * a shot budget and star thresholds from the bubble count.
 */
object LevelGenerator {

    /** Fixed field width for generated levels. */
    private const val EVEN_COLS = 8

    /** SplitMix golden gamma, used to fold the level index into a well-mixed seed. */
    private const val GOLDEN = -0x61c8864680b583ebL // 0x9E3779B97F4A7C15

    /** Builds the level numbered [level] (must be >= [GEN_MIN]). */
    fun generate(level: Int): LevelSpec {
        require(level >= GEN_MIN) { "generate is for levels >= $GEN_MIN; $level is hand-authored" }

        val p = paramsFor(level)
        val rng = Rng(Rng.mix(level.toLong() * GOLDEN))
        val colors = BubbleColor.palette(p.colors)

        // 1. silhouette + holes → the set of cells that will hold bubbles.
        val kept = pruneFloating(carveHoles(maskFor(p.archetype, p.rows), p.holeDensity, rng))
        check(kept.isNotEmpty()) { "generator produced an empty grid for level $level" }

        // 2. grow contiguous same-color blobs across the kept cells.
        val colorOf = growBlobs(kept, colors, rng)
        ensureColorFloor(kept, colorOf, colors, rng)

        // 3. lower cells into concrete bubbles, then convert some into obstacles.
        val bubbles = HashMap<GridPos, Bubble>(kept.size * 2)
        for (pos in kept) bubbles[pos] = Bubble.Colored(colorOf.getValue(pos))
        sprinkleObstacles(bubbles, p, rng)
        // Cosmic mechanics run before the final color floor: pulsars keep their cell's color, but
        // wormholes and wells strip it, so the floor pass must see (and compensate for) the loss.
        sprinklePulsars(bubbles, p, rng)
        sprinkleWormholes(bubbles, p, rng)
        sprinkleGravityWells(bubbles, p, rng)
        ensureFinalColorFloor(bubbles, colors, rng)
        sprinkleSupernovae(bubbles, p, rng)

        // 4. budget + scoring, derived from the count of bubbles that must actually be cleared.
        val clearable = bubbles.values.count { it !is Bubble.Stone }
        val shots = ceil(clearable / 2.05).toInt() + p.shotsSlack
        val stars = listOf(clearable * 12L, clearable * 26L, clearable * 42L)

        return LevelSpec(
            id = level,
            evenCols = EVEN_COLS,
            paletteSize = p.colors,
            initialGrid = BubbleGrid(bubbles, EVEN_COLS, ceilingRow = 0),
            shots = shots.coerceAtLeast(6),
            starThresholds = stars,
            bombEvery = p.bombEvery,
            rainbowEvery = p.rainbowEvery,
            descentEveryShots = p.descentEveryShots,
        )
    }

    // --- silhouette -------------------------------------------------------------------------------

    /** The archetype occupancy mask for a [rows]-tall field; the top two rows stay solid to anchor. */
    private fun maskFor(archetype: Archetype, rows: Int): LinkedHashSet<GridPos> {
        val mask = LinkedHashSet<GridPos>()
        for (row in 0 until rows) {
            val cols = GridGeometry.colsInRow(row, EVEN_COLS)
            for (col in 0 until cols) {
                if (row <= 1 || inArchetype(archetype, row, col)) {
                    mask.add(GridPos(row, col))
                }
            }
        }
        return mask
    }

    /** Whether [archetype] keeps cell ([row], [col]) below the anchoring band. */
    private fun inArchetype(archetype: Archetype, row: Int, col: Int): Boolean = when (archetype) {
        Archetype.CHECKER, Archetype.BLOBS, Archetype.FORTRESS -> true
        Archetype.ARCHES -> (col + row) % 4 != 0
        Archetype.DIAMONDS -> !((row % 4 == 2 || row % 4 == 3) && col % 4 == 2)
        Archetype.SPIRAL -> !(row % 3 == 0 && col % 5 == 4)
    }

    /** Randomly empties a [density] fraction of masked cells (never the ceiling row). */
    private fun carveHoles(mask: LinkedHashSet<GridPos>, density: Float, rng: Rng): LinkedHashSet<GridPos> {
        val result = LinkedHashSet<GridPos>(mask.size * 2)
        for (pos in mask) {
            if (pos.row != 0 && rng.nextFloat() < density) continue
            result.add(pos)
        }
        return result
    }

    /** Drops any cell that would hang unsupported, so the opening grid never has pre-fallen clusters. */
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

    // --- color -----------------------------------------------------------------------------------

    /** Assigns every kept cell a color by flooding blobs of 2..5 contiguous same-color cells. */
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
            val target = 2 + rng.nextInt(4) // 2..5
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

    /** Guarantees at least [MIN_PER_COLOR] cells of every palette color by recoloring surplus cells. */
    private fun ensureColorFloor(
        kept: Set<GridPos>,
        colorOf: HashMap<GridPos, BubbleColor>,
        colors: List<BubbleColor>,
        rng: Rng,
    ) {
        val counts = HashMap<BubbleColor, Int>()
        for (c in colors) counts[c] = 0
        for (c in colorOf.values) counts[c] = (counts[c] ?: 0) + 1

        val ordered = kept.sortedBy { it.packed }
        for (color in colors) {
            var deficit = MIN_PER_COLOR - counts.getValue(color)
            var scan = 0
            val budget = ordered.size * 4
            while (deficit > 0 && scan < budget) {
                scan++
                // Recolor a small contiguous patch (not scattered singletons) so the boosted color
                // stays matchable — a lone bubble of a color can never be popped.
                val pos = ordered[rng.nextInt(ordered.size)]
                val from = colorOf.getValue(pos)
                if (from == color || counts.getValue(from) <= MIN_PER_COLOR) continue
                colorOf[pos] = color
                counts[from] = counts.getValue(from) - 1
                counts[color] = counts.getValue(color) + 1
                deficit--
                // Pull in a couple of same-source neighbors to keep the new patch connected.
                for (nb in neighborsOf(pos)) {
                    if (deficit <= 0) break
                    if (colorOf[nb] == from && counts.getValue(from) > MIN_PER_COLOR) {
                        colorOf[nb] = color
                        counts[from] = counts.getValue(from) - 1
                        counts[color] = counts.getValue(color) + 1
                        deficit--
                    }
                }
            }
        }
    }

    /**
     * Guarantees at least [MIN_PER_COLOR] *inherently-colored* cells of every palette color in the
     * finished grid — obstacles retain their color, but stones strip it, so this runs after obstacle
     * placement. Deficient colors steal contiguous patches of surplus-colored cells so the boosted
     * color stays a poppable group.
     */
    private fun ensureFinalColorFloor(
        bubbles: HashMap<GridPos, Bubble>,
        colors: List<BubbleColor>,
        rng: Rng,
    ) {
        val counts = HashMap<BubbleColor, Int>()
        for (c in colors) counts[c] = 0
        for (b in bubbles.values) inherentColor(b)?.let { counts[it] = (counts[it] ?: 0) + 1 }

        val order = bubbles.keys.sortedBy { it.packed }
        for (color in colors) {
            var deficit = MIN_PER_COLOR - counts.getValue(color)
            var scan = 0
            val budget = order.size * 4
            while (deficit > 0 && scan < budget) {
                scan++
                val pos = order[rng.nextInt(order.size)]
                val from = (bubbles[pos] as? Bubble.Colored)?.color ?: continue
                if (from == color || counts.getValue(from) <= MIN_PER_COLOR) continue
                bubbles[pos] = Bubble.Colored(color)
                counts[from] = counts.getValue(from) - 1
                counts[color] = counts.getValue(color) + 1
                deficit--
                for (nb in neighborsOf(pos)) {
                    if (deficit <= 0) break
                    val nbColor = (bubbles[nb] as? Bubble.Colored)?.color
                    if (nbColor == from && counts.getValue(from) > MIN_PER_COLOR) {
                        bubbles[nb] = Bubble.Colored(color)
                        counts[from] = counts.getValue(from) - 1
                        counts[color] = counts.getValue(color) + 1
                        deficit--
                    }
                }
            }
        }
    }

    private fun inherentColor(bubble: Bubble): BubbleColor? = when (bubble) {
        is Bubble.Colored -> bubble.color
        is Bubble.Ice -> bubble.color
        is Bubble.Fog -> bubble.color
        is Bubble.Chained -> bubble.color
        is Bubble.Supernova -> bubble.color
        is Bubble.Pulsar -> bubble.color
        Bubble.Stone, Bubble.GravityWell, is Bubble.Wormhole -> null
    }

    // --- obstacles -------------------------------------------------------------------------------

    /** Converts a capped number of colored cells into obstacles, respecting placement rules. */
    private fun sprinkleObstacles(bubbles: HashMap<GridPos, Bubble>, p: GenParams, rng: Rng) {
        val total = bubbles.size
        val order = bubbles.keys.sortedBy { it.packed }.toMutableList()
        shuffle(order, rng)
        val bottomRow = bubbles.keys.maxOf { it.row }

        // Ice: any colored cell.
        placePass(order, bubbles, cap(total, p.iceDensity)) { pos, color ->
            Bubble.Ice(color, hitsLeft = 2).takeIf { bubbles[pos] is Bubble.Colored }
        }
        // Fog: rows 2+, never ceiling-adjacent.
        placePass(order, bubbles, cap(total, p.fogDensity)) { pos, color ->
            if (pos.row >= 2 && bubbles[pos] is Bubble.Colored) Bubble.Fog(color, revealed = false) else null
        }
        // Chained: must keep at least one non-obstacle neighbor to be unlockable.
        placePass(order, bubbles, cap(total, p.chainDensity)) { pos, color ->
            if (bubbles[pos] is Bubble.Colored && hasFreeNeighbor(bubbles, pos)) {
                Bubble.Chained(color)
            } else {
                null
            }
        }
        // Stone: never the bottom occupied row, and never walling a region in fully.
        placePass(order, bubbles, cap(total, p.stoneDensity)) { pos, _ ->
            if (pos.row != bottomRow &&
                bubbles[pos] is Bubble.Colored &&
                stoneNeighborCount(bubbles, pos) < 4
            ) {
                Bubble.Stone
            } else {
                null
            }
        }
    }

    private fun cap(total: Int, density: Float): Int = (total * density).toInt()

    /** Walks [order] converting up to [count] cells via [make] (null = skip this cell). */
    private inline fun placePass(
        order: List<GridPos>,
        bubbles: HashMap<GridPos, Bubble>,
        count: Int,
        make: (GridPos, BubbleColor) -> Bubble?,
    ) {
        if (count <= 0) return
        var placed = 0
        for (pos in order) {
            if (placed >= count) break
            val current = bubbles[pos]
            val color = (current as? Bubble.Colored)?.color ?: continue
            val replacement = make(pos, color) ?: continue
            bubbles[pos] = replacement
            placed++
        }
    }

    /** A cell has a "free" (unlockable-supporting) neighbor if some neighbor is neither Stone nor Chained. */
    private fun hasFreeNeighbor(bubbles: Map<GridPos, Bubble>, pos: GridPos): Boolean {
        for (nb in neighborsOf(pos)) {
            val b = bubbles[nb] ?: return true // an empty neighbor is free space
            if (b !is Bubble.Stone && b !is Bubble.Chained) return true
        }
        return false
    }

    private fun stoneNeighborCount(bubbles: Map<GridPos, Bubble>, pos: GridPos): Int {
        var n = 0
        for (nb in neighborsOf(pos)) if (bubbles[nb] is Bubble.Stone) n++
        return n
    }

    // --- supernovae ------------------------------------------------------------------------------

    /**
     * Promotes up to [SUPERNOVA_CAP] plain colored cells to [Bubble.Supernova] of the same color.
     * Each eligible cell is admitted with probability [GenParams.supernovaDensity]; a cell is eligible
     * only if it is still a plain [Bubble.Colored] and has at least one same-color matchable neighbor,
     * so every planted supernova can actually be reached and detonated by a like-colored match. When
     * [GenParams.supernovaDensity] is 0 (levels below [SUPERNOVA_GATE]) this consumes no RNG and leaves
     * the grid — and the downstream RNG stream — byte-for-byte unchanged.
     */
    private fun sprinkleSupernovae(bubbles: HashMap<GridPos, Bubble>, p: GenParams, rng: Rng) {
        if (p.supernovaDensity <= 0f) return
        val order = bubbles.keys.sortedBy { it.packed }.toMutableList()
        shuffle(order, rng)
        var placed = 0
        for (pos in order) {
            if (placed >= SUPERNOVA_CAP) break
            val color = (bubbles[pos] as? Bubble.Colored)?.color ?: continue
            if (!hasSameColorNeighbor(bubbles, pos, color)) continue
            if (rng.nextFloat() >= p.supernovaDensity) continue
            bubbles[pos] = Bubble.Supernova(color)
            placed++
        }
    }

    /** Whether [pos] has a neighbor that would match [color] this turn (plain colored or a supernova). */
    private fun hasSameColorNeighbor(bubbles: Map<GridPos, Bubble>, pos: GridPos, color: BubbleColor): Boolean {
        for (nb in neighborsOf(pos)) {
            when (val b = bubbles[nb]) {
                is Bubble.Colored -> if (b.color == color) return true
                is Bubble.Supernova -> if (b.color == color) return true
                else -> Unit
            }
        }
        return false
    }

    // --- cosmic mechanics ------------------------------------------------------------------------

    /**
     * Blinks up to [PULSAR_CAP] plain colored cells into lit [Bubble.Pulsar]s of the same color, each
     * admitted with probability [GenParams.pulsarDensity]. Color-preserving (a pulsar keeps its
     * cell's inherent color), so it can run before the color floor without disturbing it. Below the
     * gate ([GenParams.pulsarDensity] == 0) it consumes no RNG.
     */
    private fun sprinklePulsars(bubbles: HashMap<GridPos, Bubble>, p: GenParams, rng: Rng) {
        if (p.pulsarDensity <= 0f) return
        val order = bubbles.keys.sortedBy { it.packed }.toMutableList()
        shuffle(order, rng)
        var placed = 0
        for (pos in order) {
            if (placed >= PULSAR_CAP) break
            val color = (bubbles[pos] as? Bubble.Colored)?.color ?: continue
            if (rng.nextFloat() >= p.pulsarDensity) continue
            bubbles[pos] = Bubble.Pulsar(color, lit = true)
            placed++
        }
    }

    /**
     * With probability [GenParams.wormholePairChance] plants exactly one [Bubble.Wormhole] pair
     * (pairId 1): one portal in the left half, one in the right half, both in mid rows (off the
     * ceiling and bottom rows) and never adjacent to each other. Below the gate it consumes no RNG.
     */
    private fun sprinkleWormholes(bubbles: HashMap<GridPos, Bubble>, p: GenParams, rng: Rng) {
        if (p.wormholePairChance <= 0f) return
        if (rng.nextFloat() >= p.wormholePairChance) return

        val minRow = bubbles.keys.minOf { it.row }
        val maxRow = bubbles.keys.maxOf { it.row }
        val midRows = (minRow + 1)..(maxRow - 1)
        val half = EVEN_COLS / 2

        val order = bubbles.keys.sortedBy { it.packed }.toMutableList()
        shuffle(order, rng)
        val left = order.firstOrNull { pos ->
            bubbles[pos] is Bubble.Colored && pos.row in midRows && pos.col < half
        } ?: return
        val right = order.firstOrNull { pos ->
            bubbles[pos] is Bubble.Colored && pos.row in midRows && pos.col >= half &&
                pos !in neighborsOf(left)
        } ?: return

        bubbles[left] = Bubble.Wormhole(pairId = 1)
        bubbles[right] = Bubble.Wormhole(pairId = 1)
    }

    /**
     * Converts up to [GenParams.gravityWellCap] colored cells into [Bubble.GravityWell]s — never on
     * the bottom occupied row and never adjacent to another well. Below the gate it consumes no RNG.
     */
    private fun sprinkleGravityWells(bubbles: HashMap<GridPos, Bubble>, p: GenParams, rng: Rng) {
        if (p.gravityWellCap <= 0) return
        // Roll an actual count in 0..cap, so wells are a genuine "at most 2" spice rather than always
        // exactly two; this keeps the densest late levels comfortably clearable.
        val target = rng.nextInt(p.gravityWellCap + 1)
        if (target == 0) return
        val order = bubbles.keys.sortedBy { it.packed }.toMutableList()
        shuffle(order, rng)
        val bottomRow = bubbles.keys.maxOf { it.row }
        val ceilingRow = bubbles.keys.minOf { it.row }
        val placed = ArrayList<GridPos>(target)
        for (pos in order) {
            if (placed.size >= target) break
            // Never the ceiling row (a ceiling-anchored well can never be detached) nor the bottom row.
            if (pos.row == bottomRow || pos.row == ceilingRow) continue
            if (bubbles[pos] !is Bubble.Colored) continue
            if (placed.any { pos in neighborsOf(it) }) continue
            bubbles[pos] = Bubble.GravityWell
            placed.add(pos)
        }
    }

    // --- helpers ---------------------------------------------------------------------------------

    /** Fisher–Yates over [list] driven by [rng], for deterministic ordering. */
    private fun shuffle(list: MutableList<GridPos>, rng: Rng) {
        for (i in list.size - 1 downTo 1) {
            val j = rng.nextInt(i + 1)
            val tmp = list[i]
            list[i] = list[j]
            list[j] = tmp
        }
    }

    /** The six odd-r neighbors of [pos] that name real cells in an [EVEN_COLS]-wide field. */
    private fun neighborsOf(pos: GridPos): List<GridPos> {
        val deltas = if (pos.row.mod(2) == 0) EVEN_ROW_DELTAS else ODD_ROW_DELTAS
        val out = ArrayList<GridPos>(6)
        for ((dr, dc) in deltas) {
            val candidate = GridPos(pos.row + dr, pos.col + dc)
            if (GridGeometry.isValidCell(candidate, EVEN_COLS)) out.add(candidate)
        }
        return out
    }

    private const val MIN_PER_COLOR = 3

    /** At most this many supernovae are planted in any generated level. */
    private const val SUPERNOVA_CAP = 2

    /** At most this many pulsars are planted in any generated level. */
    private const val PULSAR_CAP = 4

    private val ANCHOR_PROBE = Bubble.Colored(BubbleColor.RED)

    private val EVEN_ROW_DELTAS = listOf(0 to -1, 0 to 1, -1 to -1, -1 to 0, 1 to -1, 1 to 0)
    private val ODD_ROW_DELTAS = listOf(0 to -1, 0 to 1, -1 to 0, -1 to 1, 1 to 0, 1 to 1)
}
