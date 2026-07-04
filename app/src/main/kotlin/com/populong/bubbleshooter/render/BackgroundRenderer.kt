package com.populong.bubbleshooter.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.populong.bubbleshooter.ui.theme.Neon

/**
 * Cheap, deterministic starfield behind the play field: the shared space gradient plus two
 * parallax star layers drifting at different speeds. Star positions come from a small seeded
 * linear congruential generator, computed once and reused for every frame/session.
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

    /** Draws the space gradient and parallax stars for the current frame count [ticks]. */
    fun draw(scope: DrawScope, ticks: Long) {
        scope.drawRect(brush = Brush.verticalGradient(Neon.spaceGradient), size = scope.size)
        scope.drawLayer(layer1, ticks, speed = 6f)
        scope.drawLayer(layer2, ticks, speed = 14f)
    }

    private fun DrawScope.drawLayer(stars: List<Star>, ticks: Long, speed: Float) {
        val drift = (ticks * speed / 1000f) % size.height
        for (star in stars) {
            val y = (star.y * size.height + drift) % size.height
            val radius = if (star.big) star.radius * 2.4f else star.radius
            val alpha = if (star.big) 0.9f else 0.55f
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = radius,
                center = Offset(star.x * size.width, y),
            )
        }
    }
}
