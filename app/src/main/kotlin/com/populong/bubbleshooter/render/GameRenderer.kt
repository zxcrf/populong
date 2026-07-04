package com.populong.bubbleshooter.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.populong.bubbleshooter.core.engine.GameState
import com.populong.bubbleshooter.core.engine.Phase
import com.populong.bubbleshooter.core.grid.GridGeometry
import com.populong.bubbleshooter.core.grid.GridPos
import com.populong.bubbleshooter.core.grid.Vec2
import com.populong.bubbleshooter.core.physics.Projectile
import com.populong.bubbleshooter.game.GameSessionHolder
import com.populong.bubbleshooter.ui.theme.Neon
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Maps unit-radius world coordinates (bubble radius = 1) to canvas pixel space. [scale] is
 * px-per-unit; [offsetX]/[offsetY] shift the projected origin (kept at 0 for a field that fills
 * the canvas width exactly). The world y passed to [toPx] is always relative to the *current*
 * ceiling plane, so unit `(0, ceilingY)` always maps to the canvas's top edge.
 */
data class FieldLayout(val scale: Float, val offsetX: Float, val offsetY: Float) {
    fun toPx(x: Float, y: Float, ceilingY: Float): Offset =
        Offset(offsetX + x * scale, offsetY + (y - ceilingY) * scale)
}

/** Draws one frame of the play field: ceiling, grid, lose line, aim preview, projectile, shooter, fever glow. */
object GameRenderer {

    fun draw(scope: DrawScope, session: GameSessionHolder, sprites: BubbleSprites, layout: FieldLayout) {
        val state = session.latestState
        val ceilingY = state.ceilingY

        scope.drawCeiling(state, layout, ceilingY)
        scope.drawGrid(state, sprites, layout, ceilingY)
        scope.drawLoseLine(state, layout, ceilingY)

        if (state.phase == Phase.AIMING) {
            scope.drawAimPreview(session, sprites, layout, ceilingY)
        }

        state.projectile?.let { scope.drawProjectile(it, sprites, layout, ceilingY) }
        scope.drawShooter(state, sprites, layout, ceilingY)

        if (state.feverActive) {
            scope.drawFeverGlow(session.frameTick.longValue)
        }
    }

