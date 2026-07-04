package com.populong.bubbleshooter.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Thin wrapper around the platform vibrator that exposes a few semantic haptic cues
 * (tick, click, heavy, and raw patterns) used throughout the game's UI and gameplay.
 *
 * All calls are guarded by [enabled] and by device vibrator availability, and every
 * platform call is defensive: a failure to vibrate must never crash the game.
 */
class HapticsManager(context: Context, private val enabled: () -> Boolean) {

    private companion object {
        const val TAG = "HapticsManager"
    }

    private val vibrator: Vibrator? = resolveVibrator(context)

    private fun resolveVibrator(context: Context): Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (t: Throwable) {
        Log.w(TAG, "Failed to resolve Vibrator", t)
        null
    }

    private fun canVibrate(): Boolean = enabled() && vibrator?.hasVibrator() == true

    /** A light, quick tap — used for minor UI feedback such as selection changes. */
    fun tick() {
        if (!canVibrate()) return
        val v = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                v.vibrate(VibrationEffect.createOneShot(10, 80))
            }
        } catch (t: Throwable) {
            Log.w(TAG, "tick() failed", t)
        }
    }

    /** A standard click — used for confirmations like firing or confirming a menu action. */
    fun click() {
        if (!canVibrate()) return
        val v = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                v.vibrate(VibrationEffect.createOneShot(20, 120))
            }
        } catch (t: Throwable) {
            Log.w(TAG, "click() failed", t)
        }
    }

    /** A heavy, strong pulse — used for big moments like chain pops or bombs. */
    fun heavy() {
        if (!canVibrate()) return
        val v = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else {
                v.vibrate(VibrationEffect.createOneShot(40, 255))
            }
        } catch (t: Throwable) {
            Log.w(TAG, "heavy() failed", t)
        }
    }

    /**
     * Plays a custom on/off waveform: [timings] in milliseconds paired with [amplitudes]
     * (0..255, or [VibrationEffect.DEFAULT_AMPLITUDE]). Does not repeat.
     */
    fun pattern(timings: LongArray, amplitudes: IntArray) {
        if (!canVibrate()) return
        val v = vibrator ?: return
        try {
            val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
            v.vibrate(effect)
        } catch (t: Throwable) {
            Log.w(TAG, "pattern() failed", t)
        }
    }
}
