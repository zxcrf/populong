package com.populong.bubbleshooter.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.populong.bubbleshooter.AppContainer
import com.populong.bubbleshooter.audio.Sfx
import com.populong.bubbleshooter.core.engine.GameInput
import com.populong.bubbleshooter.core.engine.Phase
import com.populong.bubbleshooter.core.grid.Vec2
import com.populong.bubbleshooter.core.mode.GameMode
import com.populong.bubbleshooter.render.BackgroundRenderer
import com.populong.bubbleshooter.render.BubbleSprites
import com.populong.bubbleshooter.render.FieldLayout
import com.populong.bubbleshooter.render.GameRenderer
import com.populong.bubbleshooter.ui.theme.Neon
import com.populong.bubbleshooter.ui.theme.NeonPanel
import kotlin.math.acos
import kotlinx.coroutines.isActive

private const val SHOOTER_RADIUS_DP = 48
private const val MOVEMENT_THRESHOLD_DP = 10
private const val TAP_TIME_MS = 200L
private const val PRECISION_HOLD_MS = 350L

/** ~0.5 degrees, in radians; the minimum aim-direction change before re-enqueuing [GameInput.AimAt]. */
private const val MIN_AIM_ANGLE_CHANGE_RAD = 0.008727f

/**
 * The active-game screen: owns a [GameSessionHolder] for [mode], drives its fixed-timestep loop
 * from the frame clock, renders it via [GameRenderer]/[BackgroundRenderer], and layers HUD,
 * pause, and result overlays on top.
 */
@Composable
fun GameScreen(mode: GameMode, container: AppContainer, onExit: () -> Unit) {
    var restartKey by remember(mode) { mutableStateOf(0) }
    val session = remember(mode, restartKey) {
        val seed = when (mode) {
            is GameMode.Level -> mode.spec.id.toLong()
            is GameMode.Daily -> mode.spec.id.toLong()
            is GameMode.Endless -> 20260704L
        }
        GameSessionHolder(mode, seed, container.sfx, container.haptics)
    }

    var paused by remember(session) { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) paused = true
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(session) {
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (paused) {
                    last = 0L
                } else {
                    if (last != 0L) session.advance(now - last)
                    last = now
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060B1E)),
    ) {
        val density = LocalDensity.current
        val evenCols = session.latestState.grid.evenCols
        val widthPx = with(density) { maxWidth.toPx() }
        val scale = widthPx / (2f * evenCols)
        val sprites = remember(scale) { BubbleSprites(radiusPx = scale) }

        val shooterRadiusPx = with(density) { SHOOTER_RADIUS_DP.dp.toPx() }
        val movementThresholdPx = with(density) { MOVEMENT_THRESHOLD_DP.dp.toPx() }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(session, scale) {
                    fun screenToWorld(pos: Offset): Vec2 {
                        val ceilingY = session.latestState.ceilingY
                        return Vec2(pos.x / scale, ceilingY + pos.y / scale)
                    }

                    fun originPx(): Offset {
                        val origin = session.latestState.shooterOrigin
                        val ceilingY = session.latestState.ceilingY
                        return Offset(origin.x * scale, (origin.y - ceilingY) * scale)
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downTimeMs = System.currentTimeMillis()
                        val isShooterTap = (down.position - originPx()).getDistance() <= shooterRadiusPx
                        var lastDir: Vec2? = null
                        var precisionActive = false
                        var moved = false

                        if (!isShooterTap) {
                            val dir = screenToWorld(down.position) - session.latestState.shooterOrigin
                            if (dir.y < -0.05f) {
                                lastDir = dir
                                session.enqueue(GameInput.AimAt(dir))
                            }
                        }

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break

                            if ((change.position - down.position).getDistance() > movementThresholdPx) moved = true

                            if (change.changedToUpIgnoreConsumed()) {
                                if (!isShooterTap && lastDir != null) {
                                    session.enqueue(GameInput.Fire)
                                } else if (isShooterTap && !moved && System.currentTimeMillis() - downTimeMs < TAP_TIME_MS) {
                                    session.enqueue(GameInput.Swap)
                                }
                                session.timeScale = 1f
                                session.enqueue(GameInput.Precision(false))
                                change.consume()
                                break
                            }

                            if (!isShooterTap) {
                                val dir = screenToWorld(change.position) - session.latestState.shooterOrigin
                                if (dir.y < -0.05f) {
                                    val changedEnough = lastDir == null || angleBetween(lastDir!!, dir) > MIN_AIM_ANGLE_CHANGE_RAD
                                    if (changedEnough) {
                                        lastDir = dir
                                        session.enqueue(GameInput.AimAt(dir))
                                    }
                                }
                                val heldMs = System.currentTimeMillis() - downTimeMs
                                if (heldMs > PRECISION_HOLD_MS && !precisionActive) {
                                    precisionActive = true
                                    session.timeScale = 0.25f
                                    session.enqueue(GameInput.Precision(true))
                                }
                            }
                            change.consume()
                        }
                    }
                },
        ) {
            session.frameTick.longValue // sole render-invalidation read
            BackgroundRenderer.draw(this, session.frameTick.longValue)
            val layout = FieldLayout(scale = scale, offsetX = 0f, offsetY = 0f)
            GameRenderer.draw(this, session, sprites, layout)
        }

        Column(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "分数 ${session.hud.score}",
                    color = Neon.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (session.hud.shotsLeft >= 0) {
                    Text(
                        text = "剩余 ${session.hud.shotsLeft}",
                        color = Neon.textDim,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
                Text(
                    text = "⏸",
                    color = Neon.textPrimary,
                    fontSize = 22.sp,
                    modifier = Modifier
                        .clickable {
                            container.sfx.play(Sfx.UI_TAP)
                            paused = true
                        }
                        .padding(4.dp),
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            val feverColor = if (session.hud.feverActive) Neon.magenta else Neon.gold
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(6.dp)
                    .background(Color(0x22FFFFFF), RoundedCornerShape(3.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = session.hud.feverMeter.coerceIn(0f, 1f))
                        .background(feverColor, RoundedCornerShape(3.dp)),
                )
            }
        }

        Text(
            text = "换",
            color = Neon.textDim,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 92.dp),
        )

        if (paused) {
            PauseOverlay(
                container = container,
                onResume = { paused = false },
                onRestart = {
                    paused = false
                    restartKey++
                },
                onExit = onExit,
            )
        }

        if (session.hud.phase == Phase.WON || session.hud.phase == Phase.LOST) {
            ResultOverlay(
                session = session,
                container = container,
                onRetry = { restartKey++ },
                onExit = onExit,
            )
        }
    }
}

