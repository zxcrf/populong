package com.populong.bubbleshooter.audio

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Pure-Kotlin PCM synthesis helpers. Everything here is plain math over [ShortArray] buffers
 * of 16-bit mono samples at [SAMPLE_RATE] Hz; no Android classes are involved so the clip bank
 * can be built off the main thread with only `kotlin.math`.
 */
object SoundSynth {

    /** Sample rate used for all generated clips, in Hz. */
    const val SAMPLE_RATE = 44100

    private const val MAX_AMPLITUDE = 32767.0

    /**
     * Generates a phase-continuous sine wave that linearly glides from [freqStart] to
     * [freqEnd] over [durationMs] milliseconds, at peak amplitude [amp] (0f..1f).
     */
    fun sine(freqStart: Float, freqEnd: Float, durationMs: Int, amp: Float): ShortArray {
        val sampleCount = samplesFor(durationMs)
        val out = ShortArray(sampleCount)
        val clampedAmp = amp.coerceIn(0f, 1f)
        var phase = 0.0
        for (i in 0 until sampleCount) {
            val t = if (sampleCount <= 1) 0.0 else i.toDouble() / sampleCount
            val freq = freqStart + (freqEnd - freqStart) * t
            phase += 2.0 * PI * freq / SAMPLE_RATE
            out[i] = (sin(phase) * clampedAmp * MAX_AMPLITUDE).toInt().toShort()
        }
        return out
    }

    /**
     * Generates deterministic pseudo-random noise using a simple linear congruential
     * generator seeded by [seed], at peak amplitude [amp] (0f..1f).
     */
    fun noise(durationMs: Int, amp: Float, seed: Long = 42L): ShortArray {
        val sampleCount = samplesFor(durationMs)
        val out = ShortArray(sampleCount)
        val clampedAmp = amp.coerceIn(0f, 1f)
        var state = seed
        for (i in 0 until sampleCount) {
            // Numerical Recipes LCG constants.
            state = (state * 6364136223846793005L + 1442695040888963407L)
            // Take the high bits for better randomness, map to [-1, 1).
            val bits = (state ushr 40) and 0xFFFFFF
            val unit = (bits.toDouble() / 0xFFFFFF) * 2.0 - 1.0
            out[i] = (unit * clampedAmp * MAX_AMPLITUDE).toInt().toShort()
        }
        return out
    }

    /**
     * Applies a linear attack, linear decay to [sustain] level, hold, then linear release
     * envelope to [samples]. Stage durations beyond the buffer length are truncated.
     */
    fun adsr(samples: ShortArray, attackMs: Int, decayMs: Int, sustain: Float, releaseMs: Int): ShortArray {
        val total = samples.size
        if (total == 0) return samples
        val attackSamples = min(samplesFor(attackMs), total)
        val decaySamples = min(samplesFor(decayMs), total - attackSamples)
        val releaseSamples = min(samplesFor(releaseMs), total - attackSamples - decaySamples)
        val sustainSamples = total - attackSamples - decaySamples - releaseSamples
        val clampedSustain = sustain.coerceIn(0f, 1f)

        val out = ShortArray(total)
        var index = 0

        for (i in 0 until attackSamples) {
            val gain = if (attackSamples <= 1) 1.0 else i.toDouble() / attackSamples
            out[index] = (samples[index] * gain).toInt().toShort()
            index++
        }
        for (i in 0 until decaySamples) {
            val t = if (decaySamples <= 1) 1.0 else i.toDouble() / decaySamples
            val gain = 1.0 + (clampedSustain - 1.0) * t
            out[index] = (samples[index] * gain).toInt().toShort()
            index++
        }
        for (i in 0 until sustainSamples) {
            out[index] = (samples[index] * clampedSustain).toInt().toShort()
            index++
        }
        for (i in 0 until releaseSamples) {
            val t = if (releaseSamples <= 1) 1.0 else i.toDouble() / releaseSamples
            val gain = clampedSustain * (1.0 - t)
            out[index] = (samples[index] * gain).toInt().toShort()
            index++
        }
        return out
    }

