package com.populong.bubbleshooter.engine

import android.graphics.Canvas
import android.graphics.PointF
import com.populong.bubbleshooter.audio.HapticManager
import com.populong.bubbleshooter.audio.SoundManager
import com.populong.bubbleshooter.effects.EffectsController
import com.populong.bubbleshooter.game.*
import com.populong.bubbleshooter.mode.GameMode
import com.populong.bubbleshooter.mode.LevelMode
import com.populong.bubbleshooter.mode.PostShotAction
import com.populong.bubbleshooter.render.UIRenderer
import java.util.Random
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class GameController(
    private val mode: GameMode,
    private val soundManager: SoundManager,
    private val screenWidth: Int,
    private val screenHeight: Int,
    var highScore: Int = 0,
    private val onGameEnd: (Int) -> Unit = {},
    private val onLevelComplete: (Int, Int) -> Unit = { _, _ -> },
    private val onQuit: () -> Unit = {}
) {
    val grid = BubbleGrid(GameConfig.GRID_COLUMNS, GameConfig.GRID_MAX_ROWS)
    val effects = EffectsController()
    private val collisionDetector = CollisionDetector()
    private val gridSnapper = GridSnapper()
    private val matchFinder = MatchFinder()
    private val floatingDetector = FloatingDetector()
    private val aimLine = AimLine()
    private val rng = Random()

    val inputQueue = ConcurrentLinkedQueue<TouchEvent>()

    // The UIRenderer used to draw overlays. Wired by the host so that touch
    // hit-testing uses the exact button rectangles produced during draw().
    // Both draw() and processInput() run on the same GameLoop thread, so the
    // rects are always populated by the time a tap is read.
    var uiRenderer: UIRenderer? = null

    // Optional haptic feedback, wired by the host (null in unit tests).
    var haptics: HapticManager? = null

    val bubbleRadius = screenWidth * GameConfig.BUBBLE_RADIUS_RATIO
    val gridOffsetY = screenHeight * GameConfig.GRID_TOP_MARGIN_RATIO
    val shooterX = screenWidth / 2f
    val shooterY = screenHeight * (1f - GameConfig.SHOOTER_BOTTOM_MARGIN_RATIO)

    var state: GameState = GameState.Aiming
        private set
    var score = 0
        private set
    private var shotCount = 0
    private var consecutiveMisses = 0
    private var comboCount = 0

    var currentColor = BubbleColor.RED
        private set
    var currentIsRainbow = false
        private set
    var nextColor = BubbleColor.BLUE
        private set
    var nextIsRainbow = false
        private set

    var activeProjectile: Projectile? = null
        private set
    var aimSegments = emptyList<AimLine.Segment>()
        private set
    private var aimAngle = -Math.PI.toFloat() / 2f
    private var isTouching = false

    val isAiming: Boolean get() = state is GameState.Aiming && isTouching

    private var resolvePhase = 0
    private var resolveTimer = 0f
    private var pendingMatched = emptySet<BubbleGrid.GridCell>()
    private var pendingFloating = emptySet<BubbleGrid.GridCell>()

    init {
        mode.initializeGrid(grid, rng)
        currentColor = mode.chooseNextColor(grid, rng)
        nextColor = mode.chooseNextColor(grid, rng)
        currentIsRainbow = false
        nextIsRainbow = false
    }

    fun processInput() {
        while (inputQueue.isNotEmpty()) {
            val event = inputQueue.poll() ?: break
            handleTouch(event)
        }
    }

    private fun handleTouch(event: TouchEvent) {
        when (state) {
            is GameState.Aiming -> handleAimingTouch(event)
            is GameState.Paused -> handlePausedTouch(event)
            is GameState.GameOver -> handleGameOverTouch(event)
            is GameState.LevelComplete -> handleLevelCompleteTouch(event)
            else -> {}
        }
    }

    private fun handleAimingTouch(event: TouchEvent) {
        when (event) {
            is TouchEvent.Down -> {
                if (isPauseButton(event.x, event.y)) {
                    pause()
                    return
                }
                isTouching = true
                updateAim(event.x, event.y)
            }
            is TouchEvent.Move -> {
                if (isTouching) updateAim(event.x, event.y)
            }
            is TouchEvent.Up -> {
                if (isTouching) {
                    isTouching = false
                    fire()
                }
            }
        }
    }

    private fun isPauseButton(x: Float, y: Float): Boolean {
        return uiRenderer?.pauseButtonRect?.contains(x, y) == true
    }

    private fun handlePausedTouch(event: TouchEvent) {
        if (event !is TouchEvent.Up) return
        val ui = uiRenderer ?: return
        when {
            ui.resumeButtonRect.contains(event.x, event.y) -> resume()
            ui.quitButtonRect.contains(event.x, event.y) -> onQuit()
        }
    }

    private fun handleGameOverTouch(event: TouchEvent) {
        if (event !is TouchEvent.Up) return
        val ui = uiRenderer ?: return
        when {
            ui.retryButtonRect.contains(event.x, event.y) -> restart()
            ui.menuButtonRect.contains(event.x, event.y) -> onQuit()
        }
    }

    private fun handleLevelCompleteTouch(event: TouchEvent) {
        if (event !is TouchEvent.Up) return
        val ui = uiRenderer ?: return
        when {
            ui.nextLevelButtonRect.contains(event.x, event.y) -> {
                if (mode is LevelMode) onLevelComplete(mode.levelNumber(), score)
            }
            ui.menuButtonRect.contains(event.x, event.y) -> onQuit()
        }
    }

    private fun updateAim(touchX: Float, touchY: Float) {
        if (touchY >= shooterY - 20f) return
        val dx = touchX - shooterX
        val dy = touchY - shooterY
        aimAngle = atan2(dy, dx)
        aimAngle = aimAngle.coerceIn(-GameConfig.MAX_AIM_ANGLE, -GameConfig.MIN_AIM_ANGLE)

        aimSegments = aimLine.calculate(
            PointF(shooterX, shooterY), aimAngle,
            0f, screenWidth.toFloat(), gridOffsetY,
            bubbleRadius, grid, gridOffsetY
        )
    }

    private fun fire() {
        val speed = GameConfig.PROJECTILE_SPEED
        activeProjectile = Projectile(
            x = shooterX, y = shooterY,
            dx = cos(aimAngle) * speed,
            dy = sin(aimAngle) * speed,
            color = currentColor,
            isRainbow = currentIsRainbow
        )
        state = GameState.Shooting(activeProjectile!!)
        soundManager.play(SoundManager.Sfx.SHOOT)
        haptics?.vibrate(HapticManager.Cue.SHOOT)
        aimSegments = emptyList()
    }

    fun update(deltaMs: Float) {
        effects.update(deltaMs)

        when (val s = state) {
            is GameState.Shooting -> updateShooting(deltaMs)
            is GameState.Resolving -> updateResolving(deltaMs)
            is GameState.PushingDown -> updatePushing(deltaMs)
            else -> {}
        }
    }

    private fun updateShooting(deltaMs: Float) {
        val proj = activeProjectile ?: return
        proj.update(deltaMs, 0f, screenWidth.toFloat(), bubbleRadius)

        val collision = collisionDetector.check(proj, grid, bubbleRadius, gridOffsetY)
        if (collision != null) {
            val snapCell = when (collision) {
                is CollisionResult.Ceiling ->
                    gridSnapper.snapCeiling(proj, grid, bubbleRadius, gridOffsetY)
                is CollisionResult.BubbleHit ->
                    gridSnapper.findSnapCell(proj, collision.cell, grid, bubbleRadius, gridOffsetY)
            }

            grid.set(snapCell, proj.toBubble())
            activeProjectile = null

            val matched = matchFinder.findMatches(snapCell, grid)
            if (matched.isNotEmpty()) {
                consecutiveMisses = 0
                comboCount++
                pendingMatched = matched
                for (cell in matched) {
                    val pos = grid.cellToPixel(cell, bubbleRadius, gridOffsetY)
                    val bubble = grid.get(cell)
                    if (bubble != null) {
                        effects.emitBubblePop(pos.x, pos.y, bubble.color, matched.size)
                    }
                }
                soundManager.play(SoundManager.Sfx.POP)
                haptics?.vibrate(HapticManager.Cue.POP, matched.size)

                grid.removeAll(matched)
                pendingFloating = floatingDetector.findFloating(grid)

                if (pendingFloating.isNotEmpty()) {
                    effects.spawnFallingBubbles(pendingFloating, grid, bubbleRadius, gridOffsetY)
                    effects.triggerShake(pendingFloating.size)
                    grid.removeAll(pendingFloating)
                    soundManager.play(SoundManager.Sfx.FALL)
                    haptics?.vibrate(HapticManager.Cue.FALL, pendingFloating.size)
                }

                val multiplier = comboCount.coerceAtMost(GameConfig.MAX_COMBO_MULTIPLIER)
                val pts = mode.calculateScore(matched.size, pendingFloating.size, multiplier)
                score += pts
                val popCenter = grid.cellToPixel(snapCell, bubbleRadius, gridOffsetY)
                effects.addScorePopup(popCenter.x, popCenter.y, pts)

                val isBigClear = matched.size + pendingFloating.size >= 6
                if (comboCount >= 2) {
                    effects.addComboPopup(
                        shooterX, popCenter.y - bubbleRadius * 3f, comboCount
                    )
                }
                if (comboCount >= 2 || isBigClear) {
                    val pitch = (1f + (comboCount - 1) * 0.08f).coerceIn(1f, 1.6f)
                    soundManager.play(SoundManager.Sfx.COMBO, pitch)
                }
            } else {
                consecutiveMisses++
                comboCount = 0
            }

            resolvePhase = 0
            resolveTimer = 0f
            state = GameState.Resolving(pendingMatched, pendingFloating)
        }
    }

    private fun updateResolving(deltaMs: Float) {
        resolveTimer += deltaMs
        if (resolveTimer >= GameConfig.RESOLVE_POP_DURATION_MS && !effects.hasActiveEffects()) {
            shotCount++
            resolveTimer = 0f

            if (mode.isLevelComplete(grid)) {
                if (score > highScore) highScore = score
                onGameEnd(score)
                haptics?.vibrate(HapticManager.Cue.WIN)
                state = GameState.LevelComplete
                return
            }

            if (mode.isGameOver(grid)) {
                if (score > highScore) highScore = score
                onGameEnd(score)
                state = GameState.GameOver
                return
            }

            val action = mode.onShotResolved(grid, pendingMatched.size, pendingFloating.size, shotCount)
            when (action) {
                is PostShotAction.PushRowsDown -> {
                    grid.shiftDown()
                    if (mode.isGameOver(grid)) {
                        if (score > highScore) highScore = score
                        onGameEnd(score)
                        state = GameState.GameOver
                        return
                    }
                }
                is PostShotAction.AddNewRow -> {
                    val bubbles = action.colors.map { c -> c?.let { Bubble(it) } }
                    grid.addRow(bubbles)
                    if (mode.isGameOver(grid)) {
                        if (score > highScore) highScore = score
                        onGameEnd(score)
                        state = GameState.GameOver
                        return
                    }
                }
                is PostShotAction.Nothing -> {}
            }

            advanceBubble()
            state = GameState.Aiming
        }
    }

    private fun updatePushing(deltaMs: Float) {
        state = GameState.Aiming
    }

    private fun advanceBubble() {
        currentColor = nextColor
        currentIsRainbow = nextIsRainbow

        if (mode.shouldSpawnRainbow(consecutiveMisses)) {
            nextColor = BubbleColor.RED
            nextIsRainbow = true
        } else {
            nextColor = mode.chooseNextColor(grid, rng)
            nextIsRainbow = false
        }
    }

    fun pause() {
        if (state is GameState.Aiming || state is GameState.Shooting) {
            GameState.Paused.previousState = state
            state = GameState.Paused
        }
    }

    fun resume() {
        if (state is GameState.Paused) {
            state = GameState.Paused.previousState
        }
    }

    fun restart() {
        grid.clear()
        score = 0
        shotCount = 0
        consecutiveMisses = 0
        comboCount = 0
        activeProjectile = null
        aimSegments = emptyList()
        mode.initializeGrid(grid, rng)
        currentColor = mode.chooseNextColor(grid, rng)
        nextColor = mode.chooseNextColor(grid, rng)
        currentIsRainbow = false
        nextIsRainbow = false
        state = GameState.Aiming
    }

    fun starsEarned(): Int =
        if (mode is LevelMode) mode.starsEarned(score) else 0

    fun levelNumber(): Int =
        if (mode is LevelMode) mode.levelNumber() else 0

    fun drawOverlay(canvas: Canvas, uiRenderer: UIRenderer) {
        when (state) {
            is GameState.Paused -> uiRenderer.drawPauseOverlay(canvas)
            is GameState.GameOver -> uiRenderer.drawGameOverOverlay(canvas, score, highScore)
            is GameState.LevelComplete -> {
                val stars = if (mode is LevelMode) (mode as LevelMode).starsEarned(score) else 0
                uiRenderer.drawLevelCompleteOverlay(canvas, score, stars)
            }
            else -> {}
        }
    }
}
