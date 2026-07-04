package com.populong.bubbleshooter.core.level

/**
 * The complete level catalog: hand-authored 1..50, procedurally generated
 * 51..[TOTAL]. Levels are grouped into galaxies of [GALAXY_SIZE] for the map.
 */
object LevelCatalog {
    const val TOTAL = 2000
    const val GALAXY_SIZE = 20
    const val GALAXY_COUNT = TOTAL / GALAXY_SIZE

    fun spec(level: Int): LevelSpec {
        require(level in 1..TOTAL) { "level $level out of range 1..$TOTAL" }
        return if (level <= HandAuthoredLevels.COUNT) {
            HandAuthoredLevels.all[level - 1]
        } else {
            LevelGenerator.generate(level)
        }
    }

    /** 1-based galaxy index containing [level]. */
    fun galaxyOf(level: Int): Int = (level - 1) / GALAXY_SIZE + 1

    /** The inclusive level range of 1-based [galaxy]. */
    fun levelsInGalaxy(galaxy: Int): IntRange {
        require(galaxy in 1..GALAXY_COUNT) { "galaxy $galaxy out of range 1..$GALAXY_COUNT" }
        val first = (galaxy - 1) * GALAXY_SIZE + 1
        return first..first + GALAXY_SIZE - 1
    }
}
