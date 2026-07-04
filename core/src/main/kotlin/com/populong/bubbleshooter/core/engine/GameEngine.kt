package com.populong.bubbleshooter.core.engine

import com.populong.bubbleshooter.core.grid.Bubble
import com.populong.bubbleshooter.core.grid.BubbleColor
import com.populong.bubbleshooter.core.grid.BubbleGrid
import com.populong.bubbleshooter.core.grid.GridGeometry
import com.populong.bubbleshooter.core.grid.GridPos
import com.populong.bubbleshooter.core.grid.matchableColor
import com.populong.bubbleshooter.core.level.LevelSpec
import com.populong.bubbleshooter.core.level.Rng
import com.populong.bubbleshooter.core.match.FloatDetector
import com.populong.bubbleshooter.core.match.MatchFinder
import com.populong.bubbleshooter.core.match.ObstacleRules
import com.populong.bubbleshooter.core.mode.GameMode
import com.populong.bubbleshooter.core.mode.Mutator
import com.populong.bubbleshooter.core.mode.totalMultiplier
import com.populong.bubbleshooter.core.physics.Projectile
import com.populong.bubbleshooter.core.physics.ProjectileSim
import com.populong.bubbleshooter.core.physics.SimOutcome
import kotlin.math.max

/** The new state plus the events emitted producing it. */
data class StepResult(val state: GameState, val events: List<GameEvent>)

/**
 * The deterministic core reducer. All game evolution flows through [initialState], [handleInput]
 * and [step]; given identical inputs it always produces identical states, with no wall-clock,
 * threading or global RNG. Randomness comes solely from the [Rng] state carried in [GameState].
 */
class GameEngine(private val config: GameConfig = GameConfig()) {

    private val scoring = Scoring(config)

    // --- Construction -------------------------------------------------------------------------

    /** Builds the opening state for [mode], deriving the RNG stream from [seed]. */
    fun initialState(mode: GameMode, seed: Long): GameState {
        val rng = Rng(Rng.mix(seed))
        val grid = buildGrid(mode, rng)
        val shotsLeft = when (mode) {
            is GameMode.Level -> mode.spec.shots
            is GameMode.Daily -> mode.spec.shots
            is GameMode.Endless -> -1
        }
        val current = drawAmmo(mode, grid, ordinal = 1, rng)
        val next = drawAmmo(mode, grid, ordinal = 2, rng)
        return GameState(
            mode = mode,
            config = config,
            grid = grid,
            phase = Phase.AIMING,
            projectile = null,
            currentAmmo = current,
            nextAmmo = next,
            aimDir = null,
            precision = false,
            shotsFired = 0,
            shotsLeft = shotsLeft,
            score = 0L,
            combo = 0,
            feverMeter = 0f,
            feverTicksLeft = 0,
            descentSteps = 0,
            rngState = rng.state,
            ticks = 0L,
        )
    }

    private fun buildGrid(mode: GameMode, rng: Rng): BubbleGrid = when (mode) {
        is GameMode.Level -> mode.spec.initialGrid
        is GameMode.Daily -> mode.spec.initialGrid
        is GameMode.Endless -> {
            val evenCols = if (Mutator.NARROW_FIELD in mode.mutators) 7 else 8
            val palette = BubbleColor.palette(paletteSizeOf(mode))
            val cells = HashMap<GridPos, Bubble>()
            for (row in 0 until 5) {
                val cols = GridGeometry.colsInRow(row, evenCols)
                for (col in 0 until cols) {
                    cells[GridPos(row, col)] = Bubble.Colored(palette[rng.nextInt(palette.size)])
                }
            }
            BubbleGrid(cells, evenCols, ceilingRow = 0)
        }
    }

    // --- Input --------------------------------------------------------------------------------

    /** Applies a player [input] to [state], returning the updated state and any events. */
    fun handleInput(state: GameState, input: GameInput): StepResult = when (input) {
        is GameInput.AimAt -> {
            val n = input.dir.normalized()
            if (n.y > -0.05f) StepResult(state, emptyList())
            else StepResult(state.copy(aimDir = n), emptyList())
        }

        GameInput.Fire -> {
            if (state.phase != Phase.AIMING || state.aimDir == null) {
                StepResult(state, emptyList())
            } else {
                val projectile = Projectile(
                    pos = state.shooterOrigin,
                    vel = state.aimDir * config.projectileSpeed,
                    ammo = state.currentAmmo,
                    bounces = 0,
                )
                val next = state.copy(
                    phase = Phase.FLYING,
                    projectile = projectile,
                    shotsFired = state.shotsFired + 1,
                    shotsLeft = if (state.shotsLeft >= 0) state.shotsLeft - 1 else -1,
                )
                StepResult(next, listOf(GameEvent.Fired))
            }
        }

        GameInput.Swap -> {
            if (state.phase != Phase.AIMING) StepResult(state, emptyList())
            else StepResult(
                state.copy(currentAmmo = state.nextAmmo, nextAmmo = state.currentAmmo),
                listOf(GameEvent.Swapped),
            )
        }

        is GameInput.Precision -> StepResult(state.copy(precision = input.on), emptyList())
    }

