package com.populong.bubbleshooter.core.physics

import com.populong.bubbleshooter.core.engine.Ammo
import com.populong.bubbleshooter.core.grid.Bubble
import com.populong.bubbleshooter.core.grid.BubbleGrid
import com.populong.bubbleshooter.core.grid.GridGeometry
import com.populong.bubbleshooter.core.grid.GridPos
import com.populong.bubbleshooter.core.grid.Vec2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A projectile in flight in unit-radius space.
 *
 * @property teleports how many times this flight has already been teleported by a wormhole; once it
 *   reaches [CollisionModel.MAX_TELEPORTS] every wormhole goes inert for the rest of the flight.
 */
data class Projectile(
    val pos: Vec2,
    val vel: Vec2,
    val ammo: Ammo,
    val bounces: Int = 0,
    val teleports: Int = 0,
)

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

    /** Softening floor on the squared distance in the gravity-well kernel, so a near hit stays finite. */
    const val GRAVITY_SOFTEN: Float = 4f

    /** A projectile whose center comes within this range of a wormhole portal center teleports. */
    const val WORMHOLE_TRIGGER: Float = 1.2f

    /** How far past the partner portal (along the flight direction) a teleport re-emerges. */
    const val WORMHOLE_EXIT: Float = 0.6f

    /** Whole-flight teleport budget; after this many jumps every wormhole goes inert. */
    const val MAX_TELEPORTS: Int = 4

    private fun centerOf(pos: GridPos): Vec2 =
        Vec2(GridGeometry.centerX(pos), GridGeometry.centerY(pos.row))

    private fun dist2(a: Vec2, b: Vec2): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return dx * dx + dy * dy
    }

    /**
     * The first contact at [pos]: the nearest occupied bubble within range, else the ceiling plane.
     * [Bubble.Wormhole] cells are transparent to collision — the projectile passes into them (and is
     * teleported instead), so they are never returned as a contact.
     */
    fun contactAt(grid: BubbleGrid, ceilingY: Float, pos: Vec2): Contact? {
        val hitDist2 = BUBBLE_HIT_DIST * BUBBLE_HIT_DIST
        var best: GridPos? = null
        var bestD = Float.MAX_VALUE
        for ((cell, bubble) in grid.cells) {
            if (bubble is Bubble.Wormhole) continue
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

    /**
     * Advances one substep of length [len] along [vel], reflecting off the side walls. When [wells]
     * is non-empty the substep first bends the velocity by the summed gravity-well acceleration
     * `gravityStrength * dir / max(d^2, GRAVITY_SOFTEN)` applied over `subDt = len / |vel|`, capping
     * `|vel|` at [speedCap] so a tight orbit can't run away. The advance step itself always covers
     * exactly [len] along the (possibly bent) direction, preserving the no-tunnel guarantee.
     */
    fun advance(
        pos: Vec2,
        vel: Vec2,
        fieldWidth: Float,
        len: Float,
        wells: List<Vec2> = emptyList(),
        gravityStrength: Float = 0f,
        speedCap: Float = Float.MAX_VALUE,
    ): Triple<Vec2, Vec2, Boolean> {
        var v = vel
        if (gravityStrength != 0f && wells.isNotEmpty()) {
            val speed = v.length()
            if (speed > 1e-6f) {
                val subDt = len / speed
                var ax = 0f
                var ay = 0f
                for (w in wells) {
                    val dx = w.x - pos.x
                    val dy = w.y - pos.y
                    val d2 = dx * dx + dy * dy
                    val dist = sqrt(d2)
                    if (dist < 1e-6f) continue
                    val mag = gravityStrength / max(d2, GRAVITY_SOFTEN)
                    ax += mag * (dx / dist)
                    ay += mag * (dy / dist)
                }
                v = Vec2(v.x + ax * subDt, v.y + ay * subDt)
                val sp = v.length()
                if (sp > speedCap) v = v * (speedCap / sp)
            }
        }
        val dir = v.normalized()
        val moved = pos + dir * len
        return when {
            moved.x < 1f -> Triple(Vec2(1f, moved.y), Vec2(-v.x, v.y), true)
            moved.x > fieldWidth - 1f -> Triple(Vec2(fieldWidth - 1f, moved.y), Vec2(-v.x, v.y), true)
            else -> Triple(moved, v, false)
        }
    }

    /** The centers of every [Bubble.GravityWell] cell in [grid] (empty when there are none). */
    fun gravityWells(grid: BubbleGrid): List<Vec2> {
        var out: ArrayList<Vec2>? = null
        for ((cell, bubble) in grid.cells) {
            if (bubble is Bubble.GravityWell) {
                (out ?: ArrayList<Vec2>(2).also { out = it }).add(centerOf(cell))
            }
        }
        return out ?: emptyList()
    }

    /** Wormhole portal centers grouped by pairId (empty when there are none). */
    fun wormholePairs(grid: BubbleGrid): Map<Int, List<Vec2>> {
        var map: HashMap<Int, MutableList<Vec2>>? = null
        for ((cell, bubble) in grid.cells) {
            if (bubble is Bubble.Wormhole) {
                val m = map ?: HashMap<Int, MutableList<Vec2>>().also { map = it }
                m.getOrPut(bubble.pairId) { ArrayList(2) }.add(centerOf(cell))
            }
        }
        return map ?: emptyMap()
    }

    /**
     * If the projectile just *entered* a wormhole portal's [WORMHOLE_TRIGGER] radius this substep
     * (its center crossed in from [prev] to [pos]) and the flight still has teleport budget, returns
     * the re-emergence point [WORMHOLE_EXIT] past the partner portal along [vel]; else null. Only a
     * fresh entry teleports, so the exit point (which sits inside the partner's radius) never bounces
     * the projectile straight back.
     */
    fun teleport(
        pairs: Map<Int, List<Vec2>>,
        prev: Vec2,
        pos: Vec2,
        vel: Vec2,
        teleportsUsed: Int,
    ): Vec2? {
        if (teleportsUsed >= MAX_TELEPORTS || pairs.isEmpty()) return null
        val trig2 = WORMHOLE_TRIGGER * WORMHOLE_TRIGGER
        for ((_, portals) in pairs) {
            if (portals.size != 2) continue
            for (i in 0..1) {
                val here = portals[i]
                if (dist2(pos, here) < trig2 && dist2(prev, here) >= trig2) {
                    val other = portals[1 - i]
                    return other + vel.normalized() * WORMHOLE_EXIT
                }
            }
        }
        return null
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
     *
     * [gravityStrength] (0 disables) and [speed0] (the flight's launch speed, used for the `1.5x`
     * velocity cap) feed the shared gravity-well kernel; wormhole portals in [grid] teleport the
     * flight. Both effects live in [CollisionModel], so a fired shot and its aim preview march
     * identically.
     */
    fun step(
        grid: BubbleGrid,
        ceilingY: Float,
        fieldWidth: Float,
        p: Projectile,
        dt: Float,
        gravityStrength: Float = 0f,
        speed0: Float = p.vel.length(),
    ): SimOutcome {
        val speed = p.vel.length()
        if (speed == 0f) return SimOutcome.Moving(p)

        val wells = if (gravityStrength != 0f) CollisionModel.gravityWells(grid) else emptyList()
        val wormholes = CollisionModel.wormholePairs(grid)
        val speedCap = 1.5f * speed0

        var pos = p.pos
        var vel = p.vel
        var bounces = p.bounces
        var teleports = p.teleports
        // Budget the tick's travel by the constant launch speed, not the gravity-grown current speed,
        // so each tick is exactly one MAX_SUBSTEP substep at the default cadence — keeping the shot in
        // lockstep with the aim preview, which marches uniform substeps.
        var remaining = speed0 * dt

        while (remaining > 1e-6f) {
            val len = min(CollisionModel.MAX_SUBSTEP, remaining)
            val prev = pos
            val (nextPos, nextVel, bounced) =
                CollisionModel.advance(pos, vel, fieldWidth, len, wells, gravityStrength, speedCap)
            pos = nextPos
            vel = nextVel
            if (bounced) bounces++

            if (wormholes.isNotEmpty()) {
                val exit = CollisionModel.teleport(wormholes, prev, pos, vel, teleports)
                if (exit != null) {
                    pos = exit
                    teleports++
                }
            }

            val contact = CollisionModel.contactAt(grid, ceilingY, pos)
            if (contact != null) {
                val cell = CollisionModel.snap(grid, contact, pos, prev)
                val contactCell = (contact as? Contact.Bubble)?.cell
                return SimOutcome.Landed(cell, contactCell, bounces)
            }
            remaining -= len
        }
        return SimOutcome.Moving(Projectile(pos, vel, p.ammo, bounces, teleports))
    }
}
