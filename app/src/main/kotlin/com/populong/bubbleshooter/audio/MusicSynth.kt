package com.populong.bubbleshooter.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Every switchable background-music style. [id] is the persisted identifier stored in
 * `GameSettings.musicStyle`; [title] is the Chinese display name shown in Settings.
 */
enum class MusicStyle(val id: String, val title: String) {
    CHIPTUNE("chiptune", "星河进行曲"),
    SYNTHWAVE("synthwave", "深空巡航"),
    KAWAII("kawaii", "泡泡舞曲"),
}

/** Oscillator shapes available to [MusicSynth]'s internal sequencer. */
private enum class Waveform { SINE, TRIANGLE, SQUARE, SAW, NOISE }

/**
 * One scheduled note event in the internal sequencer. Timing is expressed in beats (not
 * samples/ms) so the same event list reads naturally against a musical grid; [MusicSynth]
 * converts beats to samples using the style's BPM at render time.
 *
 * @property freqOverrideHz used instead of [midi]/[detuneCents] for non-pitched hits (e.g. the
 *   synthwave kick thump) that are specified directly in Hz.
 * @property decayK the exponential decay-rate constant applied after the fixed 5ms attack: small
 *   values (~0.5-1.5) read as a sustained pad/bass, large values (~3-8) read as a plucked/percussive hit.
 */
private data class Note(
    val startBeat: Double,
    val lengthBeats: Double,
    val midi: Int = 0,
    val waveform: Waveform,
    val amp: Float,
    val detuneCents: Double = 0.0,
    val decayK: Double = 3.0,
    val freqOverrideHz: Double? = null,
)

/**
 * Pure-Kotlin procedural BGM synthesis: three 16-bar, seamlessly-looping mono clips at
 * [SAMPLE_RATE] Hz, one per [MusicStyle]. Built from a tiny internal note-event sequencer
 * (additive mixing, per-note ADSR-ish envelope, a handful of waveforms) rather than any
 * pre-recorded asset — consistent with [SoundSynth]'s "everything is math" approach, just at a
 * lower sample rate suited to a looping background track.
 *
 * Every note's tail that would run past the end of the loop buffer is wrapped back onto the
 * start of the buffer (sample-additive), so the render itself has no audible seam: playing the
 * clip back to back via [android.media.AudioTrack.setLoopPoints] loops cleanly.
 */
object MusicSynth {

    /** Sample rate used for every generated BGM clip, in Hz. */
    const val SAMPLE_RATE = 22050

    private const val BEATS_PER_BAR = 4
    private const val BARS = 16
    private const val MAX_AMPLITUDE = 32767.0

    /** Synthesizes the full 16-bar loop for [style]. Pure CPU math; safe to call off the main thread. */
    fun synthesize(style: MusicStyle): ShortArray {
        val (bpm, notes) = when (style) {
            MusicStyle.CHIPTUNE -> 120 to buildChiptuneNotes()
            MusicStyle.SYNTHWAVE -> 90 to buildSynthwaveNotes()
            MusicStyle.KAWAII -> 140 to buildKawaiiNotes()
        }
        return render(bpm, notes)
    }

    // ---------------------------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------------------------

    private fun render(bpm: Int, notes: List<Note>): ShortArray {
        val samplesPerBeat = 60.0 / bpm * SAMPLE_RATE
        val barSamples = (samplesPerBeat * BEATS_PER_BAR).let { Math.round(it).toInt() }
        val totalSamples = barSamples * BARS
        val buffer = FloatArray(totalSamples)

        for (note in notes) {
            renderNoteInto(buffer, note, samplesPerBeat, totalSamples)
        }

        // Soft-limit rather than hard-clip if additive mixing pushed the peak above unity.
        var peak = 0f
        for (v in buffer) peak = max(peak, abs(v))
        val scale = if (peak > 1f) 0.98f / peak else 1f

        val out = ShortArray(totalSamples)
        for (i in 0 until totalSamples) {
            val sample = (buffer[i] * scale * MAX_AMPLITUDE).toInt().coerceIn(-32768, 32767)
            out[i] = sample.toShort()
        }
        return out
    }

