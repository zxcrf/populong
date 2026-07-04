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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.populong.bubbleshooter.core.level.LevelCatalog
import com.populong.bubbleshooter.core.mode.GameMode
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
 * The galaxy map for level selection lands in a later phase; for now「关卡模式」jumps straight
 * into level 1.
 */
@Composable
fun MainMenuScreen(container: AppContainer, onPlay: (GameMode) -> Unit) {
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

    fun playAndGo(mode: GameMode) {
        container.sfx.play(Sfx.UI_TAP)
        container.haptics.tick()
        onPlay(mode)
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
            Spacer(modifier = Modifier.height(48.dp))

            NeonPanel(
                modifier = Modifier
                    .width(240.dp)
                    .clickable { playAndGo(GameMode.Level(LevelCatalog.spec(1))) },
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
                    .clickable { playAndGo(GameMode.Endless(emptySet())) },
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
                    .alpha(0.5f),
            ) {
                Text(
                    text = "每日挑战 · 敬请期待",
                    color = Neon.textDim,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
