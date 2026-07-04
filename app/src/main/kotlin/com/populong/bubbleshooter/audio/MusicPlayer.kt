package com.populong.bubbleshooter.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min

/**
 * Plays a single procedurally-synthesized [MusicStyle] loop via a streaming (`MODE_STREAM`)
 * [AudioTrack], writing the pre-rendered clip to the track in small chunks on a dedicated
 * playback thread and wrapping back to the start for a seamless loop.
 *
 * `MODE_STREAM` is used instead of `MODE_STATIC` because some devices cap the size of the
 * server-side static buffer well below the ~1-2MB a full BGM clip needs (synthwave, the longest
 * style at 90 BPM/16 bars, is the worst offender): `AudioTrack.Builder.build()` can throw on those
 * devices, which the old code silently caught, leaving the game musicless. Streaming only ever
 * needs a small (tens-of-KB) hardware buffer, sidestepping that cap entirely.
 *
 * [enabled] is read on every play/resume attempt (mirroring [SfxPlayer]'s pattern) so a toggle
 * flip is honored immediately without the caller having to separately track state. All Android
 * calls are defensively try/catch guarded: music must never crash the game. State shared between
 * the caller's thread, the background prepare thread, and the playback thread is [Volatile] (or
 * touched only via the monotonically-increasing [generation] counter / [playbackLock] flag) so
 * there is no need for a broader lock across the whole class.
 */
class MusicPlayer(private val enabled: () -> Boolean) {

    private companion object {
        const val TAG = "MusicPlayer"

        /** BGM sits under SFX in the mix: scene volume (1.0 game / 0.6 menu) times this master trim. */
        const val MASTER_VOLUME = 0.5f
        const val GAME_SCENE_VOLUME = 1.0f
        const val MENU_SCENE_VOLUME = 0.6f

        /** Samples written to the track per [AudioTrack.write] call while streaming. */
        const val STREAM_CHUNK_FRAMES = 4096

        /** Floor for the hardware buffer size, in bytes, regardless of what [AudioTrack.getMinBufferSize] reports. */
        const val MIN_HARDWARE_BUFFER_BYTES = 32 * 1024
    }

    /** Synthesized loop buffer, ready to stream once non-null. Written once by the prepare thread. */
    @Volatile private var clip: ShortArray? = null

    @Volatile private var currentStyle: MusicStyle? = null

    /** Whether playback *should* be audible right now (distinct from [enabled]'s on/off toggle). */
    @Volatile private var playing = false

    @Volatile private var sceneVolume = GAME_SCENE_VOLUME

    /** The track currently owned by the live playback thread, if any; used to apply volume live. */
    @Volatile private var currentTrack: AudioTrack? = null

    /**
     * Bumped every time playback should stop (stop/switchStyle/setEnabled(false)/release). The
     * playback thread snapshots this when it starts and exits as soon as it no longer matches,
     * so at most one thread is ever actually writing to hardware.
     */
    @Volatile private var generation = 0

    /** Guards against double-spawning a playback thread from concurrent start()/setEnabled(true) calls. */
    private val playbackLock = Any()
    @Volatile private var playbackThreadAlive = false

