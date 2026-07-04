package com.populong.bubbleshooter

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.populong.bubbleshooter.audio.SfxPlayer
import com.populong.bubbleshooter.data.SaveRepository
import com.populong.bubbleshooter.haptics.HapticsManager

/**
 * Owns the process-lifetime services the game UI depends on: the synthesized sound-effect
 * player, the haptics wrapper, and their on/off toggles.
 *
 * Created once in [MainActivity.onCreate] and released in `onDestroy`. [soundEnabled] and
 * [hapticsEnabled] are the single runtime source of truth read by the hot audio/haptics paths
 * (a plain [MutableState] read, no flow collection in the hot path); they are seeded from the
 * persisted save's `settings` at construction and every change is mirrored back into [save] via
 * [setSound]/[setHaptics] so the choice survives across launches.
 */
class AppContainer(context: Context) {

    /** Persisted player progress (levels, endless highs, achievements, streaks, career stats). */
    val save: SaveRepository = SaveRepository(context.applicationContext)

    /** Whether sound effects should play. Seeded from the persisted save; mutate via [setSound]. */
    val soundEnabled: MutableState<Boolean> = mutableStateOf(save.save.value.settings.sound)

    /** Whether haptic feedback should fire. Seeded from the persisted save; mutate via [setHaptics]. */
    val hapticsEnabled: MutableState<Boolean> = mutableStateOf(save.save.value.settings.haptics)

    /** The synthesized SFX bank/player, gated by [soundEnabled]. */
    val sfx: SfxPlayer = SfxPlayer(enabled = { soundEnabled.value }).also { it.prepare() }

    /** The platform haptics wrapper, gated by [hapticsEnabled]. */
    val haptics: HapticsManager = HapticsManager(context.applicationContext, enabled = { hapticsEnabled.value })

    /** Updates the runtime sound toggle and persists the choice into [save]. */
    fun setSound(on: Boolean) {
        soundEnabled.value = on
        save.update { it.copy(settings = it.settings.copy(sound = on)) }
    }

    /** Updates the runtime haptics toggle and persists the choice into [save]. */
    fun setHaptics(on: Boolean) {
        hapticsEnabled.value = on
        save.update { it.copy(settings = it.settings.copy(haptics = on)) }
    }

    /** Releases underlying platform resources (audio tracks). Safe to call multiple times. */
    fun release() {
        sfx.release()
    }

    companion object {
        /** Convenience factory mirroring the constructor; kept for call-site clarity. */
        fun create(context: Context): AppContainer = AppContainer(context)
    }
}
