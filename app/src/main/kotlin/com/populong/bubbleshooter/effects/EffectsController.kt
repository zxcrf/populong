package com.populong.bubbleshooter.effects

import com.populong.bubbleshooter.game.BubbleColor
import com.populong.bubbleshooter.game.BubbleGrid

class EffectsController {
    val particles = ParticleSystem()
    val screenShake = ScreenShake()
    val fallingBubbles = mutableListOf<FallingBubble>()
    val scorePopups = mutableListOf<ScorePopup>()

    // Free-running clock for ambient animations (e.g. the pulsing danger line).
    var clockMs = 0f
        private set

    fun emitBubblePop(cx: Float, cy: Float, color: BubbleColor, intensity: Int = 1) {
        // More particles for chunkier clears so big pops read as more explosive.
        val count = (12 + (intensity - 1) * 3).coerceAtMost(28)
        particles.emit(cx, cy, color.argb, count)
    }

    fun addFallingBubble(x: Float, y: Float, color: BubbleColor, radius: Float) {
        fallingBubbles.add(FallingBubble(x, y, color, radius))
    }

    fun spawnFallingBubbles(
        cells: Set<BubbleGrid.GridCell>,
        grid: BubbleGrid,
        bubbleRadius: Float,
        gridOffsetY: Float
    ) {
        for (cell in cells) {
            val bubble = grid.get(cell) ?: continue
            val pos = grid.cellToPixel(cell, bubbleRadius, gridOffsetY)
            addFallingBubble(pos.x, pos.y, bubble.color, bubbleRadius)
        }
    }

    fun addScorePopup(x: Float, y: Float, score: Int) {
        // Bigger, warmer popup for higher scores so chunky clears feel rewarding.
        val (color, size) = when {
            score >= 300 -> 0xFFFFC83C.toInt() to 52f
            score >= 150 -> 0xFFFFE45C.toInt() to 44f
            else -> 0xFFFFFFFF.toInt() to 36f
        }
        scorePopups.add(ScorePopup(x, y, "+$score", color, size))
    }

    fun addComboPopup(x: Float, y: Float, comboCount: Int) {
        val label = when {
            comboCount >= 6 -> "AWESOME!"
            comboCount >= 4 -> "GREAT!"
            comboCount >= 2 -> "连击 x$comboCount"
            else -> return
        }
        val size = (44f + comboCount * 4f).coerceAtMost(72f)
        scorePopups.add(ScorePopup(x, y, label, 0xFFFF6FB5.toInt(), size, maxLife = 1000f))
    }

    fun triggerShake(bubbleCount: Int) {
        if (bubbleCount >= 3) {
            val intensity = (bubbleCount * 2f).coerceAtMost(20f)
            screenShake.trigger(intensity, 300f)
        }
    }

    fun update(deltaMs: Float) {
        clockMs += deltaMs
        particles.update(deltaMs)
        screenShake.update(deltaMs)
        fallingBubbles.removeAll { !it.update(deltaMs) }
        scorePopups.removeAll { !it.update(deltaMs) }
    }

    fun hasActiveEffects(): Boolean {
        return particles.hasActive() ||
                fallingBubbles.isNotEmpty() ||
                scorePopups.isNotEmpty() ||
                screenShake.isActive
    }
}