    // --- Time ---------------------------------------------------------------------------------

    /** Advances the simulation by exactly one tick. */
    fun step(state: GameState): StepResult {
        val events = ArrayList<GameEvent>()
        var s = state

        // Fever ticks down on every step, regardless of phase.
        if (s.feverTicksLeft > 0) {
            val remaining = s.feverTicksLeft - 1
            s = s.copy(feverTicksLeft = remaining)
            if (remaining == 0) events.add(GameEvent.FeverEnded)
        }
        s = s.copy(ticks = s.ticks + 1)

        if (s.phase != Phase.FLYING) return StepResult(s, events)

        val projectile = s.projectile!!
        val outcome = ProjectileSim.step(s.grid, s.ceilingY, 2f * s.grid.evenCols, projectile, config.TICK)
        return when (outcome) {
            is SimOutcome.Moving -> {
                val moved = outcome.projectile
                if (moved.bounces > projectile.bounces) events.add(GameEvent.Bounced(moved.pos))
                StepResult(s.copy(projectile = moved), events)
            }

            is SimOutcome.Landed -> resolve(s, outcome, events)
        }
    }

    // --- Resolution ---------------------------------------------------------------------------

    private fun resolve(state: GameState, landed: SimOutcome.Landed, events: MutableList<GameEvent>): StepResult {
        val feverOn = state.feverTicksLeft > 0
        val ammo = state.currentAmmo
        val cell = landed.cell
        val bounces = landed.bounces
        val rng = Rng(state.rngState)

        var grid = state.grid
        var score = state.score
        var combo = state.combo
        var feverMeter = state.feverMeter
        var feverTicks = state.feverTicksLeft
        var poppedForObstacles: Set<GridPos> = emptySet()
        var didPop = false

        if (ammo is Ammo.Bomb) {
            val removed = grid.cells.keys.filterTo(HashSet()) { hexDistance(cell, it) <= config.bombRadius }
            grid = grid.without(removed)
            events.add(GameEvent.Landed(cell))
            events.add(GameEvent.BombExploded(removed))
            if (removed.isNotEmpty()) {
                didPop = true
                combo += 1
                score += award(state, scoring.popScore(removed.size, combo, feverOn))
                feverMeter += config.feverGainPerPop + removed.size * config.feverGainPerBubble
            } else {
                combo = 0
                events.add(GameEvent.ComboBroken)
                feverMeter = max(0f, feverMeter - config.feverLossOnMiss)
            }
            poppedForObstacles = removed
        } else {
            val placedColor = when (ammo) {
                is Ammo.ColorAmmo -> ammo.color
                Ammo.Rainbow -> chooseRainbowColor(grid, cell)
                Ammo.Bomb -> error("handled above")
            }
            grid = grid.with(cell, Bubble.Colored(placedColor))
            events.add(GameEvent.Landed(cell))

            // Reveal fog before matching so a freshly-revealed bubble can join the match.
            val revealed = ObstacleRules.revealFogAround(grid, cell)
            val fogChanged = grid.cells.keys.filterTo(HashSet()) { p ->
                val before = grid.bubbleAt(p)
                val after = revealed.bubbleAt(p)
                before is Bubble.Fog && !before.revealed && after is Bubble.Fog && after.revealed
            }
            grid = revealed
            if (fogChanged.isNotEmpty()) events.add(GameEvent.FogRevealed(fogChanged))

            val match = MatchFinder.findMatch(grid, cell, placedColor)
            if (match.size >= 3) {
                grid = grid.without(match)
                didPop = true
                combo += 1
                val gained = award(state, scoring.popScore(match.size, combo, feverOn))
                score += gained
                events.add(GameEvent.Popped(match, placedColor, gained))
                feverMeter += config.feverGainPerPop + match.size * config.feverGainPerBubble
                if (bounces > 0) {
                    val bonus = award(state, scoring.bankBonus(bounces))
                    score += bonus
                    events.add(GameEvent.BankShot(bounces, bonus))
                }
                poppedForObstacles = match
            } else {
                combo = 0
                events.add(GameEvent.ComboBroken)
                feverMeter = max(0f, feverMeter - config.feverLossOnMiss)
            }
        }

        // Obstacle side effects from the removal.
        if (poppedForObstacles.isNotEmpty()) {
            val before = grid
            val after = ObstacleRules.afterPop(before, poppedForObstacles)
            val iceCracked = HashSet<GridPos>()
            val unchained = HashSet<GridPos>()
            for ((pos, bubble) in before.cells) {
                val now = after.bubbleAt(pos)
                if (bubble is Bubble.Ice && now != bubble) iceCracked.add(pos)
                if (bubble is Bubble.Chained && now is Bubble.Colored) unchained.add(pos)
            }
            grid = after
            if (iceCracked.isNotEmpty()) events.add(GameEvent.IceCracked(iceCracked))
            if (unchained.isNotEmpty()) events.add(GameEvent.Unchained(unchained))
        }

        // Gravity: drop everything no longer anchored.
        val floating = FloatDetector.floating(grid)
        if (floating.isNotEmpty()) {
            grid = grid.without(floating)
            val gained = award(state, scoring.fallScore(floating.size, combo, feverOn))
            score += gained
            events.add(GameEvent.Fell(floating, gained))
        }

        // Fever ignition.
        if (didPop && feverTicks == 0 && feverMeter >= 1f) {
            feverTicks = config.feverDurationTicks
            feverMeter = 0f
            events.add(GameEvent.FeverStarted)
        }

        // Descent / row insertion.
        var descentSteps = state.descentSteps
        when (state.mode) {
            is GameMode.Level -> descentSteps += levelDescent(state.mode.spec, state.shotsFired, events)
            is GameMode.Daily -> descentSteps += levelDescent(state.mode.spec, state.shotsFired, events)
            is GameMode.Endless -> {
                val everyN = if (Mutator.FASTER_DESCENT in state.mode.mutators) {
                    config.endlessRowEveryShotsFast
                } else {
                    config.endlessRowEveryShots
                }
                if (state.shotsFired % everyN == 0) {
                    grid = insertEndlessRow(state.mode, grid, rng)
                    events.add(GameEvent.RowInserted)
                }
            }
        }

        // Win / lose.
        val isLevelMode = state.mode is GameMode.Level || state.mode is GameMode.Daily
        val hasNonStone = grid.cells.values.any { it !is Bubble.Stone }
        if (isLevelMode && !hasNonStone) {
            val clearBonus = max(0, state.shotsLeft) * config.clearBonusPerRemainingShot
            val finalScore = score + clearBonus
            val stars = scoring.starsFor(finalScore, thresholdsOf(state.mode))
            events.add(GameEvent.Won(stars, finalScore))
            return StepResult(
                state.copy(
                    grid = grid, phase = Phase.WON, projectile = null,
                    score = finalScore, combo = combo, feverMeter = feverMeter, feverTicksLeft = feverTicks,
                    descentSteps = descentSteps, rngState = rng.state,
                ),
                events,
            )
        }

        val rowsUsed = grid.bottomRow() - grid.ceilingRow + 1
        val overflow = rowsUsed + descentSteps * 0.5f > config.maxRows
        val outOfShots = isLevelMode && state.shotsLeft == 0
        if (overflow || outOfShots) {
            events.add(GameEvent.Lost(score))
            return StepResult(
                state.copy(
                    grid = grid, phase = Phase.LOST, projectile = null,
                    score = score, combo = combo, feverMeter = feverMeter, feverTicksLeft = feverTicks,
                    descentSteps = descentSteps, rngState = rng.state,
                ),
                events,
            )
        }

        // Advance the ammo queue; the new next never uses a color absent from the resolved grid.
        val newCurrent = state.nextAmmo
        val newNext = drawAmmo(state.mode, grid, ordinal = state.shotsFired + 2, rng)
        return StepResult(
            state.copy(
                grid = grid, phase = Phase.AIMING, projectile = null,
                currentAmmo = newCurrent, nextAmmo = newNext,
                score = score, combo = combo, feverMeter = feverMeter, feverTicksLeft = feverTicks,
                descentSteps = descentSteps, rngState = rng.state,
            ),
            events,
        )
    }

