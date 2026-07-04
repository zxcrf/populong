package com.populong.bubbleshooter.core.progress

import com.populong.bubbleshooter.core.mode.Mutator
import kotlin.test.Test
import kotlin.test.assertEquals

class SaveCodecTest {

    @Test
    fun `round trip preserves a fully populated save`() {
        val save = SaveData(
            version = 1,
            levels = mapOf(
                1 to LevelResult(stars = 3, bestScore = 1200),
                42 to LevelResult(stars = 1, bestScore = 50),
            ),
            endlessHighs = listOf(
                EndlessRecord(score = 9000, mutators = setOf(Mutator.FASTER_DESCENT, Mutator.EXTRA_COLOR), epochDay = 5),
                EndlessRecord(score = 500, mutators = emptySet(), epochDay = 2),
            ),
            achievements = setOf("level_1_cleared", "combo_5"),
            stats = CareerStats(
                shotsFired = 1000, shotsPopped = 400, bubblesPopped = 3000,
                bubblesDropped = 200, maxCombo = 12, bankShots = 30,
                bombsDetonated = 5, feversTriggered = 2,
                levelsCompleted = 10, threeStarLevels = 4, totalScore = 55000,
            ),
            daily = DailyState(lastCompletedEpochDay = 5, streak = 3, completedDays = setOf(1L, 2L, 3L, 5L)),
            settings = GameSettings(sound = false, haptics = true),
        )

        val decoded = SaveCodec.decode(SaveCodec.encode(save))

        assertEquals(save, decoded)
    }

    @Test
    fun `round trip preserves an empty default save`() {
        val save = SaveData()
        assertEquals(save, SaveCodec.decode(SaveCodec.encode(save)))
    }

    @Test
    fun `Int-keyed level map round trips correctly despite JSON string keys`() {
        val save = SaveData(levels = mapOf(1 to LevelResult(3, 10), 2000 to LevelResult(1, 5)))
        val json = SaveCodec.encode(save)
        val decoded = SaveCodec.decode(json)
        assertEquals(save.levels, decoded.levels)
        // The map keys really are Ints after decode, not Strings that merely print the same.
        assertEquals(10L, decoded.levels.getValue(1).bestScore)
        assertEquals(5L, decoded.levels.getValue(2000).bestScore)
    }

    @Test
    fun `decode of garbage input yields defaults instead of throwing`() {
        assertEquals(SaveData(), SaveCodec.decode("not json at all {{{"))
    }

    @Test
    fun `decode of empty object yields defaults`() {
        assertEquals(SaveData(), SaveCodec.decode("{}"))
    }

    @Test
    fun `decode of truncated json yields defaults instead of throwing`() {
        val truncated = SaveCodec.encode(SaveData(version = 1)).dropLast(10)
        assertEquals(SaveData(), SaveCodec.decode(truncated))
    }

    @Test
    fun `decode of blank string yields defaults`() {
        assertEquals(SaveData(), SaveCodec.decode(""))
    }

    @Test
    fun `old save JSON without music fields decodes to music defaults, and new fields round trip`() {
        val oldJson = """
            {
              "version": 1,
              "levels": {},
              "endlessHighs": [],
              "achievements": [],
              "stats": {},
              "daily": {},
              "settings": {"sound": false, "haptics": true}
            }
        """.trimIndent()
        val decoded = SaveCodec.decode(oldJson)
        assertEquals(
            GameSettings(sound = false, haptics = true, music = true, musicStyle = "chiptune"),
            decoded.settings,
        )

        val withMusic = SaveData(
            settings = GameSettings(sound = true, haptics = false, music = false, musicStyle = "synthwave"),
        )
        assertEquals(withMusic, SaveCodec.decode(SaveCodec.encode(withMusic)))
    }

    @Test
    fun `decode tolerates unknown fields for forward compatibility`() {
        val json = """
            {
              "version": 1,
              "levels": {},
              "endlessHighs": [],
              "achievements": [],
              "stats": {},
              "daily": {},
              "settings": {},
              "futureFieldFromANewerVersion": {"whatever": 123},
              "anotherUnknownField": [1, 2, 3]
            }
        """.trimIndent()

        assertEquals(SaveData(), SaveCodec.decode(json))
    }
}
