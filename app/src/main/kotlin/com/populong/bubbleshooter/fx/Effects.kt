package com.populong.bubbleshooter.fx

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.populong.bubbleshooter.core.engine.GameEvent
import com.populong.bubbleshooter.core.grid.Bubble
import com.populong.bubbleshooter.core.grid.BubbleColor
import com.populong.bubbleshooter.core.grid.BubbleGrid
import com.populong.bubbleshooter.core.grid.GridGeometry
import com.populong.bubbleshooter.core.grid.GridPos
import com.populong.bubbleshooter.ui.theme.Neon
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val TWO_PI = (2.0 * PI).toFloat()

private val WHITE_ARGB = Color.White.toArgb()
private val GOLD_ARGB = Neon.gold.toArgb()
private val CYAN_ARGB = Neon.cyan.toArgb()
private val MAGENTA_ARGB = Neon.magenta.toArgb()
private val MINT_ARGB = Neon.mint.toArgb()
private val ORANGE_ARGB = Neon.bubbleColor(BubbleColor.ORANGE).toArgb()
private val GREY_ARGB = Color(0xFF9AA0B0).toArgb()
private val GREY_DIM_ARGB = Color(0xFF5A5F6E).toArgb()
private val CONFETTI_PALETTE: IntArray = (BubbleColor.all.map { Neon.bubbleColor(it).toArgb() } +
    listOf(GOLD_ARGB, CYAN_ARGB, MAGENTA_ARGB, WHITE_ARGB)).toIntArray()

private const val POPUP_LIFE = 1.0f
private const val POPUP_LIFE_BANK = 1.4f

/** Ring-buffer capacity for [EffectsController]'s pop-splat decals. */
@PublishedApi internal const val DECAL_CAPACITY = 24

/** Lifetime, in seconds, of a single pop-splat decal. */
@PublishedApi internal const val DECAL_LIFE = 0.5f

/** Damped-spring constants for the per-cell "impact jelly" registry (see [BubbleSpring]):
 * position offsets (landing-neighbor push) spring back faster/stiffer than the landed cell's own
 * squash-scale, which is deliberately a touch softer/slower so it reads as a bounce rather than a
 * twitch. */
private const val SPRING_K_POS = 220f
private const val SPRING_C_POS = 10f
private const val SPRING_K_SCALE = 180f
private const val SPRING_C_SCALE = 12f

/** Magnitude, in unit space, of the outward push applied to a landed cell's occupied neighbors. */
private const val NEIGHBOR_PUSH_UNITS = 0.22f

/** Starting squash-scale of a just-landed cell before it springs back to 1.0. */
private const val LANDED_SCALE_START = 1.35f

/** Spring constants for [EffectsController.shooterRecoil]: stiff and fairly damped so the recoil
 * kick reads as a snappy jolt rather than a lingering wobble. */
private const val RECOIL_K = 300f
private const val RECOIL_C = 16f
private const val RECOIL_KICK = 0.35f

/** Spring constants for [EffectsController.gridDropOffset]: intentionally underdamped (low `c`
 * relative to `k`) so the grid visibly overshoots past 0 before settling — the "dropped in" feel. */
private const val GRID_DROP_K = 140f
private const val GRID_DROP_C = 9f
private const val GRID_DROP_START = -0.5f

/** Per-row stagger, in seconds, between successive falling-actor spawns in the [GameEvent.Won]
 * cascade (row closest to the ceiling falls first). */
private const val WON_CASCADE_ROW_DELAY = 0.06f

/**
 * A single floating "+N"-style label, alive in unit space for [lifeSec] seconds. [x]/[y] are the
 * fixed unit-space spawn position (not mutated by [EffectsController.update] — the renderer is
 * responsible for translating position/alpha as a function of [age], so a popup's stored position
 * always reflects where the scoring happened). Few of these are alive at any moment, so allocating
 * one per spawn is fine; only the per-frame particle path needs to be allocation-free.
 */
