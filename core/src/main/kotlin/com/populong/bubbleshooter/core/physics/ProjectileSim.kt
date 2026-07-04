package com.populong.bubbleshooter.core.physics

import com.populong.bubbleshooter.core.engine.Ammo
import com.populong.bubbleshooter.core.grid.BubbleGrid
import com.populong.bubbleshooter.core.grid.GridGeometry
import com.populong.bubbleshooter.core.grid.GridPos
import com.populong.bubbleshooter.core.grid.Vec2
import kotlin.math.min

/** A projectile in flight in unit-radius space. */
data class Projectile(val pos: Vec2, val vel: Vec2, val ammo: Ammo, val bounces: Int = 0)

/** The result of advancing a projectile by one tick. */
sealed interface SimOutcome {

    /** The projectile is still moving; [projectile] holds its updated position, velocity and bounce count. */
    data class Moving(val projectile: Projectile) : SimOutcome

    /**
     * The projectile has come to rest. [cell] is the empty snap cell it now occupies;
     * [contact] is the bubble it struck, or null for a ceiling landing; [bounces] is the total
     * number of wall reflections along the way.
     */
    data class Landed(val cell: GridPos, val contact: GridPos?, val bounces: Int) : SimOutcome
}

/** A detected contact between a marching projectile and the grid. */
internal sealed interface Contact {
    data class Bubble(val cell: GridPos) : Contact
    data object Ceiling : Contact
}

/**
 * The single collision + snap model shared by [ProjectileSim] and
 * [AimPath], so a preview can never disagree with the real landing.
 */
internal object CollisionModel {

    /** Contact predicate distance against occupied cells: forgiving `2 * 0.9`. */
    const val BUBBLE_HIT_DIST: Float = 1.8f

    /** Longest travel permitted in a single substep, so nothing tunnels at any speed. */
    const val MAX_SUBSTEP: Float = 0.5f

    private fun centerOf(pos: GridPos): Vec2 =
        Vec2(GridGeometry.centerX(pos), GridGeometry.centerY(pos.row))