    private fun renderNoteInto(buffer: FloatArray, note: Note, samplesPerBeat: Double, totalSamples: Int) {
        if (totalSamples <= 0) return
        val startSample = Math.round(note.startBeat * samplesPerBeat).toInt()
        val lengthSamples = max(1, Math.round(note.lengthBeats * samplesPerBeat).toInt())
        val freq = note.freqOverrideHz ?: (midiToFreq(note.midi) * 2.0.pow(note.detuneCents / 1200.0))
        val attackSamples = min(lengthSamples, max(1, (0.005 * SAMPLE_RATE).toInt()))
        val decayRange = max(1, lengthSamples - attackSamples)

        // Deterministic per-note noise state so repeated NOISE notes don't all sound identical.
        var noiseState = (startSample.toLong() * 1103515245L) xor (note.midi.toLong() * 12345L) xor 0x9E3779B9L

        for (i in 0 until lengthSamples) {
            val env = if (i < attackSamples) {
                i.toDouble() / attackSamples
            } else {
                exp(-note.decayK * (i - attackSamples).toDouble() / decayRange)
            }

            val raw = if (note.waveform == Waveform.NOISE) {
                noiseState = noiseState * 6364136223846793005L + 1442695040888963407L
                val bits = (noiseState ushr 40) and 0xFFFFFF
                (bits.toDouble() / 0xFFFFFF) * 2.0 - 1.0
            } else {
                val t = i.toDouble() / SAMPLE_RATE
                val phase = frac(freq * t)
                waveformSample(note.waveform, phase)
            }

            val sample = (raw * env * note.amp).toFloat()
            // Wrap any tail that runs past the loop end back onto the start so the seam is silent.
            val idx = (startSample + i) % totalSamples
            buffer[idx] += sample
        }
    }

    private fun frac(x: Double): Double = x - floor(x)

    private fun waveformSample(waveform: Waveform, phase: Double): Double = when (waveform) {
        Waveform.SINE -> sin(2.0 * PI * phase)
        Waveform.TRIANGLE -> 4.0 * abs(phase - 0.5) - 1.0
        Waveform.SQUARE -> if (sin(2.0 * PI * phase) >= 0.0) 1.0 else -1.0
        Waveform.SAW -> 2.0 * (phase - floor(phase + 0.5))
        Waveform.NOISE -> 0.0 // handled separately in renderNoteInto (needs per-sample state)
    }

    private fun midiToFreq(n: Int): Double = 440.0 * 2.0.pow((n - 69) / 12.0)

    /** A scale degree relative to [root], where [degree] can range across octaves (negative allowed). */
    private fun degreeToMidi(root: Int, degree: Int, intervals: IntArray): Int {
        val size = intervals.size
        val octave = Math.floorDiv(degree, size)
        val idx = Math.floorMod(degree, size)
        return root + 12 * octave + intervals[idx]
    }

    /**
     * Builds an ascending run of [steps] in-scale MIDI notes, evenly spanning roughly
     * [totalSemitones] above [root] (each target snapped to the nearest note actually in the
     * scale built from [intervals]), used for the synthwave arpeggio.
     */
    private fun scaleRun(root: Int, intervals: IntArray, steps: Int, totalSemitones: Int): List<Int> {
        val candidates = mutableListOf<Int>()
        var degree = 0
        while (true) {
            val midi = degreeToMidi(root, degree, intervals)
            candidates.add(midi)
            if (midi > root + totalSemitones + 12) break
            degree++
        }
        return (0 until steps).map { i ->
            val target = root + if (steps <= 1) 0 else totalSemitones * i / (steps - 1)
            candidates.minByOrNull { abs(it - target) } ?: root
        }
    }

    // ---------------------------------------------------------------------------------------
    // CHIPTUNE — 120 BPM, A minor pentatonic (A C D E G)
    // ---------------------------------------------------------------------------------------

    private val pentatonicMinor = intArrayOf(0, 3, 5, 7, 10) // A, C, D, E, G