data class ScorePopup(
    val x: Float,
    val y: Float,
    var age: Float,
    val lifeSec: Float,
    val text: String,
    val colorArgb: Int,
)

/**
 * A damped 2D spring registered per grid cell (keyed by [GridPos.packed]) for the "impact jelly"
 * wobble: [ox]/[oy] are a unit-space positional offset from the cell's rest position, [scale] a
 * uniform scale multiplier; both spring back toward `(0, 0)` / `1f` every [EffectsController.update].
 * [vx]/[vy]/[scaleV] are the spring's internal velocities — read only by [EffectsController]'s own
 * integration step, not meant to be consumed by renderer code.
 */
class BubbleSpring(
    var ox: Float = 0f,
    var oy: Float = 0f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var scale: Float = 1f,
    var scaleV: Float = 0f,
)

/** A single falling-actor spawn still waiting out its stagger delay (see the [GameEvent.Won]
 * cascade in [EffectsController.onWonCascade]). Small, bursty allocation on a rare event; not on
 * any per-frame hot path. */
private class PendingSpawn(var delaySec: Float, val cell: GridPos, val bubble: Bubble)

/**
 * Turns [GameEvent]s into particle bursts, score popups, falling-bubble spawns, pop-splat decals,
 * per-cell impact springs, and simple camera/shooter/grid springs. Owns one shared [ParticlePool]
 * (all particle-based effect types draw from it), one shared [FallingBubbles] pool (all
 * detached/falling-bubble effect types draw from it), a small list of [ScorePopup]s, a fixed-size
 * pop-splat decal ring buffer, and a bounded per-cell [BubbleSpring] registry.
 *
 * Positions are derived from [BubbleGrid] cell coordinates via [GridGeometry.centerX]/[centerY],
 * i.e. the same absolute row-space unit coordinates the grid itself is drawn in — the renderer
 * converts to pixels the same way for both (`FieldLayout.toPx(x, y, ceilingY)`), so no additional
 * ceiling-relative bookkeeping is needed here.
 */
class EffectsController(private val rng: java.util.Random = java.util.Random(7)) {

    /** Shared particle pool for every effect type; capacity sized for the largest simultaneous burst mix. */
    val particles = ParticlePool(capacity = 512)

    /** Shared pool of detached "falling" bubble actors (see [FallingBubbles]); every effect that
     * detaches bubbles from the grid (falling out of support, bomb/supernova debris, the win
     * cascade) spawns into this same pool. */
    val falling = FallingBubbles()

    /** Currently-alive score popups; typically 0-3 at once. */
    val popups = ArrayList<ScorePopup>()

    /** Decaying unit-space shake offset, refreshed every [update]; read directly by the renderer. */
    var shakeX: Float = 0f
        private set

    /** See [shakeX]. */
    var shakeY: Float = 0f
        private set

    private var shakeTimeLeft = 0f
    private var shakeDuration = 0f
    private var shakeAmp = 0f

    /** Decaying full-field white flash (0f = none), driven by high-impact events such as a
     * supernova detonation; read directly by [com.populong.bubbleshooter.render.GameRenderer],
     * which draws a full-canvas white rect on top of everything else at this alpha. */
    var flashAlpha: Float = 0f
        private set

    private var flashTimeLeft = 0f
    private var flashDuration = 0f
    private var flashPeak = 0f

    /** True while the renderer should skip drawing grid bubbles entirely — set for the
     * [GameEvent.Won] cinematic cascade (every remaining bubble becomes a falling actor instead),
     * cleared on the next [GameEvent.Fired] or [reset]. */
    var hideGrid: Boolean = false
        private set

    /** Spring offset applied to the shooter's draw position (unit-space, added to y — positive is
     * downward): a recoil kick on every [GameEvent.Fired], springing back to 0. */
    var shooterRecoil: Float = 0f
        private set
    private var shooterRecoilV: Float = 0f