    private fun levelDescent(spec: LevelSpec, shotsFired: Int, events: MutableList<GameEvent>): Int {
        if (spec.descentEveryShots > 0 && shotsFired % spec.descentEveryShots == 0) {
            events.add(GameEvent.CeilingStepped)
            return 1
        }
        return 0
    }

    private fun insertEndlessRow(mode: GameMode.Endless, grid: BubbleGrid, rng: Rng): BubbleGrid {
        val newRow = grid.ceilingRow - 1
        val palette = BubbleColor.palette(paletteSizeOf(mode))
        val cells = HashMap(grid.cells)
        val cols = GridGeometry.colsInRow(newRow, grid.evenCols)
        for (col in 0 until cols) {
            cells[GridPos(newRow, col)] = Bubble.Colored(palette[rng.nextInt(palette.size)])
        }
        return BubbleGrid(cells, grid.evenCols, newRow)
    }

    // --- Ammo ---------------------------------------------------------------------------------

    private fun drawAmmo(mode: GameMode, grid: BubbleGrid, ordinal: Int, rng: Rng): Ammo = when (mode) {
        is GameMode.Level -> drawWithSpecials(mode.spec, grid, ordinal, rng)
        is GameMode.Daily -> drawWithSpecials(mode.spec, grid, ordinal, rng)
        is GameMode.Endless -> Ammo.ColorAmmo(drawColor(grid, paletteSizeOf(mode), rng))
    }

