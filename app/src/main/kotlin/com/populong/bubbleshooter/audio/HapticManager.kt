package com.populong.bubbleshooter.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Light-touch haptic feedback for key game events. Uses VibrationEffect on
 * API 26+ (with amplitude control) and falls back to the deprecated
 * Vibrator.vibrate(long) on API 24-25 to honour the project's minSdk of 24.
 */
class HapticManager(private val context: Context) {
    enum class Cue { SHOOT, POP, FALL, WIN }

    var enabled = true

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                    as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /** [intensity] scales POP feedback by how many bubbles were cleared. */
    fun vibrate(cue: Cue, intensity: Int = 1) {
        if (!enabled) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return

        val ms = when (cue) {
            Cue.SHOOT -> 12L
            Cue.POP -> (18L + intensity * 4L).coerceAtMost(60L)
            Cue.FALL -> 30L
            Cue.WIN -> 120L
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amplitude = when (cue) {
                Cue.SHOOT -> 60
                Cue.POP -> (90 + intensity * 15).coerceAtMost(255)
                Cue.FALL -> 140
                Cue.WIN -> 200
            }
            try {
                v.vibrate(VibrationEffect.createOneShot(ms, amplitude))
            } catch (_: Exception) {
            }
        } else {
            try {
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            } catch (_: Exception) {
            }
        }
    }
}
