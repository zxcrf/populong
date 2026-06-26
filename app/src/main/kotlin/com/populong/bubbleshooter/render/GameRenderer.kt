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

        val dangerY = controller.gridOffsetY +
                (controller.grid.maxRows - 2) * controller.bubbleRadius * 1.732f
        uiRenderer.drawDangerLine(canvas, dangerY, canvas.width.toFloat())

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
