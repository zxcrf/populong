package com.populong.bubbleshooter.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import com.populong.bubbleshooter.ui.theme.GalaxyPalette
import com.populong.bubbleshooter.ui.theme.Neon
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Cheap, deterministic starfield behind the play field: the shared space gradient plus two
 * parallax star layers drifting at different speeds, plus a sparse meteor shower layer. Star
 * positions come from a small seeded linear congruential generator, computed once and reused for
 * every frame/session; meteors are derived purely from [ticks] via the same kind of LCG so they
 * never need per-frame allocation or stored "spawn history".
 */
object BackgroundRenderer {

    private const val LAYER1_COUNT = 40
    private const val LAYER2_COUNT = 20
    private const val LCG_MULT = 1103515245L
    private const val LCG_INC = 12345L
    private val LCG_MOD = 1L shl 31

    private data class Star(val x: Float, val y: Float, val radius: Float, val big: Boolean)

    private val layer1: List<Star> = seededStars(count = LAYER1_COUNT, seed = 7L)
    private val layer2: List<Star> = seededStars(count = LAYER2_COUNT, seed = 91L)

    private fun seededStars(count: Int, seed: Long): List<Star> {
        var state = seed
        fun next(): Float {
            state = (state * LCG_MULT + LCG_INC) % LCG_MOD
            return state.toFloat() / LCG_MOD.toFloat()
        }
        return List(count) { i ->
            Star(
                x = next(),
                y = next(),
                radius = 0.8f + next() * 1.6f,
                big = i % 11 == 0,
            )
        }
    }

    // --- Meteor shower state (4 preallocated slots; render-thread only, never touched elsewhere) ---

    private const val METEOR_COUNT = 4

    /** Per-slot window length in ticks; `ticks` increments roughly once per displayed frame, so
     * these approximate 8s/10s/13s/15s at the [METEOR_TICKS_PER_SEC] assumption below — exact
     * real-time accuracy doesn't matter for a cosmetic background shower. */
    private val METEOR_WINDOW_TICKS = longArrayOf(480L, 600L, 780L, 900L)

    /** Per-slot phase offset so the 4 slots don't all roll/spawn on the same window boundary. */
    private val METEOR_PHASE_TICKS = longArrayOf(0L, 137L, 271L, 409L)

    private const val METEOR_TICKS_PER_SEC = 60f
    private const val METEOR_LIFE_SEC = 0.8f
    private const val METEOR_BASE_CHANCE = 0.6f
    private const val METEOR_SPEED = 0.55f // screen-fraction per second

    private val meteorWindowIndex = LongArray(METEOR_COUNT) { -1L }
    private val meteorSpawnActive = BooleanArray(METEOR_COUNT)
    private val meteorSpawnTick = LongArray(METEOR_COUNT)
    private val meteorStartX = FloatArray(METEOR_COUNT)
    private val meteorStartY = FloatArray(METEOR_COUNT)
    private val meteorVelX = FloatArray(METEOR_COUNT)
    private val meteorVelY = FloatArray(METEOR_COUNT)

    /**
     * Draws the space gradient, parallax stars, and meteor shower for the current frame count
     * [ticks]. [palette] tints the gradient/stars/meteors toward a galaxy's identity; null falls
     * back to the original default [Neon] colors. [feverActive] multiplies meteor spawn chance
     * (x5), so a fever run reads as a denser shower.
     */
    fun draw(scope: DrawScope, ticks: Long, palette: GalaxyPalette? = null, feverActive: Boolean = false) {
        val gradientColors = if (palette != null) {
            listOf(palette.bgTop, palette.bgMid, palette.bgDeep)
        } else {
            Neon.spaceGradient
        }
        val tint = palette?.accent
        scope.drawRect(brush = Brush.verticalGradient(gradientColors), size = scope.size)
        scope.drawLayer(layer1, ticks, speed = 6f, tint = tint)
        scope.drawLayer(layer2, ticks, speed = 14f, tint = tint)
        scope.drawMeteors(ticks, feverActive, tint ?: Color.White)
    }

    private fun DrawScope.drawLayer(stars: List<Star>, ticks: Long, speed: Float, tint: Color?) {
        val drift = (ticks * speed / 1000f) % size.height
        val starColor = if (tint != null) lerp(Color.White, tint, 0.18f) else Color.White
        for (star in stars) {
            val y = (star.y * size.height + drift) % size.height
            val radius = if (star.big) star.radius * 2.4f else star.radius
            val alpha = if (star.big) 0.9f else 0.55f
            drawCircle(
                color = starColor.copy(alpha = alpha),
                radius = radius,
                center = Offset(star.x * size.width, y),
            )
        }
    }

