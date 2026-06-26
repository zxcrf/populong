package com.populong.bubbleshooter.effects

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class ParticleSystem(poolSize: Int = 500) {
    private val particles = Array(poolSize) { Particle() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun emit(cx: Float, cy: Float, color: Int, count: Int = 12) {
        var emitted = 0
        for (p in particles) {
            if (emitted >= count) break
            if (p.active) continue
            val angle = (emitted.toFloat() / count) * 2f * PI.toFloat() +
                    (Math.random().toFloat() * 0.5f)
            val speed = 0.1f + Math.random().toFloat() * 0.3f
            p.reset(
                x = cx, y = cy,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed,
                life = 300f + Math.random().toFloat() * 400f,
                size = 3f + Math.random().toFloat() * 5f,
                color = color
            )
            emitted++
        }
    }

    fun update(deltaMs: Float) {
        for (p in particles) p.update(deltaMs)
    }

    fun draw(canvas: Canvas) {
        for (p in particles) {
            if (!p.active) continue
            paint.color = p.color
            paint.alpha = p.alpha
            canvas.drawCircle(p.x, p.y, p.size, paint)
        }
    }

    fun hasActive(): Boolean = particles.any { it.active }
}
