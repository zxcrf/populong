package com.populong.bubbleshooter.effects

class ScorePopup(
    val x: Float,
    val y: Float,
    val text: String,
    val color: Int,
    val size: Float = 36f,
    val maxLife: Float = 800f
) {
    var offsetY = 0f
    var alpha = 255
    var life = maxLife

    fun update(deltaMs: Float): Boolean {
        offsetY -= 0.08f * deltaMs
        life -= deltaMs
        alpha = ((life / maxLife) * 255f).toInt().coerceIn(0, 255)
        return life > 0f
    }
}
