package com.populong.bubbleshooter.effects

class Particle {
    var active = false
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var life = 0f
    var maxLife = 0f
    var size = 0f
    var color = 0
    var alpha = 255

    fun update(deltaMs: Float) {
        if (!active) return
        x += vx * deltaMs
        y += vy * deltaMs
        vy += 0.002f * deltaMs
        life -= deltaMs
        alpha = ((life / maxLife) * 255f).toInt().coerceIn(0, 255)
        if (life <= 0f) active = false
    }

    fun reset(
        x: Float, y: Float,
        vx: Float, vy: Float,
        life: Float, size: Float,
        color: Int
    ) {
        this.active = true
        this.x = x; this.y = y
        this.vx = vx; this.vy = vy
        this.life = life; this.maxLife = life
        this.size = size; this.color = color
        this.alpha = 255
    }
}