    /** Spring offset applied to the whole field's screen-space translate alongside camera shake
     * (unit-space, added to y): starts negative (grid appears to drop in from above) on every
     * [GameEvent.RowInserted]/[GameEvent.CeilingStepped], overshooting past 0 before settling. */
    var gridDropOffset: Float = 0f
        private set
    private var gridDropOffsetV: Float = 0f

    /** Bounded per-cell impact-spring registry, keyed by [GridPos.packed]; entries are removed once
     * settled (see [updateSprings]), so this map holds only the handful of cells still wobbling. */
    private val springs = HashMap<Int, BubbleSpring>()

    /** Falling-actor spawns still waiting out their [GameEvent.Won]-cascade stagger delay. */
    private val pendingSpawns = ArrayList<PendingSpawn>()

    // --- Pop-splat decal ring buffer (fixed-size arrays; see addDecal/forEachDecal) -------------
    @PublishedApi internal val decalX = FloatArray(DECAL_CAPACITY)
    @PublishedApi internal val decalY = FloatArray(DECAL_CAPACITY)
    @PublishedApi internal val decalColor = IntArray(DECAL_CAPACITY)
    @PublishedApi internal val decalAge = FloatArray(DECAL_CAPACITY)
    private var decalWriteIdx = 0

    init {
        // Every slot starts already past its lifetime, i.e. invisible, rather than at age=0
        // (which would otherwise render as 24 "just spawned" decals at the world origin).
        decalAge.fill(DECAL_LIFE)
    }

    /**
     * Advances particles/falling actors, ages/reaps popups and decals, decays any in-flight screen
     * shake/flash, integrates every impact/recoil/grid-drop spring, and drains the pending
     * win-cascade spawn queue — all by [dt] seconds. [fieldWidth] (unit space, `evenCols * 2f`) is
     * needed by [falling] for its side-wall bounce.
     */
    fun update(dt: Float, fieldWidth: Float) {
        particles.update(dt)
        falling.update(dt, fieldWidth)
        updatePopups(dt)
        updateShake(dt)
        updateFlash(dt)
        updateDecals(dt)
        updateSprings(dt)
        updatePendingSpawns(dt)
        updateShooterRecoil(dt)
        updateGridDropOffset(dt)
    }

    /**
     * The current shake offset as a pair, for callers that prefer a single return value. Allocates
     * a `Pair` per call — on the render hot path prefer reading [shakeX]/[shakeY] directly instead.
     */
    fun shakeOffset(): Pair<Float, Float> = shakeX to shakeY

    /** Starts (or replaces) the current screen shake: [durationSec] seconds, [amplitude] unit-space peak offset. */
    fun shake(durationSec: Float, amplitude: Float) {
        shakeDuration = durationSec
        shakeTimeLeft = durationSec
        shakeAmp = amplitude
    }

    /** Starts (or replaces) the current screen flash: [durationSec] seconds, decaying from [peakAlpha]. */
    fun flash(durationSec: Float, peakAlpha: Float) {
        flashDuration = durationSec
        flashTimeLeft = durationSec
        flashPeak = peakAlpha
    }

    /** The live impact spring registered for [pos], or null if that cell isn't currently wobbling.
     * Read by [com.populong.bubbleshooter.render.GameRenderer] once per drawn grid cell; the map is
     * bounded (~24 live entries) so this is cheap. */
    fun springAt(pos: GridPos): BubbleSpring? = springs[pos.packed]

    /** Invokes [block] for every currently-visible pop-splat decal, with its unit-space position,
     * packed ARGB color, and age fraction in `[0, 1)` (0 = just spawned). Marked `inline` so the
     * lambda never allocates on the render hot path. */
    inline fun forEachDecal(block: (x: Float, y: Float, colorArgb: Int, ageFrac: Float) -> Unit) {
        for (i in 0 until DECAL_CAPACITY) {
            val age = decalAge[i]
            if (age < DECAL_LIFE) block(decalX[i], decalY[i], decalColor[i], age / DECAL_LIFE)
        }
    }

