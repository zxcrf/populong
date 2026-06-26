package com.populong.bubbleshooter.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import com.populong.bubbleshooter.audio.SoundManager
import com.populong.bubbleshooter.engine.GameSurfaceView
import com.populong.bubbleshooter.mode.LevelData

class GameActivity : Activity() {
    private lateinit var gameSurfaceView: GameSurfaceView
    private lateinit var soundManager: SoundManager
    private lateinit var scores: ScoreRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        scores = ScoreRepository(this)
        val modeType = intent.getStringExtra("mode") ?: "endless"
        val levelNumber = intent.getIntExtra("level", 1)

        soundManager = SoundManager(this)
        soundManager.enabled = scores.isSoundEnabled()
        soundManager.init()

        gameSurfaceView = GameSurfaceView(this)
        gameSurfaceView.configure(
            mode = modeType,
            level = levelNumber,
            sound = soundManager,
            highScore = scores.getHighScore(),
            onEnd = { finalScore ->
                scores.setHighScore(finalScore)
            },
            onComplete = { level, finalScore ->
                scores.setHighScore(finalScore)
                val stars = gameSurfaceView.getController()?.starsEarned() ?: 0
                scores.setLevelStars(level, stars)
                if (level < LevelData.totalLevels()) {
                    scores.unlockLevel(level + 1)
                }
                runOnUiThread {
                    val nextLevel = level + 1
                    if (nextLevel <= LevelData.totalLevels()) {
                        val intent = Intent(this, GameActivity::class.java).apply {
                            putExtra("mode", "level")
                            putExtra("level", nextLevel)
                        }
                        startActivity(intent)
                    }
                    finish()
                }
            },
            onQuit = {
                runOnUiThread { finish() }
            }
        )
        setContentView(gameSurfaceView)
    }

    override fun onPause() {
        super.onPause()
        gameSurfaceView.pause()
    }

    override fun onResume() {
        super.onResume()
        gameSurfaceView.resume()
    }

    override fun onDestroy() {
        super.onDestroy()
        soundManager.release()
    }

    override fun onBackPressed() {
        gameSurfaceView.getController()?.let { ctrl ->
            if (ctrl.state is com.populong.bubbleshooter.engine.GameState.Paused) {
                ctrl.resume()
            } else {
                ctrl.pause()
            }
        }
    }
}
