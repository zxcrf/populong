package com.populong.bubbleshooter.core.progress

import com.populong.bubbleshooter.core.engine.GameEvent
import com.populong.bubbleshooter.core.level.LevelCatalog
import com.populong.bubbleshooter.core.mode.Mutator
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable

/** A player's best result on a single level: highest stars and highest score ever achieved. */
@Serializable
data class LevelResult(val stars: Int = 0, val bestScore: Long = 0)

/** One entry in the Endless mode leaderboard: a [score] achieved with a given [mutators] set on [epochDay]. */
@Serializable
data class EndlessRecord(val score: Long, val mutators: Set<Mutator> = emptySet(), val epochDay: Long = 0)

/**
 * Progress on the daily challenge streak.
 *
 * @property lastCompletedEpochDay the most recent day (days since Unix epoch) the daily was completed, or -1 if none.
 * @property streak the current consecutive-day completion streak.
 * @property completedDays every day number the daily challenge was ever completed on.
 */
@Serializable
data class DailyState(
    val lastCompletedEpochDay: Long = -1,
    val streak: Int = 0,
    val completedDays: Set<Long> = emptySet(),
)

/** User-configurable presentation preferences. */
@Serializable
data class GameSettings(val sound: Boolean = true, val haptics: Boolean = true)

/** Cumulative, lifetime statistics accrued across every game played. */
@Serializable
data class CareerStats(
    val shotsFired: Long = 0,
    val shotsPopped: Long = 0,
    val bubblesPopped: Long = 0,
    val bubblesDropped: Long = 0,
    val maxCombo: Int = 0,
    val bankShots: Long = 0,
    val bombsDetonated: Long = 0,
    val feversTriggered: Long = 0,
    val levelsCompleted: Long = 0,
    val threeStarLevels: Long = 0,
    val totalScore: Long = 0,
)

/** The complete persisted state of a player's save file. */
@Serializable
data class SaveData(
    val version: Int = 1,
    val levels: Map<Int, LevelResult> = emptyMap(),
    /** Kept sorted descending by score, trimmed to at most 10 entries. */
    val endlessHighs: List<EndlessRecord> = emptyList(),
    val achievements: Set<String> = emptySet(),
    val stats: CareerStats = CareerStats(),
    val daily: DailyState = DailyState(),
    val settings: GameSettings = GameSettings(),
)

/**
 * Records the result of completing [level] with [stars] stars and [score] points, keeping the
 * maximum stars and maximum score ever seen for that level. Bumps [CareerStats.levelsCompleted]
 * only the first time [level] is completed (i.e. it had no prior [LevelResult]), and
 * [CareerStats.threeStarLevels] only the first time [level] reaches 3 stars.
 */
fun SaveData.withLevelResult(level: Int, stars: Int, score: Long): SaveData {
    val previous = levels[level]
    val wasCompleted = previous != null
    val hadThreeStars = (previous?.stars ?: 0) >= 3
    val newStars = maxOf(previous?.stars ?: 0, stars)
    val newScore = maxOf(previous?.bestScore ?: 0, score)
    val updated = LevelResult(stars = newStars, bestScore = newScore)
    val nowHasThreeStars = newStars >= 3
    return copy(
        levels = levels + (level to updated),
        stats = stats.copy(
            levelsCompleted = stats.levelsCompleted + if (!wasCompleted) 1 else 0,
            threeStarLevels = stats.threeStarLevels + if (!hadThreeStars && nowHasThreeStars) 1 else 0,
        ),
    )
}

/** Inserts an Endless [score] into the leaderboard, keeping it sorted descending and trimmed to 10 entries. */
fun SaveData.withEndlessScore(score: Long, mutators: Set<Mutator>, epochDay: Long): SaveData {
    val updated = (endlessHighs + EndlessRecord(score, mutators, epochDay))
        .sortedByDescending { it.score }
        .take(10)
    return copy(endlessHighs = updated)
}

/**
 * Records completion of the daily challenge on [epochDay]. The streak increments when [epochDay]
 * is exactly one day after [DailyState.lastCompletedEpochDay], stays unchanged (idempotent) when
 * completing the same day twice, and resets to 1 on any other gap (including out-of-order days
 * older than the last completed day, which do not disturb the existing streak or last-completed
 * marker beyond recording the day).
 */
