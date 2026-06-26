package com.populong.bubbleshooter.effects

class ScorePopup(
    val x: Float,
    val y: Float,
    val text: String,
    val color: Int
) {
    var offsetY = 0f
    var alpha = 255
    var life = 800f

    fun update(deltaMs: Float): Boolean {
        offsetY -= 0.08f * deltaMs
        life -= deltaMs
        alpha = ((life / 800f) * 255f).toInt().coerceIn(0, 255)
        return life > 0f
    }
}
