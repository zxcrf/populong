package com.populong.bubbleshooter.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.populong.bubbleshooter.engine.GameState
import com.populong.bubbleshooter.game.BubbleColor

class UIRenderer {
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 40f
        textAlign = Paint.Align.LEFT
    }
    private val centerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 64f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val smallTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 36f
        textAlign = Paint.Align.CENTER
    }
    private val overlayPaint = Paint().apply {
        color = 0xCC000000.toInt()
    }
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4488FF.toInt()
    }
    private val buttonTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 36f
        textAlign = Paint.Align.CENTER
    }
    private val pauseBtnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x88FFFFFF.toInt()
        textSize = 44f
        textAlign = Paint.Align.CENTER
    }

    val pauseButtonRect = RectF()
    val resumeButtonRect = RectF()
    val quitButtonRect = RectF()
    val retryButtonRect = RectF()
    val menuButtonRect = RectF()
    val nextLevelButtonRect = RectF()

    fun drawHUD(canvas: Canvas, score: Int, highScore: Int) {
        textPaint.textSize = 36f
        canvas.drawText("得分: $score", 20f, 50f, textPaint)

        textPaint.textSize = 28f
        textPaint.color = 0xFFAAAACC.toInt()
        canvas.drawText("最高: $highScore", 20f, 84f, textPaint)
        textPaint.color = Color.WHITE

        val pw = canvas.width.toFloat()
        // Touch-friendly pause button (~110x76). The drawn rect IS the hit area
        // (GameController hit-tests pauseButtonRect directly), so keep it generous.
        pauseButtonRect.set(pw - 118f, 14f, pw - 14f, 90f)
        pauseBtnPaint.style = Paint.Style.FILL
        pauseBtnPaint.color = 0x44FFFFFF.toInt()
        canvas.drawRoundRect(pauseButtonRect, 14f, 14f, pauseBtnPaint)
        pauseBtnPaint.color = 0xCCFFFFFF.toInt()
        val cx = pauseButtonRect.centerX()
        val cy = pauseButtonRect.centerY()
        canvas.drawRect(cx - 14f, cy - 18f, cx - 4f, cy + 18f, pauseBtnPaint)
        canvas.drawRect(cx + 4f, cy - 18f, cx + 14f, cy + 18f, pauseBtnPaint)
    }

    fun drawPauseOverlay(canvas: Canvas) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        canvas.drawRect(0f, 0f, w, h, overlayPaint)

        centerTextPaint.textSize = 64f
        canvas.drawText("暂停", w / 2f, h / 2f - 80f, centerTextPaint)

        val btnW = 200f
        val btnH = 60f
        resumeButtonRect.set(w / 2f - btnW / 2f, h / 2f, w / 2f + btnW / 2f, h / 2f + btnH)
        canvas.drawRoundRect(resumeButtonRect, 16f, 16f, buttonPaint)
        canvas.drawText("继续", w / 2f, h / 2f + 42f, buttonTextPaint)

        val qy = h / 2f + btnH + 24f
        quitButtonRect.set(w / 2f - btnW / 2f, qy, w / 2f + btnW / 2f, qy + btnH)
        buttonPaint.color = 0xFF555577.toInt()
        canvas.drawRoundRect(quitButtonRect, 16f, 16f, buttonPaint)
        canvas.drawText("退出", w / 2f, qy + 42f, buttonTextPaint)
        buttonPaint.color = 0xFF4488FF.toInt()
    }

    fun drawGameOverOverlay(canvas: Canvas, score: Int, highScore: Int) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        canvas.drawRect(0f, 0f, w, h, overlayPaint)

        centerTextPaint.textSize = 64f
        canvas.drawText("游戏结束", w / 2f, h / 2f - 120f, centerTextPaint)

        smallTextPaint.textSize = 40f
        canvas.drawText("得分: $score", w / 2f, h / 2f - 50f, smallTextPaint)
        smallTextPaint.textSize = 32f
        smallTextPaint.color = 0xFFFFDD44.toInt()
        canvas.drawText("最高分: $highScore", w / 2f, h / 2f - 8f, smallTextPaint)
        smallTextPaint.color = Color.WHITE

        val btnW = 200f
        val btnH = 60f
        val by = h / 2f + 30f
        retryButtonRect.set(w / 2f - btnW / 2f, by, w / 2f + btnW / 2f, by + btnH)
        canvas.drawRoundRect(retryButtonRect, 16f, 16f, buttonPaint)
        canvas.drawText("重试", w / 2f, by + 42f, buttonTextPaint)

        val my = by + btnH + 20f
        menuButtonRect.set(w / 2f - btnW / 2f, my, w / 2f + btnW / 2f, my + btnH)
        buttonPaint.color = 0xFF555577.toInt()
        canvas.drawRoundRect(menuButtonRect, 16f, 16f, buttonPaint)
        canvas.drawText("菜单", w / 2f, my + 42f, buttonTextPaint)
        buttonPaint.color = 0xFF4488FF.toInt()
    }

    fun drawLevelCompleteOverlay(canvas: Canvas, score: Int, stars: Int) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        canvas.drawRect(0f, 0f, w, h, overlayPaint)

        centerTextPaint.textSize = 56f
        canvas.drawText("恭喜过关!", w / 2f, h / 2f - 130f, centerTextPaint)

        smallTextPaint.textSize = 48f
        smallTextPaint.color = 0xFFFFDD44.toInt()
        val starText = "★".repeat(stars) + "☆".repeat(3 - stars)
        canvas.drawText(starText, w / 2f, h / 2f - 60f, smallTextPaint)
        smallTextPaint.color = Color.WHITE

        smallTextPaint.textSize = 36f
        canvas.drawText("得分: $score", w / 2f, h / 2f - 10f, smallTextPaint)

        val btnW = 200f
        val btnH = 60f
        val ny = h / 2f + 30f
        nextLevelButtonRect.set(w / 2f - btnW / 2f, ny, w / 2f + btnW / 2f, ny + btnH)
        canvas.drawRoundRect(nextLevelButtonRect, 16f, 16f, buttonPaint)
        canvas.drawText("下一关", w / 2f, ny + 42f, buttonTextPaint)

        val my = ny + btnH + 20f
        menuButtonRect.set(w / 2f - btnW / 2f, my, w / 2f + btnW / 2f, my + btnH)
        buttonPaint.color = 0xFF555577.toInt()
        canvas.drawRoundRect(menuButtonRect, 16f, 16f, buttonPaint)
        canvas.drawText("菜单", w / 2f, my + 42f, buttonTextPaint)
        buttonPaint.color = 0xFF4488FF.toInt()
    }

    private val dangerPaint = Paint().apply {
        style = Paint.Style.STROKE
    }

    /**
     * [intensity] (0..1) is how close the stack is to the line; [clockMs] drives
     * a pulse so the warning visibly throbs as the player approaches danger.
     */
    fun drawDangerLine(
        canvas: Canvas,
        y: Float,
        width: Float,
        intensity: Float = 0f,
        clockMs: Float = 0f
    ) {
        val clamped = intensity.coerceIn(0f, 1f)
        val pulse = 0.5f + 0.5f * kotlin.math.sin(clockMs / 280f)
        // Base visibility plus a pulsing boost that grows with proximity.
        val alpha = (0x40 + (clamped * pulse * 0xBF)).toInt().coerceIn(0x40, 0xFF)
        dangerPaint.color = (alpha shl 24) or 0xFF4444
        dangerPaint.strokeWidth = 2f + clamped * 5f

        val dashLen = 12f
        val gap = 8f
        var x = 0f
        while (x < width) {
            canvas.drawLine(x, y, (x + dashLen).coerceAtMost(width), y, dangerPaint)
            x += dashLen + gap
        }
    }
}
