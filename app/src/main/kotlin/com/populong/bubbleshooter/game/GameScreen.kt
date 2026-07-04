package com.populong.bubbleshooter.game

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.populong.bubbleshooter.AppContainer
import com.populong.bubbleshooter.audio.Sfx
import com.populong.bubbleshooter.core.engine.GameConfig
import com.populong.bubbleshooter.core.engine.GameInput
import com.populong.bubbleshooter.core.engine.Phase
import com.populong.bubbleshooter.core.grid.Vec2
import com.populong.bubbleshooter.core.level.AimGuide
import com.populong.bubbleshooter.core.level.LevelCatalog
import com.populong.bubbleshooter.core.mode.GameMode
import com.populong.bubbleshooter.core.mode.Mutator
import com.populong.bubbleshooter.core.progress.Achievements
import com.populong.bubbleshooter.core.progress.CareerStats
import com.populong.bubbleshooter.core.progress.earnStardust
import com.populong.bubbleshooter.core.progress.withDailyCompleted
import com.populong.bubbleshooter.core.progress.withEndlessScore
import com.populong.bubbleshooter.core.progress.withLevelResult
import com.populong.bubbleshooter.core.progress.withRun
import com.populong.bubbleshooter.render.BackgroundRenderer
import com.populong.bubbleshooter.render.BubbleSprites
import com.populong.bubbleshooter.render.FieldLayout
import com.populong.bubbleshooter.render.GameRenderer
import com.populong.bubbleshooter.ui.theme.GalaxyTheme
import com.populong.bubbleshooter.ui.theme.Neon
import com.populong.bubbleshooter.ui.theme.NeonPanel
import java.util.Locale
import kotlin.math.acos
import kotlin.math.roundToInt
import kotlinx.coroutines.isActive

private const val PRECISION_HOLD_MS = 350L

/** Flat stardust bonus awarded for winning a level run, on top of the per-bubble-dropped rate. */
private const val LEVEL_WIN_STARDUST_BONUS = 25L

/** ~0.5 degrees, in radians; the minimum aim-direction change before re-enqueuing [GameInput.AimAt]. */
private const val MIN_AIM_ANGLE_CHANGE_RAD = 0.008727f

/** The field width (in evenCols) for [mode], mirroring [com.populong.bubbleshooter.core.engine.GameEngine]'s
 * own grid-building logic — needed up front (before a session/engine exists) to size the playfield. */
private fun evenColsOf(mode: GameMode): Int = when (mode) {
    is GameMode.Level -> mode.spec.evenCols
    is GameMode.Daily -> mode.spec.evenCols
    is GameMode.Endless -> if (Mutator.NARROW_FIELD in mode.mutators) 7 else 8
}

/**
 * The active-game screen: owns a [GameSessionHolder] for [mode], drives its fixed-timestep loop
 * from the frame clock, renders it via [GameRenderer]/[BackgroundRenderer], and layers HUD,
 * pause, and result overlays on top.
 */