private fun angleBetween(a: Vec2, b: Vec2): Float {
    val na = a.normalized()
    val nb = b.normalized()
    val dot = (na.x * nb.x + na.y * nb.y).coerceIn(-1f, 1f)
    return acos(dot)
}

@Composable
private fun PauseOverlay(
    container: AppContainer,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onExit: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000)),
        contentAlignment = Alignment.Center,
    ) {
        NeonPanel {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "已暂停", color = Neon.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                MenuButton("继续") {
                    container.sfx.play(Sfx.UI_TAP)
                    onResume()
                }
                Spacer(modifier = Modifier.height(8.dp))
                MenuButton("重新开始") {
                    container.sfx.play(Sfx.UI_TAP)
                    onRestart()
                }
                Spacer(modifier = Modifier.height(8.dp))
                MenuButton("退出") {
                    container.sfx.play(Sfx.UI_TAP)
                    onExit()
                }
            }
        }
    }
}

@Composable
private fun ResultOverlay(
    session: GameSessionHolder,
    container: AppContainer,
    onRetry: () -> Unit,
    onExit: () -> Unit,
) {
    val won = session.hud.phase == Phase.WON
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000)),
        contentAlignment = Alignment.Center,
    ) {
        NeonPanel {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (won) "过关！" else "差一点…",
                    color = if (won) Neon.mint else Neon.danger,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (won) {
                    val stars = session.finalStars ?: 0
                    Row {
                        repeat(3) { i ->
                            Text(
                                text = if (i < stars) "★" else "☆",
                                color = Neon.gold,
                                fontSize = 28.sp,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(text = "分数 ${session.hud.score}", color = Neon.textPrimary, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(16.dp))
                MenuButton("重试") {
                    container.sfx.play(Sfx.UI_TAP)
                    onRetry()
                }
                Spacer(modifier = Modifier.height(8.dp))
                MenuButton("返回") {
                    container.sfx.play(Sfx.UI_TAP)
                    onExit()
                }
            }
        }
    }
}

@Composable
private fun MenuButton(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = Neon.textPrimary,
        fontSize = 18.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .width(180.dp)
            .clickable(onClick = onClick)
            .background(Color(0x330A1030), RoundedCornerShape(10.dp))
            .padding(vertical = 10.dp),
    )
}