    /** Clears every transient effect: particles, falling actors, popups, decals, springs, the
     * win-cascade spawn queue, shake/flash, and [hideGrid]/[shooterRecoil]/[gridDropOffset]. Not
     * currently invoked anywhere in this module — [GameSessionHolder] is itself fully recreated
     * (via Compose `remember(mode, restartKey, ...)` in the read-only `GameScreen.kt`) on session
     * restart, which already yields a brand-new [EffectsController] with all-default state.
     * Kept as public API for a future caller that reuses one controller across restarts instead. */
    fun reset() {
        particles.clear()
        falling.clear()
        popups.clear()
        springs.clear()
        pendingSpawns.clear()
        decalAge.fill(DECAL_LIFE)
        decalWriteIdx = 0
        shakeX = 0f
        shakeY = 0f
        shakeTimeLeft = 0f
        shakeDuration = 0f
        shakeAmp = 0f
        flashAlpha = 0f
        flashTimeLeft = 0f
        flashDuration = 0f
        flashPeak = 0f
        hideGrid = false
        shooterRecoil = 0f
        shooterRecoilV = 0f
        gridDropOffset = 0f
        gridDropOffsetV = 0f
    }

    /**
     * Maps [events] to particle bursts / popups / falling-bubble spawns / decals / springs.
     * [preGrid] must be the grid *before* the events resolved (needed by e.g. [GameEvent.Fell] and
     * [GameEvent.BombExploded], whose cells are already gone from the post-step grid, to look up
     * which [Bubble] used to sit there); [postGrid] is the grid *after* resolution (used wherever
     * the previous single-`grid` parameter was — landing neighbors, fever/win/loss centroids,
     * etc). [feverActive] boosts Popped bursts (+50% count, gold tint mix).
     */
    fun onEvents(events: List<GameEvent>, preGrid: BubbleGrid, postGrid: BubbleGrid, feverActive: Boolean) {
        for (event in events) {
            when (event) {
                is GameEvent.Popped -> onPopped(event, feverActive)
                is GameEvent.Fell -> onFell(event, preGrid)
                is GameEvent.BombExploded -> onBombExploded(event, preGrid)
                is GameEvent.BankShot -> onBankShot(event, postGrid)
                is GameEvent.IceCracked -> onIceCracked(event)
                is GameEvent.Unchained -> onUnchained(event)
                GameEvent.FeverStarted -> onFeverStarted(postGrid)
                is GameEvent.Won -> {
                    onWon(postGrid)
                    onWonCascade(postGrid)
                }

                is GameEvent.Lost -> onLost(postGrid)
                is GameEvent.SupernovaChained -> onSupernovaChained(event, preGrid)
                is GameEvent.Landed -> onLanded(event, postGrid)
                GameEvent.Fired -> onFired()
                GameEvent.RowInserted -> onGridDrop()
                GameEvent.CeilingStepped -> onGridDrop()
                else -> Unit
            }
        }
    }

    // --- Event handlers ---------------------------------------------------------------------

    private fun onPopped(event: GameEvent.Popped, feverActive: Boolean) {
        val cells = event.cells
        if (cells.isEmpty()) return
        val baseColorArgb = event.color?.let { Neon.bubbleColor(it).toArgb() } ?: WHITE_ARGB
        val countMin = if (feverActive) 9 else 6
        val countMax = if (feverActive) 15 else 10
        for (pos in cells) {
            val (px, py) = cellCenter(pos)
            addDecal(px, py, baseColorArgb)
            val count = randInt(countMin, countMax)
            repeat(count) {
                val useGold = feverActive && rng.nextFloat() < 0.4f
                val color = if (useGold) GOLD_ARGB else baseColorArgb
                val angle = randF(0f, TWO_PI)
                val speed = randF(0.8f, 2.2f)
                val vx = cos(angle) * speed
                val vy = sin(angle) * speed - 0.4f // slight upward bias
                particles.spawn(px, py, vx, vy, randF(0.4f, 0.7f), randF(0.05f, 0.14f), color)
            }
        }
        val (cx, cy) = centroid(cells)
        val popupColor = if (feverActive) GOLD_ARGB else MINT_ARGB
        popups.add(ScorePopup(cx, cy, age = 0f, lifeSec = POPUP_LIFE, text = "+${event.scoreGained}", colorArgb = popupColor))
    }

