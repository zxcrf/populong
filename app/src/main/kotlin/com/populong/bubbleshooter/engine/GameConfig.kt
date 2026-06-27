package com.populong.bubbleshooter.engine

object GameConfig {
    const val GRID_COLUMNS = 10
    const val GRID_MAX_ROWS = 16

    const val PROJECTILE_SPEED = 0.8f

    const val MIN_AIM_ANGLE = 0.17f
    const val MAX_AIM_ANGLE = 2.97f

    const val MATCH_THRESHOLD = 3

    const val RESOLVE_POP_DURATION_MS = 200f
    const val RESOLVE_FALL_DURATION_MS = 600f

    const val ENDLESS_INITIAL_COLORS = 3
    const val ENDLESS_MAX_COLORS = 8
    const val ENDLESS_SHOTS_PER_COLOR_INCREASE = 20
    const val ENDLESS_SHOTS_PER_NEW_ROW = 8
    const val ENDLESS_INITIAL_ROWS = 5

    const val RAINBOW_MISS_THRESHOLD = 3

    // Consecutive successful clears multiply the score, capped to keep totals sane.
    const val MAX_COMBO_MULTIPLIER = 9

    const val GRID_TOP_MARGIN_RATIO = 0.08f
    const val SHOOTER_BOTTOM_MARGIN_RATIO = 0.12f
    const val BUBBLE_RADIUS_RATIO = 0.048f
}
