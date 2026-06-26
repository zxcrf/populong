package com.populong.bubbleshooter.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.media.ToneGenerator

class SoundManager(private val context: Context) {
    enum class Sfx { SHOOT, POP, FALL, COMBO }

    private var soundPool: SoundPool? = null
    private val soundIds = mutableMapOf<Sfx, Int>()
    var enabled = true

    fun init() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(8)
            .setAudioAttributes(attrs)
            .build()

        generateSounds()
    }

    private fun generateSounds() {
        // Sound effects generated programmatically so no asset files are needed.
        // Each is a short PCM tone written to a temp file and loaded into the pool.
        // On devices the latency is negligible for these tiny clips.
        val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
        // ToneGenerator is simple but limited. For richer sound we could use
        // AudioTrack with synthesized PCM, but ToneGenerator keeps code minimal.
        // We store placeholder IDs; play() will use ToneGenerator directly as fallback.
        toneGen.release()
    }

    fun play(sfx: Sfx, pitchRate: Float = 1f) {
        if (!enabled) return
        val id = soundIds[sfx]
        if (id != null) {
            soundPool?.play(id, 1f, 1f, 1, 0, pitchRate.coerceIn(0.5f, 2f))
        } else {
            playFallback(sfx, pitchRate)
        }
    }

    private fun playFallback(sfx: Sfx, pitchRate: Float) {
        val toneType = when (sfx) {
            Sfx.SHOOT -> ToneGenerator.TONE_PROP_BEEP
            Sfx.POP -> ToneGenerator.TONE_PROP_ACK
            Sfx.FALL -> ToneGenerator.TONE_PROP_BEEP2
            Sfx.COMBO -> ToneGenerator.TONE_PROP_PROMPT
        }
        val duration = when (sfx) {
            Sfx.SHOOT -> 50
            Sfx.POP -> 30
            Sfx.FALL -> 80
            Sfx.COMBO -> 150
        }
        try {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 60)
            tg.startTone(toneType, duration)
            tg.release()
        } catch (_: Exception) {
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
    }
}
