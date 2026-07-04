package com.populong.bubbleshooter.core.progress

/**
 * The definition of a single collectible constellation: a stable [id], Chinese [title]/[description]
 * (with a touch of lore), a stardust [cost] to unlock via [unlockConstellation], and a hand-authored
 * star chart.
 *
 * [stars] are normalized `(x, y)` coordinates in `0f..1f` (origin top-left, matching Compose's
 * [androidx.compose.ui.geometry.Offset] convention once scaled by a canvas size), one per star.
 * [lines] connect stars by index into [stars] to sketch the constellation's traditional shape.
 */
data class ConstellationDef(
    val id: String,
    val title: String,
    val description: String,
    val cost: Long,
    val stars: List<Pair<Float, Float>>,
    val lines: List<Pair<Int, Int>>,
)

/**
 * The full constellation catalog: 12 real constellations, unlockable with stardust earned from
 * play (see [earnStardust]), at ascending costs from 200 to 3000.
 */
object Constellations {

    /** Every defined constellation, in ascending [ConstellationDef.cost] order. */
    val all: List<ConstellationDef> = listOf(
        ConstellationDef(
            id = "dipper",
            title = "北斗七星",
            description = "由七颗亮星组成的勺形星群，自古是华夏先民辨别方向、四季更替的重要坐标。",
            cost = 200,
            stars = listOf(
                0.15f to 0.55f, // 天枢 Dubhe
                0.20f to 0.72f, // 天璇 Merak
                0.38f to 0.75f, // 天玑 Phecda
                0.42f to 0.58f, // 天权 Megrez
                0.58f to 0.50f, // 玉衡 Alioth
                0.74f to 0.40f, // 开阳 Mizar
                0.90f to 0.28f, // 摇光 Alkaid
            ),
            lines = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 0, 3 to 4, 4 to 5, 5 to 6),
        ),
        ConstellationDef(
            id = "lyra",
            title = "天琴座",
            description = "主星织女星是七夕传说中巧手的织女，隔着一道银河与牛郎遥遥相望。",
            cost = 350,
            stars = listOf(
                0.30f to 0.15f, // 织女星 Vega
                0.45f to 0.40f,
                0.68f to 0.38f,
                0.72f to 0.62f,
                0.48f to 0.65f,
            ),
            lines = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 1),
        ),
        ConstellationDef(
            id = "crux",
            title = "南十字座",
            description = "南半球夜空的航海指南针，四颗主星勾勒出一个精巧而醒目的十字。",
            cost = 500,
            stars = listOf(
                0.50f to 0.10f, // 十字架三 Gacrux
                0.50f to 0.82f, // 十字架二 Acrux
                0.18f to 0.45f, // 十字架一 Mimosa
                0.82f to 0.45f, // 十字架四 Delta Crucis
                0.58f to 0.50f, // 十字架增九 Epsilon Crucis
            ),
            lines = listOf(0 to 1, 2 to 3, 4 to 3),
        ),
        ConstellationDef(
            id = "aries",
            title = "白羊座",
            description = "金羊毛传说的主角，几颗星连成一道低垂的弧线，稳居黄道十二宫之首。",
            cost = 650,
            stars = listOf(
                0.18f to 0.62f, // 娄宿三 Hamal
                0.45f to 0.45f, // 娄宿一 Sheratan
                0.66f to 0.40f, // 娄宿二 Mesarthim
                0.82f to 0.55f,
            ),
            lines = listOf(0 to 1, 1 to 2, 2 to 3),
        ),
        ConstellationDef(
            id = "cassiopeia",
            title = "仙后座",
            description = "王后端坐王座之上，五星连成的 W 形终年绕着北极星转动不息。",
            cost = 850,
            stars = listOf(
                0.08f to 0.60f,
                0.30f to 0.28f,
                0.50f to 0.55f,
                0.70f to 0.22f,
                0.92f to 0.48f,
            ),
            lines = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4),
        ),
        ConstellationDef(
            id = "cygnus",
            title = "天鹅座",
            description = "如展翅的天鹅掠过银河，主星天津四是夏夜大三角的顶点之一，终年不落。",
            cost = 1050,
            stars = listOf(
                0.50f to 0.08f, // 天津四 Deneb
                0.50f to 0.45f, // 天津一 Sadr
                0.50f to 0.88f, // 辇道增七 Albireo
                0.18f to 0.55f, // 左翼
                0.82f to 0.55f, // 右翼
            ),
            lines = listOf(0 to 1, 1 to 2, 3 to 1, 1 to 4),
        ),
        ConstellationDef(
            id = "gemini",
            title = "双子座",
            description = "卡斯托耳与波吕克斯，希腊神话中形影不离的孪生兄弟，化作并肩的双星链。",
            cost = 1300,
            stars = listOf(
                0.25f to 0.08f, // 北河二 Castor 头
                0.22f to 0.35f,
                0.20f to 0.62f,
                0.18f to 0.90f, // Castor 足
                0.58f to 0.12f, // 北河三 Pollux 头
                0.60f to 0.40f,
                0.62f to 0.66f,
                0.64f to 0.90f, // Pollux 足
            ),
            lines = listOf(0 to 1, 1 to 2, 2 to 3, 4 to 5, 5 to 6, 6 to 7, 0 to 4),
        ),
        ConstellationDef(
            id = "orion",
            title = "猎户座",
            description = "腰间三星并列如带，是冬夜最容易辨认的猎人身影，肩扛参宿四、脚踏参宿七。",
            cost = 1550,
            stars = listOf(
                0.25f to 0.15f, // 参宿四 Betelgeuse
                0.72f to 0.20f, // 参宿五 Bellatrix
                0.35f to 0.48f, // 参宿三 Mintaka
                0.50f to 0.50f, // 参宿二 Alnilam
                0.65f to 0.52f, // 参宿一 Alnitak
                0.30f to 0.88f, // 参宿六 Saiph
                0.70f to 0.90f, // 参宿七 Rigel
            ),
            lines = listOf(0 to 2, 1 to 4, 2 to 3, 3 to 4, 2 to 5, 4 to 6),
        ),
        ConstellationDef(
            id = "leo",
            title = "狮子座",
            description = "镰刀状的头部与三角形的身躯，每年十一月的狮子座流星雨正从这头巨兽的鬃毛间倾泻而下。",
            cost = 1800,
            stars = listOf(
                0.15f to 0.68f, // 轩辕十四 Regulus
                0.13f to 0.52f,
                0.20f to 0.36f,
                0.32f to 0.28f,
                0.42f to 0.36f, // 轩辕十二 Algieba
                0.62f to 0.55f, // 五帝座一 Zosma
                0.88f to 0.62f, // 五帝座一 Denebola
            ),
            lines = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 0 to 5, 5 to 6),
        ),
        ConstellationDef(
            id = "scorpius",
            title = "天蝎座",
            description = "心宿二如烈焰般燃烧在蝎子的胸口，弯曲上扬的尾巴末端，藏着致命的毒刺。",
            cost = 2100,
            stars = listOf(
                0.12f to 0.10f,
                0.24f to 0.08f,
                0.20f to 0.26f, // 心宿二 Antares
                0.30f to 0.42f,
                0.42f to 0.56f,
                0.56f to 0.66f,
                0.70f to 0.70f,
                0.82f to 0.60f,
                0.90f to 0.44f, // 尾宿八 尾刺
            ),
            lines = listOf(0 to 2, 1 to 2, 2 to 3, 3 to 4, 4 to 5, 5 to 6, 6 to 7, 7 to 8),
        ),
        ConstellationDef(
            id = "pegasus",
            title = "飞马座",
            description = "珀伽索斯振翅欲飞，四颗星围成的秋季大四边形，是夜空中最醒目的路标之一。",
            cost = 2450,
            stars = listOf(
                0.20f to 0.25f, // 室宿二 Scheat
                0.58f to 0.22f, // 室宿一 Markab
                0.60f to 0.58f,
                0.22f to 0.60f,
                0.78f to 0.12f, // 颈
                0.94f to 0.05f, // 头
            ),
            lines = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 0, 1 to 4, 4 to 5),
        ),
        ConstellationDef(
            id = "draco",
            title = "龙座",
            description = "蜿蜒盘绕在北天极附近的巨龙，自古守护着传说中的金苹果园，从不曾睡去。",
            cost = 3000,
            stars = listOf(
                0.10f to 0.85f,
                0.20f to 0.68f,
                0.16f to 0.48f,
                0.26f to 0.32f,
                0.40f to 0.20f,
                0.55f to 0.15f,
                0.70f to 0.20f,
                0.82f to 0.34f,
                0.76f to 0.55f, // 龙首
            ),
            lines = listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 5, 5 to 6, 6 to 7, 7 to 8),
        ),
    )

    init {
        require(all.map { it.id }.toSet().size == all.size) { "Constellation ids must be unique" }
        require(all.zipWithNext().all { (a, b) -> a.cost <= b.cost }) { "Constellation costs must be ascending" }
    }
}