    private fun buildChiptuneNotes(): List<Note> {
        val notes = mutableListOf<Note>()
        val rootA4 = 69

        // (a) Square-wave hook: a 2-bar (16 eighth-slot) earworm motif —
        // A4 C5 D5 (E5 D5) C5 A4 G4 — with syncopated ties across beat boundaries and a rest
        // (slots 9-10) before the final held note.
        data class HookEvent(val eighthSlot: Int, val eighthLen: Int, val degree: Int)
        val hookMotif = listOf(
            HookEvent(0, 1, 0),  // A4
            HookEvent(1, 1, 1),  // C5
            HookEvent(2, 2, 2),  // D5 (held, syncopated across the beat)
            HookEvent(4, 1, 3),  // E5
            HookEvent(5, 1, 2),  // D5
            HookEvent(6, 1, 1),  // C5
            HookEvent(7, 2, 0),  // A4 (held, syncopated across the beat)
            // slots 9-10: rest, before the final long note
            HookEvent(11, 5, -1), // G4, held out to the end of the 2-bar phrase
        )

        fun emitHookPhrase(phraseStartBar: Int, degreeShift: Int) {
            for (event in hookMotif) {
                val startBeat = phraseStartBar * BEATS_PER_BAR + event.eighthSlot * 0.5
                val lengthBeats = event.eighthLen * 0.5
                val midi = degreeToMidi(rootA4, event.degree + degreeShift, pentatonicMinor)
                notes += Note(startBeat, lengthBeats, midi, Waveform.SQUARE, amp = 0.18f, decayK = 2.5)
            }
        }

        // Bars 1-8: original motif (four 2-bar repeats).
        for (phraseBar in intArrayOf(0, 2, 4, 6)) emitHookPhrase(phraseBar, degreeShift = 0)
        // Bars 9-12: variation — shifted up two pentatonic scale degrees (stays in-scale).
        for (phraseBar in intArrayOf(8, 10)) emitHookPhrase(phraseBar, degreeShift = 2)
        // Bars 13-16: original motif again.
        for (phraseBar in intArrayOf(12, 14)) emitHookPhrase(phraseBar, degreeShift = 0)
        // Small fill in bar 16 (bar index 15): a quick ascending run into the loop seam.
        for ((i, degree) in listOf(0, 1, 2, 3).withIndex()) {
            val startBeat = 15 * BEATS_PER_BAR + 3.0 + i * 0.25
            val midi = degreeToMidi(rootA4, degree, pentatonicMinor)
            notes += Note(startBeat, 0.2, midi, Waveform.SQUARE, amp = 0.16f, decayK = 3.5)
        }

        // (b) Triangle bass ostinato: A2 A2 E2 G2, quarter notes, with an octave jump every 4th bar.
        val rootA2 = 45
        val bassPattern = intArrayOf(0, 0, 3, 4) // A, A, E, G (pentatonic degree indices)
        for (bar in 0 until BARS) {
            val octaveUp = if ((bar + 1) % 4 == 0) 12 else 0
            for (beat in 0 until BEATS_PER_BAR) {
                val midi = degreeToMidi(rootA2, bassPattern[beat], pentatonicMinor) + octaveUp
                notes += Note(
                    startBeat = bar * BEATS_PER_BAR + beat.toDouble(),
                    lengthBeats = 0.9,
                    midi = midi,
                    waveform = Waveform.TRIANGLE,
                    amp = 0.22f,
                    decayK = 1.2,
                )
            }
        }

        // (c) Noise hi-hats: 8ths from bar 5, accents on off-beats, drop out bar 12 for a breath.
        for (bar in 4 until BARS) {
            if (bar == 11) continue // bar 12 (1-indexed) breath
            for (slot in 0 until 8) {
                val accent = slot % 2 == 1
                notes += Note(
                    startBeat = bar * BEATS_PER_BAR + slot * 0.5,
                    lengthBeats = 0.12,
                    midi = 0,
                    waveform = Waveform.NOISE,
                    amp = if (accent) 0.14f else 0.07f,
                    decayK = 8.0,
                )
            }
        }

        return notes
    }

    // ---------------------------------------------------------------------------------------
    // SYNTHWAVE — 90 BPM, A minor (natural)
    // ---------------------------------------------------------------------------------------

    private val naturalMinorA = intArrayOf(0, 2, 3, 5, 7, 8, 10) // A B C D E F G

    private fun buildSynthwaveNotes(): List<Note> {
        val notes = mutableListOf<Note>()

        // (a) Detuned saw pad: whole-note chords Am - F - C - G, each voice stacked in 3rds and
        // doubled as a ±6-cent detuned pair at low amplitude.
        val chords = listOf(
            intArrayOf(57, 60, 64), // Am: A3 C4 E4
            intArrayOf(53, 57, 60), // F:  F3 A3 C4
            intArrayOf(60, 64, 67), // C:  C4 E4 G4
            intArrayOf(55, 59, 62), // G:  G3 B3 D4
        )
        for (bar in 0 until BARS) {
            val chord = chords[bar % chords.size]
            for (tone in chord) {
                for (detune in doubleArrayOf(-6.0, 6.0)) {
                    notes += Note(
                        startBeat = bar * BEATS_PER_BAR.toDouble(),
                        lengthBeats = BEATS_PER_BAR.toDouble(),
                        midi = tone,
                        waveform = Waveform.SAW,
                        amp = 0.10f,
                        detuneCents = detune,
                        decayK = 0.5,
                    )
                }
            }
        }

        // (b) Slow sine arpeggio: rises across ~2 octaves every 2 bars (8th notes), in natural
        // A minor, repeated for all eight 2-bar phrases.
        val arpRun = scaleRun(root = 57, intervals = naturalMinorA, steps = 16, totalSemitones = 24)
        for (phrase in 0 until 8) {
            val phraseStartBeat = phrase * 8.0
            for (i in 0 until 16) {
                notes += Note(
                    startBeat = phraseStartBeat + i * 0.5,
                    lengthBeats = 0.45,
                    midi = arpRun[i],
                    waveform = Waveform.SINE,
                    amp = 0.12f,
                    decayK = 3.0,
                )
            }
        }

        // (c) Soft kick-ish sine thump at 80Hz / 60ms on beats 1 and 3 of every bar.
        val kickLengthBeats = 0.06 * 90.0 / 60.0
        for (bar in 0 until BARS) {
            for (beat in intArrayOf(0, 2)) {
                notes += Note(
                    startBeat = bar * BEATS_PER_BAR + beat.toDouble(),
                    lengthBeats = kickLengthBeats,
                    waveform = Waveform.SINE,
                    amp = 0.5f,
                    decayK = 6.0,
                    freqOverrideHz = 80.0,
                )
            }
        }

        return notes
    }

