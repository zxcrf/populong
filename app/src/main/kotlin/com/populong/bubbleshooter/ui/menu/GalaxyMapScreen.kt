package com.populong.bubbleshooter.ui.menu

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.populong.bubbleshooter.AppContainer
import com.populong.bubbleshooter.audio.Sfx
import com.populong.bubbleshooter.core.grid.Bubble
import com.populong.bubbleshooter.core.level.LevelCatalog
import com.populong.bubbleshooter.core.progress.SaveData
import com.populong.bubbleshooter.core.progress.highestUnlockedLevel
import com.populong.bubbleshooter.ui.theme.GalaxyTheme
import com.populong.bubbleshooter.ui.theme.Neon
import com.populong.bubbleshooter.ui.theme.NeonPanel
import kotlin.math.cos
import kotlin.math.sin

private const val TWO_PI = 6.2831855f
private val GALAXY_BOX_HEIGHT = 360.dp
private const val NODE_HIT_RADIUS_DP = 27f
private const val NODE_RADIUS_DP = 17f
private const val LOCKED_RADIUS_DP = 15f
private const val FRONTIER_RADIUS_DP = 21f
private const val BOSS_RADIUS_DP = 21f

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
                fx = (baseX + jitterX).coerceIn(0.07f, 0.93f),
                fy = (baseY + jitterY).coerceIn(0.1f, 0.9f),
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

/** A five-point star: 10 alternating outer/inner vertices, tip pointing up. */
private fun starPath(center: Offset, radius: Float): Path {
    val path = Path()
    val inner = radius * 0.45f
    for (k in 0 until 10) {
        val r = if (k % 2 == 0) radius else inner
        val angle = (Math.PI.toFloat() / 5f) * k - (Math.PI.toFloat() / 2f)
        val x = center.x + r * cos(angle)
        val y = center.y + r * sin(angle)
        if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

private fun DrawScope.drawStarRow(center: Offset, nodeRadius: Float, earned: Int) {
    val starRadius = nodeRadius * 0.32f
    val spacing = starRadius * 2.4f
    val y = center.y - nodeRadius - starRadius * 1.6f
    val startX = center.x - spacing
    for (s in 0 until 3) {
        val c = Offset(startX + spacing * s, y)
        if (s < earned) {
            drawPath(starPath(c, starRadius), color = Neon.gold, style = Fill)
        } else {
            drawPath(starPath(c, starRadius), color = Neon.gold, alpha = 0.28f, style = Stroke(width = 1.5f))
        }
    }
}

/**
 * The constellation-style level map: a vertically scrollable starfield of all
 * [LevelCatalog.GALAXY_COUNT] galaxies with sticky per-galaxy progress headers. Tapping an
 * unlocked node opens a preview dialog (level number, earned stars, mechanics, shot budget)
 * before starting — no accidental instant launches. Only visible items compose, so the map
 * never places 2000 nodes at once.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalaxyMapScreen(container: AppContainer, onPick: (Int) -> Unit, onBack: () -> Unit) {
    val save by container.save.save.collectAsState()
    val unlocked = save.highestUnlockedLevel()
    val totalStars = save.levels.values.sumOf { it.stars }

    var preview by remember { mutableStateOf<Int?>(null) }
    // Composed after App-level navigation handlers, so this wins while the dialog is open:
    // gesture-back closes the preview instead of leaving the map, matching the ✕/取消 buttons.
    BackHandler(enabled = preview != null) { preview = null }

    val initialGalaxy = ((unlocked - 1) / LevelCatalog.GALAXY_SIZE).coerceIn(0, LevelCatalog.GALAXY_COUNT - 1)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialGalaxy * 2)

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
        preview = level
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(Neon.spaceGradient)),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            NeonTopBar(container = container, title = "星图", onBack = onBack) {
                Text(text = "★ $totalStars", color = Neon.gold, fontSize = 16.sp)
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp,
                ),
            ) {
                for (galaxy in 1..LevelCatalog.GALAXY_COUNT) {
                    stickyHeader(key = "header-$galaxy") {
                        GalaxyHeader(galaxy = galaxy, save = save)
                    }
                    item(key = "galaxy-$galaxy") {
                        GalaxyItem(
                            galaxy = galaxy,
                            save = save,
                            unlocked = unlocked,
                            pulsePhase = pulsePhase,
                            onTapLevel = ::tapLevel,
                        )
                    }
                }
            }
        }

        preview?.let { level ->
            LevelPreviewDialog(
                container = container,
                level = level,
                earnedStars = (save.levels[level]?.stars ?: 0).coerceIn(0, 3),
                onStart = {
                    preview = null
                    onPick(level)
                },
                onDismiss = { preview = null },
            )
        }
    }
}

/** Sticky translucent header: galaxy title on the left, star/clear progress on the right. */
@Composable
private fun GalaxyHeader(galaxy: Int, save: SaveData) {
    val range = LevelCatalog.levelsInGalaxy(galaxy)
    val stars = range.sumOf { save.levels[it]?.stars ?: 0 }
    val cleared = range.count { (save.levels[it]?.stars ?: 0) >= 1 }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xCC0A1030))
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "第 $galaxy 星系 · ${GalaxyTheme.forGalaxy(galaxy).name}",
            color = GalaxyTheme.forGalaxy(galaxy).accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$stars/60 ★ · $cleared/20 关",
            color = Neon.textDim,
            fontSize = 12.sp,
        )
    }
}

