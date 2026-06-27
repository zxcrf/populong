package com.populong.bubbleshooter.engine

import android.content.Context
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.populong.bubbleshooter.audio.HapticManager
import com.populong.bubbleshooter.audio.SoundManager
import com.populong.bubbleshooter.mode.EndlessMode
import com.populong.bubbleshooter.mode.GameMode
import com.populong.bubbleshooter.mode.LevelData
import com.populong.bubbleshooter.mode.LevelMode
import com.populong.bubbleshooter.render.GameRenderer

class GameSurfaceView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {

    private var gameLoop: GameLoop? = null
    private var gameThread: Thread? = null
    private var controller: GameController? = null
    private val renderer = GameRenderer()

    private var modeType: String = "endless"
    private var levelNumber: Int = 1
    private var soundManager: SoundManager? = null
    private var hapticManager: HapticManager? = null
    private var onGameEnd: (Int) -> Unit = {}
    private var onLevelComplete: (Int, Int) -> Unit = { _, _ -> }
    private var onQuit: () -> Unit = {}
    private var savedHighScore: Int = 0

    fun configure(
        mode: String,
        level: Int,
        sound: SoundManager,
        haptics: HapticManager,
        highScore: Int,
        onEnd: (Int) -> Unit,
        onComplete: (Int, Int) -> Unit,
        onQuit: () -> Unit
    ) {
        this.modeType = mode
        this.levelNumber = level
        this.soundManager = sound
        this.hapticManager = haptics
        this.savedHighScore = highScore
        this.onGameEnd = onEnd
        this.onLevelComplete = onComplete
        this.onQuit = onQuit
    }

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        startGame(holder)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopGame()
    }

    private fun startGame(holder: SurfaceHolder) {
        val sm = soundManager ?: return
        val gameMode: GameMode = when (modeType) {
            "level" -> LevelMode(LevelData.getLevel(levelNumber))
            else -> EndlessMode()
        }

        val ctrl = GameController(
            mode = gameMode,
            soundManager = sm,
            screenWidth = width,
            screenHeight = height,
            highScore = savedHighScore,
            onGameEnd = onGameEnd,
            onLevelComplete = onLevelComplete,
            onQuit = onQuit
        )
        ctrl.uiRenderer = renderer.uiRenderer
        ctrl.haptics = hapticManager
        controller = ctrl

        val loop = GameLoop(holder, ctrl, renderer)
        loop.attachChoreographer()
        gameLoop = loop
        val thread = Thread(loop, "GameLoop")
        gameThread = thread
        thread.start()
    }

    private fun stopGame() {
        gameLoop?.stop()
        try {
            gameThread?.join(1000)
        } catch (_: InterruptedException) {
        }
        gameLoop = null
        gameThread = null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val ctrl = controller ?: return true
        when (event.action) {
            MotionEvent.ACTION_DOWN -> ctrl.inputQueue.add(TouchEvent.Down(event.x, event.y))
            MotionEvent.ACTION_MOVE -> ctrl.inputQueue.add(TouchEvent.Move(event.x, event.y))
            MotionEvent.ACTION_UP -> ctrl.inputQueue.add(TouchEvent.Up(event.x, event.y))
        }
        return true
    }

    fun pause() {
        controller?.pause()
    }

    fun resume() {
        controller?.resume()
    }

    fun getController(): GameController? = controller
}
