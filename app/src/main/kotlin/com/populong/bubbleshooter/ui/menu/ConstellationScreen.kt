package com.populong.bubbleshooter.ui.menu

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.populong.bubbleshooter.AppContainer
import com.populong.bubbleshooter.audio.Sfx
import com.populong.bubbleshooter.core.progress.ConstellationDef
import com.populong.bubbleshooter.core.progress.Constellations
import com.populong.bubbleshooter.core.progress.unlockConstellation
import com.populong.bubbleshooter.ui.theme.Neon
import com.populong.bubbleshooter.ui.theme.NeonPanel

/** Dim gray used for a locked constellation's stars, matching [AchievementsScreen]'s locked badge tone. */
private val lockedStarColor = Color(0xFF5A6178)

/** Alpha applied to a locked constellation's title/description, matching [AchievementsScreen]'s convention. */
private const val LOCKED_CONTENT_ALPHA = 0.5f

/** How long the one-shot line-draw reveal takes once a constellation is unlocked. */
private const val LINE_DRAW_DURATION_MS = 900

/**
 * The constellation gallery: a scrollable list of every [Constellations.all] entry, each rendered
 * as a [NeonPanel] card with a hand-drawn star chart. Tapping an affordable, locked card's unlock
 * row spends stardust via [unlockConstellation] and plays a one-shot line-draw reveal animation.
 * The top bar's trailing slot shows the current stardust balance.
 */
@Composable
fun ConstellationScreen(container: AppContainer, onBack: () -> Unit) {
    val save by container.save.save.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(Neon.spaceGradient)),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            NeonTopBar(
                container = container,
                title = "星座图鉴",
                onBack = onBack,
                trailing = {
                    Text(
                        text = "✦ ${save.stardust}",
                        color = Neon.gold,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                },
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // Bottom system-bar inset merged into content padding, matching AchievementsScreen,
                // so the last card never sits under the gesture nav bar.
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 12.dp + navigationBarsBottomDp(),
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(items = Constellations.all, key = { it.id }) { def ->
                    val unlocked = def.id in save.unlockedConstellations
                    ConstellationCard(
                        def = def,
                        unlocked = unlocked,
                        stardust = save.stardust,
                        onUnlock = {
                            container.sfx.play(Sfx.UI_TAP)
                            container.haptics.click()
                            container.save.update { it.unlockConstellation(def.id, def.cost) ?: it }
                        },
                    )
                }
            }
        }
    }
}

/**
 * One constellation card: a [Canvas] star chart, title/description, and — while locked — an
 * unlock row priced in stardust. Unlocked stars are bright gold with cyan connecting lines drawn
 * segment by segment as [progress] advances from 0 to 1 on the unlock transition; locked stars are
 * dim gray points with no lines at all.
 */
@Composable
private fun ConstellationCard(
    def: ConstellationDef,
    unlocked: Boolean,
    stardust: Long,
    onUnlock: () -> Unit,
) {
    // One-shot reveal: sits at 0 while locked, animates up to 1 the moment `unlocked` flips true,
    // and simply stays at 1 afterward (recomposition with the same target is a no-op tween).
    val progress by animateFloatAsState(
        targetValue = if (unlocked) 1f else 0f,
        animationSpec = tween(durationMillis = LINE_DRAW_DURATION_MS),
        label = "constellation-reveal-${def.id}",
    )

    NeonPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
            ) {
                if (unlocked) {
                    val segmentCount = def.lines.size
                    def.lines.forEachIndexed { index, (fromIdx, toIdx) ->
                        val from = def.stars[fromIdx]
                        val to = def.stars[toIdx]
                        val start = Offset(from.first * size.width, from.second * size.height)
                        val end = Offset(to.first * size.width, to.second * size.height)

                        // Segment `index` owns the [index/n, (index+1)/n] slice of `progress`, so
                        // lines draw in order rather than all fading in together.
                        val segStart = index.toFloat() / segmentCount
                        val segEnd = (index + 1).toFloat() / segmentCount
                        val segFraction = ((progress - segStart) / (segEnd - segStart)).coerceIn(0f, 1f)
                        if (segFraction <= 0f) return@forEachIndexed

                        val drawnEnd = Offset(
                            x = start.x + (end.x - start.x) * segFraction,
                            y = start.y + (end.y - start.y) * segFraction,
                        )
                        drawLine(
                            color = Neon.cyan.copy(alpha = 0.4f),
                            start = start,
                            end = drawnEnd,
                            strokeWidth = 2.dp.toPx(),
                        )
                    }
                }

                for ((sx, sy) in def.stars) {
                    val center = Offset(sx * size.width, sy * size.height)
                    if (unlocked) {
                        drawCircle(color = Neon.gold.copy(alpha = 0.25f), radius = 8.dp.toPx(), center = center)
                        drawCircle(color = Neon.gold, radius = 3.dp.toPx(), center = center)
                    } else {
                        drawCircle(color = lockedStarColor, radius = 3.dp.toPx(), center = center)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(modifier = Modifier.alpha(if (unlocked) 1f else LOCKED_CONTENT_ALPHA)) {
                Text(
                    text = def.title,
                    color = Neon.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = def.description,
                    color = Neon.textDim,
                    fontSize = 13.sp,
                )
            }

            if (!unlocked) {
                Spacer(modifier = Modifier.height(10.dp))
                val affordable = stardust >= def.cost
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = affordable, onClick = onUnlock)
                        .background(Color(0x330A1030), RoundedCornerShape(10.dp))
                        .padding(vertical = 10.dp),
                ) {
                    Text(
                        text = "✦ ${def.cost} 解锁",
                        color = if (affordable) Neon.gold else Neon.danger.copy(alpha = 0.6f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