@Composable
fun GameScreen(mode: GameMode, container: AppContainer, onExit: () -> Unit, onNext: (() -> Unit)? = null) {
    var restartKey by remember(mode) { mutableStateOf(0) }

    // Per-galaxy visual identity: Level uses the palette of the galaxy containing its level id;
    // Daily/Endless use fixed palettes (index 3 = aurora green, index 5 = ice blue deep space) so
    // those modes read as consistent, distinct identities rather than cycling with level progress.
    val palette = when (mode) {
        is GameMode.Level -> GalaxyTheme.forLevel(mode.spec.id)
        is GameMode.Daily -> GalaxyTheme.palettes[3]
        is GameMode.Endless -> GalaxyTheme.palettes[5]
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(palette.bgTop, palette.bgMid, palette.bgDeep))),
    ) {
        val density = LocalDensity.current
        val evenCols = evenColsOf(mode)
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val scale = widthPx / (2f * evenCols)
        val sprites = remember(scale) { BubbleSprites(radiusPx = scale) }
        val iconSprites = remember(density) { BubbleSprites(radiusPx = with(density) { 14.dp.toPx() }) }

        // Insets: content must never sit under the status bar (punch-hole/notch) or the gesture
        // nav bar. hudBottomPx/shooterScreenY are computed by construction to match the HUD
        // Column and swap-button overlay actually rendered below, so this is deterministic rather
        // than measured.
        val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val topInsetPx = with(density) { topInset.toPx() }
        val bottomInsetPx = with(density) { bottomInset.toPx() }
        val hudBottomPx = topInsetPx + with(density) { 56.dp.toPx() } // 48dp score/pause row + 8dp fever bar
        val ceilingScreenY = hudBottomPx + with(density) { 8.dp.toPx() }
        val shooterScreenY = heightPx - bottomInsetPx - with(density) { 76.dp.toPx() }

        // Never place the shooter closer than the default (lose-line) distance.
        val minShooterDistance = GameConfig().shooterDistance
        val shooterDistance = ((shooterScreenY - ceilingScreenY) / scale - 1f).coerceAtLeast(minShooterDistance)

        val session = remember(mode, restartKey, maxWidth, maxHeight) {
            val seed = when (mode) {
                is GameMode.Level -> mode.spec.id.toLong()
                is GameMode.Daily -> mode.spec.id.toLong()
                is GameMode.Endless -> 20260704L
            }
            val aimLength = when (mode) {
                is GameMode.Level -> AimGuide.lengthForLevel(mode.spec.id)
                // Fixed mid-difficulty guide length: the daily challenge doesn't sit on the
                // campaign's 1..2000 curve, so there's no natural level id to derive a length from.
                is GameMode.Daily -> AimGuide.lengthForLevel(60)
                is GameMode.Endless -> AimGuide.lengthForEndless(mode.mutators)
            }
            val config = GameConfig(shooterDistance = shooterDistance, aimLength = aimLength)
            val holder = GameSessionHolder(mode, seed, container.sfx, container.haptics, config)
            holder.onGameEnd = { won, score, stars ->
                container.save.update { saved ->
                    var updated = saved
                    when (mode) {
                        is GameMode.Level -> if (won) {
                            updated = updated.withLevelResult(mode.spec.id, stars, score)
                        }

                        is GameMode.Endless -> if (!won) {
                            updated = updated.withEndlessScore(score, mode.mutators, currentEpochDay())
                        }

                        is GameMode.Daily -> if (won) {
                            updated = updated.withDailyCompleted(currentEpochDay())
                        }
                    }

                    val mergedStats = updated.stats
                        .plusSessionDeltas(holder.sessionStats)
                        .withRun(holder.maxComboSeen, score)
                    var finalSave = updated.copy(stats = mergedStats)
                    val newlyEarned = Achievements.evaluate(finalSave)
                    if (newlyEarned.isNotEmpty()) {
                        finalSave = finalSave.copy(achievements = finalSave.achievements + newlyEarned)
                    }

                    // Stardust: 1 per bubble dropped this run, plus a flat bonus for winning a level.
                    val stardustEarned = holder.sessionStats.bubblesDropped +
                        if (mode is GameMode.Level && won) LEVEL_WIN_STARDUST_BONUS else 0L
                    finalSave = finalSave.earnStardust(stardustEarned)

                    finalSave
                }
            }
            holder
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

        // Routes both the system back gesture/button and the in-HUD pause button through the
        // same pause/confirm flow, so App.kt needs no back handling of its own for this screen.
        BackHandler(enabled = true) {
            when {
                session.hud.phase == Phase.WON || session.hud.phase == Phase.LOST -> onExit()
                paused -> paused = false
                else -> paused = true
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(session, scale, ceilingScreenY) {
                    fun screenToWorld(pos: Offset): Vec2 {
                        // The ceiling plane is glued to ceilingScreenY: unit y = ceilingY maps
                        // there, and everything else is relative to it (matches FieldLayout.toPx).
                        val ceilingY = session.latestState.ceilingY
                        return Vec2(pos.x / scale, ceilingY + (pos.y - ceilingScreenY) / scale)
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downTimeMs = System.currentTimeMillis()
                        var lastDir: Vec2? = null
                        var precisionActive = false

                        val initialDir = screenToWorld(down.position) - session.latestState.shooterOrigin
                        if (initialDir.y < -0.05f) {
                            lastDir = initialDir
                            session.enqueue(GameInput.AimAt(initialDir))
                        }

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break

                            if (change.changedToUpIgnoreConsumed()) {
                                if (lastDir != null) session.enqueue(GameInput.Fire)
                                session.timeScale = 1f
                                session.enqueue(GameInput.Precision(false))
                                change.consume()
                                break
                            }

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
                            change.consume()
                        }
                    }
                },
        ) {
            session.frameTick.longValue // sole render-invalidation read
            BackgroundRenderer.draw(this, session.frameTick.longValue, palette, session.hud.feverActive)
            // FieldLayout.toPx maps unit y relative to the LIVE ceiling plane (y - ceilingY), so
            // offsetY is simply the ceiling's fixed screen position. Endless row insertions move
            // ceilingY negative; bubbles then shift down while the ceiling stays glued to the HUD
            // (passing ceilingScreenY - ceilingY*scale here would double-count the shift and sink
            // the whole field — the bug seen on device).
            val layout = FieldLayout(scale = scale, offsetX = 0f, offsetY = ceilingScreenY)
            GameRenderer.draw(this, session, sprites, layout)
        }

        // --- HUD ------------------------------------------------------------------------------
        val feverColor = if (session.hud.feverActive) Neon.magenta else Neon.gold
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "分数 " + String.format(Locale.US, "%,d", session.hud.score),
                    color = Neon.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (session.hud.shotsLeft >= 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 12.dp),
                    ) {
                        Text(text = "剩余", color = Neon.textDim, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "●", color = Neon.gold, fontSize = 14.sp)
                        Text(text = " × ${session.hud.shotsLeft}", color = Neon.textDim, fontSize = 16.sp)
                    }
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable {
                            container.sfx.play(Sfx.UI_TAP)
                            paused = true
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "⏸", color = Neon.textPrimary, fontSize = 22.sp)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(8.dp)
                    .background(Color(0x22FFFFFF), RoundedCornerShape(4.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = session.hud.feverMeter.coerceIn(0f, 1f))
                        .background(feverColor, RoundedCornerShape(4.dp)),
                )
            }
        }

        // --- Swap controls (visual == hit zone) ------------------------------------------------
        // The shooter's actual rendered position (post-clamp), so these buttons always sit right
        // where the shooter is drawn, never at the pre-clamp target.
        val actualShooterScreenY = ceilingScreenY + shooterDistance * scale
        val shooterScreenX = session.latestState.shooterOrigin.x * scale
        val shooterVisualRadiusPx = scale * 1.6f
        val buttonGapPx = with(density) { 16.dp.toPx() }
        val canSwap = session.hud.phase == Phase.AIMING

        fun performSwap() {
            if (!canSwap) return
            container.sfx.play(Sfx.UI_TAP)
            container.haptics.tick()
            session.enqueue(GameInput.Swap)
        }

        val nextButtonSizePx = with(density) { 48.dp.toPx() }
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset {
                    IntOffset(
                        (shooterScreenX - shooterVisualRadiusPx - buttonGapPx - nextButtonSizePx).roundToInt(),
                        (actualShooterScreenY - nextButtonSizePx / 2f).roundToInt(),
                    )
                }
                .size(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0x330A1030))
                .clickable(enabled = canSwap) { performSwap() },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = iconSprites.forAmmo(session.hud.nextAmmo),
                contentDescription = "下一发",
                modifier = Modifier.size(36.dp),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC0A1030)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "⇄", color = Neon.cyan, fontSize = 9.sp)
            }
        }

        val pillButtonHeightPx = with(density) { 40.dp.toPx() }
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset {
                    IntOffset(
                        (shooterScreenX + shooterVisualRadiusPx + buttonGapPx).roundToInt(),
                        (actualShooterScreenY - pillButtonHeightPx / 2f).roundToInt(),
                    )
                }
                .height(40.dp)
                .widthIn(min = 64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0x330A1030))
                .border(width = 1.dp, color = Neon.cyan.copy(alpha = 0.4f), shape = RoundedCornerShape(20.dp))
                .clickable(enabled = canSwap) { performSwap() }
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "⇄ 交换", color = Neon.cyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

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
                mode = mode,
                container = container,
                onRetry = { restartKey++ },
                onExit = onExit,
                onNext = onNext,
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
    var confirmingExit by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000)),
        contentAlignment = Alignment.Center,
    ) {
        NeonPanel {
            if (!confirmingExit) {
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
                        confirmingExit = true
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "退出对局？本局进度将丢失",
                        color = Neon.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    MenuButton("继续") {
                        container.sfx.play(Sfx.UI_TAP)
                        confirmingExit = false
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
}

@Composable
private fun ResultOverlay(
    session: GameSessionHolder,
    mode: GameMode,
    container: AppContainer,
    onRetry: () -> Unit,
    onExit: () -> Unit,
    onNext: (() -> Unit)?,
) {
    val won = session.hud.phase == Phase.WON
    val showNext = won && onNext != null && mode is GameMode.Level && mode.spec.id < LevelCatalog.TOTAL

    // Slide/fade the whole panel in on first composition (this overlay is only ever composed
    // once per run-end, so `Unit` as the LaunchedEffect key is correct: it should fire exactly once).
    var panelShown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { panelShown = true }
    val panelOffsetY by animateFloatAsState(
        targetValue = if (panelShown) 0f else 60f,
        animationSpec = tween(durationMillis = 320),
        label = "resultPanelOffset",
    )
    val panelAlpha by animateFloatAsState(
        targetValue = if (panelShown) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "resultPanelAlpha",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000)),
        contentAlignment = Alignment.Center,
    ) {
        NeonPanel(
            modifier = Modifier.graphicsLayer {
                translationY = panelOffsetY
                alpha = panelAlpha
            },
        ) {
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
                    var starsShown by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) { starsShown = true }
                    Row {
                        repeat(3) { i ->
                            val starScale by animateFloatAsState(
                                targetValue = if (starsShown) 1f else 0f,
                                animationSpec = tween(durationMillis = 300, delayMillis = i * 250),
                                label = "star$i",
                            )
                            Text(
                                text = if (i < stars) "★" else "☆",
                                color = Neon.gold,
                                fontSize = 28.sp,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = starScale
                                    scaleY = starScale
                                },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(text = "分数 ${session.hud.score}", color = Neon.textPrimary, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(16.dp))
                if (showNext) {
                    MenuButton("下一关") {
                        container.sfx.play(Sfx.UI_TAP)
                        onNext?.invoke()
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
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

/** Adds a run's session-scoped delta counters into lifetime totals, field by field. Career maxima
 * ([CareerStats.maxCombo]) and per-run totals ([CareerStats.totalScore],
 * [CareerStats.levelsCompleted], [CareerStats.threeStarLevels]) are intentionally excluded here —
 * those are folded in separately via [withRun]/[withLevelResult]. */
private fun CareerStats.plusSessionDeltas(delta: CareerStats): CareerStats = copy(
    shotsFired = shotsFired + delta.shotsFired,
    shotsPopped = shotsPopped + delta.shotsPopped,
    bubblesPopped = bubblesPopped + delta.bubblesPopped,
    bubblesDropped = bubblesDropped + delta.bubblesDropped,
    bankShots = bankShots + delta.bankShots,
    bombsDetonated = bombsDetonated + delta.bombsDetonated,
    feversTriggered = feversTriggered + delta.feversTriggered,
)

/** The current UTC day number (days since the Unix epoch), matching [DailyLevel.forEpochDay]'s
 * day boundary and the epoch-day keys stored in [com.populong.bubbleshooter.core.progress.SaveData]. */
private fun currentEpochDay(): Long = System.currentTimeMillis() / 86_400_000L

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
