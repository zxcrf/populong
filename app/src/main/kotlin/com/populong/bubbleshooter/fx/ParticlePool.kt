package com.populong.bubbleshooter.fx

/**
 * A fixed-capacity, zero-per-frame-allocation particle system operating in unit space (bubble
 * radius = 1); the renderer maps positions to pixels via `FieldLayout`.
 *
 * Backed by parallel primitive arrays so [spawn]/[update]/[forEachAlive] never allocate once the
 * pool itself has been constructed. Dead particles are reaped by swapping in the current last
 * live particle and shrinking [count], so the live range is always the contiguous `[0, count)`
 * prefix of every array — no separate "alive" flag array, no per-frame compaction pass.
 *
 * [gravity] is a single pool-wide constant (units/s^2, applied to every particle's vertical
 * velocity every [update]); it is not stored per-particle, so all particles sharing this pool
 * fall at the same rate. Callers wanting different gravity behavior per effect should either bias
 * initial velocity/lifetime instead, or use a second pool.
 */
class ParticlePool(private val capacity: Int = 512, var gravity: Float = 2.4f) {
    // These six are read directly from the public inline `forEachAlive` below. A `public inline
    // fun` is not permitted to touch `private` members of its own class (the inlined bytecode
    // would need illegal cross-class private access at each call site), so they're `@PublishedApi
    // internal` instead — the standard pattern the Kotlin stdlib itself uses for inline-friendly
    // collections. `vxs`/`vys` are only ever touched from non-inline `spawn`/`update`, so those two
    // stay plain `private`.
    @PublishedApi internal val xs = FloatArray(capacity)
    @PublishedApi internal val ys = FloatArray(capacity)
    private val vxs = FloatArray(capacity)
    private val vys = FloatArray(capacity)
    @PublishedApi internal val life = FloatArray(capacity)
    @PublishedApi internal val maxLife = FloatArray(capacity)
    @PublishedApi internal val sizes = FloatArray(capacity)
    @PublishedApi internal val colors = IntArray(capacity)

    /** Number of currently-alive particles; also the length of the live prefix of every array. */
    var count: Int = 0
        private set

    /** Alias for [count], for callers that want an at-a-glance "how busy is this pool" read. */
    val aliveCount: Int get() = count

    /**
     * Spawns one particle at ([x], [y]) with initial velocity ([vx], [vy]), unit-space [size],
     * packed ARGB [color], and a lifetime of [lifeSec] seconds. Silently dropped if the pool is
     * already at [capacity] — callers doing large bursts should size the pool generously rather
     * than rely on partial spawns being visible.
     */
    fun spawn(x: Float, y: Float, vx: Float, vy: Float, lifeSec: Float, size: Float, color: Int) {
        if (count >= capacity) return
        val i = count
        xs[i] = x
        ys[i] = y
        vxs[i] = vx
        vys[i] = vy
        life[i] = lifeSec
        maxLife[i] = lifeSec
        sizes[i] = size
        colors[i] = color
        count++
    }

    /**
     * Integrates position by [dt] seconds, applies [gravity] to vertical velocity, decays
     * remaining life, and reaps particles whose life has run out.
     */
    fun update(dt: Float) {
        var i = 0
        while (i < count) {
            life[i] -= dt
            if (life[i] <= 0f) {
                val last = count - 1
                xs[i] = xs[last]
                ys[i] = ys[last]
                vxs[i] = vxs[last]
                vys[i] = vys[last]
                life[i] = life[last]
                maxLife[i] = maxLife[last]
                sizes[i] = sizes[last]
                colors[i] = colors[last]
                count--
                continue // re-examine index i, which now holds the former last particle
            }
            vys[i] += gravity * dt
            xs[i] += vxs[i] * dt
            ys[i] += vys[i] * dt
            i++
        }
    }

    /**
     * Invokes [block] for every alive particle with its unit-space position, remaining-life
     * fraction in `[0, 1]` (1 = just spawned, 0 = about to die), unit-space size, and packed ARGB
     * color. Marked `inline` so the lambda never allocates on the render hot path.
     */
    inline fun forEachAlive(block: (x: Float, y: Float, alphaFrac: Float, size: Float, colorArgb: Int) -> Unit) {
        for (i in 0 until count) {
            val frac = if (maxLife[i] > 0f) (life[i] / maxLife[i]).coerceIn(0f, 1f) else 0f
            block(xs[i], ys[i], frac, sizes[i], colors[i])
        }
    }

    /** Kills every alive particle immediately (e.g. on session restart). */
    fun clear() {
        count = 0
    }
}
