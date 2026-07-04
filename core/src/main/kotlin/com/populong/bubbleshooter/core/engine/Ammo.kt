package com.populong.bubbleshooter.core.engine

import com.populong.bubbleshooter.core.grid.BubbleColor

/** A single loaded projectile the shooter can fire. */
sealed interface Ammo {

    /** A plain colored bubble; placed at the landing cell and matched normally. */
    data class ColorAmmo(val color: BubbleColor) : Ammo

    /** A bomb; not placed, it clears every bubble within a hex radius of the landing cell. */
    data object Bomb : Ammo

    /** A rainbow bubble; resolves to the neighbor color that yields the largest match. */
    data object Rainbow : Ammo
}
