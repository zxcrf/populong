package com.populong.bubbleshooter.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.populong.bubbleshooter.core.engine.Ammo
import com.populong.bubbleshooter.core.grid.Bubble
import com.populong.bubbleshooter.core.grid.BubbleColor
import com.populong.bubbleshooter.ui.theme.Neon
import kotlin.math.max

/**
 * Bakes every bubble/ammo visual variant into an [ImageBitmap] once per [radiusPx], so per-frame
 * drawing is a cheap `drawImage` blit rather than re-running gradients and paths every tick.
 *
 * Sprites are square with side [sizePx]: the bubble body (`radiusPx` radius) plus a glow margin.
 */
class BubbleSprites(private val radiusPx: Float) {

    /** Side length in px of every baked sprite bitmap: body diameter plus a glow margin. */
    val sizePx: Int = max(2, (radiusPx * 2f * 1.6f).toInt())

    private val colored: Map<BubbleColor, ImageBitmap> =
        BubbleColor.all.associateWith { plainSprite(Neon.bubbleColor(it)) }
    private val ice: Map<BubbleColor, ImageBitmap> =
        BubbleColor.all.associateWith { iceSprite(Neon.bubbleColor(it), cracked = false) }
    private val iceCracked: Map<BubbleColor, ImageBitmap> =
        BubbleColor.all.associateWith { iceSprite(Neon.bubbleColor(it), cracked = true) }
    private val fogRevealed: Map<BubbleColor, ImageBitmap> =
        BubbleColor.all.associateWith { fogRevealedSprite(Neon.bubbleColor(it)) }
    private val chained: Map<BubbleColor, ImageBitmap> =
        BubbleColor.all.associateWith { chainedSprite(Neon.bubbleColor(it)) }
    private val supernova: Map<BubbleColor, ImageBitmap> =
        BubbleColor.all.associateWith { supernovaSprite(Neon.bubbleColor(it)) }
    private val pulsarLit: Map<BubbleColor, ImageBitmap> =
        BubbleColor.all.associateWith { pulsarSprite(Neon.bubbleColor(it), lit = true) }
    private val pulsarUnlit: Map<BubbleColor, ImageBitmap> =
        BubbleColor.all.associateWith { pulsarSprite(Neon.bubbleColor(it), lit = false) }
    private val stoneSprite: ImageBitmap = stoneSpriteImpl()
    private val gravityWellSprite: ImageBitmap = gravityWellSpriteImpl()
    private val wormholeSprite1: ImageBitmap = wormholeSpriteImpl(warm = false)
    private val wormholeSprite2: ImageBitmap = wormholeSpriteImpl(warm = true)
    private val fogCloudA: ImageBitmap = fogCloudSpriteImpl(variant = 0)
    private val fogCloudB: ImageBitmap = fogCloudSpriteImpl(variant = 1)
    private val bombSprite: ImageBitmap = bombSpriteImpl()
    private val rainbowSprite: ImageBitmap = rainbowSpriteImpl()

    /** The baked sprite for a placed grid bubble [b]. An unrevealed [Bubble.Fog] here always bakes
     * as variant 0 ([fogCloudA]) — callers with cell context (the grid draw loop) that want the
     * two arrangements to alternate per-cell should call [fogCloud] directly instead. */
    fun forBubble(b: Bubble): ImageBitmap = when (b) {
        is Bubble.Colored -> colored.getValue(b.color)
        Bubble.Stone -> stoneSprite
        is Bubble.Ice -> if (b.hitsLeft <= 1) iceCracked.getValue(b.color) else ice.getValue(b.color)
        is Bubble.Fog -> if (b.revealed) fogRevealed.getValue(b.color) else fogCloudA
        is Bubble.Chained -> chained.getValue(b.color)
        is Bubble.Supernova -> supernova.getValue(b.color)
        is Bubble.Pulsar -> if (b.lit) pulsarLit.getValue(b.color) else pulsarUnlit.getValue(b.color)
        Bubble.GravityWell -> gravityWellSprite
        is Bubble.Wormhole -> if (b.pairId >= 2) wormholeSprite2 else wormholeSprite1
    }

