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
        pauseButtonRect.set(pw - 90f, 10f, pw - 10f, 70f)
        pauseBtnPaint.style = Paint.Style.FILL
        pauseBtnPaint.color = 0x44FFFFFF.toInt()
        canvas.drawRoundRect(pauseButtonRect, 12f, 12f, pauseBtnPaint)
        pauseBtnPaint.color = 0xCCFFFFFF.toInt()
        pauseBtnPaint.style = Paint.Style.FILL
        canvas.drawRect(pw - 62f, 26f, pw - 54f, 54f, pauseBtnPaint)
        canvas.drawRect(pw - 46f, 26f, pw - 38f, 54f, pauseBtnPaint)
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

    fun drawDangerLine(canvas: Canvas, y: Float, width: Float) {
        val paint = Paint().apply {
            color = 0x44FF4444.toInt()
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        val dashLen = 12f
        val gap = 8f
        var x = 0f
        while (x < width) {
            canvas.drawLine(x, y, (x + dashLen).coerceAtMost(width), y, paint)
            x += dashLen + gap
        }
    }
}