    /**
     * Cells that lost support now fall as real physics actors (see [FallingBubbles]) instead of
     * bursting into particles — the old per-cell "twinkle" particle burst is gone; the score popup
     * is unchanged.
     */
    private fun onFell(event: GameEvent.Fell, preGrid: BubbleGrid) {
        val cells = event.cells
        if (cells.isEmpty()) return
        for (pos in cells) {
            val bubble = preGrid.bubbleAt(pos) ?: continue
            falling.spawn(pos, bubble)
        }
        val (cx, cy) = centroid(cells)
        popups.add(ScorePopup(cx, cy, age = 0f, lifeSec = POPUP_LIFE, text = "+${event.scoreGained}", colorArgb = CYAN_ARGB))
    }

    /** Half of the exploded cells (alternating) become falling debris actors; the other half get a
     * small localized particle puff. A shared centroid burst (reduced from the old single-burst
     * count, since per-cell puffs now add texture) plus the shake stays as the overall "boom". */
    private fun onBombExploded(event: GameEvent.BombExploded, preGrid: BubbleGrid) {
        val cells = event.cells
        for ((idx, pos) in cells.withIndex()) {
            if (idx % 2 == 0) {
                val bubble = preGrid.bubbleAt(pos)
                if (bubble != null) {
                    falling.spawn(pos, bubble)
                    continue
                }
            }
            spawnBombPuff(pos)
        }
        val (cx, cy) = centroid(cells)
        repeat(randInt(20, 30)) {
            val angle = randF(0f, TWO_PI)
            val speed = randF(1.0f, 3.0f)
            val color = if (rng.nextBoolean()) ORANGE_ARGB else WHITE_ARGB
            particles.spawn(cx, cy, cos(angle) * speed, sin(angle) * speed, randF(0.4f, 0.8f), randF(0.08f, 0.18f), color)
        }
        shake(durationSec = 0.35f, amplitude = 0.35f)
    }

    private fun spawnBombPuff(pos: GridPos) {
        val (px, py) = cellCenter(pos)
        repeat(randInt(6, 10)) {
            val angle = randF(0f, TWO_PI)
            val speed = randF(0.8f, 2.0f)
            val color = if (rng.nextBoolean()) ORANGE_ARGB else WHITE_ARGB
            particles.spawn(px, py, cos(angle) * speed, sin(angle) * speed, randF(0.4f, 0.7f), randF(0.06f, 0.14f), color)
        }
    }

    private fun onBankShot(event: GameEvent.BankShot, grid: BubbleGrid) {
        val (cx, cy) = gridTopCenter(grid)
        popups.add(ScorePopup(cx, cy, age = 0f, lifeSec = POPUP_LIFE_BANK, text = "神射！+${event.bonus}", colorArgb = MAGENTA_ARGB))
    }

    private fun onIceCracked(event: GameEvent.IceCracked) {
        for (pos in event.cells) {
            val (px, py) = cellCenter(pos)
            repeat(4) {
                val angle = randF(0f, TWO_PI)
                val speed = randF(0.4f, 1.2f)
                val color = if (rng.nextBoolean()) WHITE_ARGB else CYAN_ARGB
                particles.spawn(px, py, cos(angle) * speed, sin(angle) * speed - 0.2f, randF(0.3f, 0.6f), randF(0.04f, 0.09f), color)
            }
        }
    }

    private fun onUnchained(event: GameEvent.Unchained) {
        for (pos in event.cells) {
            val (px, py) = cellCenter(pos)
            repeat(6) {
                val angle = randF(0f, TWO_PI)
                val speed = randF(0.3f, 1.0f)
                particles.spawn(px, py, cos(angle) * speed, sin(angle) * speed, randF(0.3f, 0.5f), randF(0.04f, 0.08f), GREY_ARGB)
            }
        }
    }

