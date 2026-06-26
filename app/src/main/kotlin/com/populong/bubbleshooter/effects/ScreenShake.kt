package com.populong.bubbleshooter.effects

class ScreenShake {
    private var intensity = 0f
    private var duration = 0f
    private var elapsed = 0f

    val offsetX: Float
        get() = if (duration > 0f) {
            val decay = 1f - elapsed / duration
            (Math.random().toFloat() - 0.5f) * 2f * intensity * decay
        } else 0f

    val offsetY: Float
        get() = if (duration > 0f) {
            val decay = 1f - elapsed / duration
            (Math.random().toFloat() - 0.5f) * 2f * intensity * decay
        } else 0f

    val isActive: Boolean get() = elapsed < duration

    fun trigger(intensity: Float, durationMs: Float) {
        this.intensity = intensity
        this.duration = durationMs
        this.elapsed = 0f
    }

    fun update(deltaMs: Float) {
        if (elapsed < duration) {
            elapsed += deltaMs
        }
    }
}
