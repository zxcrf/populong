package com.populong.bubbleshooter.core.level

/**
 * Hand-authored levels 1-25 - progressive-disclosure tutorials, part 1.
 *
 * Teaching order: matching basics (1-3) -> falling clusters (4-6) -> bank shots (7-9)
 * -> showcase (10) -> rainbow (11) -> stone (12-14) -> palette5 stone mazes (15-17)
 * -> density ramp (18-19) -> boss (20) -> bomb (21) -> ice (22-24) -> ice+stone capstone (25).
 *
 * Shot budgets follow the brief's guidance: `ceil(N/2.2 + 5)` for tutorials 1-11, then
 * `ceil(N/2.2 + 3)` from 12 on (N = total placed bubbles); star thresholds are `N*10`,
 * `N*22`, `N*38` rounded to friendly numbers. L9 gets one extra shot over the raw formula
 * since it demands two separate banked pops, the hardest ask in the bank-shot trio.
 */
internal val levels1to25: List<LevelSpec> = listOf(
    // L1 「初次接触」 3 straight/blocky groups, can't miss. Stars: generous.
    level(
        id = 1, palette = 4, shots = 10, star1 = 100, star2 = 200, star3 = 400,
        grid = """
            R R R . B B B .
             G G G G . . .
        """,
    ),

    // L2 「错位三角」 offset (diagonal) 2+1 triangles teach hex adjacency across row parity.
    level(
        id = 2, palette = 4, shots = 16, star1 = 250, star2 = 550, star3 = 900,
        grid = """
            R R B B G G Y Y
             R . B . G . Y
            Y Y G G B B R R
             Y . G . B . R
        """,
    ),

    // L3 「穿隙一击」 a narrow ceiling gap hides a pocket; first taste of aiming precision.
    level(
        id = 3, palette = 4, shots = 11, star1 = 100, star2 = 250, star3 = 450,
        grid = """
            R R Y Y Y B B B
             R . . . B B B
        """,
    ),

    // L4 「悬挂的秘密」 pop the 3-bubble neck, watch the whole pendant fall for big points (falling > popping).
    level(
        id = 4, palette = 4, shots = 12, star1 = 150, star2 = 350, star3 = 550,
        grid = """
            . . R R R . . .
             . . B B B . .
            . G G G G G . .
             . Y Y Y Y . .
        """,
    ),

    // L5 「双吊坠」 two independent neck+pendant rigs; pick either, same lesson at bigger scale.
    level(
        id = 5, palette = 4, shots = 15, star1 = 200, star2 = 450, star3 = 750,
        grid = """
            R R R . . G G G
             B B B . Y Y Y
            G G G . . R R R
             . B . . . Y .
        """,
    ),

    // L6 「一网打尽」 one wide neck bridges a single mega-pendant: one shot, one huge cascade.
    level(
        id = 6, palette = 4, shots = 14, star1 = 200, star2 = 400, star3 = 700,
        grid = """
            . . R R R R . .
             . B B B Y Y .
            G G G B B Y Y Y
             . G . . . Y .
        """,
    ),

    // L7 「弹墙初探」 a corner pocket tucked beside the wall; first bank-off-the-wall shot.
    level(
        id = 7, palette = 4, shots = 10, star1 = 100, star2 = 250, star3 = 400,
        grid = """
            R R R . B B B .
             R . . . . . G
            . . . . . G G G
        """,
    ),

    // L8 「转角宝藏」 mirror of L7 on the other wall, slightly bigger normal-match warm-up too.
    level(
        id = 8, palette = 4, shots = 12, star1 = 150, star2 = 300, star3 = 550,
        grid = """
            . R R R . B B B
             Y . G . . . B
            Y Y G G G . . .
        """,
    ),

    // L9 「双重弹跳」 two separate corner pockets: needs a banked pop on each side to fully clear.
    level(
        id = 9, palette = 4, shots = 12, star1 = 100, star2 = 250, star3 = 450,
        grid = """
            R R . . . . B B
             R . . . . . B
            Y Y . . . . G G
             Y . . . . . G
        """,
    ),

    // L10 「盛装徽章」 big symmetric banded medallion; popping the middle band drops everything below it.
    level(
        id = 10, palette = 4, shots = 23, star1 = 400, star2 = 850, star3 = 1500,
        grid = """
            R R R R R R R .
             B B B B B B .
            G G G G G G G .
             Y Y Y Y Y Y .
            R R R R R R R .
             B B B B B B .
        """,
    ),

    // L11 「彩虹馈赠」 rainbowEvery=5; scattered awkward color pairs so the rainbow feels like a gift.
    level(
        id = 11, palette = 4, shots = 9, star1 = 100, star2 = 200, star3 = 300, rainbowEvery = 5,
        grid = """
            R R . B B . G G
             . . Y . . Y .
        """,
    ),

    // L12 「破顶石落」 a tiny stone bar hangs centered under a full-width colored roof; the only two
    // open slots (both roof ends) let a matching shot complete either color and drop the stone below.
    level(
        id = 12, palette = 4, shots = 9, star1 = 100, star2 = 250, star3 = 400,
        grid = """
            . R R R B B B .
             . S S S S S .
        """,
    ),

    // L13 「碎石阵」 two more stone nubs plus color pillars; stone keeps falling once undercut.
    level(
        id = 13, palette = 4, shots = 12, star1 = 200, star2 = 400, star3 = 700,
        grid = """
            R R R . B B B .
             S S . . S S .
            G G G . Y Y Y .
             S . . . . . S
        """,
    ),

    // L14 「石框」 a stone U-frame cages a color core; a gap in the floor lets a shot climb into the
    // hollow chamber to crack the B core, opening the path further up to undercut the roof and drop it all.
    level(
        id = 14, palette = 4, shots = 13, star1 = 200, star2 = 480, star3 = 800,
        grid = """
            . R R R R R R .
             S B B B B B S
            S . . . . . . S
             S S S . . S S
        """,
    ),

    // L15 「五彩石迷宫」 palette5 introduces purple; a stone lattice partitions five small color pockets.
    level(
        id = 15, palette = 5, shots = 13, star1 = 200, star2 = 450, star3 = 750,
        grid = """
            R R R . B B B .
             . S S S S S .
            G G G . Y Y Y .
             . P P P . . .
        """,
    ),

    // L16 「歪斜堡垒」 asymmetric: a stone nub under the roof on one side, a bank-shot pocket on the other.
    level(
        id = 16, palette = 5, shots = 10, star1 = 150, star2 = 300, star3 = 550,
        grid = """
            R R R . B B B B
             S S . . . . G
            S . . . . G G G
        """,
    ),

    // L17 「吊灯」 a single stone pivot (not a solid hub) leaves both roof gaps beside each gem open on
    // every arm; grow each pair through them, and clearing the roof drops the whole chandelier at once.
    level(
        id = 17, palette = 5, shots = 13, star1 = 200, star2 = 450, star3 = 750,
        grid = """
            R . . B B . . G
             . . . S . . .
            R R S B B S G G
             R S S B S S G
        """,
    ),

    // L18 「渐入佳境」 denser 5-row mix: matching, a stone-boxed pocket, and a hanging tier, tighter shots.
    level(
        id = 18, palette = 5, shots = 16, star1 = 250, star2 = 600, star3 = 1050,
        grid = """
            R R R R . P P P
             R . . . P P .
            S S B B B B S S
             S . G G G . S
            . . Y Y Y Y . .
        """,
    ),

    // L19 「步步为营」 L18's mix extended two rows deeper and tighter still, ramping toward the boss.
    level(
        id = 19, palette = 5, shots = 19, star1 = 350, star2 = 750, star3 = 1350,
        grid = """
            R R R R . P P P
             R . . . P P .
            S S B B B B S S
             S . G G G . S
            . . Y Y Y Y . .
             . S . . . S .
            . B B S S B B .
        """,
    ),

    // L20 「石垒」 BOSS: a symmetric stone fortress around a five-color core; bombEvery=8 is the siege weapon.
    level(
        id = 20, palette = 5, shots = 27, star1 = 500, star2 = 1100, star3 = 1950, bombEvery = 8,
        grid = """
            S . S S S S . S
             S P R R R P S
            S R . B B . R S
             S B G Y G B S
            . B G P . G B .
             S B G Y G B S
            S R . B B . R S
             S Y R R R Y S
        """,
    ),

    // L21 「爆破嘉年华」 bombEvery=6 on a big cheerful banded grid — pure demolition fun.
    level(
        id = 21, palette = 5, shots = 20, star1 = 350, star2 = 800, star3 = 1350, bombEvery = 6,
        grid = """
            R R R R R R . .
             B B B B B B .
            G G G G G G . .
             Y Y Y Y Y Y .
            R R R R R R . .
             B B B B B B .
        """,
    ),

    // L22 「冰封三卫」 three ice bubbles, each thawed by two different neighboring pops, guard a simple core.
    level(
        id = 22, palette = 5, shots = 10, star1 = 150, star2 = 350, star3 = 550,
        grid = """
            R R R . B B B .
             IG . . IR . . IY
            G G G . Y Y Y .
        """,
    ),

    // L23 「双冰阵」 four ice guardians and a fifth color (purple) raise the thawing puzzle a notch.
    level(
        id = 23, palette = 5, shots = 13, star1 = 200, star2 = 500, star3 = 850,
        grid = """
            R R R . B B B .
             IG . . . . . IY
            G G G . Y Y Y .
             IP . . . . . IR
            P P P . R R R .
        """,
    ),

    // L24 「冰墙合围」 a solid wall of ice needs its roof popped AND its floor popped — planned double-pops.
    level(
        id = 24, palette = 5, shots = 12, star1 = 200, star2 = 400, star3 = 700,
        grid = """
            . R R R R R R .
             IY IY IY IY IY IY IY
            . Y Y Y Y Y Y .
        """,
    ),

    // L25 「冰封石堡」 capstone: two ice walls sandwiched in a stone-framed keep, ~39 bubbles. The floor
    // and inner ice ring each leave two gaps so a climbing shot can reach every ring in turn, and
    // clearing the roof at the end drops the whole keep.
    level(
        id = 25, palette = 5, shots = 27, star1 = 400, star2 = 850, star3 = 1450,
        grid = """
            . . R R R R R .
             . . IY IY IY IY .
            S . B B B B B S
             S IG IG IG IG IG S
            S . G G G G G S
             S . Y Y Y . S
            S . . S S . . S
        """,
    ),
)
