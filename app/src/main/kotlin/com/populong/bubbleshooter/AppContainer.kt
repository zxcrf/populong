package com.populong.bubbleshooter

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.populong.bubbleshooter.audio.SfxPlayer
import com.populong.bubbleshooter.haptics.HapticsManager

/**
 * Owns the process-lifetime services the game UI depends on: the synthesized sound-effect
 * player, the haptics wrapper, and their on/off toggles.
 *
 * Created once in [MainActivity.onCreate] and released in `onDestroy`. Toggle state defaults
 * to enabled; persisting the user's choice across launches lands in a later phase.
 */
class AppContainer(context: Context) {

    /** Whether sound effects should play. */
    val soundEnabled: MutableState<Boolean> = mutableStateOf(true)

    /** Whether haptic feedback should fire. */
    val hapticsEnabled: MutableState<Boolean> = mutableStateOf(true)

    /** The synthesized SFX bank/player, gated by [soundEnabled]. */
    val sfx: SfxPlayer = SfxPlayer(enabled = { soundEnabled.value }).also { it.prepare() }

    /** The platform haptics wrapper, gated by [hapticsEnabled]. */
    val haptics: HapticsManager = HapticsManager(context.applicationContext, enabled = { hapticsEnabled.value })

    /** Releases underlying platform resources (audio tracks). Safe to call multiple times. */
    fun release() {
        sfx.release()
    }

    companion object {
        /** Convenience factory mirroring the constructor; kept for call-site clarity. */
        fun create(context: Context): AppContainer = AppContainer(context)
    }
}
