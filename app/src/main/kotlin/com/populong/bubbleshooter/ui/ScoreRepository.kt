package com.populong.bubbleshooter.ui

import android.content.Context

class ScoreRepository(context: Context) {
    private val prefs = context.getSharedPreferences("bubble_scores", Context.MODE_PRIVATE)

    fun getHighScore(): Int = prefs.getInt("high_score", 0)
    fun setHighScore(score: Int) {
        if (score > getHighScore()) {
            prefs.edit().putInt("high_score", score).apply()
        }
    }

    fun getLevelStars(level: Int): Int = prefs.getInt("level_${level}_stars", 0)
    fun setLevelStars(level: Int, stars: Int) {
        if (stars > getLevelStars(level)) {
            prefs.edit().putInt("level_${level}_stars", stars).apply()
        }
    }

    fun getMaxUnlockedLevel(): Int = prefs.getInt("max_level", 1)
    fun unlockLevel(level: Int) {
        if (level > getMaxUnlockedLevel()) {
            prefs.edit().putInt("max_level", level).apply()
        }
    }

    fun isSoundEnabled(): Boolean = prefs.getBoolean("sound_enabled", true)
    fun setSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("sound_enabled", enabled).apply()
    }
}
