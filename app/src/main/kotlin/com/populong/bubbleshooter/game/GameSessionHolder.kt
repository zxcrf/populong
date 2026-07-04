package com.populong.bubbleshooter.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.populong.bubbleshooter.audio.Sfx
import com.populong.bubbleshooter.audio.SfxBank
import com.populong.bubbleshooter.audio.SfxPlayer
import com.populong.bubbleshooter.core.engine.Ammo
import com.populong.bubbleshooter.core.engine.GameConfig
import com.populong.bubbleshooter.core.engine.GameEngine
import com.populong.bubbleshooter.core.engine.GameEvent
import com.populong.bubbleshooter.core.engine.GameInput
import com.populong.bubbleshooter.core.engine.GameState
import com.populong.bubbleshooter.core.engine.Phase
import com.populong.bubbleshooter.core.grid.BubbleGrid
import com.populong.bubbleshooter.core.grid.GridGeometry
import com.populong.bubbleshooter.core.mode.GameMode
import com.populong.bubbleshooter.core.physics.AimResult
import com.populong.bubbleshooter.core.progress.CareerStats
import com.populong.bubbleshooter.core.progress.accumulate
import com.populong.bubbleshooter.fx.EffectsController
import com.populong.bubbleshooter.haptics.HapticsManager

/**
 * A small read-only snapshot of the values the HUD overlay needs. A new instance is only
 * assigned to [GameSessionHolder.hud] when its contents actually differ from the previous one,
 * so the HUD only recomposes on meaningful change rather than every simulated tick.
 */
data class HudSnapshot(
    val score: Long,
    val shotsLeft: Int,
    val combo: Int,
    val feverMeter: Float,
    val feverActive: Boolean,
    val phase: Phase,
    val currentAmmo: Ammo,
    val nextAmmo: Ammo,
)

/**
 * Bridges the pure [GameEngine] reducer to the Compose render loop.
 *
 * Owns the authoritative [GameState] as a plain field (never Compose state, so per-tick
 * mutation never triggers recomposition), advances it in fixed fractional-tick steps from
 * [advance], and turns emitted [GameEvent]s into sound/haptic feedback. [frameTick] is the
 * sole piece of Compose state a rendering `Canvas` should read to invalidate itself every
 * frame; [hud] is the only piece that should drive composable recomposition.
 */
