package com.populong.bubbleshooter.core.level


/**
 * Hand-authored levels 26-50 — progressive disclosure part 2.
 *
 * Arc: ice mastery (26-31) -> fog intro & mastery (32-37) -> pre-boss ramp and the first BOSS
 * (38-40) -> chain intro & puzzles (41-46) -> grand mastery and the mid-galaxy-3 showcase (47-50).
 *
 * Every level below is solvable within its shot budget without relying on any granted specials
 * (bomb/rainbow), and every color that appears (including colors locked behind ice/fog/chain)
 * has at least 3 total poppable cells once thawed/revealed/unlocked.
 */

internal val levels26to50: List<LevelSpec> = listOf(
    // L26 「冰环」 ice ring around a core — pop the border colors to crack the ring twice per cell and reach the purple heart.
    level(
        id = 26, palette = 5, shots = 20,
        star1 = 380L, star2 = 840L, star3 = 1440L,
        grid = """
        G R IB IB IB IB R G
         G R IB IB IB R G
        G IB P P P P IB G
         G IB P P P IB G
        G R IB IB IB IB R G
        """,
    ),
    // L27 「冰彩条纹」 alternating ice/color stripes — a single floor gap lets the first shot reach the
    // P stripe directly; popping it cracks both neighboring ice rows at once, and rebuilding a match
    // in the freed row finishes each crack — plan double-pops through the stack from there. The full
    // six-layer stack needs extra shots over the usual formula to work all the way up.
    level(
        id = 27, palette = 5, shots = 34,
        star1 = 450L, star2 = 990L, star3 = 1710L,
        grid = """
        R R R R R R R R
         IB IB IB IB IB IB IB
        G G G G G G G G
         IY IY IY IY IY IY IY
        P P P P P P P P
         IB IB IB . . IB IB
        """,
    ),
    // L28 「冰吊灯」 an ice chandelier hangs by two thin necks — free the necks, thaw them, then match to drop the pendant for a fall bonus.
    level(
        id = 28, palette = 5, shots = 19,
        star1 = 360L, star2 = 790L, star3 = 1370L,
        grid = """
        . R . . . . R .
         . R . . . R .
        . R . . . . R .
         R IB G G G IB R
        R IB P P P P IB R
         R IB P P P IB R
        R R IB IB IB IB R R
        """,
    ),
    // L29 「冰石回廊」 ice pairs behind a stone spine — bank shots around the stone to find and crack the ice pockets.
    level(
        id = 29, palette = 5, shots = 19,
        star1 = 360L, star2 = 790L, star3 = 1370L,
        grid = """
        R S IY IY IY IY S R
         R S IY IY IY S R
        B S G G G G S B
         B . G G G . B
        B R IY IY IY IY R B
        """,
    ),
    // L30 「冰宫」 midpoint spectacle — a big symmetric ice palace; rainbowEvery=6 grants periodic mercy through its layered walls.
    level(
        id = 30, palette = 5, shots = 30,
        star1 = 600L, star2 = 1320L, star3 = 2280L,
        rainbowEvery = 6,
        grid = """
        P P IB IB IB IB P P
         P IB R R R IB P
        P IB R G G R IB P
         IB R G G G R IB
        IB R G Y Y G R IB
         R G Y Y Y G R
        R G Y IY IY Y G R
         G Y IY IY IY Y G
        """,
    ),
    // L31 「窄巷」 stone walls carve tight lanes — precision aim matters as much as color matching.
    level(
        id = 31, palette = 5, shots = 17,
        star1 = 300L, star2 = 660L, star3 = 1140L,
        grid = """
        S R R . . R R S
         S B . . . B S
        S B G . . G B S
         S G . . . G S
        S G Y . . Y G S
         S Y . . . Y S
        """,
    ),
    // L32 「迷雾口袋」 fog intro — a small fog pocket (7 cells) hides behind a plain wall; land within two cells to scout it before committing.
    level(
        id = 32, palette = 5, shots = 20,
        star1 = 380L, star2 = 840L, star3 = 1440L,
        grid = """
        R R G G G G R R
         R B G B G B R
        B B FY FY FY FY B B
         B FY G FY G FY B
        R R G G G G R R
        """,
    ),
    // L33 「迷雾帘幕」 a fog curtain spans the middle row — clear a path and reveal it wide before the colors underneath can be read.
    level(
        id = 33, palette = 5, shots = 23,
        star1 = 450L, star2 = 990L, star3 = 1710L,
        grid = """
        R R G G G G R R
         R B G B G B R
        FP FP FP FP FP FP FP FP
         FP FP FP FP FP FP FP
        B B Y Y Y Y B B
         B G Y G Y G B
        """,
    ),
    // L34 「雾冰交织」 fog and ice interwoven — revealed fog may still need a thawed ice partner of the same color; plan reveals and thaws together.
    level(
        id = 34, palette = 5, shots = 20,
        star1 = 380L, star2 = 840L, star3 = 1440L,
        grid = """
        R R G G G G R R
         R IB G IB G IB R
        B IB FY FY FY FY IB B
         B FY P FY P FY B
        B B P P P P B B
        """,
    ),
    // L35 「迷雾回廊」 fog mastery — stone corridors force bank shots that land deep inside to scout and clear the fog beyond.
    level(
        id = 35, palette = 5, shots = 16,
        star1 = 290L, star2 = 640L, star3 = 1100L,
        grid = """
        S R R . . R R S
         S B . . . B S
        S FB FB . . FB FB S
         S FB G G G FB S
        S G G . . G G S
        """,
    ),
    // L36 「迷雾守卫」 a fog boss-lite flanked by stone ribs — a floor gap lets the first shot climb into
    // the Y core; the roof arches unbroken over both ribs so clearing a path up through the guard's
    // body eventually reaches it, and popping the whole roof drops every rib and flank at once. Wide
    // reveals are needed to read the whole guard before popping it.
    level(
        id = 36, palette = 5, shots = 27,
        star1 = 530L, star2 = 1170L, star3 = 2010L,
        grid = """
        R R R R R R R R
         R R R R R R R
        G S FP FP FP FP S G
         G S FP Y FP S G
        G S Y Y Y Y S G
         B S Y Y Y S B
        B B R . . R B B
        """,
    ),
    // L37 「雾色六域」 palette=6 debut — orange joins inside a wide fog field; rainbowEvery=7 is a mercy shot when the fog gets thick.
    level(
        id = 37, palette = 6, shots = 23,
        star1 = 450L, star2 = 990L, star3 = 1710L,
        rainbowEvery = 7,
        grid = """
        R O G G G G O R
         R G G G G G R
        O FG FY FY FY FY FG O
         O FY FY FY FY FY O
        B B P P P P B B
         B P P P P P B
        """,
    ),
    // L38 「层层考验 I」 pre-boss ramp — ice, fog and stone all appear together, denser and taller.
    level(
        id = 38, palette = 6, shots = 27,
        star1 = 530L, star2 = 1170L, star3 = 2010L,
        grid = """
        R S IB IB IB IB S R
         R IB O O O IB R
        G IB FO FO FO FO IB G
         G FO FO P FO FO G
        Y Y P P P P Y Y
         Y S P P P S Y
        B B O O O O B B
        """,
    ),
    // L39 「层层考验 II」 pre-boss ramp — an even denser mix across eight rows, tightening the margin before the boss.
    level(
        id = 39, palette = 6, shots = 30,
        star1 = 600L, star2 = 1320L, star3 = 2280L,
        grid = """
        S R IY IY IY IY R S
         S IY B B B IY S
        G IY FB FB FB FB IY G
         G FB FB O FB FB G
        P P O O O O P P
         P S O R O S P
        Y Y B B B B Y Y
         Y G B B B G Y
        """,
    ),
    // L40 「霧冰城」 BOSS — a galaxy-2 finale citadel of fog, ice and stone buttresses; bombEvery=8 helps punch through, and this is the game's FIRST descentEveryShots (10) — the ceiling-compression pressure debut.
    level(
        id = 40, palette = 6, shots = 34,
        star1 = 680L, star2 = 1500L, star3 = 2580L,
        bombEvery = 8,
        descentEveryShots = 10,
        grid = """
        S IB IB IB IB IB IB S
         S IB R R R IB S
        R IB FP FP FP FP IB R
         R FP FP FP FP FP R
        R G FY FY FY FY G R
         G G FY FY FY G G
        G O O Y Y O O G
         O O Y Y Y O O
        B B P P P P B B
        """,
    ),
    // L41 「铁链吊坠」 chained intro — a single chained bubble anchors a fat pendant that will not fall until the chain is popped loose by an adjacent match.
    level(
        id = 41, palette = 5, shots = 14,
        star1 = 250L, star2 = 550L, star3 = 950L,
        grid = """
        . R R R . . . .
         R . . . . . .
        . CG G G G G G .
         G G G G G G G
        G G G G G G G G
        """,
    ),
    // L42 「双锁次序」 two chain colors must be unlocked in the right order to keep the structure manageable.
    level(
        id = 42, palette = 5, shots = 20,
        star1 = 380L, star2 = 840L, star3 = 1440L,
        grid = """
        R R R R R R R R
         R CB R R R CB R
        B B CY Y Y CY B B
         B Y Y Y Y Y B
        G G Y Y Y Y G G
        """,
    ),
    // L43 「锁链石板」 the chain holds up a heavy stone slab — unlock it, cut it loose, and enjoy the crash.
    level(
        id = 43, palette = 5, shots = 12,
        star1 = 190L, star2 = 420L, star3 = 720L,
        grid = """
        G G G . R R R .
         G . . . R . .
        . . . . CG . B B
         . . . S S B B
        . . S S S S . .
        """,
    ),
    // L44 「吊桥」 chain puzzles — chains anchor both ends of a stone bridge; free either end and the span drops.
    level(
        id = 44, palette = 5, shots = 27,
        star1 = 530L, star2 = 1170L, star3 = 2010L,
        grid = """
        CR R R R R R R CR
         R B B B B B R
        R B S S S S B R
         B S S S S S B
        G G S S S S G G
         G B B B B B G
        G G B CB CB B G G
        """,
    ),
    // L45 「迷雾宝库」 chains guard a fog-filled vault — unlock the chains to reach in and reveal what is hidden.
    level(
        id = 45, palette = 5, shots = 27,
        star1 = 530L, star2 = 1170L, star3 = 2010L,
        grid = """
        R CR R R R R CR R
         R R R R R R R
        G G FP FP FP FP G G
         G FP FP FP FP FP G
        G G FP FP FP FP G G
         Y Y Y Y Y Y Y
        Y Y CY Y Y CY Y Y
        """,
    ),
    // L46 「锁链寒冰」 chains plus ice at palette=6 — free the chain, then thaw the ice trapped behind it.
    level(
        id = 46, palette = 6, shots = 27,
        star1 = 530L, star2 = 1170L, star3 = 2010L,
        grid = """
        O CO O O O O CO O
         O O O O O O O
        B B IY IY IY IY B B
         B IY IY IY IY IY B
        G G IY IY IY IY G G
         G P P P P P G
        P P CP P P CP P P
        """,
    ),
    // L47 「万法归宗 I」 grand mastery — stone, ice, fog and chains interleave for the first all-mechanics test.
    level(
        id = 47, palette = 6, shots = 27,
        star1 = 530L, star2 = 1170L, star3 = 2010L,
        grid = """
        R CR IB IB IB IB CR R
         R IB O O O IB R
        R IB FO FO FO FO IB R
         R FO FO G FO FO R
        G G O O O O G G
         G S O O O S G
        Y Y P CP CP P Y Y
        """,
    ),
    // L48 「万法归宗 II」 grand mastery — the same mix, denser, with rainbowEvery=8 as the only mercy.
    level(
        id = 48, palette = 6, shots = 27,
        star1 = 530L, star2 = 1170L, star3 = 2010L,
        rainbowEvery = 8,
        grid = """
        CB B IY IY IY IY B CB
         B IY G G G IY B
        R IY FG FG FG FG IY R
         R FG FG O FG FG R
        O O P P P P O O
         O S P P P S O
        Y Y CY Y Y CY Y Y
        """,
    ),
    // L49 「极限考验」 the hardest hand-authored level — near-boss density with descentEveryShots=12 keeping constant pressure.
    level(
        id = 49, palette = 6, shots = 34,
        star1 = 680L, star2 = 1500L, star3 = 2580L,
        descentEveryShots = 12,
        grid = """
        G CG IB IB IB IB CG G
         S IB P P P IB S
        R IB FP FP FP FP IB R
         R FP FP FP FP FP R
        R O FY FY FY FY O R
         O O FY FY FY O O
        O S S Y Y S S O
         B S Y Y Y S B
        B B CP P P CP B B
        """,
    ),
    // L50 「星河大观」 SPECIAL — mid-galaxy-3 showcase: a deliberately beautiful symmetric composition using every obstacle type at once, a victory lap that still demands real play.
    level(
        id = 50, palette = 6, shots = 34,
        star1 = 680L, star2 = 1500L, star3 = 2580L,
        bombEvery = 9,
        grid = """
        CB IB IB IB IB IB IB CB
         B IB P P P IB B
        R IB FP FP FP FP IB R
         R S FP FP FP S R
        G S FY FY FY FY S G
         G G FY FY FY G G
        Y O O P P O O Y
         O O P P P O O
        Y Y CP P P CP Y Y
        """,
    ),
)
