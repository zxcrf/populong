package com.populong.bubbleshooter.ui.menu

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.populong.bubbleshooter.AppContainer
import com.populong.bubbleshooter.audio.Sfx
import com.populong.bubbleshooter.core.level.LevelCatalog
import com.populong.bubbleshooter.core.progress.SaveData
import com.populong.bubbleshooter.core.progress.highestUnlockedLevel
import com.populong.bubbleshooter.ui.theme.Neon
import kotlin.math.cos
import kotlin.math.sin

private const val TWO_PI = 6.2831855f
private val GALAXY_BOX_HEIGHT = 340.dp
private const val NODE_HIT_RADIUS_DP = 24f
private const val NODE_RADIUS_DP = 12f
private const val BOSS_RADIUS_DP = 17f

/** One node's fractional (0f..1f) position within its galaxy's box. */
private data class NodeSlot(val fx: Float, val fy: Float)

/** Tiny seeded linear-congruential generator; deterministic per-galaxy jitter, nothing more. */
private class Lcg(seed: Long) {
    private var state = seed
    fun nextFloat(): Float {
        state = state * 6364136223846793005L + 1442695040888963407L
        val bits = (state ushr 40) and 0xFFFFFF
        return bits.toFloat() / 0xFFFFFF.toFloat()
    }
}

/**
 * Deterministic pseudo-constellation layout for [galaxy]: 20 nodes in 4 or 5 zigzagging rows
 * with small jitter, always in the same order/positions for the same galaxy number.
 */
private fun nodeSlots(galaxy: Int): List<NodeSlot> {
    val lcg = Lcg(seed = galaxy.toLong() * 0x9E3779B9L + 12345L)
    val rows = if (lcg.nextFloat() < 0.5f) 4 else 5
    val cols = LevelCatalog.GALAXY_SIZE / rows
    val slots = ArrayList<NodeSlot>(LevelCatalog.GALAXY_SIZE)
    for (i in 0 until LevelCatalog.GALAXY_SIZE) {
        val row = i / cols
        val col = i % cols
        val zigzag = if (row % 2 == 1) 0.5f / cols else 0f
        val baseX = (col + 0.5f) / cols + zigzag
        val baseY = (row + 0.5f) / rows
        val jitterX = (lcg.nextFloat() - 0.5f) * (0.6f / cols)
        val jitterY = (lcg.nextFloat() - 0.5f) * (0.5f / rows)
        slots.add(
            NodeSlot(
                fx = (baseX + jitterX).coerceIn(0.05f, 0.95f),
                fy = (baseY + jitterY).coerceIn(0.08f, 0.92f),
            ),
        )
    }
    return slots
}

private fun DrawScope.drawHexagon(center: Offset, radius: Float, color: Color, alpha: Float) {
    val path = Path()
    for (k in 0 until 6) {
        val angle = (Math.PI.toFloat() / 3f) * k - (Math.PI.toFloat() / 2f)
        val x = center.x + radius * cos(angle)
        val y = center.y + radius * sin(angle)
        if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color = color, alpha = alpha, style = Fill)
}

/**
 * The constellation-style level map: a vertically scrollable starfield of all
 * [LevelCatalog.GALAXY_COUNT] galaxies, each holding [LevelCatalog.GALAXY_SIZE] level nodes drawn
 * as a small pseudo-constellation. Only the visible [LazyColumn] items are ever composed, so this
 * never has to place 2000 individual nodes at once.
 */
@Composable
fun GalaxyMapScreen(container: AppContainer, onPick: (Int) -> Unit, onBack: () -> Unit) {
    val save by container.save.save.collectAsState()
    val unlocked = save.highestUnlockedLevel()
    val totalStars = save.levels.values.sumOf { it.stars }

    val initialGalaxy = ((unlocked - 1) / LevelCatalog.GALAXY_SIZE).coerceIn(0, LevelCatalog.GALAXY_COUNT - 1)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialGalaxy)

    val transition = rememberInfiniteTransition(label = "galaxy-map-pulse")
    val pulsePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec = infiniteRepeatable(tween(durationMillis = 2200, easing = LinearEasing)),
        label = "pulse-phase",
    )

    fun tapLevel(level: Int) {
        container.sfx.play(Sfx.UI_TAP)
        container.haptics.tick()
        onPick(level)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(Neon.spaceGradient)),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 72.dp, bottom = 24.dp),
        ) {
            items(count = LevelCatalog.GALAXY_COUNT) { idx ->
                val galaxy = idx + 1
                GalaxyItem(
                    galaxy = galaxy,
                    save = save,
                    unlocked = unlocked,
                    pulsePhase = pulsePhase,
                    onTapLevel = ::tapLevel,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color(0xEE060B1E))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "←",
                color = Neon.textPrimary,
                fontSize = 22.sp,
                modifier = Modifier.clickable {
                    container.sfx.play(Sfx.UI_TAP)
                    onBack()
                },
            )
            Text(
                text = "星图",
                color = Neon.cyan,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Text(text = "★ $totalStars", color = Neon.gold, fontSize = 16.sp)
        }
    }
}

