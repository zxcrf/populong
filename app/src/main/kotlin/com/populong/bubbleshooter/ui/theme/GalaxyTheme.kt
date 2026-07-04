package com.populong.bubbleshooter.ui.theme

import androidx.compose.ui.graphics.Color
import com.populong.bubbleshooter.core.level.LevelCatalog

/**
 * A per-galaxy visual identity: three deep-space gradient stops (mirroring [Neon.spaceGradient]'s
 * top/mid/deep structure) plus one bright accent hue used for highlights (headers, meteor tint).
 */
data class GalaxyPalette(
    val name: String,
    val bgTop: Color,
    val bgMid: Color,
    val bgDeep: Color,
    val accent: Color,
)

/**
 * Six deep-space palettes cycled across [LevelCatalog.GALAXY_COUNT] galaxies, so traveling through
 * the star map (or playing through galaxies) shifts the ambient color story while staying dark
 * enough for bubbles/text to read clearly against it. All [GalaxyPalette.bgTop] values sit near the
 * luminance of the original [Neon.bgTop] (`0xFF060B1E`) — only the hue cast changes.
 */
object GalaxyTheme {

    val palettes: List<GalaxyPalette> = listOf(
        // 1. 紫罗兰星云 (violet nebula)
        GalaxyPalette(
            name = "紫罗兰星云",
            bgTop = Color(0xFF0B0620),
            bgMid = Color(0xFF170B3A),
            bgDeep = Color(0xFF241048),
            accent = Color(0xFFB44DFF),
        ),
        // 2. 蓝巨星 (blue giant)
        GalaxyPalette(
            name = "蓝巨星",
            bgTop = Color(0xFF050B1E),
            bgMid = Color(0xFF0A1636),
            bgDeep = Color(0xFF10245A),
            accent = Color(0xFF4D9FFF),
        ),
        // 3. 红矮星 (red dwarf ember)
        GalaxyPalette(
            name = "红矮星",
            bgTop = Color(0xFF1A0705),
            bgMid = Color(0xFF2A0C09),
            bgDeep = Color(0xFF3C130D),
            accent = Color(0xFFFF6A3D),
        ),
        // 4. 翠绿极光 (aurora green)
        GalaxyPalette(
            name = "翠绿极光",
            bgTop = Color(0xFF051A12),
            bgMid = Color(0xFF0A2A1E),
            bgDeep = Color(0xFF0F3A2A),
            accent = Color(0xFF5DFFB0),
        ),
        // 5. 金色尘埃 (golden dust)
        GalaxyPalette(
            name = "金色尘埃",
            bgTop = Color(0xFF1A1405),
            bgMid = Color(0xFF2A200A),
            bgDeep = Color(0xFF3A2E10),
            accent = Color(0xFFFFC94D),
        ),
        // 6. 冰蓝深空 (ice blue deep space)
        GalaxyPalette(
            name = "冰蓝深空",
            bgTop = Color(0xFF04121E),
            bgMid = Color(0xFF082036),
            bgDeep = Color(0xFF0C304A),
            accent = Color(0xFF7FE0FF),
        ),
    )

    /** The palette for 1-based [galaxy], cycling every [palettes]`.size` galaxies. */
    fun forGalaxy(galaxy: Int): GalaxyPalette = palettes[Math.floorMod(galaxy - 1, palettes.size)]

    /** The palette for the galaxy containing 1-based [level] (see [LevelCatalog.galaxyOf]). */
    fun forLevel(level: Int): GalaxyPalette = forGalaxy(LevelCatalog.galaxyOf(level))
}