    private fun dist2(a: Vec2, b: Vec2): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return dx * dx + dy * dy
    }

    /** The first contact at [pos]: the nearest occupied bubble within range, else the ceiling plane. */
    fun contactAt(grid: BubbleGrid, ceilingY: Float, pos: Vec2): Contact? {
        val hitDist2 = BUBBLE_HIT_DIST * BUBBLE_HIT_DIST
        var best: GridPos? = null
        var bestD = Float.MAX_VALUE
        for (cell in grid.cells.keys) {
            val d = dist2(pos, centerOf(cell))
            if (d < hitDist2 && d < bestD) {
                bestD = d
                best = cell
            }
        }
        if (best != null) return Contact.Bubble(best)
        if (pos.y - 1f <= ceilingY) return Contact.Ceiling
        return null
    }

    /** Advances one substep of length [len] along [vel], reflecting off the side walls. */
    fun advance(pos: Vec2, vel: Vec2, fieldWidth: Float, len: Float): Triple<Vec2, Vec2, Boolean> {
        val dir = vel.normalized()
        val moved = pos + dir * len
        return when {
            moved.x < 1f -> Triple(Vec2(1f, moved.y), Vec2(-vel.x, vel.y), true)
            moved.x > fieldWidth - 1f -> Triple(Vec2(fieldWidth - 1f, moved.y), Vec2(-vel.x, vel.y), true)
            else -> Triple(moved, vel, false)
        }
    }

    /** The empty valid cell a projectile at [projPos] (arriving from [prevPos]) should snap into. */
    fun snap(grid: BubbleGrid, contact: Contact, projPos: Vec2, prevPos: Vec2): GridPos = when (contact) {
        is Contact.Bubble -> snapForBubble(grid, contact.cell, projPos, prevPos)
        Contact.Ceiling -> nearestEmptyInRow(grid, grid.ceilingRow, projPos.x)
            ?: fallbackEmpty(grid, projPos)
    }

    private fun snapForBubble(grid: BubbleGrid, contactCell: GridPos, projPos: Vec2, prevPos: Vec2): GridPos {
        val direct = grid.emptyNeighbors(contactCell)
        if (direct.isNotEmpty()) return pickNearest(direct, projPos, prevPos)

        val outward = LinkedHashSet<GridPos>()
        for (occ in grid.occupiedNeighbors(contactCell)) outward.addAll(grid.emptyNeighbors(occ))
        if (outward.isNotEmpty()) return pickNearest(outward, projPos, prevPos)

        return nearestEmptyInRow(grid, grid.ceilingRow, projPos.x) ?: fallbackEmpty(grid, projPos)
    }

    private fun pickNearest(candidates: Collection<GridPos>, projPos: Vec2, prevPos: Vec2): GridPos =
        candidates.minWithOrNull(
            compareBy(
                { dist2(centerOf(it), projPos) },
                { dist2(centerOf(it), prevPos) },
                { it.packed },
            ),
        )!!

    private fun nearestEmptyInRow(grid: BubbleGrid, row: Int, projX: Float): GridPos? {
        val cols = GridGeometry.colsInRow(row, grid.evenCols)
        var best: GridPos? = null
        var bestD = Float.MAX_VALUE
        for (col in 0 until cols) {
            val p = GridPos(row, col)
            if (!grid.isOccupied(p)) {
                val dx = GridGeometry.centerX(p) - projX
                val d = dx * dx
                if (d < bestD) {
                    bestD = d
                    best = p
                }
            }
        }
        return best
    }

    /** Last-resort empty cell: nearest empty neighbor of any occupied cell, or the ceiling row itself. */
    private fun fallbackEmpty(grid: BubbleGrid, projPos: Vec2): GridPos {
        val candidates = LinkedHashSet<GridPos>()
        for (row in grid.ceilingRow..grid.bottomRow() + 1) {
            val cols = GridGeometry.colsInRow(row, grid.evenCols)
            for (col in 0 until cols) {
                val p = GridPos(row, col)
                if (!grid.isOccupied(p)) candidates.add(p)
            }
        }
        return pickNearest(candidates, projPos, projPos)
    }
}

/** Steps a projectile forward by exactly one tick, substepping so it can never tunnel. */
object ProjectileSim {

    /**
     * Advances [p] by [dt] against [grid]. Returns [SimOutcome.Landed] the moment it contacts a
     * bubble (center distance `< 1.8`) or the ceiling plane (`pos.y - 1 <= ceilingY`), otherwise
     * [SimOutcome.Moving]. Travel is split into substeps of at most `0.5` units; side-wall crossings
     * clamp x into `[1, fieldWidth - 1]`, flip `vel.x` and increment the bounce count.
     */
    fun step(grid: BubbleGrid, ceilingY: Float, fieldWidth: Float, p: Projectile, dt: Float): SimOutcome {
        val speed = p.vel.length()
        if (speed == 0f) return SimOutcome.Moving(p)

        var pos = p.pos
        var vel = p.vel
        var bounces = p.bounces
        var remaining = speed * dt

        while (remaining > 1e-6f) {
            val len = min(CollisionModel.MAX_SUBSTEP, remaining)
            val prev = pos
            val (nextPos, nextVel, bounced) = CollisionModel.advance(pos, vel, fieldWidth, len)
            pos = nextPos
            vel = nextVel
            if (bounced) bounces++

            val contact = CollisionModel.contactAt(grid, ceilingY, pos)
            if (contact != null) {
                val cell = CollisionModel.snap(grid, contact, pos, prev)
                val contactCell = (contact as? Contact.Bubble)?.cell
                return SimOutcome.Landed(cell, contactCell, bounces)
            }
            remaining -= len
        }
        return SimOutcome.Moving(Projectile(pos, vel, p.ammo, bounces))
    }
}
