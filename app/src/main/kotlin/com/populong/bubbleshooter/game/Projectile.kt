package com.populong.bubbleshooter.game

data class Projectile(
    var x: Float,
    var y: Float,
    var dx: Float,
    var dy: Float,
    val color: BubbleColor,
    val isRainbow: Boolean = false
) {
    fun update(deltaMs: Float, leftWall: Float, rightWall: Float, bubbleRadius: Float) {
        x += dx * deltaMs
        y += dy * deltaMs

        if (x - bubbleRadius <= leftWall) {
            x = leftWall + bubbleRadius
            dx = -dx
        } else if (x + bubbleRadius >= rightWall) {
            x = rightWall - bubbleRadius
            dx = -dx
        }
    }

    fun toBubble(): Bubble = Bubble(color, isRainbow)
}
