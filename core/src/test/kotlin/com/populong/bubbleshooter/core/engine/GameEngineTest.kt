package com.populong.bubbleshooter.core.engine

import com.populong.bubbleshooter.core.grid.Bubble
import com.populong.bubbleshooter.core.grid.BubbleColor
import com.populong.bubbleshooter.core.grid.BubbleGrid
import com.populong.bubbleshooter.core.grid.GridPos
import com.populong.bubbleshooter.core.grid.Vec2
import com.populong.bubbleshooter.core.level.LevelSpec
import com.populong.bubbleshooter.core.mode.GameMode
import com.populong.bubbleshooter.core.mode.Mutator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameEngineTest {

    private val RED = BubbleColor.RED
    private val BLUE = BubbleColor.BLUE
    private val GREEN = BubbleColor.GREEN
    private val engine = GameEngine()

    // --- helpers ------------------------------------------------------------------------------

    private fun level(
        cells: Map<GridPos, Bubble>,
        shots: Int = 99,
        thresholds: List<Long> = listOf(100L, 300L, 1000L),
        bombEvery: Int = 0,
        rainbowEvery: Int = 0,
        descentEveryShots: Int = 0,
        evenCols: Int = 9,
    ): GameMode.Level = GameMode.Level(
        LevelSpec(
            id = 1,
            evenCols = evenCols,
            paletteSize = 6,
            initialGrid = BubbleGrid(cells, evenCols, ceilingRow = 0),
            shots = shots,
            starThresholds = thresholds,
            bombEvery = bombEvery,
            rainbowEvery = rainbowEvery,
            descentEveryShots = descentEveryShots,
        ),
    )

    private fun start(
        mode: GameMode,
        seed: Long = 1L,
        current: Ammo? = null,
        next: Ammo? = null,
        engine: GameEngine = this.engine,
    ): GameState {
        var s = engine.initialState(mode, seed)
        if (current != null) s = s.copy(currentAmmo = current)
        if (next != null) s = s.copy(nextAmmo = next)
        return s
    }

    private fun fire(
        s0: GameState,
        dir: Vec2 = Vec2(0f, -1f),
        engine: GameEngine = this.engine,
    ): Pair<GameState, List<GameEvent>> {
        val events = ArrayList<GameEvent>()
        var s = engine.handleInput(s0, GameInput.AimAt(dir)).state
        val fired = engine.handleInput(s, GameInput.Fire)
        s = fired.state
        events += fired.events
        var guard = 0
        while (s.phase == Phase.FLYING && guard++ < 500_000) {
            val r = engine.step(s)
            s = r.state
            events += r.events
        }
        return s to events
    }

    private inline fun <reified T : GameEvent> List<GameEvent>.only(): T =
        filterIsInstance<T>().single()

    private fun pos(row: Int, col: Int) = GridPos(row, col)

    // --- scripted resolutions -----------------------------------------------------------------

    @Test
    fun `a three-match pops exactly the connected cluster`() {
        val mode = level(mapOf(pos(0, 3) to Bubble.Colored(RED), pos(0, 5) to Bubble.Colored(RED), pos(0, 8) to Bubble.Colored(GREEN)))
        val (s, events) = fire(start(mode, current = Ammo.ColorAmmo(RED)))

        val popped = events.only<GameEvent.Popped>()
        assertEquals(setOf(pos(0, 3), pos(0, 4), pos(0, 5)), popped.cells)
        assertEquals(RED, popped.color)
        assertEquals(30L, popped.scoreGained)
        assertEquals(30L, s.score)
        assertEquals(1, s.combo)
        assertEquals(Phase.AIMING, s.phase)
        assertNull(s.grid.bubbleAt(pos(0, 3)))
        assertEquals(Bubble.Colored(GREEN), s.grid.bubbleAt(pos(0, 8)))
        assertTrue(events.none { it is GameEvent.Fell })
    }

    @Test
    fun `a custom shooterDistance repositions the shooter and shots still land correctly`() {
        val customEngine = GameEngine(GameConfig(shooterDistance = 30f))
        val mode = level(mapOf(pos(0, 3) to Bubble.Colored(RED), pos(0, 5) to Bubble.Colored(RED), pos(0, 8) to Bubble.Colored(GREEN)))
        val s0 = start(mode, current = Ammo.ColorAmmo(RED), engine = customEngine)
        assertEquals(s0.ceilingY + 30f, s0.shooterOrigin.y)

        val (s, events) = fire(s0, engine = customEngine)
        val popped = events.only<GameEvent.Popped>()
        assertEquals(setOf(pos(0, 3), pos(0, 4), pos(0, 5)), popped.cells)
        assertEquals(RED, popped.color)
    }

    @Test
    fun `a non-matching shot breaks the combo`() {
        val mode = level(mapOf(pos(0, 0) to Bubble.Colored(BLUE)))
        val base = start(mode, current = Ammo.ColorAmmo(RED)).copy(combo = 3)
        val (s, events) = fire(base)

        assertTrue(events.any { it is GameEvent.ComboBroken })
        assertTrue(events.none { it is GameEvent.Popped })
        assertEquals(0, s.combo)
        assertEquals(0L, s.score)
        assertEquals(Bubble.Colored(RED), s.grid.bubbleAt(pos(0, 4)))
    }

    @Test
    fun `stranded cluster falls after its bridge pops`() {
        val mode = level(
            mapOf(
                pos(0, 3) to Bubble.Colored(RED),
                pos(0, 5) to Bubble.Colored(RED),
                pos(1, 5) to Bubble.Colored(BLUE),
                pos(2, 5) to Bubble.Colored(BLUE),
                pos(0, 8) to Bubble.Colored(GREEN),
            ),
        )
        val (s, events) = fire(start(mode, current = Ammo.ColorAmmo(RED)))

        assertEquals(setOf(pos(0, 3), pos(0, 4), pos(0, 5)), events.only<GameEvent.Popped>().cells)
        val fell = events.only<GameEvent.Fell>()
        assertEquals(setOf(pos(1, 5), pos(2, 5)), fell.cells)
        assertEquals(40L, fell.scoreGained)
        assertEquals(70L, s.score)
        assertEquals(Bubble.Colored(GREEN), s.grid.bubbleAt(pos(0, 8)))
    }

    @Test
    fun `a bank shot earns a bounce bonus and pops`() {
        // A full RED top row: after reflecting off the left wall the shot strikes the wall of
        // bubbles and snaps below into a 3-match, no matter the exact contact column.
        val mode = level((1..7).associate { pos(0, it) to Bubble.Colored(RED) })
        val (s, events) = fire(start(mode, current = Ammo.ColorAmmo(RED)), dir = Vec2(-0.5f, -0.8660254f))

        val bank = events.only<GameEvent.BankShot>()
        assertTrue(bank.bounces >= 1, "bank shot must have reflected")
        assertEquals(50L * bank.bounces, bank.bonus)
        val popped = events.only<GameEvent.Popped>()
        assertTrue(popped.cells.size >= 3, "should have popped a match of at least 3")
        assertTrue(s.score >= 30L + bank.bonus)
    }

    @Test
    fun `swap exchanges the current and next ammo`() {
        val mode = level(mapOf(pos(0, 4) to Bubble.Colored(RED)))
        val s0 = start(mode, current = Ammo.ColorAmmo(RED), next = Ammo.ColorAmmo(BLUE))
        val result = engine.handleInput(s0, GameInput.Swap)
        assertEquals(Ammo.ColorAmmo(BLUE), result.state.currentAmmo)
        assertEquals(Ammo.ColorAmmo(RED), result.state.nextAmmo)
        assertTrue(result.events.any { it is GameEvent.Swapped })
    }

    @Test
    fun `a bomb clears stones and chains within its radius`() {
        val mode = level(
            mapOf(
                pos(0, 3) to Bubble.Stone,
                pos(0, 5) to Bubble.Stone,
                pos(0, 2) to Bubble.Chained(RED),
                pos(0, 6) to Bubble.Colored(RED),
                pos(0, 8) to Bubble.Stone, // hex distance 4, survives
                pos(0, 0) to Bubble.Colored(RED), // keeps grid non-empty and non-stone
            ),
        )
        val (s, events) = fire(start(mode, current = Ammo.Bomb))

        val boom = events.only<GameEvent.BombExploded>()
        assertEquals(setOf(pos(0, 2), pos(0, 3), pos(0, 5), pos(0, 6)), boom.cells)
        assertNull(s.grid.bubbleAt(pos(0, 3)))
        assertEquals(Bubble.Stone, s.grid.bubbleAt(pos(0, 8)))
        assertEquals(Bubble.Colored(RED), s.grid.bubbleAt(pos(0, 0)))
        assertEquals(40L, s.score) // popBase 10 * 4 removed * combo 1
    }

    @Test
    fun `a rainbow resolves to the color with the largest group`() {
        val mode = level(
            mapOf(
                pos(0, 2) to Bubble.Colored(RED),
                pos(0, 3) to Bubble.Colored(RED),
                pos(0, 5) to Bubble.Colored(BLUE),
            ),
        )
        val (s, events) = fire(start(mode, current = Ammo.Rainbow))

        val popped = events.only<GameEvent.Popped>()
        assertEquals(RED, popped.color)
        assertEquals(setOf(pos(0, 2), pos(0, 3), pos(0, 4)), popped.cells)
        assertEquals(Bubble.Colored(BLUE), s.grid.bubbleAt(pos(0, 5)))
    }

    @Test
    fun `fog is revealed before matching so it can join the match`() {
        val mode = level(
            mapOf(
                pos(0, 3) to Bubble.Colored(RED),
                pos(0, 5) to Bubble.Fog(RED, revealed = false),
                pos(0, 0) to Bubble.Colored(RED), // survives, keeps grid non-empty
            ),
        )
        val (s, events) = fire(start(mode, current = Ammo.ColorAmmo(RED)))

        assertTrue(events.only<GameEvent.FogRevealed>().cells.contains(pos(0, 5)))
        assertEquals(setOf(pos(0, 3), pos(0, 4), pos(0, 5)), events.only<GameEvent.Popped>().cells)
        assertNull(s.grid.bubbleAt(pos(0, 5)))
    }

    @Test
    fun `ice keeps a hit and stays frozen when cracked once`() {
        val mode = level(
            mapOf(
                pos(0, 2) to Bubble.Colored(RED),
                pos(0, 3) to Bubble.Colored(RED),
                pos(0, 5) to Bubble.Ice(BLUE, hitsLeft = 2),
            ),
        )
        val (s, events) = fire(start(mode, current = Ammo.ColorAmmo(RED)))

        assertEquals(setOf(pos(0, 2), pos(0, 3), pos(0, 4)), events.only<GameEvent.Popped>().cells)
        assertTrue(events.only<GameEvent.IceCracked>().cells.contains(pos(0, 5)))
        assertEquals(Bubble.Ice(BLUE, hitsLeft = 1), s.grid.bubbleAt(pos(0, 5)))
    }

    @Test
    fun `ice thaws then pops on a following shot`() {
        val mode = level(
            mapOf(
                pos(0, 2) to Bubble.Colored(RED),
                pos(0, 3) to Bubble.Colored(RED),
                pos(0, 5) to Bubble.Ice(RED, hitsLeft = 1),
                pos(0, 6) to Bubble.Colored(RED),
                pos(0, 7) to Bubble.Colored(RED),
            ),
        )
        val (s1, e1) = fire(start(mode, current = Ammo.ColorAmmo(RED), next = Ammo.ColorAmmo(RED)))
        assertTrue(e1.only<GameEvent.IceCracked>().cells.contains(pos(0, 5)))
        assertEquals(Bubble.Colored(RED), s1.grid.bubbleAt(pos(0, 5)))

        // Second shot lands at (0,4) again and pops the thawed bubble with its RED neighbors.
        val (s2, e2) = fire(s1.copy(currentAmmo = Ammo.ColorAmmo(RED)))
        assertTrue(e2.only<GameEvent.Popped>().cells.contains(pos(0, 5)))
        assertNull(s2.grid.bubbleAt(pos(0, 5)))
    }

    @Test
    fun `a chained bubble unlocks then pops on a following shot`() {
        val mode = level(
            mapOf(
                pos(0, 2) to Bubble.Colored(RED),
                pos(0, 3) to Bubble.Colored(RED),
                pos(0, 5) to Bubble.Chained(RED),
                pos(0, 6) to Bubble.Colored(RED),
                pos(0, 7) to Bubble.Colored(RED),
            ),
        )
        val (s1, e1) = fire(start(mode, current = Ammo.ColorAmmo(RED)))
        assertTrue(e1.only<GameEvent.Unchained>().cells.contains(pos(0, 5)))
        assertEquals(Bubble.Colored(RED), s1.grid.bubbleAt(pos(0, 5)))

        val (s2, e2) = fire(s1.copy(currentAmmo = Ammo.ColorAmmo(RED)))
        assertTrue(e2.only<GameEvent.Popped>().cells.contains(pos(0, 5)))
        assertNull(s2.grid.bubbleAt(pos(0, 5)))
    }

    // --- supernova shockwave ------------------------------------------------------------------

    private val YELLOW = BubbleColor.YELLOW

    @Test
    fun `a match popping a supernova clears small off-color groups of any size within its radius`() {
        // Landing RED at (0,4) completes a RED trio that includes the supernova at (0,5). Its
        // shockwave then vaporizes the lone off-color BLUE at (0,7) — a group of size 1 that a plain
        // match could never remove — while the distant GREEN survivor is left untouched.
        val mode = level(
            mapOf(
                pos(0, 0) to Bubble.Colored(GREEN), // far survivor, keeps the level unwon
                pos(0, 3) to Bubble.Colored(RED),
                pos(0, 5) to Bubble.Supernova(RED),
                pos(0, 7) to Bubble.Colored(BLUE), // off-color singleton within radius 2
            ),
        )
        val (s, events) = fire(start(mode, current = Ammo.ColorAmmo(RED)))

        val popped = events.only<GameEvent.Popped>()
        assertEquals(setOf(pos(0, 3), pos(0, 4), pos(0, 5), pos(0, 7)), popped.cells)
        assertEquals(RED, popped.color)

        val nova = events.only<GameEvent.SupernovaChained>()
        assertEquals(setOf(pos(0, 5)), nova.origins)
        assertTrue(pos(0, 7) in nova.removed, "the shockwave should reach the off-color singleton")
        assertEquals(1, nova.waves)

        assertNull(s.grid.bubbleAt(pos(0, 7)))
        assertEquals(Bubble.Colored(GREEN), s.grid.bubbleAt(pos(0, 0)))
        // Scoring is at the current combo (1) over the whole cleared set: 10 * 4 * 1.
        assertEquals(40L, popped.scoreGained)
        assertEquals(40L, s.score)
        assertEquals(Phase.AIMING, s.phase)
    }

    @Test
    fun `a supernova shockwave chains into a second supernova and extends its reach`() {
        // The first supernova (RED, (0,5)) detonates and engulfs the BLUE supernova at (0,7); that
        // second detonation then clears the GREEN at (2,8), which lies beyond the first blast's radius.
        val mode = level(
            mapOf(
                pos(0, 0) to Bubble.Colored(YELLOW), // far survivor
                pos(0, 3) to Bubble.Colored(RED),
                pos(0, 5) to Bubble.Supernova(RED),
                pos(0, 7) to Bubble.Supernova(BLUE),
                pos(2, 8) to Bubble.Colored(GREEN), // reachable only from the second supernova
            ),
        )
        val (s, events) = fire(start(mode, current = Ammo.ColorAmmo(RED)))

        val nova = events.only<GameEvent.SupernovaChained>()
        assertEquals(setOf(pos(0, 5), pos(0, 7)), nova.origins)
        assertEquals(2, nova.waves)
        assertTrue(pos(2, 8) in nova.removed, "the chained blast should reach the far green")

        assertNull(s.grid.bubbleAt(pos(0, 7)))
        assertNull(s.grid.bubbleAt(pos(2, 8)))
        assertEquals(Bubble.Colored(YELLOW), s.grid.bubbleAt(pos(0, 0)))
    }

    @Test
    fun `a shockwave passes over stone and chained and cracks ice without removing it`() {
        // Non-matchable obstacles in the blast radius are left in place: the stone and chained bubbles
        // survive, and the ice loses a hit via afterPop (adjacent to a cleared cell) but stays frozen.
        val mode = level(
            mapOf(
                pos(0, 0) to Bubble.Colored(GREEN), // far survivor
                pos(0, 3) to Bubble.Colored(RED),
                pos(0, 5) to Bubble.Supernova(RED),
                pos(0, 6) to Bubble.Ice(BLUE, hitsLeft = 2), // adjacent to the nova, within radius
                pos(0, 7) to Bubble.Stone, // within radius 2
                pos(0, 8) to Bubble.Chained(RED), // survivor just outside the radius
            ),
        )
        val (s, events) = fire(start(mode, current = Ammo.ColorAmmo(RED)))

        val nova = events.only<GameEvent.SupernovaChained>()
        assertEquals(setOf(pos(0, 3), pos(0, 4), pos(0, 5)), nova.removed)

        assertTrue(events.only<GameEvent.IceCracked>().cells.contains(pos(0, 6)))
        assertEquals(Bubble.Ice(BLUE, hitsLeft = 1), s.grid.bubbleAt(pos(0, 6)))
        assertEquals(Bubble.Stone, s.grid.bubbleAt(pos(0, 7)))
        assertEquals(Bubble.Chained(RED), s.grid.bubbleAt(pos(0, 8)))
    }

    @Test
    fun `a supernova script resolves identically on repeat`() {
        val cells = mapOf(
            pos(0, 0) to Bubble.Colored(GREEN),
            pos(0, 3) to Bubble.Colored(RED),
            pos(0, 5) to Bubble.Supernova(RED),
            pos(0, 7) to Bubble.Supernova(BLUE),
            pos(2, 8) to Bubble.Colored(GREEN),
        )
        val mode = level(cells)
        val (a, ea) = fire(start(mode, current = Ammo.ColorAmmo(RED)))
        val (b, eb) = fire(start(mode, current = Ammo.ColorAmmo(RED)))
        assertEquals(a.grid.cells, b.grid.cells)
        assertEquals(a.score, b.score)
        assertEquals(a.rngState, b.rngState)
        assertEquals(ea.filterIsInstance<GameEvent.SupernovaChained>(), eb.filterIsInstance<GameEvent.SupernovaChained>())
    }

    // --- determinism --------------------------------------------------------------------------

    @Test
    fun `the same script from the same seed yields identical states`() {
        val cells = mapOf(
            pos(0, 1) to Bubble.Colored(BLUE),
            pos(0, 3) to Bubble.Colored(RED),
            pos(0, 5) to Bubble.Colored(RED),
            pos(0, 8) to Bubble.Colored(GREEN),
        )
        val mode = level(cells) // shared instance so the two runs' modes are reference-equal
        fun run(): GameState {
            var s = engine.initialState(mode, seed = 4242L)
            repeat(3) { s = fire(s).first }
            return s
        }
        val a = run()
        val b = run()
        // BubbleGrid (frozen) has no structural equals, so compare the state field-wise.
        assertEquals(a.grid.cells, b.grid.cells)
        assertEquals(a.grid.ceilingRow, b.grid.ceilingRow)
        assertEquals(a.rngState, b.rngState)
        assertEquals(a.score, b.score)
        assertEquals(a.combo, b.combo)
        assertEquals(a.shotsFired, b.shotsFired)
        assertEquals(a.currentAmmo, b.currentAmmo)
        assertEquals(a.nextAmmo, b.nextAmmo)
        assertEquals(a.feverMeter, b.feverMeter)
        assertEquals(a.ticks, b.ticks)
        assertEquals(a.copy(grid = b.grid), b, "all non-grid fields must match")
    }

    // --- win / lose ---------------------------------------------------------------------------

    @Test
    fun `clearing the grid wins with stars and a remaining-shot bonus`() {
        val mode = level(
            mapOf(pos(0, 3) to Bubble.Colored(RED), pos(0, 5) to Bubble.Colored(RED)),
            shots = 5,
            thresholds = listOf(100L, 300L, 1000L),
        )
        val (s, events) = fire(start(mode, current = Ammo.ColorAmmo(RED)))

        val won = events.only<GameEvent.Won>()
        // pop 30 + clear bonus (4 remaining shots * 100) = 430 -> crosses 100 and 300 => 2 stars
        assertEquals(430L, won.finalScore)
        assertEquals(2, won.stars)
        assertEquals(Phase.WON, s.phase)
        assertEquals(430L, s.score)
    }

    @Test
    fun `running out of shots without clearing loses`() {
        val mode = level(mapOf(pos(0, 0) to Bubble.Colored(BLUE)), shots = 1)
        val (s, events) = fire(start(mode, current = Ammo.ColorAmmo(RED)))

        assertTrue(events.any { it is GameEvent.Lost })
        assertEquals(Phase.LOST, s.phase)
        assertEquals(0, s.shotsLeft)
    }

    @Test
    fun `endless descent mutator keeps inserting rows until the field overflows and loses`() {
        val tightEngine = GameEngine(GameConfig(maxRows = 6, endlessRowEveryShots = 1))
        var s = tightEngine.initialState(GameMode.Endless(setOf(Mutator.FASTER_DESCENT)), seed = 3L)
        var sawRowInserted = false
        var guard = 0
        while (s.phase != Phase.LOST && guard++ < 50) {
            val (ns, events) = fire(s, engine = tightEngine)
            s = ns
            if (events.any { it is GameEvent.RowInserted }) sawRowInserted = true
        }
        assertEquals(Phase.LOST, s.phase)
        assertTrue(sawRowInserted, "rows should have been inserted before losing")
    }

    @Test
    fun `endless without descent never inserts on shot cadence but refills a low board`() {
        // Descent off: after many shots on a healthy board, no rows appear...
        var s = engine.initialState(GameMode.Endless(emptySet()), seed = 3L)
        val startingBubbles = s.grid.cells.size
        var guard = 0
        while (s.phase == Phase.AIMING && guard++ < 8 && s.grid.cells.size >= startingBubbles / 2) {
            val (ns, events) = fire(s)
            s = ns
            assertTrue(
                events.none { it is GameEvent.RowInserted } ||
                    s.grid.cells.size < engine.let { GameConfig().endlessRefillThreshold } + 8,
                "row inserted while the board was still healthy and descent was off",
            )
        }
        // ...but a nearly-empty board triggers a refill on the next resolution.
        val lowGrid = BubbleGrid(
            cells = mapOf(pos(0, 3) to Bubble.Colored(BLUE), pos(0, 4) to Bubble.Colored(GREEN)),
            evenCols = 8,
            ceilingRow = 0,
        )
        val lowState = engine.initialState(GameMode.Endless(emptySet()), seed = 7L).copy(grid = lowGrid)
        val (after, events) = fire(lowState.copy(currentAmmo = Ammo.ColorAmmo(RED)))
        assertTrue(events.any { it is GameEvent.RowInserted }, "low board should refill a row")
        assertTrue(after.grid.cells.size > 2)
    }

    // --- fever --------------------------------------------------------------------------------

    @Test
    fun `fever doubles scoring while active`() {
        val mode = level(mapOf(pos(0, 3) to Bubble.Colored(RED), pos(0, 5) to Bubble.Colored(RED), pos(0, 0) to Bubble.Colored(RED)))
        val feverState = start(mode, current = Ammo.ColorAmmo(RED)).copy(feverTicksLeft = 100)
        val (s, events) = fire(feverState)
        // popScore(3, combo 1, fever) = 10 * 3 * 1 * 2 = 60
        assertEquals(60L, events.only<GameEvent.Popped>().scoreGained)
        assertEquals(60L, s.score)
    }

    @Test
    fun `fever ignites from the meter and ends after its countdown`() {
        val cfg = GameConfig(feverGainPerPop = 1.5f, feverGainPerBubble = 0f, feverDurationTicks = 2)
        val hotEngine = GameEngine(cfg)
        val mode = level(mapOf(pos(0, 3) to Bubble.Colored(RED), pos(0, 5) to Bubble.Colored(RED), pos(0, 0) to Bubble.Colored(RED)))
        val (s1, e1) = fire(start(mode, current = Ammo.ColorAmmo(RED), engine = hotEngine), engine = hotEngine)
        assertTrue(e1.any { it is GameEvent.FeverStarted })
        assertEquals(2, s1.feverTicksLeft)

        val step1 = hotEngine.step(s1)
        assertEquals(1, step1.state.feverTicksLeft)
        assertTrue(step1.events.none { it is GameEvent.FeverEnded })
        val step2 = hotEngine.step(step1.state)
        assertEquals(0, step2.state.feverTicksLeft)
        assertTrue(step2.events.any { it is GameEvent.FeverEnded })
    }

    // --- ammo never dead ----------------------------------------------------------------------

    @Test
    fun `drawn ammo colors always exist in the grid`() {
        val cells = mapOf(
            pos(0, 1) to Bubble.Colored(BLUE),
            pos(0, 3) to Bubble.Colored(RED),
            pos(0, 5) to Bubble.Colored(RED),
            pos(0, 8) to Bubble.Colored(BLUE),
        )
        var s = engine.initialState(level(cells), seed = 77L)
        repeat(4) {
            if (s.phase == Phase.AIMING) {
                assertAmmoInGrid(s)
                s = fire(s).first
                assertAmmoInGrid(s)
            }
        }
    }

    private fun assertAmmoInGrid(s: GameState) {
        val colors = s.grid.cells.values.mapNotNull {
            when (it) {
                is Bubble.Colored -> it.color
                is Bubble.Ice -> it.color
                is Bubble.Fog -> it.color
                is Bubble.Chained -> it.color
                is Bubble.Supernova -> it.color
                Bubble.Stone -> null
            }
        }.toSet()
        for (ammo in listOf(s.currentAmmo, s.nextAmmo)) {
            if (ammo is Ammo.ColorAmmo && colors.isNotEmpty()) {
                assertTrue(ammo.color in colors, "dead ammo ${ammo.color}; grid has $colors")
            }
        }
    }
}
