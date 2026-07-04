package com.populong.bubbleshooter.core.level

import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * The generator archetypes; the coarse silhouette a generated level grows its color blobs into.
 */
enum class Archetype { CHECKER, ARCHES, DIAMONDS, BLOBS, FORTRESS, SPIRAL }

/**
 * Every tunable the procedural [LevelGenerator] reads for one level. Derived purely from the level
 * number by [paramsFor], so the whole 2000-level campaign is a deterministic function of the index.
 *
 * @property rows number of occupied rows the layout may fill (bounded so a level never overflows the
 *   engine's [com.populong.bubbleshooter.core.engine.GameConfig.maxRows] on the opening frame).
 * @property colors palette size, 4..6.
 * @property holeDensity fraction of masked cells carved empty.
 * @property stoneDensity fraction of cells eligible for [com.populong.bubbleshooter.core.grid.Bubble.Stone].
 * @property iceDensity fraction eligible for [com.populong.bubbleshooter.core.grid.Bubble.Ice].
 * @property fogDensity fraction eligible for [com.populong.bubbleshooter.core.grid.Bubble.Fog].
 * @property chainDensity fraction eligible for [com.populong.bubbleshooter.core.grid.Bubble.Chained].
 * @property shotsSlack spare shots added on top of the bubble-count estimate.
 * @property archetype the silhouette to grow into.
 * @property isBoss whether this is a galaxy-capping fortress level.
 * @property descentEveryShots ceiling-compression pacing (0 = never).
 * @property bombEvery bomb-ammo cadence (0 = never).
 * @property rainbowEvery rainbow-ammo cadence (0 = never).
 */
data class GenParams(
    val rows: Int,
    val colors: Int,
    val holeDensity: Float,
    val stoneDensity: Float,
    val iceDensity: Float,
    val fogDensity: Float,
    val chainDensity: Float,
    val shotsSlack: Int,
    val archetype: Archetype,
    val isBoss: Boolean,
    val descentEveryShots: Int,
    val bombEvery: Int,
    val rainbowEvery: Int,
)

/**
 * Maps a level number (>= 51; hand-authored levels own 1..50) to its [GenParams].
 *
 * The campaign is organized into 20-level galaxies. Difficulty ramps log-ishly with the level
 * number while a per-galaxy sawtooth makes the first levels of each galaxy a breather. Every 20th
 * level is a denser [Archetype.FORTRESS] boss. Mechanics unlock at fixed gates — stone at 51, ice at
 * 60, fog at 80, chains at 100 — and each galaxy themes itself around one dominant obstacle.
 */
fun paramsFor(level: Int): GenParams {
    require(level >= 51) { "paramsFor is for generated levels (>= 51); $level is hand-authored" }

    val galaxy = level / GALAXY_SIZE
    val posInGalaxy = level % GALAXY_SIZE
    val isBoss = posInGalaxy == 0

    // Log-ish progress across the generated range, 0 at level 51, ~1 by level 2000.
    val span = (GEN_MAX - GEN_MIN).toDouble()
    val t = ((level - GEN_MIN).coerceAtLeast(0)).toDouble() / span
    val logRamp = ln(1.0 + t * (Math.E - 1.0)) // 0..1, front-loaded

    var rows = 7 + (3.0 * logRamp).roundToInt() // 7..10
    rows += when (posInGalaxy) { // early-galaxy breather
        in 1..4 -> -2
        in 5..9 -> -1
        else -> 0
    }
    if (isBoss) rows += 2
    rows = rows.coerceIn(5, MAX_GEN_ROWS)

    val colors = when {
        level >= 700 -> 6
        level >= 120 -> 5
        else -> 4
    }

    // Dominant obstacle rotates per galaxy so each feels themed.
    val dominant = galaxy % 4
    fun weight(index: Int) = if (index == dominant) 1.6f else 0.55f
    val bossScale = if (isBoss) 1.5f else 1f

    val stoneDensity = ramp(level, STONE_GATE, 0.02f, 0.08f) * weight(0) * bossScale
    val iceDensity = ramp(level, ICE_GATE, 0.02f, 0.08f) * weight(1) * bossScale
    val fogDensity = ramp(level, FOG_GATE, 0.02f, 0.07f) * weight(2) * bossScale
    val chainDensity = ramp(level, CHAIN_GATE, 0.02f, 0.06f) * weight(3) * bossScale

    val shotsSlack = (6.0 - 4.0 * t).roundToInt().coerceIn(2, 6)

    val bombEvery = when {
        isBoss -> 8 // mercy on bosses
        level % 13 == 5 -> 9
        else -> 0
    }
    val rainbowEvery = if (level % 7 == 3) 7 else 0

    val descentEveryShots = when {
        level < DESCENT_GATE -> 0
        isBoss -> 10
        galaxy % 5 == 0 -> 12
        else -> 0
    }

    val archetype = if (isBoss) Archetype.FORTRESS else pickArchetype(level, galaxy)

    return GenParams(
        rows = rows,
        colors = colors,
        holeDensity = ramp(level, GEN_MIN, 0.12f, 0.22f).coerceIn(0.12f, 0.22f),
        stoneDensity = stoneDensity,
        iceDensity = iceDensity,
        fogDensity = fogDensity,
        chainDensity = chainDensity,
        shotsSlack = shotsSlack,
        archetype = archetype,
        isBoss = isBoss,
        descentEveryShots = descentEveryShots,
        bombEvery = bombEvery,
        rainbowEvery = rainbowEvery,
    )
}

/** Deterministic non-boss archetype rotation, biased toward blob-friendly shapes early. */
private fun pickArchetype(level: Int, galaxy: Int): Archetype {
    val pool = if (level < 120) {
        arrayOf(Archetype.BLOBS, Archetype.CHECKER, Archetype.ARCHES)
    } else {
        arrayOf(
            Archetype.BLOBS, Archetype.CHECKER, Archetype.ARCHES,
            Archetype.DIAMONDS, Archetype.SPIRAL,
        )
    }
    return pool[(level + galaxy) % pool.size]
}

/** Linear density ramp from [gate] (value [lo]) to [GEN_MAX] (value [hi]); 0 before [gate]. */
private fun ramp(level: Int, gate: Int, lo: Float, hi: Float): Float {
    if (level < gate) return 0f
    val f = ((level - gate).toFloat() / (GEN_MAX - gate).toFloat()).coerceIn(0f, 1f)
    return lo + (hi - lo) * f
}

/** First generated level; hand-authored levels cover 1..50. */
const val GEN_MIN = 51

/** Highest campaign level the curve is tuned against. */
const val GEN_MAX = 2000

/** Levels per galaxy. */
const val GALAXY_SIZE = 20

/** Hard cap on generated rows, leaving headroom below the engine's 12-row lose line for build-up. */
const val MAX_GEN_ROWS = 10

private const val STONE_GATE = 51
private const val ICE_GATE = 60
private const val FOG_GATE = 80
private const val CHAIN_GATE = 100
private const val DESCENT_GATE = 300