    private fun DrawScope.drawMeteors(ticks: Long, feverActive: Boolean, tint: Color) {
        val lifeTicks = (METEOR_LIFE_SEC * METEOR_TICKS_PER_SEC).toLong().coerceAtLeast(1L)
        for (i in 0 until METEOR_COUNT) {
            val windowTicks = METEOR_WINDOW_TICKS[i]
            val phase = METEOR_PHASE_TICKS[i]
            val windowIndex = Math.floorDiv(ticks + phase, windowTicks)
            if (windowIndex != meteorWindowIndex[i]) {
                meteorWindowIndex[i] = windowIndex
                recomputeMeteorSpawn(i, windowIndex, windowTicks, phase, feverActive)
            }
            if (!meteorSpawnActive[i]) continue
            val age = ticks - meteorSpawnTick[i]
            if (age < 0L || age > lifeTicks) continue
            val t = age.toFloat() / METEOR_TICKS_PER_SEC
            val lifeFrac = age.toFloat() / lifeTicks.toFloat()
            val headX = (meteorStartX[i] + meteorVelX[i] * t) * size.width
            val headY = (meteorStartY[i] + meteorVelY[i] * t) * size.height
            drawMeteorTrail(headX, headY, meteorVelX[i], meteorVelY[i], lifeFrac, tint)
        }
    }

    /** Recomputes slot [slot]'s spawn parameters once per window (not per frame) from a small LCG
     * seeded by `(slot, windowIndex)`, so the whole shower is deterministic from [ticks] alone. */
    private fun recomputeMeteorSpawn(slot: Int, windowIndex: Long, windowTicks: Long, phase: Long, feverActive: Boolean) {
        var state = slot.toLong() * 1_000_003L + windowIndex * 2_654_435_761L + 17L
        fun next(): Float {
            state = (state * LCG_MULT + LCG_INC) and 0x7FFFFFFFL
            return state.toFloat() / 0x7FFFFFFFL.toFloat()
        }

        val roll = next()
        val chance = (METEOR_BASE_CHANCE * (if (feverActive) 5f else 1f)).coerceAtMost(1f)
        if (roll >= chance) {
            meteorSpawnActive[slot] = false
            return
        }

        val edge = (next() * 4f).toInt().coerceIn(0, 3)
        val along = next()
        val jitter = (next() - 0.5f) * (Math.PI.toFloat() / 3f) // +-30 degrees

        // Base diagonal direction per edge (down/right/left/up), jittered for variety.
        val baseAngle = when (edge) {
            0 -> Math.PI.toFloat() / 2f // top edge -> moving down
            1 -> 0f // left edge -> moving right
            2 -> Math.PI.toFloat() // right edge -> moving left
            else -> -Math.PI.toFloat() / 2f // bottom edge -> moving up
        } + jitter

        val startX: Float
        val startY: Float
        when (edge) {
            0 -> { startX = along; startY = -0.05f }
            1 -> { startX = -0.05f; startY = along }
            2 -> { startX = 1.05f; startY = along }
            else -> { startX = along; startY = 1.05f }
        }

        val withinWindowOffset = (next() * (windowTicks - 1).coerceAtLeast(1L).toFloat()).toLong()
        meteorSpawnTick[slot] = windowIndex * windowTicks - phase + withinWindowOffset
        meteorStartX[slot] = startX
        meteorStartY[slot] = startY
        meteorVelX[slot] = cos(baseAngle) * METEOR_SPEED
        meteorVelY[slot] = sin(baseAngle) * METEOR_SPEED
        meteorSpawnActive[slot] = true
    }

    /** A bright head circle plus a 3-segment fading trail, oriented opposite the meteor's velocity. */
    private fun DrawScope.drawMeteorTrail(headX: Float, headY: Float, velX: Float, velY: Float, lifeFrac: Float, tint: Color) {
        val fadeIn = (lifeFrac / 0.12f).coerceIn(0f, 1f)
        val fadeOut = ((1f - lifeFrac) / 0.3f).coerceIn(0f, 1f)
        val envelope = if (fadeIn < fadeOut) fadeIn else fadeOut
        if (envelope <= 0f) return

        val speedPxX = velX * size.width
        val speedPxY = velY * size.height
        val dirLen = sqrt(speedPxX * speedPxX + speedPxY * speedPxY).coerceAtLeast(0.0001f)
        val dirX = -speedPxX / dirLen
        val dirY = -speedPxY / dirLen
        val segLen = size.minDimension * 0.02f
        val headColor = lerp(Color.White, tint, 0.3f)

        drawCircle(
            color = headColor.copy(alpha = 0.95f * envelope),
            radius = size.minDimension * 0.006f,
            center = Offset(headX, headY),
        )

        var prevX = headX
        var prevY = headY
        for (seg in 1..3) {
            val alpha = (envelope * (1f - seg * 0.28f)).coerceIn(0f, 1f)
            if (alpha <= 0f) break
            val nx = headX + dirX * segLen * seg
            val ny = headY + dirY * segLen * seg
            drawLine(
                color = headColor.copy(alpha = alpha),
                start = Offset(prevX, prevY),
                end = Offset(nx, ny),
                strokeWidth = size.minDimension * 0.003f,
            )
            prevX = nx
            prevY = ny
        }
    }
}