fun SaveData.withDailyCompleted(epochDay: Long): SaveData {
    val last = daily.lastCompletedEpochDay
    val newStreak = when (epochDay) {
        last + 1 -> daily.streak + 1
        last -> daily.streak
        else -> if (epochDay in daily.completedDays) daily.streak else 1
    }
    val newLast = maxOf(last, epochDay)
    return copy(
        daily = daily.copy(
            lastCompletedEpochDay = newLast,
            streak = newStreak,
            completedDays = daily.completedDays + epochDay,
        ),
    )
}

/**
 * The highest level the player may currently play: one past the highest level with at least one
 * star, clamped to [LevelCatalog.TOTAL] and never less than 1.
 */
fun SaveData.highestUnlockedLevel(): Int {
    val highestStarred = levels.entries
        .filter { it.value.stars >= 1 }
        .maxOfOrNull { it.key } ?: 0
    return (highestStarred + 1).coerceIn(1, LevelCatalog.TOTAL)
}

/**
 * Folds a list of in-run [GameEvent]s into stat deltas: [Fired][GameEvent.Fired] increments
 * [CareerStats.shotsFired]; [Popped][GameEvent.Popped] increments [CareerStats.shotsPopped] and adds
 * the popped cell count to [CareerStats.bubblesPopped]; [Fell][GameEvent.Fell] adds the dropped cell
 * count to [CareerStats.bubblesDropped]; [BankShot][GameEvent.BankShot] increments
 * [CareerStats.bankShots]; [BombExploded][GameEvent.BombExploded] increments
 * [CareerStats.bombsDetonated]; [FeverStarted][GameEvent.FeverStarted] increments
 * [CareerStats.feversTriggered]. Per-run maxima and totals (combo, score) are not derived from events
 * here — see [withRun].
 */
fun CareerStats.accumulate(events: List<GameEvent>): CareerStats {
    var shotsFired = this.shotsFired
    var shotsPopped = this.shotsPopped
    var bubblesPopped = this.bubblesPopped
    var bubblesDropped = this.bubblesDropped
    var bankShots = this.bankShots
    var bombsDetonated = this.bombsDetonated
    var feversTriggered = this.feversTriggered
    for (event in events) {
        when (event) {
            is GameEvent.Fired -> shotsFired += 1
            is GameEvent.Popped -> {
                shotsPopped += 1
                bubblesPopped += event.cells.size
            }
            is GameEvent.Fell -> bubblesDropped += event.cells.size
            is GameEvent.BankShot -> bankShots += 1
            is GameEvent.BombExploded -> bombsDetonated += 1
            is GameEvent.FeverStarted -> feversTriggered += 1
            else -> Unit
        }
    }
    return copy(
        shotsFired = shotsFired,
        shotsPopped = shotsPopped,
        bubblesPopped = bubblesPopped,
        bubblesDropped = bubblesDropped,
        bankShots = bankShots,
        bombsDetonated = bombsDetonated,
        feversTriggered = feversTriggered,
    )
}

/** Folds a completed run's [maxComboInRun] and [runScore] into the lifetime maxima/totals. */
fun CareerStats.withRun(maxComboInRun: Int, runScore: Long): CareerStats = copy(
    maxCombo = maxOf(maxCombo, maxComboInRun),
    totalScore = totalScore + runScore,
)

/** Encodes and decodes [SaveData] to/from JSON, tolerating corrupt or forward-incompatible input. */
object SaveCodec {

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Serializes [save] to a JSON string. */
    fun encode(save: SaveData): String = json.encodeToString(save)

    /**
     * Parses [text] as a [SaveData]. Any parse failure (malformed JSON, truncated input, wrong
     * shape) is swallowed and yields fresh [SaveData] defaults rather than throwing — a corrupt
     * save file must never crash the app.
     */
    fun decode(text: String): SaveData = try {
        json.decodeFromString<SaveData>(text)
    } catch (_: Exception) {
        SaveData()
    }
}
