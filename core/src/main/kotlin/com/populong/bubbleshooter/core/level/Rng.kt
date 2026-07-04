package com.populong.bubbleshooter.core.level

/**
 * A deterministic, version-independent pseudo-random generator implementing SplitMix64.
 *
 * The algorithm is fully specified by its constants and bit operations, so the same [seed]
 * yields the same stream on every platform and Kotlin version. The engine snapshots [state]
 * into game state and rebuilds an [Rng] from it, keeping the whole simulation reproducible.
 */
class Rng(seed: Long) {

    /** The mutable 64-bit internal state; snapshot and restore this to resume the stream. */
    var state: Long = seed

    /** The next 64-bit output; advances [state] by the SplitMix64 golden gamma. */
    fun nextLong(): Long {
        state += GAMMA
        var z = state
        z = (z xor (z ushr 30)) * C1
        z = (z xor (z ushr 27)) * C2
        return z xor (z ushr 31)
    }

    /** A uniformly distributed value in `[0, bound)`, using floor-mod of [nextLong]. */
    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive: $bound" }
        return Math.floorMod(nextLong(), bound.toLong()).toInt()
    }

    /** A value in `[0, 1)` built from the top 24 bits of [nextLong] divided by 2^24. */
    fun nextFloat(): Float {
        val bits = nextLong() ushr 40
        return bits.toFloat() / (1 shl 24).toFloat()
    }

    companion object {
        private const val GAMMA: Long = -0x61c8864680b583ebL // 0x9E3779B97F4A7C15
        private const val C1: Long = -0x40a7b892e31b1a47L // 0xBF58476D1CE4E5B9
        private const val C2: Long = -0x6b2fb644ecceee15L // 0x94D049BB133111EB

        /** The SplitMix64 finalizer applied to [seed]; used to derive an initial state from a seed. */
        fun mix(seed: Long): Long {
            var z = seed
            z = (z xor (z ushr 30)) * C1
            z = (z xor (z ushr 27)) * C2
            return z xor (z ushr 31)
        }
    }
}
