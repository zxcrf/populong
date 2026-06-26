package com.populong.bubbleshooter.effects

import com.populong.bubbleshooter.game.BubbleColor

class FallingBubble(
    var x: Float,
    var y: Float,
    val color: BubbleColor,
    val radius: Float
) {
    var vy = 0f
    var vx = (Math.random().toFloat() - 0.5f) * 0.1f
    var rotation = 0f
    var rotationSpeed = (Math.random().toFloat() - 0.5f) * 0.01f
    var alpha = 1f

    fun update(deltaMs: Float): Boolean {
        vy += 0.008f * deltaMs
        y += vy * deltaMs
        x += vx * deltaMs
        rotation += rotationSpeed * deltaMs
        alpha -= 0.0008f * deltaMs
        return alpha > 0f && y < 3000f
    }
}
