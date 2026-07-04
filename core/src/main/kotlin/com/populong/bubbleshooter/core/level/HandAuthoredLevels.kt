package com.populong.bubbleshooter.core.level

/**
 * The hand-authored tutorial arc: levels 1-50, designed for progressive
 * disclosure of every mechanic before the procedural generator takes over.
 */
object HandAuthoredLevels {
    const val COUNT = 50

    val all: List<LevelSpec> by lazy {
        val levels = levels1to25 + levels26to50
        require(levels.map { it.id } == (1..COUNT).toList()) {
            "hand-authored levels must have ids exactly 1..$COUNT in order, got ${levels.map { it.id }}"
        }
        levels
    }
}
