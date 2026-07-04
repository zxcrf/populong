package com.populong.bubbleshooter.core.physics

import com.populong.bubbleshooter.core.grid.BubbleGrid
import com.populong.bubbleshooter.core.grid.GridPos
import com.populong.bubbleshooter.core.grid.Vec2

/**
 * The previewed trajectory of a shot.
 *
 * @property points polyline vertices: the origin, each wall-bounce point, and the contact point.
 * @property landingCell the empty cell a fired projectile would snap into, or null if none is in range.
 * @property bounces the number of wall reflections along the previewed path.
 */
data class AimResult(val points: List<Vec2>, val landingCell: GridPos?, val bounces: Int)

/**
 * Computes the aiming preview. It marches the same substeps and uses the exact same contact
 * predicate and snap rule as [ProjectileSim] (via [CollisionModel]), so the preview's
 * [AimResult.landingCell] always equals where a projectile fired along the same direction lands.
 */
object AimPath {

    private const val GUARD_LIMIT = 100_000

    /**
     * The polyline from [origin] along [dir] (expected normalized with `dir.y < 0`), reflecting off
     * the side walls up to [maxBounces] times, ending at the first bubble or ceiling contact.
     *
     * When [maxLength] is finite the polyline is truncated once its cumulative arc length reaches it:
     * the final point is the exact cutoff on the current segment. If the landing (bubble/ceiling
     * contact or a bounce past [maxBounces]) would occur *beyond* [maxLength], the returned
     * [AimResult.landingCell] is null — the ghost is hidden — while the truncated points are kept.
     * Bounces are counted only within the visible length. A landing within [maxLength] is unchanged.
     */
    fun compute(
        grid: BubbleGrid,
        ceilingY: Float,
        origin: Vec2,
        dir: Vec2,
        maxBounces: Int,
        maxLength: Float = Float.MAX_VALUE,
        speed: Float = 60f,
        gravityStrength: Float = 0f,
    ): AimResult {
        val fieldWidth = 2f * grid.evenCols
        // Same field hazards and velocity-space the real simulation marches in, so a curved or
        // through-portal preview lands exactly where a fired shot would.
        val wells = if (gravityStrength != 0f) CollisionModel.gravityWells(grid) else emptyList()
        val wormholes = CollisionModel.wormholePairs(grid)
        val speedCap = 1.5f * speed

        val points = ArrayList<Vec2>()
        points.add(origin)

        var pos = origin
        var vel = dir.normalized() * speed
        var bounces = 0
        var teleports = 0
        var traveled = 0f
        var guard = 0

        while (guard++ < GUARD_LIMIT) {
            val prev = pos
            val (nextPos, nextVel, bounced) =
                CollisionModel.advance(pos, vel, fieldWidth, CollisionModel.MAX_SUBSTEP, wells, gravityStrength, speedCap)

            // Truncate before consuming this substep if it would cross the visible-length cap; the
            // cutoff point is the exact position where cumulative length equals maxLength.
            val segLen = (nextPos - prev).length()
            if (traveled + segLen >= maxLength) {
                val remain = maxLength - traveled
                val cut = if (segLen <= 1e-9f) prev else prev + (nextPos - prev) * (remain / segLen)
                points.add(cut)
                return AimResult(points, null, bounces)
            }
            traveled += segLen

            pos = nextPos
            vel = nextVel
            if (bounced) {
                if (bounces + 1 > maxBounces) {
                    points.add(pos)
                    return AimResult(points, null, bounces)
                }
                bounces++
                points.add(pos)
            }

            if (wormholes.isNotEmpty()) {
                val exit = CollisionModel.teleport(wormholes, prev, pos, vel, teleports)
                if (exit != null) {
                    points.add(pos) // portal entry
                    pos = exit
                    teleports++
                    points.add(pos) // portal exit
                }
            }

            val contact = CollisionModel.contactAt(grid, ceilingY, pos)
            if (contact != null) {
                points.add(pos)
                val cell = CollisionModel.snap(grid, contact, pos, prev)
                return AimResult(points, cell, bounces)
            }
        }
        points.add(pos)
        return AimResult(points, null, bounces)
    }
}
