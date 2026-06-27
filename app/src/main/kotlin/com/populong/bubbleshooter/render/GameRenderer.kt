package com.populong.bubbleshooter.render

import android.graphics.Canvas
import com.populong.bubbleshooter.engine.GameController

class GameRenderer {
    val backgroundRenderer = BackgroundRenderer()
    val bubbleRenderer = BubbleRenderer()
    val effectsRenderer = EffectsRenderer()
    val uiRenderer = UIRenderer()

    fun draw(canvas: Canvas, controller: GameController) {
        val shake = controller.effects.screenShake

        backgroundRenderer.draw(canvas)

        canvas.save()
        canvas.translate(shake.offsetX, shake.offsetY)

        bubbleRenderer.drawGrid(
            canvas, controller.grid, controller.bubbleRadius, controller.gridOffsetY
        )

        val dangerRow = controller.grid.maxRows - 2
        val dangerY = controller.gridOffsetY +
                dangerRow * controller.bubbleRadius * 1.732f
        // Proximity ramps up over the last 4 rows before game over.
        val rowsAway = (dangerRow - controller.grid.lowestOccupiedRow()).coerceAtLeast(0)
        val dangerIntensity = (1f - rowsAway / 4f).coerceIn(0f, 1f)
        uiRenderer.drawDangerLine(
            canvas, dangerY, canvas.width.toFloat(),
            dangerIntensity, controller.effects.clockMs
        )

        controller.activeProjectile?.let {
            bubbleRenderer.drawProjectileWithRadius(canvas, it, controller.bubbleRadius)
        }

        if (controller.isAiming) {
            bubbleRenderer.drawAimLine(canvas, controller.aimSegments)
        }

        effectsRenderer.draw(canvas, controller.effects)

        canvas.restore()

        bubbleRenderer.drawShooterBase(
            canvas,
            controller.shooterX, controller.shooterY,
            controller.bubbleRadius,
            controller.currentColor, controller.currentIsRainbow
        )
        bubbleRenderer.drawNextBubble(
            canvas,
            controller.shooterX - controller.bubbleRadius * 2.5f,
            controller.shooterY,
            controller.bubbleRadius,
            controller.nextColor, controller.nextIsRainbow
        )

        uiRenderer.drawHUD(canvas, controller.score, controller.highScore)

        controller.drawOverlay(canvas, uiRenderer)
    }
}
