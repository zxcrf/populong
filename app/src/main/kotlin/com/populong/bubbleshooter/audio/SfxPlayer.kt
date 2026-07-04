package com.populong.bubbleshooter.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.concurrent.thread
import kotlin.math.max

/**
 * Plays the synthesized [Sfx] clip bank via [AudioTrack] in `MODE_STATIC`.
 *
 * Each sound effect gets a small round-robin pool of [AudioTrack] instances so that
 * rapid repeated triggers (e.g. a pop combo) do not cut each other off. All playback calls
 * are defensive: a failure to play a sound must never crash the game.
 */
class SfxPlayer(private val enabled: () -> Boolean) {

    private companion object {
        const val TAG = "SfxPlayer"
        const val TRACKS_PER_CLIP = 2
    }

    private data class TrackPool(val tracks: List<AudioTrack>) {
        var nextIndex = 0
    }

    @Volatile private var pools: Map<Sfx, TrackPool> = emptyMap()
    @Volatile private var prepared = false

    /**
     * Synthesizes the clip bank and builds the [AudioTrack] pools on a background thread.
     * Safe to call from the main thread: the actual synthesis/allocation work is dispatched
     * via [kotlin.concurrent.thread] so it never blocks the caller. [play] silently no-ops
     * until preparation finishes.
     */
    fun prepare() {
        thread(name = "SfxPlayer-prepare") {
            try {
                val bank = SfxBank.build()
                val builtPools = mutableMapOf<Sfx, TrackPool>()
                for ((sfx, clip) in bank) {
                    val tracks = (0 until TRACKS_PER_CLIP).mapNotNull { buildTrack(clip) }
                    if (tracks.isNotEmpty()) {
                        builtPools[sfx] = TrackPool(tracks)
                    }
                }
                pools = builtPools
                prepared = true
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to prepare SFX bank", t)
                prepared = false
            }
        }
    }

    private fun buildTrack(clip: ShortArray): AudioTrack? {
        return try {
            val bytesPerSample = 2
            val bufferSizeInBytes = max(clip.size * bytesPerSample, minBufferSizeBytes())
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SoundSynth.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(bufferSizeInBytes)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(clip, 0, clip.size)
            track
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to build AudioTrack", t)
            null
        }
    }

    private fun minBufferSizeBytes(): Int {
        val minSize = AudioTrack.getMinBufferSize(
            SoundSynth.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        return if (minSize > 0) minSize else 4096
    }

    /** Plays [sfx] at [volume] (0f..1f) if sound is enabled and the bank prepared successfully. */
    fun play(sfx: Sfx, volume: Float = 1f) {
        if (!prepared) return
        if (!enabled()) return
        val pool = pools[sfx] ?: return
        try {
            val track = pool.tracks[pool.nextIndex]
            pool.nextIndex = (pool.nextIndex + 1) % pool.tracks.size
            track.stop()
            track.setPlaybackHeadPosition(0)
            track.setVolume(volume.coerceIn(0f, 1f))
            track.play()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Failed to play $sfx", e)
        }
    }

    /** Releases all underlying [AudioTrack] resources. Safe to call multiple times. */
    fun release() {
        try {
            for (pool in pools.values) {
                for (track in pool.tracks) {
                    try {
                        track.stop()
                    } catch (e: IllegalStateException) {
                        // Track may already be stopped; ignore.
                    }
                    track.release()
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to release SFX tracks", t)
        } finally {
            pools = emptyMap()
            prepared = false
        }
    }
}
