package com.populong.bubbleshooter.core.grid

/**
 * A single bubble occupying a grid cell.
 */
sealed interface Bubble {

    /** A plain colored bubble; matches with other bubbles of the same color. */
    data class Colored(val color: BubbleColor) : Bubble

    /** An indestructible obstacle; never matches, and is removed only by falling. */
    data object Stone : Bubble

    /**
     * A colored bubble encased in ice. Not matchable while frozen. Loses one hit per adjacent
     * pop-event; when [hitsLeft] would reach 0, the cell is replaced by [Colored] instead of
     * storing an `Ice` with zero hits left.
     */
    data class Ice(val color: BubbleColor, val hitsLeft: Int) : Bubble {
        init {
            require(hitsLeft > 0) { "hitsLeft must be positive; a fully-cracked Ice becomes Colored" }
        }
    }

    /** A colored bubble hidden under fog; matchable only once [revealed]. */
    data class Fog(val color: BubbleColor, val revealed: Boolean) : Bubble

    /**
     * A colored bubble locked in a chain. Not matchable, and anchors its whole connected
     * component to the backdrop until it is unlocked (replaced by [Colored]).
     */
    data class Chained(val color: BubbleColor) : Bubble
}

/** The color this bubble currently matches with, or null if it cannot participate in a match right now. */
fun Bubble.matchableColor(): BubbleColor? = when (this) {
    is Bubble.Colored -> color
    is Bubble.Fog -> if (revealed) color else null
    is Bubble.Stone, is Bubble.Ice, is Bubble.Chained -> null
}
