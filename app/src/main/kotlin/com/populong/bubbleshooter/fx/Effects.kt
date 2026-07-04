package com.populong.bubbleshooter.fx

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.populong.bubbleshooter.core.engine.GameEvent
import com.populong.bubbleshooter.core.grid.BubbleColor
import com.populong.bubbleshooter.core.grid.BubbleGrid
import com.populong.bubbleshooter.core.grid.GridGeometry
import com.populong.bubbleshooter.core.grid.GridPos
import com.populong.bubbleshooter.ui.theme.Neon
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
 * Turns [GameEvent]s into particle bursts, score popups, and camera shake. Owns one shared
 * [ParticlePool] (all effect types draw from it) plus a small list of [ScorePopup]s and simple
 * decaying screen-shake state.
 *
 * Positions are derived from [BubbleGrid] cell coordinates via [GridGeometry.centerX]/[centerY],
 * i.e. the same absolute row-space unit coordinates the grid itself is drawn in — the renderer
 * converts to pixels the same way for both (`FieldLayout.toPx(x, y, ceilingY)`), so no additional
 * ceiling-relative bookkeeping is needed here.
 */
class EffectsController(private val rng: java.util.Random = java.util.Random(7)) {

    /** Shared particle pool for every effect type; capacity sized for the largest simultaneous burst mix. */
    val particles = ParticlePool(capacity = 512)

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

    /** Advances particles, ages/reaps popups, and decays any in-flight screen shake by [dt] seconds. */
    fun update(dt: Float) {
        particles.update(dt)
        updatePopups(dt)
        updateShake(dt)
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

    /**
     * Maps [events] to particle bursts / popups / shake. [grid] must be the state *after* the
     * events resolved (so e.g. popped cells are already gone — [GameEvent.Popped.cells] carries
     * the positions instead). [feverActive] boosts Popped bursts (+50% count, gold tint mix).
     */
    fun onEvents(events: List<GameEvent>, grid: BubbleGrid, feverActive: Boolean) {
        for (event in events) {
            when (event) {
                is GameEvent.Popped -> onPopped(event, feverActive)
                is GameEvent.Fell -> onFell(event)
                is GameEvent.BombExploded -> onBombExploded(event)
                is GameEvent.BankShot -> onBankShot(event, grid)
                is GameEvent.IceCracked -> onIceCracked(event)
                is GameEvent.Unchained -> onUnchained(event)
                GameEvent.FeverStarted -> onFeverStarted(grid)
                is GameEvent.Won -> onWon(grid)
                is GameEvent.Lost -> onLost(grid)
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

    private fun onFell(event: GameEvent.Fell) {
        val cells = event.cells
        if (cells.isEmpty()) return
        for (pos in cells) {
            val (px, py) = cellCenter(pos)
            repeat(randInt(2, 3)) {
                val vx = randF(-0.3f, 0.3f)
                val vy = randF(0.2f, 0.6f)
                particles.spawn(px, py, vx, vy, randF(0.4f, 0.8f), randF(0.05f, 0.1f), CYAN_ARGB)
            }
        }
        val (cx, cy) = centroid(cells)
        popups.add(ScorePopup(cx, cy, age = 0f, lifeSec = POPUP_LIFE, text = "+${event.scoreGained}", colorArgb = CYAN_ARGB))
    }

    private fun onBombExploded(event: GameEvent.BombExploded) {
        val (cx, cy) = centroid(event.cells)
        repeat(randInt(40, 60)) {
            val angle = randF(0f, TWO_PI)
            val speed = randF(1.0f, 3.0f)
            val color = if (rng.nextBoolean()) ORANGE_ARGB else WHITE_ARGB
            particles.spawn(cx, cy, cos(angle) * speed, sin(angle) * speed, randF(0.4f, 0.8f), randF(0.08f, 0.18f), color)
        }
        shake(durationSec = 0.35f, amplitude = 0.35f)
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

    // --- Internals ---------------------------------------------------------------------------

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
