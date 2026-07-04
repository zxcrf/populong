package com.populong.bubbleshooter.fx

import com.populong.bubbleshooter.core.grid.Bubble
import com.populong.bubbleshooter.core.grid.GridGeometry
import com.populong.bubbleshooter.core.grid.GridPos

/**
 * A fixed-capacity pool of detached "falling" bubble actors: bubbles that lost support (or were
 * cleared by a bomb/supernova/win) and are now real physics props tumbling out of the play field,
 * rather than instantly vanishing into particles. This is the single biggest contributor to the
 * reference video's "bubbles feel physical" read.
 *
 * Struct-of-arrays like [ParticlePool]: parallel primitive arrays for position/velocity plus a
 * parallel `Array<Bubble?>` recording which sprite each actor should draw as. Storing the actual
 * [Bubble] value (rather than e.g. just a color) means a spawned actor renders as an ice cube,
 * chained bubble, etc. exactly as it looked in the grid; that reference is only ever written at
 * [spawn] time, so the strict zero-allocation requirement applies only to [update]/[forEachAlive].
 * Dead actors are reaped by swapping in the current last live actor and shrinking [count], so the
 * live range is always the contiguous `[0, count)` prefix of every array — same pattern as
 * [ParticlePool.update].
 */
class FallingBubbles(private val capacity: Int = 72) {

    // xs/ys/bubbles are read from the public inline `forEachAlive` below, so — same reasoning as
    // ParticlePool's own fields — they must be `@PublishedApi internal` rather than `private` (a
    // public inline fun cannot touch private members of its own class, since the inlined bytecode
    // at an external call site would need illegal cross-class private access). vxs/vys are only
    // ever touched from non-inline `spawn`/`update`, so those two stay plain `private`.
    @PublishedApi internal val xs = FloatArray(capacity)
    @PublishedApi internal val ys = FloatArray(capacity)
    private val vxs = FloatArray(capacity)
    private val vys = FloatArray(capacity)
    @PublishedApi internal val bubbles = arrayOfNulls<Bubble>(capacity)

    /** Number of currently-alive actors; also the length of the live prefix of every array. */
    var count: Int = 0
        private set

    /**
     * World-space y (unit space, same coordinates as [GridGeometry]) at which a falling actor is
     * reaped. Callers (see [com.populong.bubbleshooter.game.GameSessionHolder.advance]) update this
     * every frame from the live field/lose-line geometry, since it depends on level config that
     * this pool has no access to.
     */
    var killY: Float = Float.MAX_VALUE

    /** Simple LCG for spawn-time velocity variety; deterministic seed, not meant to be secure or
     * to match any particular replay — falling-debris motion is cosmetic only. */
    private var lcgState: Long = -0x61c8864680b583ebL

    private fun nextUnitFloat(): Float {
        lcgState = lcgState * 0x5DEECE66DL + 0xBL
        val bits = ((lcgState ushr 24) and 0xFFFFFF).toInt()
        return bits / 16777216f // [0, 1)
    }

    /**
     * Spawns one falling actor at [cell]'s grid center, rendered as [bubble]. Initial velocity is a
     * tiny upward "pop" (`vy` in `-2f..0f`) plus a small sideways kick (`vx` in `±(0.5f..3f)`, sign
     * randomized). Silently dropped if the pool is already at [capacity].
     */
    fun spawn(cell: GridPos, bubble: Bubble) {
        if (count >= capacity) return
        val i = count
        xs[i] = GridGeometry.centerX(cell)
        ys[i] = GridGeometry.centerY(cell.row)
        vys[i] = -nextUnitFloat() * 2f
        val speed = 0.5f + nextUnitFloat() * 2.5f
        vxs[i] = if (nextUnitFloat() < 0.5f) -speed else speed
        bubbles[i] = bubble
        count++
    }

    /**
     * Integrates every actor by [dt] seconds under constant [GRAVITY], bounces off the side walls
     * of a field [fieldWidth] units wide (energy loss on bounce: `vx *= -0.55f`), and reaps any
     * actor that has fallen past [killY].
     */
    fun update(dt: Float, fieldWidth: Float) {
        var i = 0
        while (i < count) {
            vys[i] += GRAVITY * dt
            xs[i] += vxs[i] * dt
            ys[i] += vys[i] * dt

            if (xs[i] < 1f) {
                xs[i] = 1f
                vxs[i] = -vxs[i] * 0.55f
            } else if (xs[i] > fieldWidth - 1f) {
                xs[i] = fieldWidth - 1f
                vxs[i] = -vxs[i] * 0.55f
            }

            if (ys[i] > killY) {
                val last = count - 1
                xs[i] = xs[last]
                ys[i] = ys[last]
                vxs[i] = vxs[last]
                vys[i] = vys[last]
                bubbles[i] = bubbles[last]
                bubbles[last] = null
                count--
                continue // re-examine index i, which now holds the former last actor
            }
            i++
        }
    }

    /**
     * Invokes [block] for every alive actor with its unit-space position and the [Bubble] it
     * should render as. Marked `inline` so the lambda never allocates on the render hot path.
     */
    inline fun forEachAlive(block: (x: Float, y: Float, bubble: Bubble) -> Unit) {
        for (i in 0 until count) {
            val bubble = bubbles[i] ?: continue
            block(xs[i], ys[i], bubble)
        }
    }

    /** Kills every alive actor immediately (e.g. on session restart). */
    fun clear() {
        for (i in 0 until count) bubbles[i] = null
        count = 0
    }

    private companion object {
        /** Downward acceleration, unit-space units per second squared. */
        const val GRAVITY = 38f
    }
}
