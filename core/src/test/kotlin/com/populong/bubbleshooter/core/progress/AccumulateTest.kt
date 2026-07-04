package com.populong.bubbleshooter.core.progress

import com.populong.bubbleshooter.core.engine.GameEvent
import com.populong.bubbleshooter.core.grid.BubbleColor
import com.populong.bubbleshooter.core.grid.GridPos
import kotlin.test.Test
import kotlin.test.assertEquals

class AccumulateTest {

    @Test
    fun `accumulate folds a mixed event list into the expected stat deltas`() {
        val events = listOf(
            GameEvent.Fired,
            GameEvent.Fired,
            GameEvent.Popped(cells = setOf(GridPos(0, 0), GridPos(0, 1), GridPos(0, 2)), color = BubbleColor.RED, scoreGained = 30),
            GameEvent.Fired,
            GameEvent.Fell(cells = setOf(GridPos(1, 0), GridPos(1, 1)), scoreGained = 20),
            GameEvent.BankShot(bounces = 1, bonus = 5),
            GameEvent.BombExploded(cells = setOf(GridPos(2, 0))),
            GameEvent.FeverStarted,
            GameEvent.FeverEnded,
            GameEvent.Landed(cell = GridPos(3, 0)),
        )

        val stats = CareerStats().accumulate(events)

        assertEquals(3, stats.shotsFired)
        assertEquals(1, stats.shotsPopped)
        assertEquals(3, stats.bubblesPopped)
        assertEquals(2, stats.bubblesDropped)
        assertEquals(1, stats.bankShots)
        assertEquals(1, stats.bombsDetonated)
        assertEquals(1, stats.feversTriggered)
    }

    @Test
    fun `accumulate is additive on top of pre-existing stats`() {
        val existing = CareerStats(shotsFired = 10, bubblesPopped = 100)
        val events = listOf(
            GameEvent.Fired,
            GameEvent.Popped(cells = setOf(GridPos(0, 0)), color = BubbleColor.BLUE, scoreGained = 10),
        )

        val stats = existing.accumulate(events)

        assertEquals(11, stats.shotsFired)
        assertEquals(1, stats.shotsPopped)
        assertEquals(101, stats.bubblesPopped)
    }

    @Test
    fun `accumulate ignores events that carry no career stat`() {
        val events = listOf(
            GameEvent.Bounced(at = com.populong.bubbleshooter.core.grid.Vec2(0f, 0f)),
            GameEvent.ComboBroken,
            GameEvent.Swapped,
            GameEvent.RowInserted,
            GameEvent.CeilingStepped,
            GameEvent.Won(stars = 3, finalScore = 1000),
        )

        assertEquals(CareerStats(), CareerStats().accumulate(events))
    }

    @Test
    fun `accumulate handles an empty event list as a no-op`() {
        val existing = CareerStats(shotsFired = 5)
        assertEquals(existing, existing.accumulate(emptyList()))
    }
}
