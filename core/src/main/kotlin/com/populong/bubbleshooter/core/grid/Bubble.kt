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

    /**
     * A supernova (超新星) bubble. It matches like a plain [Colored] of its [color] and can be
     * popped as part of any same-color match; when a match *pop* removes it, it detonates a
     * shockwave that clears same-color groups seeded from every matchable bubble within hex
     * radius 2, chaining through further supernovas.
     */
    data class Supernova(val color: BubbleColor) : Bubble

    /**
     * A gravity well (引力井). Colorless and never matchable, but a solid obstacle: projectiles
     * collide with and snap next to it, and it falls when detached exactly like a [Stone]. A bomb
     * can clear it, but a supernova shockwave passes it by. In flight, a well bends the projectile's
     * path (see [com.populong.bubbleshooter.core.physics.CollisionModel]); it is not itself matter
     * the projectile can rest inside.
     */
    data object GravityWell : Bubble

    /**
     * A wormhole (虫洞) portal, one of a [pairId] pair. Colorless, never matchable and indestructible
     * (a bomb skips it, a shockwave skips it) and a permanent anchor: it bolts its whole connected
     * component to the backdrop like a [Chained] cell and therefore never falls. A projectile flying
     * within range of one portal is teleported to its partner rather than colliding with it.
     */
    data class Wormhole(val pairId: Int) : Bubble

    /**
     * A pulsar (脉冲星): a colored bubble that blinks on a fixed 240-tick cadence. While [lit] it
     * matches (and pops) as a plain [Colored] of its [color]; while unlit it is inert for matching
     * like a [Stone], though projectiles still snap next to it and it falls when detached like a
     * normal bubble. The engine toggles [lit] purely from the tick counter, so the blink is fully
     * deterministic.
     */
    data class Pulsar(val color: BubbleColor, val lit: Boolean) : Bubble
}

/** The color this bubble currently matches with, or null if it cannot participate in a match right now. */
fun Bubble.matchableColor(): BubbleColor? = when (this) {
    is Bubble.Colored -> color
    is Bubble.Supernova -> color
    is Bubble.Fog -> if (revealed) color else null
    is Bubble.Pulsar -> if (lit) color else null
    is Bubble.Stone, is Bubble.Ice, is Bubble.Chained,
    is Bubble.GravityWell, is Bubble.Wormhole,
    -> null
}
