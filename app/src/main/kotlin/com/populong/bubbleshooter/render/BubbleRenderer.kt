package com.populong.bubbleshooter.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import com.populong.bubbleshooter.game.AimLine
import com.populong.bubbleshooter.game.BubbleColor
import com.populong.bubbleshooter.game.BubbleGrid
import com.populong.bubbleshooter.game.Projectile

class BubbleRenderer {
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        alpha = 180
    }
    private val aimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xAAFFFFFF.toInt()
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val rainbowColors = BubbleColor.values().map { it.argb }.toIntArray()

    fun drawGrid(canvas: Canvas, grid: BubbleGrid, bubbleRadius: Float, gridOffsetY: Float) {
        for ((cell, bubble) in grid.allOccupied()) {
            val center = grid.cellToPixel(cell, bubbleRadius, gridOffsetY)
            if (bubble.isRainbow) {
                drawRainbowBubble(canvas, center.x, center.y, bubbleRadius)
            } else {
                drawBubble(canvas, center.x, center.y, bubbleRadius, bubble.color)
            }
        }
    }

    fun drawProjectile(canvas: Canvas, projectile: Projectile?) {
        projectile ?: return
        if (projectile.isRainbow) {
            drawRainbowBubble(canvas, projectile.x, projectile.y, 0f)
        } else {
            drawBubble(canvas, projectile.x, projectile.y, 0f, projectile.color)
        }
    }

    fun drawProjectileWithRadius(canvas: Canvas, projectile: Projectile?, radius: Float) {
        projectile ?: return
        if (projectile.isRainbow) {
            drawRainbowBubble(canvas, projectile.x, projectile.y, radius)
        } else {
            drawBubble(canvas, projectile.x, projectile.y, radius, projectile.color)
        }
    }

    fun drawBubble(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: BubbleColor) {
        val r = if (radius <= 0f) 20f else radius
        bubblePaint.shader = RadialGradient(
            cx - r * 0.3f, cy - r * 0.3f, r * 1.4f,
            lighten(color.argb, 0.4f), darken(color.argb, 0.3f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, r, bubblePaint)

        highlightPaint.alpha = 160
        canvas.drawCircle(cx - r * 0.25f, cy - r * 0.25f, r * 0.28f, highlightPaint)
    }

    fun drawRainbowBubble(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val r = if (radius <= 0f) 20f else radius
        val positions = FloatArray(rainbowColors.size) { it.toFloat() / (rainbowColors.size - 1) }
        bubblePaint.shader = SweepGradient(cx, cy, rainbowColors, positions)
        canvas.drawCircle(cx, cy, r, bubblePaint)
        highlightPaint.alpha = 140
        canvas.drawCircle(cx - r * 0.25f, cy - r * 0.25f, r * 0.28f, highlightPaint)
    }

    fun drawAimLine(canvas: Canvas, segments: List<AimLine.Segment>) {
        for (segment in segments) {
            val dashLen = 8f
            val gapLen = 6f
            val dx = segment.end.x - segment.start.x
            val dy = segment.end.y - segment.start.y
            val len = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
            if (len < 1f) continue
            val ux = dx / len
            val uy = dy / len
            var t = 0f
            while (t < len) {
                val endT = (t + dashLen).coerceAtMost(len)
                canvas.drawLine(
                    segment.start.x + ux * t,
                    segment.start.y + uy * t,
                    segment.start.x + ux * endT,
                    segment.start.y + uy * endT,
                    aimPaint
                )
                t += dashLen + gapLen
            }
        }
    }

    fun drawShooterBase(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: BubbleColor, isRainbow: Boolean) {
        if (isRainbow) {
            drawRainbowBubble(canvas, cx, cy, radius)
        } else {
            drawBubble(canvas, cx, cy, radius, color)
        }
        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = 0xFF333366.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawCircle(cx, cy, radius + 4f, basePaint)
    }

    fun drawNextBubble(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: BubbleColor, isRainbow: Boolean) {
        val smallR = radius * 0.65f
        if (isRainbow) {
            drawRainbowBubble(canvas, cx, cy, smallR)
        } else {
            drawBubble(canvas, cx, cy, smallR, color)
        }
    }

    private fun lighten(color: Int, factor: Float): Int {
        val r = ((color shr 16) and 0xFF).let { (it + (255 - it) * factor).toInt().coerceAtMost(255) }
        val g = ((color shr 8) and 0xFF).let { (it + (255 - it) * factor).toInt().coerceAtMost(255) }
        val b = (color and 0xFF).let { (it + (255 - it) * factor).toInt().coerceAtMost(255) }
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun darken(color: Int, factor: Float): Int {
        val r = ((color shr 16) and 0xFF).let { (it * (1f - factor)).toInt().coerceAtLeast(0) }
        val g = ((color shr 8) and 0xFF).let { (it * (1f - factor)).toInt().coerceAtLeast(0) }
        val b = (color and 0xFF).let { (it * (1f - factor)).toInt().coerceAtLeast(0) }
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