    /** Sums all [clips] sample-by-sample and hard-clips to 16-bit range. Result length = longest clip. */
    fun mix(vararg clips: ShortArray): ShortArray {
        if (clips.isEmpty()) return ShortArray(0)
        val length = clips.maxOf { it.size }
        val out = ShortArray(length)
        for (i in 0 until length) {
            var sum = 0
            for (clip in clips) {
                if (i < clip.size) sum += clip[i]
            }
            out[i] = sum.coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    /** Concatenates [clips] end to end in order. */
    fun concat(vararg clips: ShortArray): ShortArray {
        val length = clips.sumOf { it.size }
        val out = ShortArray(length)
        var offset = 0
        for (clip in clips) {
            System.arraycopy(clip, 0, out, offset, clip.size)
            offset += clip.size
        }
        return out
    }

    /**
     * Naively resamples [base] by [semitones] using nearest-neighbour lookup, i.e. plays it
     * back faster/slower to shift pitch. Good enough for a short combo ladder, not audio-grade.
     */
    fun pitched(base: ShortArray, semitones: Int): ShortArray {
        if (base.isEmpty() || semitones == 0) return base.copyOf()
        val ratio = Math.pow(2.0, semitones / 12.0)
        val newLength = max(1, (base.size / ratio).toInt())
        val out = ShortArray(newLength)
        for (i in 0 until newLength) {
            val sourceIndex = (i * ratio).toInt().coerceIn(0, base.size - 1)
            out[i] = base[sourceIndex]
        }
        return out
    }

    private fun samplesFor(durationMs: Int): Int =
        max(0, (SAMPLE_RATE.toLong() * durationMs / 1000L).toInt())
}

/** Identifiers for every synthesized sound effect in the game. */
enum class Sfx {
    FIRE,
    BOUNCE,
    POP0,
    POP1,
    POP2,
    POP3,
    POP4,
    POP5,
    POP6,
    POP7,
    LAND,
    FALL,
    BANK,
    FEVER_START,
    ICE_CRACK,
    CHAIN_BREAK,
    BOMB,
    WIN,
    LOSE,
    UI_TAP,
    SWAP,
}

/** Builds the full set of synthesized clips used by [Sfx]. */
object SfxBank {

    private val popLadder = listOf(Sfx.POP0, Sfx.POP1, Sfx.POP2, Sfx.POP3, Sfx.POP4, Sfx.POP5, Sfx.POP6, Sfx.POP7)

    /** Combo index (0-based) to the [Sfx] pop clip to play, clamped to the top of the ladder. */
    fun popClip(combo: Int): Sfx = popLadder[combo.coerceIn(0, popLadder.size - 1)]

    /** Synthesizes every clip. Safe to call off the main thread; pure CPU math, no I/O. */
    fun build(): Map<Sfx, ShortArray> {
        val result = mutableMapOf<Sfx, ShortArray>()

        // FIRE: 120ms noise whoosh with fast decay, plus a quiet rising sine underlay.
        val fireNoise = SoundSynth.adsr(
            SoundSynth.noise(durationMs = 120, amp = 0.5f, seed = 1L),
            attackMs = 5,
            decayMs = 60,
            sustain = 0.1f,
            releaseMs = 55,
        )
        val fireTone = SoundSynth.sine(freqStart = 300f, freqEnd = 500f, durationMs = 120, amp = 0.15f)
        result[Sfx.FIRE] = SoundSynth.mix(fireNoise, fireTone)

        // BOUNCE: 40ms 220Hz tone, sharp decay.
        result[Sfx.BOUNCE] = SoundSynth.adsr(
            SoundSynth.sine(freqStart = 220f, freqEnd = 220f, durationMs = 40, amp = 0.6f),
            attackMs = 2,
            decayMs = 10,
            sustain = 0.2f,
            releaseMs = 28,
        )

        // POP0: base pop, pentatonic-ish ladder built from pitched() for POP1..POP7.
        val popTone = SoundSynth.adsr(
            SoundSynth.sine(freqStart = 620f, freqEnd = 940f, durationMs = 80, amp = 0.7f),
            attackMs = 5,
            decayMs = 45,
            sustain = 0.15f,
            releaseMs = 30,
        )
        val popTransient = SoundSynth.adsr(
            SoundSynth.noise(durationMs = 8, amp = 0.4f, seed = 2L),
            attackMs = 1,
            decayMs = 4,
            sustain = 0.0f,
            releaseMs = 3,
        )
        val pop0 = SoundSynth.mix(popTone, popTransient)
        result[Sfx.POP0] = pop0
        val semitoneSteps = listOf(2, 4, 5, 7, 9, 11, 12)
        popLadder.drop(1).forEachIndexed { index, sfx ->
            result[sfx] = SoundSynth.pitched(pop0, semitoneSteps[index])
        }

        // LAND: 30ms 180Hz thud.
        result[Sfx.LAND] = SoundSynth.adsr(
            SoundSynth.sine(freqStart = 180f, freqEnd = 180f, durationMs = 30, amp = 0.6f),
            attackMs = 1,
            decayMs = 10,
            sustain = 0.1f,
            releaseMs = 19,
        )

        // FALL: 250ms soft downward glide.
        result[Sfx.FALL] = SoundSynth.adsr(
            SoundSynth.sine(freqStart = 700f, freqEnd = 220f, durationMs = 250, amp = 0.5f),
            attackMs = 20,
            decayMs = 80,
            sustain = 0.3f,
            releaseMs = 150,
        )

        // BANK: two quick ascending sines concatenated.
        val bankLow = SoundSynth.adsr(
            SoundSynth.sine(freqStart = 880f, freqEnd = 880f, durationMs = 60, amp = 0.5f),
            attackMs = 3,
            decayMs = 20,
            sustain = 0.2f,
            releaseMs = 37,
        )
        val bankHigh = SoundSynth.adsr(
            SoundSynth.sine(freqStart = 1320f, freqEnd = 1320f, durationMs = 60, amp = 0.5f),
            attackMs = 3,
            decayMs = 20,
            sustain = 0.2f,
            releaseMs = 37,
        )
        result[Sfx.BANK] = SoundSynth.concat(bankLow, bankHigh)

        // FEVER_START: 500ms chord with slow attack, plus low-amp noise shimmer.
        val chord = SoundSynth.mix(
            SoundSynth.sine(freqStart = 440f, freqEnd = 440f, durationMs = 500, amp = 0.3f),
            SoundSynth.sine(freqStart = 660f, freqEnd = 660f, durationMs = 500, amp = 0.25f),
            SoundSynth.sine(freqStart = 880f, freqEnd = 880f, durationMs = 500, amp = 0.2f),
        )
        val shimmer = SoundSynth.noise(durationMs = 500, amp = 0.06f, seed = 3L)
        val feverMix = SoundSynth.mix(chord, shimmer)
        result[Sfx.FEVER_START] = SoundSynth.adsr(
            feverMix,
            attackMs = 120,
            decayMs = 80,
            sustain = 0.7f,
            releaseMs = 300,
        )

        // ICE_CRACK: 60ms bright noise burst.
        result[Sfx.ICE_CRACK] = SoundSynth.adsr(
            SoundSynth.noise(durationMs = 60, amp = 0.7f, seed = 4L),
            attackMs = 1,
            decayMs = 20,
            sustain = 0.1f,
            releaseMs = 39,
        )

        // CHAIN_BREAK: 90ms metallic downward glide.
        result[Sfx.CHAIN_BREAK] = SoundSynth.adsr(
            SoundSynth.sine(freqStart = 1200f, freqEnd = 800f, durationMs = 90, amp = 0.55f),
            attackMs = 2,
            decayMs = 30,
            sustain = 0.2f,
            releaseMs = 58,
        )

        // BOMB: 350ms low downward glide plus a big noise burst, punchy envelope.
        val bombTone = SoundSynth.sine(freqStart = 90f, freqEnd = 45f, durationMs = 350, amp = 0.8f)
        val bombNoise = SoundSynth.noise(durationMs = 350, amp = 0.6f, seed = 5L)
        result[Sfx.BOMB] = SoundSynth.adsr(
            SoundSynth.mix(bombTone, bombNoise),
            attackMs = 2,
            decayMs = 60,
            sustain = 0.35f,
            releaseMs = 288,
        )

        // WIN: three ascending notes concatenated.
        val winNotes = listOf(523f, 659f, 784f).map { freq ->
            SoundSynth.adsr(
                SoundSynth.sine(freqStart = freq, freqEnd = freq, durationMs = 120, amp = 0.6f),
                attackMs = 5,
                decayMs = 30,
                sustain = 0.4f,
                releaseMs = 85,
            )
        }
        result[Sfx.WIN] = SoundSynth.concat(*winNotes.toTypedArray())

        // LOSE: three descending notes concatenated.
        val loseNotes = listOf(392f, 330f, 262f).map { freq ->
            SoundSynth.adsr(
                SoundSynth.sine(freqStart = freq, freqEnd = freq, durationMs = 150, amp = 0.5f),
                attackMs = 5,
                decayMs = 40,
                sustain = 0.35f,
                releaseMs = 105,
            )
        }
        result[Sfx.LOSE] = SoundSynth.concat(*loseNotes.toTypedArray())

        // UI_TAP: 15ms quiet tick.
        result[Sfx.UI_TAP] = SoundSynth.adsr(
            SoundSynth.sine(freqStart = 1000f, freqEnd = 1000f, durationMs = 15, amp = 0.3f),
            attackMs = 1,
            decayMs = 5,
            sustain = 0.1f,
            releaseMs = 9,
        )

        // SWAP: 50ms rising blip.
        result[Sfx.SWAP] = SoundSynth.adsr(
            SoundSynth.sine(freqStart = 500f, freqEnd = 700f, durationMs = 50, amp = 0.5f),
            attackMs = 3,
            decayMs = 15,
            sustain = 0.2f,
            releaseMs = 32,
        )

        return result
    }
}
