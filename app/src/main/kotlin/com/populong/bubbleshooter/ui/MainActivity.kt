package com.populong.bubbleshooter.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.populong.bubbleshooter.R
import com.populong.bubbleshooter.mode.LevelData

class MainActivity : Activity() {
    private lateinit var scores: ScoreRepository
    private var showingLevels = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scores = ScoreRepository(this)
        showMainMenu()
    }

    override fun onResume() {
        super.onResume()
        if (showingLevels) showLevelSelect() else showMainMenu()
    }

    private fun showMainMenu() {
        showingLevels = false
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btn_endless).setOnClickListener {
            startGame("endless", 1)
        }
        findViewById<Button>(R.id.btn_levels).setOnClickListener {
            showLevelSelect()
        }

        val soundBtn = findViewById<Button>(R.id.btn_sound)
        updateSoundButton(soundBtn)
        soundBtn.setOnClickListener {
            scores.setSoundEnabled(!scores.isSoundEnabled())
            updateSoundButton(soundBtn)
        }

        val vibrationBtn = findViewById<Button>(R.id.btn_vibration)
        updateVibrationButton(vibrationBtn)
        vibrationBtn.setOnClickListener {
            scores.setVibrationEnabled(!scores.isVibrationEnabled())
            updateVibrationButton(vibrationBtn)
        }
    }

    private fun updateSoundButton(btn: Button) {
        btn.setText(if (scores.isSoundEnabled()) R.string.sound_on else R.string.sound_off)
    }

    private fun updateVibrationButton(btn: Button) {
        btn.setText(if (scores.isVibrationEnabled()) R.string.vibration_on else R.string.vibration_off)
    }

    private fun showLevelSelect() {
        showingLevels = true
        setContentView(R.layout.activity_level_select)

        val grid = findViewById<GridLayout>(R.id.level_grid)
        grid.removeAllViews()
        val maxLevel = scores.getMaxUnlockedLevel()

        for (i in 1..LevelData.totalLevels()) {
            val btn = Button(this).apply {
                text = "$i"
                setTextColor(Color.WHITE)
                textSize = 20f
                isEnabled = i <= maxLevel
                setBackgroundColor(if (i <= maxLevel) 0xFF4488FF.toInt() else 0xFF333355.toInt())

                val stars = scores.getLevelStars(i)
                if (stars > 0) {
                    text = "$i\n${"★".repeat(stars)}"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                }

                setOnClickListener { startGame("level", i) }
            }

            val params = GridLayout.LayoutParams().apply {
                width = dpToPx(90)
                height = dpToPx(90)
                setMargins(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            }
            grid.addView(btn, params)
        }

        findViewById<Button>(R.id.btn_back).setOnClickListener {
            showMainMenu()
        }
    }

    private fun startGame(mode: String, level: Int) {
        val intent = Intent(this, GameActivity::class.java).apply {
            putExtra("mode", mode)
            putExtra("level", level)
        }
        startActivity(intent)
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    override fun onBackPressed() {
        if (showingLevels) {
            showMainMenu()
        } else {
            super.onBackPressed()
        }
    }
}
