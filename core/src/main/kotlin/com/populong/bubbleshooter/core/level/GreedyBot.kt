package com.populong.bubbleshooter.core.level

import com.populong.bubbleshooter.core.engine.Ammo
import com.populong.bubbleshooter.core.engine.GameConfig
import com.populong.bubbleshooter.core.engine.GameEngine
import com.populong.bubbleshooter.core.engine.GameInput
import com.populong.bubbleshooter.core.engine.GameState
import com.populong.bubbleshooter.core.engine.Phase
import com.populong.bubbleshooter.core.grid.Bubble
import com.populong.bubbleshooter.core.grid.BubbleColor
import com.populong.bubbleshooter.core.grid.BubbleGrid
import com.populong.bubbleshooter.core.grid.GridPos
import com.populong.bubbleshooter.core.grid.Vec2
import com.populong.bubbleshooter.core.grid.matchableColor
import com.populong.bubbleshooter.core.mode.GameMode
import com.populong.bubbleshooter.core.physics.AimPath
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * A deterministic greedy reference player that drives a real [GameEngine]. It is the validation
 * harness for the level generator — a level the bot cannot clear within a shot budget is too hard —
 * and lives in the main source set so future authoring/QA tooling can reuse it.
 *
 * On each turn it sweeps a fixed fan of aim directions, previews each with [AimPath] (matching the
 * engine's own collision model), scores the landing per ammo type, and fires the best shot; when no
 * shot pops it falls back to the placement that best grows a same-color group, and it swaps ammo
 * when the queued shot is clearly better.
 */
object GreedyBot {

    /** The outcome of a bot playthrough. */
    data class Result(val won: Boolean, val shotsUsed: Int, val finalScore: Long)

    private val config = GameConfig()
    private val engine = GameEngine(config)

    /** Precomputed aim fan: θ ∈ [-85°, 85°] step 1.5°, as `(sin θ, -cos θ)` directions. */
    private val AIM_DIRS: List<Vec2> = buildList {
        var deg = -85.0
        while (deg <= 85.0 + 1e-9) {
            val r = Math.toRadians(deg)
            add(Vec2(sin(r).toFloat(), (-cos(r)).toFloat()))
            deg += 1.5
        }
    }

    /**
     * Plays [spec] (with its shot budget replaced by [maxShots]) to a win or loss, returning the
     * result. Fully deterministic: the same spec always produces the same play.
     */
    fun play(spec: LevelSpec, maxShots: Int = spec.shots): Result {
        val mode = GameMode.Level(spec.copy(shots = maxShots))
        var s = engine.initialState(mode, seed = spec.id.toLong())

        var guard = 0
        while (s.phase == Phase.AIMING && guard++ < maxShots + 4) {
            s = takeTurn(s)
        }
        return Result(won = s.phase == Phase.WON, shotsUsed = s.shotsFired, finalScore = s.score)
    }

    /** Chooses and fires one shot, then advances the engine until it is aiming again (or the game ends). */
    private fun takeTurn(state: GameState): GameState {
        val grid = state.grid
        val origin = state.shooterOrigin
        val ceilingY = state.ceilingY

        // Landing cells depend only on the grid and direction, so compute them once for both ammos.
        val landings = ArrayList<Landing>(AIM_DIRS.size)
        for (dir in AIM_DIRS) {
            val cell = AimPath.compute(
                grid, ceilingY, origin, dir, maxBounces = 2,
                speed = config.projectileSpeed, gravityStrength = config.gravityWellStrength,
            ).landingCell ?: continue
            landings.add(Landing(dir, cell))
        }
        if (landings.isEmpty()) return fire(state, Vec2(0f, -1f), swap = false)

        val current = plan(grid, landings, state.currentAmmo)
        val next = plan(grid, landings, state.nextAmmo)

        // Swap only when the queued ammo yields a strictly better pop.
        return if (next.popValue > 0 && next.popValue >= current.popValue + 2) {
            fire(state, next.dir, swap = true)
        } else {
            fire(state, current.dir, swap = false)
        }
    }

    /** The best plan for one ammo across all [landings]: the popping shot if any, else a group-builder. */
    private fun plan(grid: BubbleGrid, landings: List<Landing>, ammo: Ammo): Plan {
        var bestPopDir: Vec2? = null
        var bestPop = 0
        var bestAdjDir = landings.first().dir
        var bestAdj = Int.MIN_VALUE

        for (landing in landings) {
            val cell = landing.cell
            val pop = popValue(grid, cell, ammo)
            if (pop > bestPop) {
                bestPop = pop
                bestPopDir = landing.dir
            }
            // Group-builder: strongly favor touching same-color bubbles, then staying high so the
            // stack never grows down into the lose line.
            val adj = adjacencyValue(grid, cell, ammo) * 1000 - (cell.row - grid.ceilingRow)
            if (adj > bestAdj) {
                bestAdj = adj
                bestAdjDir = landing.dir
            }
        }
        return Plan(dir = bestPopDir ?: bestAdjDir, popValue = bestPop)
    }

    /** How many bubbles this ammo would clear if it landed at [cell]; 0 if it would not pop. */
    private fun popValue(grid: BubbleGrid, cell: GridPos, ammo: Ammo): Int = when (ammo) {
        Ammo.Bomb -> grid.cells.keys.count { hexDistance(cell, it) <= config.bombRadius }
        Ammo.Rainbow -> {
            var best = 0
            for (color in neighborColors(grid, cell)) {
                val group = matchGroupAt(grid, cell, color)
                val value = group.size + novaBonus(grid, group)
                if (group.size >= 3 && value > best) best = value
            }
            if (best >= 3) best + landingBonus(grid, cell) else 0
        }
        is Ammo.ColorAmmo -> {
            val group = matchGroupAt(grid, cell, ammo.color)
            if (group.size >= 3) group.size + landingBonus(grid, cell) + novaBonus(grid, group) else 0
        }
    }

    /** Extra value for clearing supernovae in a hypothetical match, so the bot seeks their shockwaves. */
    private fun novaBonus(grid: BubbleGrid, group: Set<GridPos>): Int =
        4 * group.count { grid.bubbleAt(it) is Bubble.Supernova }

    /** A small nudge toward high (anchor-cutting) landings and toward cracking adjacent ice. */
    private fun landingBonus(grid: BubbleGrid, cell: GridPos): Int {
        val height = grid.bottomRow() - grid.ceilingRow + 1
        var bonus = 0
        if (cell.row - grid.ceilingRow < height / 2) bonus += 2
        if (grid.occupiedNeighbors(cell).any { grid.bubbleAt(it) is Bubble.Ice }) bonus += 1
        return bonus
    }

    /** Group-building score for a non-popping shot: how many same-target neighbors it would touch. */
    private fun adjacencyValue(grid: BubbleGrid, cell: GridPos, ammo: Ammo): Int = when (ammo) {
        Ammo.Bomb -> grid.occupiedNeighbors(cell).size
        Ammo.Rainbow -> neighborColors(grid, cell).maxOfOrNull { color ->
            grid.occupiedNeighbors(cell).count { grid.bubbleAt(it)?.matchableColor() == color }
        } ?: 0
        is Ammo.ColorAmmo -> grid.occupiedNeighbors(cell).count {
            sameTarget(grid.bubbleAt(it), ammo.color)
        }
    }

    /**
     * The same-color cluster (including [cell]) a bubble of [color] placed at the empty [cell] would
     * join. Optimistically treats fog of the matching color as revealed (the engine reveals fog
     * around a landing before matching) and counts supernovae of that color, but never still-frozen
     * ice or locked chains.
     */
    private fun matchGroupAt(grid: BubbleGrid, cell: GridPos, color: BubbleColor): Set<GridPos> {
        val visited = HashSet<GridPos>()
        visited.add(cell)
        val stack = ArrayDeque<GridPos>()
        stack.addLast(cell)
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            for (nb in grid.occupiedNeighbors(cur)) {
                if (nb in visited) continue
                if (sameTarget(grid.bubbleAt(nb), color)) {
                    visited.add(nb)
                    stack.addLast(nb)
                }
            }
        }
        return visited
    }

    /** Whether [bubble] would match [color] this turn (colored/supernova/lit-pulsar, or fog about to
     * reveal). An unlit pulsar is valued like a [Bubble.Stone] — inert, no match. */
    private fun sameTarget(bubble: Bubble?, color: BubbleColor): Boolean = when (bubble) {
        is Bubble.Colored -> bubble.color == color
        is Bubble.Supernova -> bubble.color == color
        is Bubble.Fog -> bubble.color == color
        is Bubble.Pulsar -> bubble.lit && bubble.color == color
        else -> false
    }

    private fun neighborColors(grid: BubbleGrid, cell: GridPos): Set<BubbleColor> {
        val out = HashSet<BubbleColor>()
        for (nb in grid.occupiedNeighbors(cell)) {
            val c = grid.bubbleAt(nb)?.matchableColor() ?: (grid.bubbleAt(nb) as? Bubble.Fog)?.color
            if (c != null) out.add(c)
        }
        return out
    }

    private fun fire(state: GameState, dir: Vec2, swap: Boolean): GameState {
        var s = state
        if (swap) s = engine.handleInput(s, GameInput.Swap).state
        s = engine.handleInput(s, GameInput.AimAt(dir)).state
        s = engine.handleInput(s, GameInput.Fire).state
        var guard = 0
        while (s.phase == Phase.FLYING && guard++ < 1_000_000) {
            s = engine.step(s).state
        }
        return s
    }

    // --- hex distance (odd-r → cube) -------------------------------------------------------------

    private fun hexDistance(a: GridPos, b: GridPos): Int {
        val (ax, ay, az) = toCube(a)
        val (bx, by, bz) = toCube(b)
        return (abs(ax - bx) + abs(ay - by) + abs(az - bz)) / 2
    }

    private fun toCube(pos: GridPos): Triple<Int, Int, Int> {
        val parity = pos.row.mod(2)
        val x = pos.col - (pos.row - parity) / 2
        val z = pos.row
        return Triple(x, -x - z, z)
    }

    private class Landing(val dir: Vec2, val cell: GridPos)

    private class Plan(val dir: Vec2, val popValue: Int)
}