    private fun drawWithSpecials(spec: LevelSpec, grid: BubbleGrid, ordinal: Int, rng: Rng): Ammo {
        if (spec.bombEvery > 0 && ordinal % spec.bombEvery == 0) return Ammo.Bomb
        if (spec.rainbowEvery > 0 && ordinal % spec.rainbowEvery == 0) return Ammo.Rainbow
        return Ammo.ColorAmmo(drawColor(grid, spec.paletteSize, rng))
    }

    private fun drawColor(grid: BubbleGrid, paletteSize: Int, rng: Rng): BubbleColor {
        val colors = distinctGridColors(grid).ifEmpty { BubbleColor.palette(paletteSize) }
        return colors[rng.nextInt(colors.size)]
    }

    private fun distinctGridColors(grid: BubbleGrid): List<BubbleColor> =
        grid.cells.values.mapNotNull { inherentColor(it) }.distinct().sortedBy { it.ordinal }

    private fun chooseRainbowColor(grid: BubbleGrid, cell: GridPos): BubbleColor {
        val neighborColors = grid.occupiedNeighbors(cell)
            .mapNotNull { grid.bubbleAt(it)?.matchableColor() }
            .distinct()
            .sortedBy { it.ordinal }
        if (neighborColors.isEmpty()) return mostCommonColor(grid) ?: BubbleColor.RED

        var best = neighborColors.first()
        var bestSize = -1
        for (color in neighborColors) {
            val size = MatchFinder.findMatch(grid.with(cell, Bubble.Colored(color)), cell, color).size
            if (size > bestSize) {
                bestSize = size
                best = color
            }
        }
        return best
    }

    private fun mostCommonColor(grid: BubbleGrid): BubbleColor? {
        val counts = HashMap<BubbleColor, Int>()
        for (bubble in grid.cells.values) {
            val color = inherentColor(bubble) ?: continue
            counts[color] = (counts[color] ?: 0) + 1
        }
        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<BubbleColor, Int>> { it.value }.thenBy { it.key.ordinal })
            .firstOrNull()?.key
    }

    // --- Helpers ------------------------------------------------------------------------------

    private fun award(state: GameState, base: Long): Long {
        val multiplier = (state.mode as? GameMode.Endless)?.mutators?.totalMultiplier() ?: 1f
        return if (multiplier == 1f) base else (base * multiplier).toLong()
    }

    private fun thresholdsOf(mode: GameMode): List<Long> = when (mode) {
        is GameMode.Level -> mode.spec.starThresholds
        is GameMode.Daily -> mode.spec.starThresholds
        is GameMode.Endless -> emptyList()
    }

    private fun paletteSizeOf(mode: GameMode): Int = when (mode) {
        is GameMode.Level -> mode.spec.paletteSize
        is GameMode.Daily -> mode.spec.paletteSize
        is GameMode.Endless -> if (Mutator.EXTRA_COLOR in mode.mutators) 5 else 4
    }

    private fun inherentColor(bubble: Bubble): BubbleColor? = when (bubble) {
        is Bubble.Colored -> bubble.color
        is Bubble.Ice -> bubble.color
        is Bubble.Fog -> bubble.color
        is Bubble.Chained -> bubble.color
        Bubble.Stone -> null
    }

    /** Hex grid-distance between two odd-r offset cells, via cube coordinates. */
    private fun hexDistance(a: GridPos, b: GridPos): Int {
        val (ax, ay, az) = toCube(a)
        val (bx, by, bz) = toCube(b)
        return (kotlin.math.abs(ax - bx) + kotlin.math.abs(ay - by) + kotlin.math.abs(az - bz)) / 2
    }

    private fun toCube(pos: GridPos): Triple<Int, Int, Int> {
        val parity = pos.row.mod(2)
        val x = pos.col - (pos.row - parity) / 2
        val z = pos.row
        val y = -x - z
        return Triple(x, y, z)
    }
}
