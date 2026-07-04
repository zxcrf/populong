package com.populong.bubbleshooter.core.grid

/**
 * The playable bubble colors. Palette size per level selects a prefix of this
 * list via [palette], so generated levels stay stable if new colors are added.
 */
enum class BubbleColor {
    RED, BLUE, GREEN, YELLOW, PURPLE, ORANGE;

    companion object {
        val all: List<BubbleColor> = entries

        /** The first [size] colors, the palette used by a level. */
        fun palette(size: Int): List<BubbleColor> {
            require(size in 2..entries.size) { "palette size $size out of range" }
            return entries.subList(0, size)
        }
    }
}