    /**
     * The nebula-cloud sprite for an unrevealed [Bubble.Fog] cell: [variant] (any int; only its
     * parity matters) picks one of two baked blob arrangements, so [GameRenderer] can alternate
     * them per-cell (by `pos.packed and 1`) and keep neighboring fog cells from tiling identically.
     */
    fun fogCloud(variant: Int): ImageBitmap = if (variant and 1 == 0) fogCloudA else fogCloudB

    /** The baked sprite for a loaded/in-flight ammo [a]. */
    fun forAmmo(a: Ammo): ImageBitmap = when (a) {
        is Ammo.ColorAmmo -> colored.getValue(a.color)
        Ammo.Bomb -> bombSprite
        Ammo.Rainbow -> rainbowSprite
    }

    // --- Baking ---------------------------------------------------------------------------

    private fun bake(draw: DrawScope.() -> Unit): ImageBitmap {
        val bmp = ImageBitmap(sizePx, sizePx)
        val canvas = Canvas(bmp)
        CanvasDrawScope().draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = canvas,
            size = Size(sizePx.toFloat(), sizePx.toFloat()),
            block = draw,
        )
        return bmp
    }

    private fun plainSprite(base: Color): ImageBitmap = bake {
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(base.copy(alpha = 0.35f), base.copy(alpha = 0f)),
                center = c,
                radius = radiusPx * 1.6f,
            ),
            radius = radiusPx * 1.6f,
            center = c,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(lighten(base, 0.55f), base, darken(base, 0.35f)),
                center = c + Offset(-radiusPx * 0.3f, -radiusPx * 0.3f),
                radius = radiusPx * 1.3f,
            ),
            radius = radiusPx,
            center = c,
        )
        drawCircle(
            color = darken(base, 0.45f).copy(alpha = 0.6f),
            radius = radiusPx,
            center = c,
            style = Stroke(width = max(1f, radiusPx * 0.06f)),
        )
        drawOval(
            color = Color.White.copy(alpha = 0.55f),
            topLeft = c + Offset(-radiusPx * 0.55f, -radiusPx * 0.62f),
            size = Size(radiusPx * 0.55f, radiusPx * 0.35f),
        )
    }

    private fun stoneSpriteImpl(): ImageBitmap = bake {
        val c = Offset(size.width / 2f, size.height / 2f)
        val base = Color(0xFF3A4152)
        drawCircle(color = base, radius = radiusPx, center = c)
        drawCircle(
            color = darken(base, 0.5f).copy(alpha = 0.5f),
            radius = radiusPx,
            center = c,
            style = Stroke(width = max(1f, radiusPx * 0.08f)),
        )
        val craterOffsets = listOf(
            Offset(-0.35f, -0.1f),
            Offset(0.25f, 0.3f),
            Offset(0.05f, -0.4f),
            Offset(-0.3f, 0.35f),
        )
        for (o in craterOffsets) {
            drawCircle(
                color = darken(base, 0.35f),
                radius = radiusPx * 0.14f,
                center = c + Offset(o.x * radiusPx, o.y * radiusPx),
            )
        }
    }

    private fun iceSprite(base: Color, cracked: Boolean): ImageBitmap = bake {
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color = darken(base, 0.25f), radius = radiusPx, center = c)
        drawArc(
            color = Color(0xCCEAF6FF),
            startAngle = 200f,
            sweepAngle = 160f,
            useCenter = true,
            topLeft = c - Offset(radiusPx, radiusPx),
            size = Size(radiusPx * 2f, radiusPx * 2f),
            alpha = 0.55f,
        )
        drawCircle(
            color = Color(0x66FFFFFF),
            radius = radiusPx,
            center = c,
            style = Stroke(width = max(1f, radiusPx * 0.08f)),
        )
        if (cracked) {
            val strokeW = max(1f, radiusPx * 0.08f)
            drawLine(
                color = Color(0xAAFFFFFF),
                start = c + Offset(-radiusPx * 0.5f, -radiusPx * 0.2f),
                end = c + Offset(radiusPx * 0.4f, radiusPx * 0.5f),
                strokeWidth = strokeW,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color(0xAAFFFFFF),
                start = c + Offset(radiusPx * 0.5f, -radiusPx * 0.35f),
                end = c + Offset(-radiusPx * 0.1f, radiusPx * 0.4f),
                strokeWidth = strokeW,
                cap = StrokeCap.Round,
            )
        }
    }

    /**
     * A nebula cloud (星云迷雾): three layered translucent blobs (violet, deep blue, white haze) at
     * slight offsets over a dark backdrop, plus a few faint speck dots glimpsed through the cloud —
     * no question mark. [variant] arranges the blobs/specks differently so [fogCloud]'s two baked
     * bitmaps read as distinct when tiled across neighboring unrevealed-fog cells.
     */
    private fun fogCloudSpriteImpl(variant: Int): ImageBitmap = bake {
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color = Color(0xFF191530), radius = radiusPx, center = c)

        val violet = Color(0x66B44DFF)
        val deepBlue = Color(0x554D9FFF)
        val whiteHaze = Color(0x22FFFFFF)
        val blobSize = Size(radiusPx * 1.15f, radiusPx * 0.85f)
        val blobs = if (variant == 0) {
            listOf(Offset(-0.32f, -0.22f) to violet, Offset(0.28f, 0.18f) to deepBlue, Offset(-0.08f, 0.32f) to whiteHaze)
        } else {
            listOf(Offset(0.30f, -0.28f) to deepBlue, Offset(-0.30f, 0.12f) to violet, Offset(0.10f, 0.34f) to whiteHaze)
        }
        for ((offset, color) in blobs) {
            drawOval(
                color = color,
                topLeft = c + Offset(offset.x * radiusPx - blobSize.width / 2f, offset.y * radiusPx - blobSize.height / 2f),
                size = blobSize,
            )
        }

        val specks = if (variant == 0) {
            listOf(Offset(-0.42f, 0.04f), Offset(0.36f, -0.32f), Offset(0.02f, 0.42f))
        } else {
            listOf(Offset(0.40f, 0.08f), Offset(-0.22f, -0.36f), Offset(-0.06f, 0.40f))
        }
        for (o in specks) {
            drawCircle(color = Color.White.copy(alpha = 0.5f), radius = radiusPx * 0.05f, center = c + Offset(o.x * radiusPx, o.y * radiusPx))
        }

        drawCircle(
            color = Color(0xFF6A5AA0).copy(alpha = 0.35f),
            radius = radiusPx,
            center = c,
            style = Stroke(width = max(1f, radiusPx * 0.07f)),
        )
    }

    private fun fogRevealedSprite(base: Color): ImageBitmap = bake {
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color = base, radius = radiusPx, center = c)
        drawCircle(
            color = lighten(base, 0.4f),
            radius = radiusPx * 0.6f,
            center = c - Offset(radiusPx * 0.25f, radiusPx * 0.25f),
            alpha = 0.4f,
        )
        for (i in 1..3) {
            drawCircle(
                color = Color.White.copy(alpha = 0.15f / i),
                radius = radiusPx * (0.5f + i * 0.2f),
                center = c,
                style = Stroke(width = max(1f, radiusPx * 0.04f)),
            )
        }
    }

    private fun chainedSprite(base: Color): ImageBitmap = bake {
        val c = Offset(size.width / 2f, size.height / 2f)
        val strokeW = max(1f, radiusPx * 0.16f)
        val dark = Color(0xFF14141C)
        drawCircle(color = darken(base, 0.15f), radius = radiusPx, center = c)
        drawLine(
            color = dark,
            start = c + Offset(-radiusPx * 0.6f, -radiusPx * 0.6f),
            end = c + Offset(radiusPx * 0.6f, radiusPx * 0.6f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = dark,
            start = c + Offset(radiusPx * 0.6f, -radiusPx * 0.6f),
            end = c + Offset(-radiusPx * 0.6f, radiusPx * 0.6f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round,
        )
        val linkOffsets = listOf(Offset(0f, -1f), Offset(0f, 1f), Offset(-1f, 0f), Offset(1f, 0f))
        for (o in linkOffsets) {
            drawCircle(
                color = Color(0xFF8A8FA0),
                radius = radiusPx * 0.12f,
                center = c + Offset(o.x * radiusPx * 0.95f, o.y * radiusPx * 0.95f),
                style = Stroke(width = max(1f, radiusPx * 0.05f)),
            )
        }
    }

    /** A radiant star orb: bright core, base-color body, soft halo, and a 4-point diffraction-spike
     * cross (the thin bright horizontal/vertical lines a camera lens shows around a very bright
     * point light) to read distinctly as "about to detonate" among the other sprite variants. */
    private fun supernovaSprite(base: Color): ImageBitmap = bake {
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(lighten(base, 0.6f).copy(alpha = 0.5f), base.copy(alpha = 0f)),
                center = c,
                radius = radiusPx * 1.7f,
            ),
            radius = radiusPx * 1.7f,
            center = c,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, lighten(base, 0.3f), base, darken(base, 0.3f)),
                center = c,
                radius = radiusPx * 1.1f,
            ),
            radius = radiusPx,
            center = c,
        )
        drawCircle(color = Color.White.copy(alpha = 0.9f), radius = radiusPx * 0.35f, center = c)
        val spikeColor = Color.White.copy(alpha = 0.85f)
        val spikeLen = radiusPx * 1.55f
        val strokeW = max(1f, radiusPx * 0.08f)
        drawLine(
            color = spikeColor,
            start = c - Offset(spikeLen, 0f),
            end = c + Offset(spikeLen, 0f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = spikeColor,
            start = c - Offset(0f, spikeLen),
            end = c + Offset(0f, spikeLen),
            strokeWidth = strokeW,
            cap = StrokeCap.Round,
        )
    }

    /** A pulsar (脉冲星): lit shows a bright radiant core with a 4-point cross flare over its body
     * color; unlit is a dim, faceted crystal that reads as dormant. */
    private fun pulsarSprite(base: Color, lit: Boolean): ImageBitmap = bake {
        val c = Offset(size.width / 2f, size.height / 2f)
        if (lit) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(lighten(base, 0.5f).copy(alpha = 0.5f), base.copy(alpha = 0f)),
                    center = c,
                    radius = radiusPx * 1.7f,
                ),
                radius = radiusPx * 1.7f,
                center = c,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, lighten(base, 0.35f), base, darken(base, 0.25f)),
                    center = c,
                    radius = radiusPx * 1.05f,
                ),
                radius = radiusPx,
                center = c,
            )
            drawCircle(color = Color.White.copy(alpha = 0.9f), radius = radiusPx * 0.32f, center = c)
            val flare = Color.White.copy(alpha = 0.8f)
            val len = radiusPx * 1.35f
            val w = max(1f, radiusPx * 0.09f)
            drawLine(flare, c - Offset(len, 0f), c + Offset(len, 0f), strokeWidth = w, cap = StrokeCap.Round)
            drawLine(flare, c - Offset(0f, len), c + Offset(0f, len), strokeWidth = w, cap = StrokeCap.Round)
        } else {
            drawCircle(color = darken(base, 0.55f), radius = radiusPx, center = c)
            drawCircle(
                color = darken(base, 0.3f).copy(alpha = 0.6f),
                radius = radiusPx,
                center = c,
                style = Stroke(width = max(1f, radiusPx * 0.08f)),
            )
            val facet = lighten(base, 0.2f).copy(alpha = 0.4f)
            val fw = max(1f, radiusPx * 0.06f)
            drawLine(
                facet,
                c + Offset(-radiusPx * 0.4f, -radiusPx * 0.5f),
                c + Offset(radiusPx * 0.4f, radiusPx * 0.5f),
                strokeWidth = fw,
                cap = StrokeCap.Round,
            )
            drawLine(
                facet,
                c + Offset(radiusPx * 0.4f, -radiusPx * 0.5f),
                c + Offset(-radiusPx * 0.4f, radiusPx * 0.5f),
                strokeWidth = fw,
                cap = StrokeCap.Round,
            )
            drawCircle(color = lighten(base, 0.3f).copy(alpha = 0.35f), radius = radiusPx * 0.18f, center = c)
        }
    }

    /** A gravity well (引力井): a dark core ringed by two tilted elliptical accretion streaks. */
    private fun gravityWellSpriteImpl(): ImageBitmap = bake {
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF0A0A12), Color(0xFF1B1030), Color(0xFF06060B)),
                center = c,
                radius = radiusPx,
            ),
            radius = radiusPx,
            center = c,
        )
        val strokeW = max(1f, radiusPx * 0.09f)
        // A wide/flat and a tall/narrow accretion ring framing the dark core.
        drawOval(
            color = Color(0xFF6C4BD6).copy(alpha = 0.75f),
            topLeft = c + Offset(-radiusPx * 1.15f, -radiusPx * 0.45f),
            size = Size(radiusPx * 2.3f, radiusPx * 0.9f),
            style = Stroke(width = strokeW),
        )
        drawOval(
            color = Color(0xFF35E0F2).copy(alpha = 0.55f),
            topLeft = c + Offset(-radiusPx * 0.5f, -radiusPx * 1.1f),
            size = Size(radiusPx * 1.0f, radiusPx * 2.2f),
            style = Stroke(width = strokeW * 0.8f),
        )
        drawCircle(color = Color.Black.copy(alpha = 0.9f), radius = radiusPx * 0.4f, center = c)
    }

    /** A wormhole (虫洞) portal: three nested swirling arcs in cyan/violet; the second pair id ([warm])
     * shifts the palette warmer so the two ends read as distinct. */
    private fun wormholeSpriteImpl(warm: Boolean): ImageBitmap = bake {
        val c = Offset(size.width / 2f, size.height / 2f)
        val inner = if (warm) Color(0xFFFFB25C) else Color(0xFF35E0F2)
        val outer = if (warm) Color(0xFFFF5C8A) else Color(0xFF9A6BFF)
        drawCircle(color = Color(0xFF0B0A1A), radius = radiusPx, center = c)
        for (i in 0 until 3) {
            val r = radiusPx * (0.9f - i * 0.26f)
            val color = if (i % 2 == 0) outer else inner
            drawArc(
                color = color.copy(alpha = 0.85f),
                startAngle = i * 120f,
                sweepAngle = 260f,
                useCenter = false,
                topLeft = c - Offset(r, r),
                size = Size(r * 2f, r * 2f),
                style = Stroke(width = max(1f, radiusPx * 0.1f), cap = StrokeCap.Round),
            )
        }
        drawCircle(color = inner.copy(alpha = 0.9f), radius = radiusPx * 0.14f, center = c)
    }

    private fun bombSpriteImpl(): ImageBitmap = bake {
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color = Color(0xFF0D0D12), radius = radiusPx, center = c)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFC94D), Color(0xFFFF5C1A).copy(alpha = 0.6f), Color.Transparent),
                center = c,
                radius = radiusPx * 0.9f,
            ),
            radius = radiusPx * 0.75f,
            center = c,
        )
        drawCircle(
            color = Color(0xFFFFE9B0),
            radius = radiusPx * 0.12f,
            center = c - Offset(radiusPx * 0.25f, radiusPx * 0.25f),
        )
    }

    private fun rainbowSpriteImpl(): ImageBitmap = bake {
        val c = Offset(size.width / 2f, size.height / 2f)
        val colors = BubbleColor.all.map { Neon.bubbleColor(it) }
        val n = colors.size
        for ((i, color) in colors.withIndex()) {
            val r = radiusPx * (1f - i.toFloat() / n)
            drawCircle(color = color, radius = r, center = c)
        }
        drawOval(
            color = Color.White.copy(alpha = 0.5f),
            topLeft = c + Offset(-radiusPx * 0.5f, -radiusPx * 0.6f),
            size = Size(radiusPx * 0.5f, radiusPx * 0.3f),
        )
    }

    private fun lighten(c: Color, amt: Float): Color = Color(
        red = c.red + (1f - c.red) * amt,
        green = c.green + (1f - c.green) * amt,
        blue = c.blue + (1f - c.blue) * amt,
        alpha = c.alpha,
    )

    private fun darken(c: Color, amt: Float): Color = Color(
        red = c.red * (1f - amt),
        green = c.green * (1f - amt),
        blue = c.blue * (1f - amt),
        alpha = c.alpha,
    )
}
