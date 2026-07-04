package com.populong.bubbleshooter.ui.menu

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.populong.bubbleshooter.AppContainer
import com.populong.bubbleshooter.audio.Sfx
import com.populong.bubbleshooter.ui.theme.Neon
import com.populong.bubbleshooter.ui.theme.NeonPanel
import kotlin.math.sin
import kotlin.random.Random

/** A single twinkling background star: normalized position, screen-space radius, and phase offset. */
private data class MenuStar(val x: Float, val y: Float, val radius: Float, val phase: Float)

private const val STAR_COUNT = 80
private const val TWO_PI = 6.2831855f

/**
 * The deep-space main menu: glowing title, a twinkling starfield, and mode-select panels.
 * 「关卡模式」opens the galaxy map, 「无尽模式」opens its mutator setup, and 「每日挑战」opens
 * the daily-challenge screen; each mode's [AppContainer.save] state (stars, streak) is reflected
 * directly on this screen. 「⚙ 设置」opens the audio/haptics + about screen via [onOpenSettings],
 * 「🏆 成就」opens the achievement list via [onOpenAchievements], 「📊 统计」opens the
 * career-stats screen via [onOpenStats], and 「✦ 图鉴」opens the constellation gallery via
 * [onOpenConstellations].
 */
@Composable
fun MainMenuScreen(
    container: AppContainer,
    onPlayLevel: () -> Unit,
    onPlayEndless: () -> Unit,
    onPlayDaily: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAchievements: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenConstellations: () -> Unit,
) {
    val save by container.save.save.collectAsState()
    val totalStars = remember(save) { save.levels.values.sumOf { it.stars } }

    val stars = remember {
        val rng = Random(20260704L)
        List(STAR_COUNT) {
            MenuStar(
                x = rng.nextFloat(),
                y = rng.nextFloat(),
                radius = 0.6f + rng.nextFloat() * 1.8f,
                phase = rng.nextFloat() * TWO_PI,
            )
        }
    }

    val transition = rememberInfiniteTransition(label = "menu-star-twinkle")
    val twinkle by transition.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec = infiniteRepeatable(tween(durationMillis = 6000, easing = LinearEasing)),
        label = "twinkle-phase",
    )

    fun playAndGo(action: () -> Unit) {
        container.sfx.play(Sfx.UI_TAP)
        container.haptics.tick()
        action()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(Neon.spaceGradient)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            for (star in stars) {
                val a = (0.35f + 0.65f * ((sin(twinkle + star.phase) + 1f) / 2f)).coerceIn(0f, 1f)
                drawCircle(
                    color = Color.White.copy(alpha = a),
                    radius = star.radius,
                    center = Offset(star.x * size.width, star.y * size.height),
                    style = Fill,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "泡泡星河",
                color = Neon.cyan,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Nebula Bubbles",
                color = Neon.textDim,
                fontSize = 16.sp,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "★ $totalStars",
                color = Neon.gold,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(36.dp))

            NeonPanel(
                modifier = Modifier
                    .width(240.dp)
                    .clickable { playAndGo(onPlayLevel) },
            ) {
                Text(
                    text = "关卡模式",
                    color = Neon.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            NeonPanel(
                modifier = Modifier
                    .width(240.dp)
                    .clickable { playAndGo(onPlayEndless) },
            ) {
                Text(
                    text = "无尽模式",
                    color = Neon.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            NeonPanel(
                modifier = Modifier
                    .width(240.dp)
                    .clickable { playAndGo(onPlayDaily) },
            ) {
                Text(
                    text = "每日挑战",
                    color = Neon.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }

            if (save.daily.streak > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "连续 ${save.daily.streak} 天",
                    color = Neon.gold,
                    fontSize = 13.sp,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MenuIconButton(label = "⚙ 设置", onClick = { playAndGo(onOpenSettings) })
                MenuIconButton(label = "🏆 成就", onClick = { playAndGo(onOpenAchievements) })
                MenuIconButton(label = "📊 统计", onClick = { playAndGo(onOpenStats) })
                MenuIconButton(label = "✦ 图鉴", onClick = { playAndGo(onOpenConstellations) })
            }
        }
    }
}

/**
 * A bottom icon-row tap target, at least 48dp tall (matching [NeonTopBar]'s back-button target)
 * but sized to its label's content width rather than a fixed 48dp square — a fixed-width square
 * was too narrow for a 4-character label plus emoji and forced it onto two lines.
 */
@Composable
private fun MenuIconButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .padding(horizontal = 8.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Neon.textDim,
            fontSize = 13.sp,
            maxLines = 1,
            softWrap = false,
        )
    }
}