    // ---------------------------------------------------------------------------------------
    // KAWAII — 140 BPM, C major pentatonic (C D E G A)
    // ---------------------------------------------------------------------------------------

    private val pentatonicMajorC = intArrayOf(0, 2, 4, 7, 9) // C, D, E, G, A

    private fun buildKawaiiNotes(): List<Note> {
        val notes = mutableListOf<Note>()
        val rootC5 = 72
        val rootC3 = 48

        // One-bar bouncy dotted-8th/16th motif: C5 E5 G5 A5 G5 E5 C5, staccato, tiled every bar.
        // Pairs of (dotted-8th, 16th) fill three beats; the fourth beat repeats the tonic and
        // leaves a short rest to breathe before the next bar.
        data class LeadEvent(val beatStart: Double, val lengthBeats: Double, val degree: Int)
        val leadMotif = listOf(
            LeadEvent(0.0, 0.75, 0),  // C5
            LeadEvent(0.75, 0.25, 2), // E5
            LeadEvent(1.0, 0.75, 3),  // G5
            LeadEvent(1.75, 0.25, 4), // A5
            LeadEvent(2.0, 0.75, 3),  // G5
            LeadEvent(2.75, 0.25, 2), // E5
            LeadEvent(3.0, 0.75, 0),  // C5
            // beat 3.75-4.0: rest
        )

        for (bar in 0 until BARS) {
            for (event in leadMotif) {
                val startBeat = bar * BEATS_PER_BAR + event.beatStart
                val soundingLength = event.lengthBeats * 0.6 // staccato
                val midi = degreeToMidi(rootC5, event.degree, pentatonicMajorC)
                // Sine-bell: fundamental + a quiet octave-up partial.
                notes += Note(startBeat, soundingLength, midi, Waveform.SINE, amp = 0.22f, decayK = 4.0)
                notes += Note(startBeat, soundingLength, midi + 12, Waveform.SINE, amp = 0.22f * 0.3f, decayK = 4.0)

                // (b) Counter blip (triangle) answering in the staccato gap left behind, two
                // scale degrees below the lead note.
                val blipStart = startBeat + soundingLength
                val blipLength = event.lengthBeats - soundingLength
                if (blipLength > 0.05) {
                    val blipMidi = degreeToMidi(rootC5, event.degree - 2, pentatonicMajorC)
                    notes += Note(blipStart, blipLength * 0.8, blipMidi, Waveform.TRIANGLE, amp = 0.12f, decayK = 5.0)
                }
            }

            // (c) Bass: C3 G2 alternating quarters.
            // (In this C,D,E,G,A interval ordering, A sits last in the array, so degree -1 would
            // wrap to A2, not G2 — degree -2 is the one that lands on G2, one whole octave below
            // rootC3's G.)
            val bassPattern = intArrayOf(0, -2, 0, -2) // degree 0 = C3, degree -2 = G2
            for (beat in 0 until BEATS_PER_BAR) {
                val midi = degreeToMidi(rootC3, bassPattern[beat], pentatonicMajorC)
                notes += Note(
                    startBeat = bar * BEATS_PER_BAR + beat.toDouble(),
                    lengthBeats = 0.85,
                    midi = midi,
                    waveform = Waveform.TRIANGLE,
                    amp = 0.20f,
                    decayK = 1.5,
                )
            }

            // (d) Light noise ticks on 16th off-beats, every other bar.
            if (bar % 2 == 0) {
                for (slot in 1 until 16 step 2) {
                    notes += Note(
                        startBeat = bar * BEATS_PER_BAR + slot * 0.25,
                        lengthBeats = 0.08,
                        waveform = Waveform.NOISE,
                        amp = 0.05f,
                        decayK = 10.0,
                    )
                }
            }
        }

        return notes
    }
}
