package com.populong.bubbleshooter.effects

import com.populong.bubbleshooter.game.BubbleColor
import com.populong.bubbleshooter.game.BubbleGrid

class EffectsController {
    val particles = ParticleSystem()
    val screenShake = ScreenShake()
    val fallingBubbles = mutableListOf<FallingBubble>()
    val scorePopups = mutableListOf<ScorePopup>()

    fun emitBubblePop(cx: Float, cy: Float, color: BubbleColor) {
        particles.emit(cx, cy, color.argb, 12)
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
        scorePopups.add(ScorePopup(x, y, "+$score", 0xFFFFFFFF.toInt()))
    }

    fun triggerShake(bubbleCount: Int) {
        if (bubbleCount >= 3) {
            val intensity = (bubbleCount * 2f).coerceAtMost(20f)
            screenShake.trigger(intensity, 300f)
        }
    }

    fun update(deltaMs: Float) {
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
