package com.populong.bubbleshooter.render

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader

class BackgroundRenderer {
    private val paint = Paint()
    private var gradient: LinearGradient? = null
    private var lastHeight = 0

    fun draw(canvas: Canvas) {
        val h = canvas.height
        if (h != lastHeight) {
            gradient = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                0xFF0F0F2E.toInt(), 0xFF1A1A3E.toInt(),
                Shader.TileMode.CLAMP
            )
            lastHeight = h
        }
        paint.shader = gradient
        canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), paint)
    }
}