class GameSessionHolder(
    mode: GameMode,
    seed: Long,
    private val sfx: SfxPlayer,
    private val haptics: HapticsManager,
    config: GameConfig = GameConfig(),
) {
    private companion object {
        /** Safety valve: never simulate more than this many ticks in a single [advance] call. */
        const val MAX_STEPS_PER_FRAME = 10

        /** Cap on how many events [lastEvents] retains, oldest dropped first. */
        const val MAX_EVENTS = 64
    }

    private val engine = GameEngine(config)

    /** The authoritative game state. Mutated only inside [advance]/[enqueue]; read freely. */
    var latestState: GameState = engine.initialState(mode, seed)
        private set

    /** Fixed simulation tick length, taken from the engine's own config. */
    private val tick: Float = latestState.config.TICK

    /** Bumped once per [advance] call; the sole value a render `Canvas` should observe. */
    val frameTick = mutableLongStateOf(0L)

    /** Simulation speed multiplier; the [GameScreen] sets this to 0.25 while precision-aiming. */
    var timeScale = 1f

    private var accumulator = 0f
    private val pendingInputs = ArrayDeque<GameInput>()
    private val frameEvents = ArrayList<GameEvent>()

    /** HUD-relevant snapshot of [latestState]; only reassigned when its contents change. */
    var hud by mutableStateOf(snapshotOf(latestState))
        private set

    /** Star rating from the most recent [GameEvent.Won], or null if the run hasn't been won. */
    var finalStars: Int? = null
        private set

    /** The events emitted during the most recent [advance] call, for renderer-side one-shots. */
    var lastEvents: List<GameEvent> = emptyList()
        private set

    /** Particle bursts, score popups, and screen shake driven off [GameEvent]s; read by [GameRenderer]. */
    val effects = EffectsController()

    /**
     * Career-stat deltas accrued so far *this run only* (starts at all-zero, never merged with
     * lifetime totals here). Folded into the saved lifetime [CareerStats] by the caller once the
     * run ends; see [onGameEnd].
     */
    var sessionStats: CareerStats = CareerStats()
        private set

    /** The highest [GameState.combo] observed so far this run. */
    var maxComboSeen: Int = 0
        private set

    /** Set by the caller; invoked exactly once, the first time this run reaches Won or Lost. */
    var onGameEnd: ((won: Boolean, score: Long, stars: Int) -> Unit)? = null

    private var gameEndReported = false

    /** Queues a player [input]; applied at the start of the next simulated tick. */
    fun enqueue(input: GameInput) {
        pendingInputs.addLast(input)
    }

    /** The current aim preview (polyline + landing cell), or null if no aim direction is set. */
    fun aimResult(): AimResult? = latestState.aimResult()

    /**
     * Advances the simulation by [frameNanos] of wall-clock time (scaled by [timeScale]), in
     * fixed [tick]-sized steps. Queued inputs are drained into the engine immediately before
     * each step, so input latency is at most one tick. Always bumps [frameTick], even if no
     * full tick elapsed, so the canvas redraws every displayed frame.
     */
    fun advance(frameNanos: Long) {
        frameEvents.clear()

        val dt = (frameNanos / 1_000_000_000f).coerceAtMost(0.25f) * timeScale
        accumulator += dt

        var steps = 0
        while (accumulator >= tick && steps < MAX_STEPS_PER_FRAME) {
            drainInputsIntoEngine()
            // Captured before the engine steps: events like Fell/BombExploded/SupernovaChained
            // carry only the *positions* of cells that just left the grid, so turning them into
            // falling-bubble actors needs the pre-step grid to look up what was actually sitting
            // there (the post-step grid no longer has it).
            val pre = latestState
            val result = engine.step(latestState)
            latestState = result.state
            trackCombo()
            dispatch(result.events, pre.grid)
            accumulator -= tick
            steps++
        }
        if (steps == MAX_STEPS_PER_FRAME) accumulator = 0f

        // Falling-actor reap line: field-bottom (lose line) + 4 units of margin, so debris falls
        // fully off-screen before being culled rather than popping out at the visible edge.
        effects.falling.killY = latestState.ceilingY + latestState.config.maxRows * GridGeometry.ROW_HEIGHT + 4f

        // Uses the timeScale-scaled dt (not raw wall-clock time), so particles/shake/popups slow
        // down together with the sim during precision-aim slow-mo — deliberate choice: slow-mo FX
        // reads as "cinematic" rather than "sluggish" for this kind of arcade aiming.
        effects.update(dt, fieldWidth = latestState.grid.evenCols * 2f)

        lastEvents = frameEvents.toList()
        refreshHudIfChanged()
        frameTick.longValue++
    }

    private fun drainInputsIntoEngine() {
        while (pendingInputs.isNotEmpty()) {
            val input = pendingInputs.removeFirst()
            val pre = latestState.grid
            val result = engine.handleInput(latestState, input)
            latestState = result.state
            trackCombo()
            if (result.events.isNotEmpty()) dispatch(result.events, pre)
        }
    }

    private fun trackCombo() {
        if (latestState.combo > maxComboSeen) maxComboSeen = latestState.combo
    }

    private fun refreshHudIfChanged() {
        val snapshot = snapshotOf(latestState)
        if (snapshot != hud) hud = snapshot
    }

    private fun dispatch(events: List<GameEvent>, preGrid: BubbleGrid) {
        if (events.isEmpty()) return
        sessionStats = sessionStats.accumulate(events)
        effects.onEvents(events, preGrid, latestState.grid, latestState.feverActive)
        for (event in events) {
            when (event) {
                GameEvent.Fired -> {
                    sfx.play(Sfx.FIRE)
                    haptics.tick()
                }

                is GameEvent.Bounced -> sfx.play(Sfx.BOUNCE)
                is GameEvent.Landed -> Unit
                is GameEvent.Popped -> {
                    sfx.play(SfxBank.popClip(latestState.combo - 1))
                    haptics.click()
                }

                is GameEvent.BombExploded -> {
                    sfx.play(Sfx.BOMB)
                    haptics.heavy()
                }

                is GameEvent.SupernovaChained -> {
                    sfx.play(Sfx.BOMB)
                    haptics.heavy()
                }

                is GameEvent.Fell -> sfx.play(Sfx.FALL)
                is GameEvent.BankShot -> sfx.play(Sfx.BANK)
                is GameEvent.IceCracked -> sfx.play(Sfx.ICE_CRACK)
                is GameEvent.Unchained -> sfx.play(Sfx.CHAIN_BREAK)
                is GameEvent.FogRevealed -> Unit
                GameEvent.FeverStarted -> {
                    sfx.play(Sfx.FEVER_START)
                    haptics.heavy()
                }

                GameEvent.FeverEnded -> Unit
                GameEvent.ComboBroken -> Unit
                GameEvent.Swapped -> sfx.play(Sfx.SWAP)
                GameEvent.RowInserted -> Unit
                GameEvent.CeilingStepped -> Unit
                is GameEvent.Won -> {
                    finalStars = event.stars
                    sfx.play(Sfx.WIN)
                    haptics.heavy()
                    reportGameEndOnce(won = true, score = event.finalScore, stars = event.stars)
                }

                is GameEvent.Lost -> {
                    sfx.play(Sfx.LOSE)
                    reportGameEndOnce(won = false, score = event.finalScore, stars = 0)
                }
            }
        }
        frameEvents.addAll(events)
        while (frameEvents.size > MAX_EVENTS) frameEvents.removeAt(0)
    }

    /** Invokes [onGameEnd] with the run's outcome, but only the first time this is called. */
    private fun reportGameEndOnce(won: Boolean, score: Long, stars: Int) {
        if (gameEndReported) return
        gameEndReported = true
        onGameEnd?.invoke(won, score, stars)
    }
}

private fun snapshotOf(state: GameState): HudSnapshot = HudSnapshot(
    score = state.score,
    shotsLeft = state.shotsLeft,
    combo = state.combo,
    feverMeter = state.feverMeter,
    feverActive = state.feverActive,
    phase = state.phase,
    currentAmmo = state.currentAmmo,
    nextAmmo = state.nextAmmo,
)