/** One galaxy's title plus its constellation box: 20 level nodes, connecting lines, and taps. */
@Composable
private fun GalaxyItem(
    galaxy: Int,
    save: SaveData,
    unlocked: Int,
    pulsePhase: Float,
    onTapLevel: (Int) -> Unit,
) {
    val range = LevelCatalog.levelsInGalaxy(galaxy)
    val slots = remember(galaxy) { nodeSlots(galaxy) }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            text = "第 $galaxy 星系",
            color = Neon.textDim,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(GALAXY_BOX_HEIGHT)
                .pointerInput(galaxy, unlocked) {
                    val hitRadiusPx = NODE_HIT_RADIUS_DP.dp.toPx()
                    detectTapGestures { tap ->
                        val widthPx = size.width.toFloat()
                        val heightPx = size.height.toFloat()
                        for (i in slots.indices) {
                            val level = range.first + i
                            if (level > unlocked) continue
                            val node = Offset(slots[i].fx * widthPx, slots[i].fy * heightPx)
                            if ((tap - node).getDistance() <= hitRadiusPx) {
                                onTapLevel(level)
                                break
                            }
                        }
                    }
                },
        ) {
            val widthDp: Dp = maxWidth

            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Constellation lines, in level order.
                for (i in 0 until slots.size - 1) {
                    val a = Offset(slots[i].fx * w, slots[i].fy * h)
                    val b = Offset(slots[i + 1].fx * w, slots[i + 1].fy * h)
                    drawLine(
                        color = Neon.cyan.copy(alpha = 0.18f),
                        start = a,
                        end = b,
                        strokeWidth = 1.5f,
                    )
                }

                val nodeRadiusPx = NODE_RADIUS_DP.dp.toPx()
                val bossRadiusPx = BOSS_RADIUS_DP.dp.toPx()

                for (i in slots.indices) {
                    val level = range.first + i
                    val center = Offset(slots[i].fx * w, slots[i].fy * h)
                    val stars = (save.levels[level]?.stars ?: 0).coerceIn(0, 3)
                    val isBoss = level % 20 == 0
                    val isCompleted = stars >= 1
                    val isFrontier = !isCompleted && level == unlocked
                    val isLocked = !isCompleted && !isFrontier

                    val color = when {
                        isBoss -> Neon.gold
                        isCompleted -> Neon.cyan
                        isFrontier -> Neon.mint
                        else -> Neon.textDim
                    }
                    val alpha = if (isLocked) 0.35f else 1f
                    val radius = if (isBoss) bossRadiusPx else nodeRadiusPx * if (isLocked) 0.75f else 1f

                    if (isBoss) {
                        drawHexagon(center, radius, color, alpha)
                    } else {
                        drawCircle(color = color, radius = radius, center = center, alpha = alpha, style = Fill)
                    }

                    if (isFrontier) {
                        val pulse = (sin(pulsePhase) + 1f) / 2f
                        drawCircle(
                            color = Neon.mint,
                            radius = radius + 4f + pulse * 6f,
                            center = center,
                            alpha = 0.35f + 0.35f * pulse,
                            style = Stroke(width = 2.5f),
                        )
                    }

                    if (isCompleted) {
                        val dotRadius = radius * 0.18f
                        val dotY = center.y - radius - dotRadius * 2f
                        val spacing = dotRadius * 2.6f
                        val startX = center.x - spacing * (stars - 1) / 2f
                        for (s in 0 until stars.coerceIn(0, 3)) {
                            drawCircle(
                                color = Neon.gold,
                                radius = dotRadius,
                                center = Offset(startX + spacing * s, dotY),
                                style = Fill,
                            )
                        }
                    }
                }
            }

            for (i in slots.indices) {
                val level = range.first + i
                val isLocked = (save.levels[level]?.stars ?: 0) < 1 && level != unlocked
                Text(
                    text = "$level",
                    color = if (isLocked) Neon.textDim else Neon.textPrimary,
                    fontSize = 9.sp,
                    modifier = Modifier.offset(
                        x = widthDp * slots[i].fx - 10.dp,
                        y = GALAXY_BOX_HEIGHT * slots[i].fy + 10.dp,
                    ),
                )
            }
        }
    }
}
