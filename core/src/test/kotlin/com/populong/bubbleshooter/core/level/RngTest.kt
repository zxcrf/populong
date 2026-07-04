package com.populong.bubbleshooter.core.level

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RngTest {

    /** An independent reimplementation of SplitMix64 used as the oracle. */
    private class Reference(var state: Long) {
        fun next(): Long {
            state += -0x61c8864680b583ebL
            var z = state
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            return z xor (z ushr 31)
        }

        fun finalize(seed: Long): Long {
            var z = seed
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            return z xor (z ushr 31)
        }
    }

    @Test
    fun `nextLong matches the reference stream for several seeds`() {
        for (seed in listOf(0L, 1L, -1L, 42L, 0x123456789ABCDEFL, Long.MIN_VALUE)) {
            val rng = Rng(seed)
            val ref = Reference(seed)
            repeat(64) { i ->
                assertEquals(ref.next(), rng.nextLong(), "seed=$seed draw=$i")
            }
        }
    }

    @Test
    fun `seed 0 produces the known SplitMix64 vector`() {
        val rng = Rng(0)
        val expected = longArrayOf(
            0xE220A8397B1DCDAFuL.toLong(),
            0x6E789E6AA1B965F4uL.toLong(),
            0x06C45D188009454FuL.toLong(),
            0xF88BB8A8724C81ECuL.toLong(),
        )
        for ((i, e) in expected.withIndex()) {
            assertEquals(e, rng.nextLong(), "draw $i")
        }
    }

    @Test
    fun `mix is the finalizer and mix of zero is zero`() {
        val ref = Reference(0)
        assertEquals(0L, Rng.mix(0L))
        for (seed in listOf(1L, 2L, 1000L, -7L)) {
            assertEquals(ref.finalize(seed), Rng.mix(seed), "seed=$seed")
        }
    }

    @Test
    fun `same seed is fully deterministic`() {
        val a = Rng(12345L)
        val b = Rng(12345L)
        repeat(100) { assertEquals(a.nextLong(), b.nextLong()) }
    }

    @Test
    fun `nextInt stays in range and covers the space`() {
        val rng = Rng(99L)
        val seen = HashSet<Int>()
        repeat(10_000) {
            val v = rng.nextInt(6)
            assertTrue(v in 0 until 6, "value $v out of range")
            seen.add(v)
        }
        assertEquals((0 until 6).toSet(), seen, "every bucket should appear")
    }

    @Test
    fun `nextFloat stays in the unit interval`() {
        val rng = Rng(7L)
        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        repeat(10_000) {
            val f = rng.nextFloat()
            assertTrue(f >= 0f && f < 1f, "value $f out of [0,1)")
            min = minOf(min, f)
            max = maxOf(max, f)
        }
        assertTrue(min < 0.05f && max > 0.95f, "distribution should span the interval: [$min,$max]")
    }
}