/** The preview dialog shown before a level starts: stars, mechanics, budget, and 开始/取消. */
@Composable
private fun LevelPreviewDialog(
    container: AppContainer,
    level: Int,
    earnedStars: Int,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    val spec = remember(level) { LevelCatalog.spec(level) }
    val mechanics = remember(level) {
        val kinds = LinkedHashSet<String>()
        for (bubble in spec.initialGrid.cells.values) {
            when (bubble) {
                is Bubble.Stone -> kinds.add("石头")
                is Bubble.Ice -> kinds.add("冰冻")
                is Bubble.Fog -> kinds.add("迷雾")
                is Bubble.Chained -> kinds.add("锁链")
                is Bubble.Supernova -> kinds.add("超新星")
                is Bubble.Colored -> Unit
            }
        }
        if (kinds.isEmpty()) listOf("纯色关") else kinds.toList()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        NeonPanel(modifier = Modifier.padding(horizontal = 36.dp).clickable(enabled = false) {}) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "第 $level 关" + if (level % 20 == 0) " · 星系首领" else "",
                    color = if (level % 20 == 0) Neon.gold else Neon.cyan,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = buildString {
                        for (s in 0 until 3) append(if (s < earnedStars) "★" else "☆")
                    },
                    color = Neon.gold,
                    fontSize = 22.sp,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (name in mechanics) {
                        Text(
                            text = name,
                            color = Neon.textPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .background(Color(0x1AFFFFFF))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "弹药 ×${spec.shots}", color = Neon.textDim, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "取消",
                        color = Neon.textDim,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .clickable {
                                container.sfx.play(Sfx.UI_TAP)
                                onDismiss()
                            }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    )
                    Text(
                        text = "开始",
                        color = Neon.cyan,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color(0x334DE3FF))
                            .clickable {
                                container.sfx.play(Sfx.UI_TAP)
                                container.haptics.tick()
                                onStart()
                            }
                            .padding(horizontal = 22.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

/** One galaxy's constellation box: 20 level nodes, connecting lines, and tap hit-testing. */
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

            // A soft wash of the galaxy's own nebula palette, so scrolling the map
            // reads as traveling through differently-hued regions of space.
            val palette = GalaxyTheme.forGalaxy(galaxy)
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        palette.bgMid.copy(alpha = 0.0f),
                        palette.bgDeep.copy(alpha = 0.35f),
                        palette.bgMid.copy(alpha = 0.0f),
                    ),
                ),
                size = size,
            )

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
                val radius = when {
                    isBoss -> BOSS_RADIUS_DP.dp.toPx()
                    isFrontier -> FRONTIER_RADIUS_DP.dp.toPx()
                    isLocked -> LOCKED_RADIUS_DP.dp.toPx()
                    else -> NODE_RADIUS_DP.dp.toPx()
                }

                if (isBoss) {
                    drawHexagon(center, radius, color, alpha)
                } else {
                    drawCircle(color = color, radius = radius, center = center, alpha = alpha, style = Fill)
                }

                if (isFrontier) {
                    val pulse = (sin(pulsePhase) + 1f) / 2f
                    drawCircle(
                        color = Neon.mint,
                        radius = radius + 5f + pulse * 7f,
                        center = center,
                        alpha = 0.35f + 0.35f * pulse,
                        style = Stroke(width = 2.5f),
                    )
                }

                if (isCompleted) {
                    drawStarRow(center, radius, stars)
                }
            }
        }

        for (i in slots.indices) {
            val level = range.first + i
            val isLocked = (save.levels[level]?.stars ?: 0) < 1 && level != unlocked
            Text(
                text = "$level",
                color = if (isLocked) Neon.textDim else Neon.textPrimary,
                fontSize = 10.sp,
                modifier = Modifier.offset(
                    x = widthDp * slots[i].fx - 10.dp,
                    y = GALAXY_BOX_HEIGHT * slots[i].fy + 20.dp,
                ),
            )
        }
    }
}
