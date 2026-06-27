package com.populong.bubbleshooter.render

import android.graphics.Canvas
import android.graphics.Paint
import com.populong.bubbleshooter.effects.EffectsController

class EffectsRenderer {
    private val bubbleRenderer = BubbleRenderer()
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 36f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    fun draw(canvas: Canvas, effects: EffectsController) {
        effects.particles.draw(canvas)

        for (fb in effects.fallingBubbles) {
            canvas.save()
            canvas.rotate(fb.rotation, fb.x, fb.y)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.alpha = (fb.alpha * 255f).toInt().coerceIn(0, 255)
            bubbleRenderer.drawBubble(canvas, fb.x, fb.y, fb.radius, fb.color)
            canvas.restore()
        }

        for (popup in effects.scorePopups) {
            textPaint.textSize = popup.size
            textPaint.color = popup.color
            textPaint.alpha = popup.alpha
            canvas.drawText(popup.text, popup.x, popup.y + popup.offsetY, textPaint)
        }
    }
}