    /**
     * Synthesizes [style] on a background thread; never blocks the caller. If [playing] and
     * [enabled] are both true once synthesis finishes, playback starts immediately (covers both
     * "prepare then start" and "start then prepare" call orders).
     */
    fun prepare(style: MusicStyle) {
        thread(name = "MusicPlayer-prepare") {
            try {
                val newClip = MusicSynth.synthesize(style)
                if (newClip.isEmpty()) {
                    Log.w(TAG, "Synthesized empty clip for $style")
                    return@thread
                }
                clip = newClip
                currentStyle = style
                if (playing && enabled()) {
                    maybeStartPlaybackThread()
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to prepare music for $style", t)
            }
        }
    }

    /** Marks playback as wanted and, if [enabled] and a clip is ready, spawns the playback thread. */
    fun start() {
        playing = true
        if (!enabled()) return
        maybeStartPlaybackThread()
    }

    /** Stops audible playback: flips the "wants to play" intent off and retires the live thread. */
    fun stop() {
        playing = false
        generation++
    }

    /**
     * Releases the current playback thread/track and prepares [style] in its place, resuming
     * playback if it was already playing. The "wants to play" intent is restored *before*
     * [prepare] runs (not after), since [prepare]'s completion callback reads [playing] to decide
     * whether to auto-start — restoring it afterwards would race the background prepare thread
     * finishing first and skip that auto-start.
     */
    fun switchStyle(style: MusicStyle) {
        if (style == currentStyle) return
        val wasPlaying = playing
        playing = false
        generation++
        clip = null
        playing = wasPlaying
        prepare(style)
    }

    /** Sets the scene volume: [inGame] plays at full (times master trim), the menu at 60%. */
    fun setScene(inGame: Boolean) {
        sceneVolume = if (inGame) GAME_SCENE_VOLUME else MENU_SCENE_VOLUME
        applyVolume()
    }

    private fun applyVolume() {
        try {
            currentTrack?.setVolume(sceneVolume * MASTER_VOLUME)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to set music volume", t)
        }
    }

    /** Starts or stops playback to match [on], without disturbing the "wants to play" [playing] intent tracking elsewhere. */
    fun setEnabled(on: Boolean) {
        if (on) {
            if (playing) maybeStartPlaybackThread()
        } else {
            generation++
        }
    }

    /** Releases the underlying playback thread/track and clears the prepared clip. Safe to call multiple times. */
    fun release() {
        playing = false
        generation++
        clip = null
        currentStyle = null
    }

    /** Spawns the streaming playback thread if none is currently alive, guarded against double-spawn. */
    private fun maybeStartPlaybackThread() {
        val loopClip = clip ?: return
        synchronized(playbackLock) {
            if (playbackThreadAlive) return
            playbackThreadAlive = true
        }
        val myGeneration = generation
        thread(name = "MusicPlayer-playback", isDaemon = true) {
            try {
                runPlaybackLoop(loopClip, myGeneration)
            } catch (t: Throwable) {
                Log.w(TAG, "Music playback thread crashed", t)
            } finally {
                playbackThreadAlive = false
                // Guards a narrow race: switchStyle()/stop()+start() can bump generation and want
                // to play again before this (now-stale) thread noticed and exited. Whichever
                // thread finishes last re-checks and restarts so playback never gets stranded off.
                if (playing && enabled() && generation != myGeneration) {
                    maybeStartPlaybackThread()
                }
            }
        }
    }

    private fun runPlaybackLoop(loopClip: ShortArray, myGeneration: Int) {
        var track: AudioTrack? = null
        try {
            track = buildStreamTrack() ?: return
            currentTrack = track
            applyVolume()
            track.play()

            var offset = 0
            while (generation == myGeneration) {
                val remaining = loopClip.size - offset
                val chunkFrames = min(STREAM_CHUNK_FRAMES, remaining)
                val written = track.write(loopClip, offset, chunkFrames)
                if (written < 0) break // AudioTrack error code; bail out defensively.
                offset += chunkFrames
                if (offset >= loopClip.size) offset = 0 // seamless wrap; clip is seam-free by construction
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Music streaming failed", t)
        } finally {
            if (currentTrack === track) currentTrack = null
            track?.let { safeStopAndRelease(it) }
        }
    }

    /** Builds a small MODE_STREAM hardware buffer; independent of the (much larger) clip length. */
    private fun buildStreamTrack(): AudioTrack? {
        return try {
            val minBufferBytes = AudioTrack.getMinBufferSize(
                MusicSynth.SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            val bufferSizeInBytes = max(
                if (minBufferBytes > 0) minBufferBytes else 0,
                MIN_HARDWARE_BUFFER_BYTES,
            )
            AudioTrack.Builder()
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
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to build streaming music AudioTrack", t)
            null
        }
    }

    private fun safeStopAndRelease(t: AudioTrack) {
        try {
            t.pause()
        } catch (e: IllegalStateException) {
            // Already stopped; ignore.
        }
        try {
            t.flush()
        } catch (t2: Throwable) {
            Log.w(TAG, "Failed to flush AudioTrack", t2)
        }
        try {
            t.release()
        } catch (t2: Throwable) {
            Log.w(TAG, "Failed to release AudioTrack", t2)
        }
    }
}
