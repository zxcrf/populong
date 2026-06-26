package com.populong.bubbleshooter.engine

import android.graphics.Canvas
import android.graphics.PointF
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
        return x > screenWidth - 100f && y < 80f
    }

    private fun handlePausedTouch(event: TouchEvent) {
        if (event !is TouchEvent.Up) return
        val uiRenderer = UIRenderer()
        if (uiRenderer.resumeButtonRect.contains(event.x, event.y) ||
            event.y < screenHeight / 2f - 100f) {
            resume()
        }
        if (event.y > screenHeight / 2f + 80f) {
            onQuit()
        }
    }

    private fun handleGameOverTouch(event: TouchEvent) {
        if (event !is TouchEvent.Up) return
        val cy = screenHeight / 2f
        if (event.y in cy..(cy + 100f)) {
            restart()
        } else if (event.y > cy + 100f) {
            onQuit()
        }
    }

    private fun handleLevelCompleteTouch(event: TouchEvent) {
        if (event !is TouchEvent.Up) return
        val cy = screenHeight / 2f
        if (event.y in (cy + 20f)..(cy + 100f)) {
            if (mode is LevelMode) {
                onLevelComplete(mode.levelNumber(), score)
            }
        } else if (event.y > cy + 100f) {
            onQuit()
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
                pendingMatched = matched
                for (cell in matched) {
                    val pos = grid.cellToPixel(cell, bubbleRadius, gridOffsetY)
                    val bubble = grid.get(cell)
                    if (bubble != null) effects.emitBubblePop(pos.x, pos.y, bubble.color)
                }
                soundManager.play(SoundManager.Sfx.POP)

                grid.removeAll(matched)
                pendingFloating = floatingDetector.findFloating(grid)

                if (pendingFloating.isNotEmpty()) {
                    effects.spawnFallingBubbles(pendingFloating, grid, bubbleRadius, gridOffsetY)
                    effects.triggerShake(pendingFloating.size)
                    grid.removeAll(pendingFloating)
                    soundManager.play(SoundManager.Sfx.FALL)
                }

                val pts = mode.calculateScore(matched.size, pendingFloating.size, 1)
                score += pts
                val popCenter = grid.cellToPixel(snapCell, bubbleRadius, gridOffsetY)
                effects.addScorePopup(popCenter.x, popCenter.y, pts)
                if (matched.size + pendingFloating.size >= 6) {
                    soundManager.play(SoundManager.Sfx.COMBO)
                }
            } else {
                consecutiveMisses++
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