    private fun onFeverStarted(grid: BubbleGrid) {
        val (cx, cy) = gridCentroid(grid)
        repeat(30) {
            val angle = randF(0f, TWO_PI)
            val speed = randF(0.6f, 1.6f)
            val color = if (rng.nextBoolean()) MAGENTA_ARGB else GOLD_ARGB
            particles.spawn(cx, cy, cos(angle) * speed, sin(angle) * speed, randF(0.5f, 0.9f), randF(0.06f, 0.14f), color)
        }
        shake(durationSec = 0.2f, amplitude = 0.15f)
    }

    private fun onWon(grid: BubbleGrid) {
        val width = grid.evenCols * 2f
        val topRow = grid.cells.keys.minOfOrNull { it.row } ?: 0
        val topY = GridGeometry.centerY(topRow)
        repeat(80) {
            val px = randF(0f, width.coerceAtLeast(1f))
            val py = topY - randF(0f, 2f)
            val color = CONFETTI_PALETTE[rng.nextInt(CONFETTI_PALETTE.size)]
            particles.spawn(px, py, randF(-0.4f, 0.4f), randF(-0.2f, 0.3f), randF(1.2f, 2.0f), randF(0.05f, 0.12f), color)
        }
    }

    /**
     * The reference's win cascade: every remaining occupied cell of [postGrid] becomes a falling
     * actor, staggered by row (rows closest to the ceiling fall first) so the whole board reads as
     * crumbling away rather than vanishing at once. Also sets [hideGrid] so the renderer stops
     * drawing the (now-conceptually-empty) static grid underneath the falling actors; cleared on
     * the next [GameEvent.Fired] or [reset].
     */
    private fun onWonCascade(postGrid: BubbleGrid) {
        hideGrid = true
        val ceilingRow = postGrid.ceilingRow
        for ((pos, bubble) in postGrid.cells) {
            val delay = ((pos.row - ceilingRow) * WON_CASCADE_ROW_DELAY).coerceAtLeast(0f)
            pendingSpawns.add(PendingSpawn(delay, pos, bubble))
        }
    }

    /**
     * A supernova detonation: a bright screen flash, a heavier shake, a radial shockwave of
     * sparks from each [GameEvent.SupernovaChained.origins] cell, plus a light defensive burst for
     * most [GameEvent.SupernovaChained.removed] cells — every third removed cell instead becomes a
     * falling debris actor (using [preGrid] to look up what used to sit there, since the post-step
     * grid no longer has it) for a bit of the same physical-debris texture as bombs/falls. The
     * engine may or may not also emit a regular [GameEvent.Popped] for the same removed cells — if
     * it does, this burst simply overlaps it at reduced density, which reads fine visually and
     * isn't worth de-duplicating.
     */
    private fun onSupernovaChained(event: GameEvent.SupernovaChained, preGrid: BubbleGrid) {
        flash(durationSec = 0.12f, peakAlpha = 0.85f)
        shake(durationSec = 0.3f, amplitude = 0.3f)

        for (origin in event.origins) {
            val (cx, cy) = cellCenter(origin)
            repeat(20) {
                val color = if (rng.nextBoolean()) GOLD_ARGB else WHITE_ARGB
                val angle = randF(0f, TWO_PI)
                val speed = randF(1.2f, 2.6f)
                particles.spawn(cx, cy, cos(angle) * speed, sin(angle) * speed, randF(0.5f, 0.9f), randF(0.06f, 0.14f), color)
            }
        }

        for ((idx, pos) in event.removed.withIndex()) {
            if (idx % 3 == 0) {
                val bubble = preGrid.bubbleAt(pos)
                if (bubble != null) {
                    falling.spawn(pos, bubble)
                    continue
                }
            }
            val (px, py) = cellCenter(pos)
            repeat(randInt(3, 5)) {
                val angle = randF(0f, TWO_PI)
                val speed = randF(0.8f, 2.2f)
                val color = if (rng.nextFloat() < 0.4f) GOLD_ARGB else WHITE_ARGB
                particles.spawn(px, py, cos(angle) * speed, sin(angle) * speed - 0.4f, randF(0.4f, 0.7f), randF(0.05f, 0.14f), color)
            }
        }
    }

