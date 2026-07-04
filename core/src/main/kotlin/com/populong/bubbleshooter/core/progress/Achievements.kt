package com.populong.bubbleshooter.core.progress

import com.populong.bubbleshooter.core.level.LevelCatalog

/**
 * The definition of a single achievement: a stable [id], Chinese [title]/[description], and a
 * pure [check] over a [SaveData] snapshot deciding whether it is currently satisfied.
 */
data class AchievementDef(
    val id: String,
    val title: String,
    val description: String,
    val check: (SaveData) -> Boolean,
)

/** The full achievement catalog and evaluation logic. */
object Achievements {

    /** Every defined achievement, in a stable order. */
    val all: List<AchievementDef> = listOf(
        // -- Progression --
        AchievementDef(
            id = "level_1_cleared",
            title = "初露锋芒",
            description = "通关第 1 关。",
        ) { save -> (save.levels[1]?.stars ?: 0) >= 1 },
        AchievementDef(
            id = "level_20_cleared",
            title = "攻城略地",
            description = "通关第 20 关。",
        ) { save -> (save.levels[20]?.stars ?: 0) >= 1 },
        AchievementDef(
            id = "level_50_cleared",
            title = "势如破竹",
            description = "通关第 50 关。",
        ) { save -> (save.levels[50]?.stars ?: 0) >= 1 },
        AchievementDef(
            id = "level_100_cleared",
            title = "百关达成",
            description = "通关第 100 关。",
        ) { save -> (save.levels[100]?.stars ?: 0) >= 1 },
        AchievementDef(
            id = "level_500_cleared",
            title = "长征不辍",
            description = "通关第 500 关。",
        ) { save -> (save.levels[500]?.stars ?: 0) >= 1 },
        AchievementDef(
            id = "galaxy_10_unlocked",
            title = "星海征途",
            description = "解锁第 10 个星系。",
        ) { save -> save.highestUnlockedLevel() > 9 * LevelCatalog.GALAXY_SIZE },

        // -- Skill --
        AchievementDef(
            id = "combo_5",
            title = "连消大师",
            description = "单次连击达到 5 连。",
        ) { save -> save.stats.maxCombo >= 5 },
        AchievementDef(
            id = "combo_10",
            title = "连消宗师",
            description = "单次连击达到 10 连。",
        ) { save -> save.stats.maxCombo >= 10 },
        AchievementDef(
            id = "bank_shots_50",
            title = "弹无虚发",
            description = "累计完成 50 次弹射反弹命中。",
        ) { save -> save.stats.bankShots >= 50 },
        AchievementDef(
            id = "bubbles_dropped_1000",
            title = "雪崩制造者",
            description = "累计掉落 1000 个泡泡。",
        ) { save -> save.stats.bubblesDropped >= 1000 },
        AchievementDef(
            id = "bombs_detonated_25",
            title = "爆破专家",
            description = "累计引爆 25 个炸弹。",
        ) { save -> save.stats.bombsDetonated >= 25 },
        AchievementDef(
            id = "fevers_triggered_10",
            title = "烈焰狂潮",
            description = "累计触发 10 次狂热模式。",
        ) { save -> save.stats.feversTriggered >= 10 },

        // -- Collection --
        AchievementDef(
            id = "three_star_10",
            title = "摘星者",
            description = "获得 10 个三星关卡。",
        ) { save -> save.stats.threeStarLevels >= 10 },
        AchievementDef(
            id = "three_star_50",
            title = "星辰收藏家",
            description = "获得 50 个三星关卡。",
        ) { save -> save.stats.threeStarLevels >= 50 },
        AchievementDef(
            id = "shots_fired_1000",
            title = "百发百中",
            description = "累计弹射 1000 次。",
        ) { save -> save.stats.shotsFired >= 1000 },
        AchievementDef(
            id = "shots_fired_10000",
            title = "永不停歇",
            description = "累计弹射 10000 次。",
        ) { save -> save.stats.shotsFired >= 10000 },

        // -- Daily --
        AchievementDef(
            id = "daily_streak_3",
            title = "三日打卡",
            description = "每日挑战连续完成 3 天。",
        ) { save -> save.daily.streak >= 3 },
        AchievementDef(
            id = "daily_streak_7",
            title = "七日之约",
            description = "每日挑战连续完成 7 天。",
        ) { save -> save.daily.streak >= 7 },
        AchievementDef(
            id = "daily_completed_30",
            title = "持之以恒",
            description = "累计完成每日挑战 30 天。",
        ) { save -> save.daily.completedDays.size >= 30 },
    )

    private val byId: Map<String, AchievementDef> = all.associateBy { it.id }

    init {
        require(byId.size == all.size) { "Achievement ids must be unique" }
    }

    /** The ids of achievements newly satisfied by [save] that are not already in [SaveData.achievements]. */
    fun evaluate(save: SaveData): Set<String> {
        val satisfied = all.filter { it.check(save) }.mapTo(mutableSetOf()) { it.id }
        return satisfied - save.achievements
    }
}
