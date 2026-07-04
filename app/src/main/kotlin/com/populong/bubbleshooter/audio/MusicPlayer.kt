package com.populong.bubbleshooter.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.concurrent.thread

/**
 * Plays a single procedurally-synthesized [MusicStyle] loop via a `MODE_STATIC` [AudioTrack]
 * with native loop points, so the background music repeats seamlessly with no per-iteration
 * decode/scheduling cost.
 *
 * [enabled] is read on every play/resume attempt (mirroring [SfxPlayer]'s pattern) so a toggle
 * flip is honored immediately without the caller having to separately track state. All Android
 * calls are defensively try/catch guarded: music must never crash the game. State touched from
 * both the caller's thread and the background prepare thread is [Volatile] or synchronized.
 */
class MusicPlayer(private val enabled: () -> Boolean) {

    private companion object {
        const val TAG = "MusicPlayer"

        /** BGM sits under SFX in the mix: scene volume (1.0 game / 0.6 menu) times this master trim. */
        const val MASTER_VOLUME = 0.5f
        const val GAME_SCENE_VOLUME = 1.0f
        const val MENU_SCENE_VOLUME = 0.6f
    }

    private val lock = Any()

    @Volatile private var track: AudioTrack? = null
    @Volatile private var currentStyle: MusicStyle? = null
    @Volatile private var prepared = false

    /** Whether playback *should* be audible right now (distinct from [enabled]'s on/off toggle). */
    @Volatile private var playing = false

    @Volatile private var sceneVolume = GAME_SCENE_VOLUME

    /**
     * Synthesizes [style] and builds its looping [AudioTrack] on a background thread; never
     * blocks the caller. If [playing] and [enabled] are both true once preparation finishes, the
     * new track starts immediately (covers both "prepare then start" and "start then prepare"
     * call orders).
     */
    fun prepare(style: MusicStyle) {
        thread(name = "MusicPlayer-prepare") {
            try {
                val clip = MusicSynth.synthesize(style)
                val newTrack = buildTrack(clip)
                val previous: AudioTrack?
                synchronized(lock) {
                    previous = track
                    track = newTrack
                    currentStyle = style
                    prepared = newTrack != null
                }
                previous?.let { safeRelease(it) }
                applyVolumeLocked()
                if (prepared && playing && enabled()) {
                    playInternal()
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to prepare music for $style", t)
                prepared = false
            }
        }
    }

    private fun buildTrack(clip: ShortArray): AudioTrack? {
        if (clip.isEmpty()) return null
        return try {
            val bytesPerSample = 2
            val bufferSizeInBytes = clip.size * bytesPerSample
            val newTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(MusicSynth.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(bufferSizeInBytes)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            newTrack.write(clip, 0, clip.size)
            // Loop the entire clip forever; the clip itself is rendered seam-free (see MusicSynth).
            newTrack.setLoopPoints(0, clip.size, -1)
            newTrack
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to build music AudioTrack", t)
            null
        }
    }

    /** Marks playback as wanted and, if [enabled], starts the current track immediately. */
    fun start() {
        playing = true
        if (!enabled()) return
        playInternal()
    }

    private fun playInternal() {
        try {
            val t = track ?: return
            if (!prepared) return
            t.play()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Failed to start music playback", e)
        }
    }

    /**
     * Stops audible playback but keeps the underlying track for a cheap resume. For a
     * `MODE_STATIC` track, a later [start] simply calls `play()` again, which restarts from the
     * head — acceptable for a looping background track.
     */
    fun stop() {
        playing = false
        try {
            track?.stop()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Failed to stop music playback", e)
        }
    }

    /** Releases the current track and prepares [style] in its place, resuming playback if it was already playing. */
    fun switchStyle(style: MusicStyle) {
        if (style == currentStyle) return
        val wasPlaying = playing
        try {
            track?.stop()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Failed to stop music before switching style", e)
        }
        prepare(style)
        playing = wasPlaying
    }

    /** Sets the scene volume: [inGame] plays at full (times master trim), the menu at 60%. */
    fun setScene(inGame: Boolean) {
        sceneVolume = if (inGame) GAME_SCENE_VOLUME else MENU_SCENE_VOLUME
        applyVolumeLocked()
    }

    private fun applyVolumeLocked() {
        try {
            track?.setVolume(sceneVolume * MASTER_VOLUME)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Failed to set music volume", e)
        }
    }

    /** Starts or stops playback to match [on], without disturbing the "wants to play" [playing] intent tracking elsewhere. */
    fun setEnabled(on: Boolean) {
        if (on) {
            if (playing) playInternal()
        } else {
            try {
                track?.stop()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Failed to stop music on disable", e)
            }
        }
    }

    /** Releases the underlying [AudioTrack]. Safe to call multiple times. */
    fun release() {
        try {
            track?.let { safeRelease(it) }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to release music track", t)
        } finally {
            track = null
            prepared = false
            playing = false
        }
    }

    private fun safeRelease(t: AudioTrack) {
        try {
            t.stop()
        } catch (e: IllegalStateException) {
            // Already stopped; ignore.
        }
        try {
            t.release()
        } catch (t2: Throwable) {
            Log.w(TAG, "Failed to release AudioTrack", t2)
        }
    }
}