    private fun onLost(grid: BubbleGrid) {
        val width = grid.evenCols * 2f
        val topRow = grid.cells.keys.minOfOrNull { it.row } ?: 0
        val topY = GridGeometry.centerY(topRow)
        repeat(20) {
            val px = randF(0f, width.coerceAtLeast(1f))
            val py = topY - randF(0f, 1f)
            particles.spawn(px, py, randF(-0.15f, 0.15f), randF(0.1f, 0.3f), randF(1.0f, 1.6f), randF(0.05f, 0.1f), GREY_DIM_ARGB)
        }
    }

    /** A bubble came to rest at [event]'s cell: the landed cell gets a squash-scale spring (starts
     * at [LANDED_SCALE_START], springs back to 1.0), and every occupied neighbor (per [postGrid])
     * gets a small positional push directly away from the landing cell, which also springs back. */
    private fun onLanded(event: GameEvent.Landed, postGrid: BubbleGrid) {
        val landed = springFor(event.cell)
        landed.scale = LANDED_SCALE_START
        landed.scaleV = 0f

        val (lx, ly) = cellCenter(event.cell)
        for (neighbor in postGrid.occupiedNeighbors(event.cell)) {
            val spring = springFor(neighbor)
            val (nx, ny) = cellCenter(neighbor)
            var dx = nx - lx
            var dy = ny - ly
            val len = sqrt(dx * dx + dy * dy)
            if (len > 1e-4f) {
                dx /= len
                dy /= len
            } else {
                dx = 0f
                dy = -1f
            }
            spring.ox = dx * NEIGHBOR_PUSH_UNITS
            spring.oy = dy * NEIGHBOR_PUSH_UNITS
            spring.vx = 0f
            spring.vy = 0f
        }
    }

    /** A shot was fired: recoil the shooter and make sure a stale [hideGrid] (left over from an
     * end-of-run win cascade) never lingers into a fresh run. */
    private fun onFired() {
        hideGrid = false
        shooterRecoil = RECOIL_KICK
        shooterRecoilV = 0f
    }

    /** A fresh row was inserted / the ceiling stepped down: kick off the grid-drop overshoot spring. */
    private fun onGridDrop() {
        gridDropOffset = GRID_DROP_START
        gridDropOffsetV = 0f
    }

    // --- Internals ---------------------------------------------------------------------------

    private fun springFor(pos: GridPos): BubbleSpring = springs.getOrPut(pos.packed) { BubbleSpring() }

    private fun addDecal(x: Float, y: Float, colorArgb: Int) {
        val i = decalWriteIdx
        decalX[i] = x
        decalY[i] = y
        decalColor[i] = colorArgb
        decalAge[i] = 0f
        decalWriteIdx = (decalWriteIdx + 1) % DECAL_CAPACITY
    }

    private fun updateDecals(dt: Float) {
        for (i in 0 until DECAL_CAPACITY) {
            if (decalAge[i] < DECAL_LIFE) decalAge[i] += dt
        }
    }

    private fun updateSprings(dt: Float) {
        val it = springs.entries.iterator()
        while (it.hasNext()) {
            val spring = it.next().value

            val ax = -SPRING_K_POS * spring.ox - SPRING_C_POS * spring.vx
            spring.vx += ax * dt
            spring.ox += spring.vx * dt

            val ay = -SPRING_K_POS * spring.oy - SPRING_C_POS * spring.vy
            spring.vy += ay * dt
            spring.oy += spring.vy * dt

            val scaleDelta = spring.scale - 1f
            val ascale = -SPRING_K_SCALE * scaleDelta - SPRING_C_SCALE * spring.scaleV
            spring.scaleV += ascale * dt
            spring.scale += spring.scaleV * dt

            val settled = abs(spring.ox) < 0.01f && abs(spring.oy) < 0.01f &&
                abs(spring.vx) < 0.01f && abs(spring.vy) < 0.01f &&
                abs(spring.scale - 1f) < 0.01f && abs(spring.scaleV) < 0.01f
            if (settled) {
                it.remove()
            }
        }
    }