    private fun DrawScope.drawCeiling(state: GameState, layout: FieldLayout, ceilingY: Float) {
        // Level-mode ceiling compression is scored, not re-indexed into the grid; render it as
        // the ceiling bar visually sinking toward the bubbles by half a row per compression step.
        val visualY = ceilingY + state.descentSteps * 0.5f * GridGeometry.ROW_HEIGHT
        val left = layout.toPx(0f, visualY, ceilingY)
        val right = layout.toPx(2f * state.grid.evenCols, visualY, ceilingY)

        drawLine(color = Neon.cyan.copy(alpha = 0.7f), start = left, end = right, strokeWidth = 3f)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Neon.cyan.copy(alpha = 0.16f), Color.Transparent),
                startY = left.y,
                endY = left.y + layout.scale * 2f,
            ),
            topLeft = Offset(left.x, left.y),
            size = Size(right.x - left.x, layout.scale * 2f),
        )
    }

    private fun DrawScope.drawGrid(state: GameState, sprites: BubbleSprites, layout: FieldLayout, ceilingY: Float) {
        for ((pos, bubble) in state.grid.cells) {
            val worldX = GridGeometry.centerX(pos)
            val worldY = GridGeometry.centerY(pos.row)
            val center = layout.toPx(worldX, worldY, ceilingY)
            drawSpriteCentered(sprites.forBubble(bubble), center)
        }
    }

    private fun DrawScope.drawLoseLine(state: GameState, layout: FieldLayout, ceilingY: Float) {
        val loseY = ceilingY + state.config.maxRows * GridGeometry.ROW_HEIGHT
        val left = layout.toPx(0f, loseY, ceilingY)
        val right = layout.toPx(2f * state.grid.evenCols, loseY, ceilingY)
        drawLine(
            color = Neon.danger.copy(alpha = 0.5f),
            start = left,
            end = right,
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f),
        )
    }

    private fun DrawScope.drawAimPreview(
        session: GameSessionHolder,
        sprites: BubbleSprites,
        layout: FieldLayout,
        ceilingY: Float,
    ) {
        val aim = session.aimResult() ?: return
        val pointsPx = aim.points.map { layout.toPx(it.x, it.y, ceilingY) }
        if (pointsPx.size < 2) return

        var totalLength = 0f
        for (i in 0 until pointsPx.size - 1) totalLength += (pointsPx[i + 1] - pointsPx[i]).getDistance()

        val dotSpacingPx = layout.scale * 0.5f
        var traveled = 0f
        var nextDot = 0f
        for (i in 0 until pointsPx.size - 1) {
            val a = pointsPx[i]
            val b = pointsPx[i + 1]
            val segLen = (b - a).getDistance()
            if (segLen <= 0f) continue
            val dir = Offset((b.x - a.x) / segLen, (b.y - a.y) / segLen)
            var d = nextDot - traveled
            while (d < segLen) {
                val p = Offset(a.x + dir.x * d, a.y + dir.y * d)
                val fade = (1f - (traveled + d) / (totalLength + 1f)).coerceIn(0.15f, 1f)
                drawCircle(color = Neon.textPrimary.copy(alpha = 0.55f * fade), radius = layout.scale * 0.08f, center = p)
                nextDot += dotSpacingPx
                d = nextDot - traveled
            }
            traveled += segLen
        }

        val landingCell: GridPos = aim.landingCell ?: return
        val landingWorldX = GridGeometry.centerX(landingCell)
        val landingWorldY = GridGeometry.centerY(landingCell.row)
        val landingPx = layout.toPx(landingWorldX, landingWorldY, ceilingY)
        val ghost = sprites.forAmmo(session.latestState.currentAmmo)
        drawImage(
            image = ghost,
            dstOffset = IntOffset(
                (landingPx.x - ghost.width / 2f).roundToInt(),
                (landingPx.y - ghost.height / 2f).roundToInt(),
            ),
            dstSize = IntSize(ghost.width, ghost.height),
            alpha = 0.4f,
        )
    }

    private fun DrawScope.drawProjectile(projectile: Projectile, sprites: BubbleSprites, layout: FieldLayout, ceilingY: Float) {
        val center = layout.toPx(projectile.pos.x, projectile.pos.y, ceilingY)
        drawSpriteCentered(sprites.forAmmo(projectile.ammo), center)
    }

    private fun DrawScope.drawShooter(state: GameState, sprites: BubbleSprites, layout: FieldLayout, ceilingY: Float) {
        val origin: Vec2 = state.shooterOrigin
        val center = layout.toPx(origin.x, origin.y, ceilingY)

        drawCircle(
            color = Neon.cyan.copy(alpha = 0.25f),
            radius = layout.scale * 1.6f,
            center = center,
            style = Stroke(width = 2f),
        )

        val currentSprite = sprites.forAmmo(state.currentAmmo)
        val currentScale = 1.15f
        val currentW = currentSprite.width * currentScale
        val currentH = currentSprite.height * currentScale
        drawImage(
            image = currentSprite,
            dstOffset = IntOffset((center.x - currentW / 2f).roundToInt(), (center.y - currentH / 2f).roundToInt()),
            dstSize = IntSize(currentW.roundToInt(), currentH.roundToInt()),
        )

        val nextSprite = sprites.forAmmo(state.nextAmmo)
        val nextScale = 0.55f
        val nextW = nextSprite.width * nextScale
        val nextH = nextSprite.height * nextScale
        val nextCenter = center + Offset(layout.scale * 1.8f, layout.scale * 0.6f)
        drawImage(
            image = nextSprite,
            dstOffset = IntOffset((nextCenter.x - nextW / 2f).roundToInt(), (nextCenter.y - nextH / 2f).roundToInt()),
            dstSize = IntSize(nextW.roundToInt(), nextH.roundToInt()),
            alpha = 0.85f,
        )
    }

    private fun DrawScope.drawFeverGlow(ticks: Long) {
        val pulse = (0.35f + 0.25f * sin(ticks / 12f)).coerceIn(0.1f, 0.6f)
        val thickness = size.minDimension * 0.06f
        val color = Neon.magenta.copy(alpha = pulse)
        drawRect(brush = Brush.horizontalGradient(listOf(color, Color.Transparent)), topLeft = Offset.Zero, size = Size(thickness, size.height))
        drawRect(
            brush = Brush.horizontalGradient(listOf(Color.Transparent, color)),
            topLeft = Offset(size.width - thickness, 0f),
            size = Size(thickness, size.height),
        )
        drawRect(brush = Brush.verticalGradient(listOf(color, Color.Transparent)), topLeft = Offset.Zero, size = Size(size.width, thickness))
        drawRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, color)),
            topLeft = Offset(0f, size.height - thickness),
            size = Size(size.width, thickness),
        )
    }

    private fun DrawScope.drawSpriteCentered(sprite: ImageBitmap, center: Offset) {
        drawImage(
            image = sprite,
            dstOffset = IntOffset((center.x - sprite.width / 2f).roundToInt(), (center.y - sprite.height / 2f).roundToInt()),
            dstSize = IntSize(sprite.width, sprite.height),
        )
    }
}
