package com.populong.bubbleshooter.core.mode

/**
 * Optional difficulty modifiers for Endless mode. Each raises risk and, in exchange, multiplies
 * the score awarded while it is active; a chosen set multiplies together via [totalMultiplier].
 */
enum class Mutator(val scoreMultiplier: Float) {
    FASTER_DESCENT(1.5f),
    EXTRA_COLOR(1.4f),
    SHORT_AIM(1.3f),
    NARROW_FIELD(1.25f),
}

/** The product of every mutator's [Mutator.scoreMultiplier]; `1f` for the empty set. */
fun Set<Mutator>.totalMultiplier(): Float {
    var product = 1f
    for (mutator in this) product *= mutator.scoreMultiplier
    return product
}