    private fun updatePendingSpawns(dt: Float) {
        var i = pendingSpawns.size - 1
        while (i >= 0) {
            val pending = pendingSpawns[i]
            pending.delaySec -= dt
            if (pending.delaySec <= 0f) {
                falling.spawn(pending.cell, pending.bubble)
                pendingSpawns.removeAt(i)
            }
            i--
        }
    }

    private fun updateShooterRecoil(dt: Float) {
        val a = -RECOIL_K * shooterRecoil - RECOIL_C * shooterRecoilV
        shooterRecoilV += a * dt
        shooterRecoil += shooterRecoilV * dt
        if (abs(shooterRecoil) < 0.001f && abs(shooterRecoilV) < 0.001f) {
            shooterRecoil = 0f
            shooterRecoilV = 0f
        }
    }

    private fun updateGridDropOffset(dt: Float) {
        val a = -GRID_DROP_K * gridDropOffset - GRID_DROP_C * gridDropOffsetV
        gridDropOffsetV += a * dt
        gridDropOffset += gridDropOffsetV * dt
        if (abs(gridDropOffset) < 0.001f && abs(gridDropOffsetV) < 0.001f) {
            gridDropOffset = 0f
            gridDropOffsetV = 0f
        }
    }

    private fun updatePopups(dt: Float) {
        var i = popups.size - 1
        while (i >= 0) {
            val popup = popups[i]
            popup.age += dt
            if (popup.age >= popup.lifeSec) popups.removeAt(i)
            i--
        }
    }

    private fun updateShake(dt: Float) {
        if (shakeTimeLeft <= 0f) {
            shakeX = 0f
            shakeY = 0f
            return
        }
        shakeTimeLeft = (shakeTimeLeft - dt).coerceAtLeast(0f)
        val envelope = if (shakeDuration > 0f) shakeTimeLeft / shakeDuration else 0f
        shakeX = shakeAmp * envelope * sin(shakeTimeLeft * 45f)
        shakeY = shakeAmp * envelope * cos(shakeTimeLeft * 37f)
    }

    private fun updateFlash(dt: Float) {
        if (flashTimeLeft <= 0f) {
            flashAlpha = 0f
            return
        }
        flashTimeLeft = (flashTimeLeft - dt).coerceAtLeast(0f)
        val envelope = if (flashDuration > 0f) flashTimeLeft / flashDuration else 0f
        flashAlpha = flashPeak * envelope
    }

    private fun cellCenter(pos: GridPos): Pair<Float, Float> =
        GridGeometry.centerX(pos) to GridGeometry.centerY(pos.row)

    private fun centroid(cells: Set<GridPos>): Pair<Float, Float> {
        if (cells.isEmpty()) return 0f to 0f
        var sx = 0f
        var sy = 0f
        for (pos in cells) {
            sx += GridGeometry.centerX(pos)
            sy += GridGeometry.centerY(pos.row)
        }
        val n = cells.size.toFloat()
        return (sx / n) to (sy / n)
    }

    private fun gridCentroid(grid: BubbleGrid): Pair<Float, Float> = centroid(grid.cells.keys)

    private fun gridTopCenter(grid: BubbleGrid): Pair<Float, Float> {
        val midX = grid.evenCols.toFloat()
        val topRow = grid.cells.keys.minOfOrNull { it.row } ?: 0
        return midX to GridGeometry.centerY(topRow)
    }

    private fun randF(min: Float, max: Float): Float = min + rng.nextFloat() * (max - min)

    private fun randInt(min: Int, max: Int): Int = min + rng.nextInt(max - min + 1)
}
