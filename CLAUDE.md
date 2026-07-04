# CLAUDE.md — 泡泡星河 开发指南

写给接手本仓库的下一个 agent/开发者。先读完本文再动手。

## 一分钟全貌

Kotlin 泡泡龙 Android 游戏，双模块：

- **`:core`** — 纯 Kotlin JVM（零 Android 依赖）。全部游戏逻辑：六边形网格、匹配/悬浮 BFS、确定性引擎（纯 reducer）、物理与瞄准、2000 关生成器、GreedyBot 求解器、存档模型、成就。**所有可测逻辑都在这里**。
- **`:app`** — Jetpack Compose，仅渲染与接线：单 Activity、`withFrameNanos` 固定步进循环、单 Canvas 渲染、精灵烘焙、粒子/弹簧特效、程序合成音效与 BGM、各 UI 屏、文件存档。

## 构建与验证（重要：环境无 Android SDK 时）

```bash
# 核心逻辑全量测试 —— 本地唯一验证手段，任何 core 改动后必跑
/opt/gradle/bin/gradle :core:test        # 开发容器内（勿用 ./gradlew：代理挡分发下载）
./gradlew :core:test                     # 普通环境

# :app 只能靠 CI 编译：push 到 main / claude/** 即触发 .github/workflows/build.yml
# 产出 debug 签名的 release APK artifact（bubble-shooter-apk），可直接装机
```

`settings.gradle.kts` 按 `ANDROID_HOME` 条件包含 `:app`——无 SDK 环境下 gradle 完全不解析 AGP。

**Compose 代码无法本地编译**，历史上 CI 抓过的高频错误（写 `:app` 时自查）：
1. `by` 委托缺 `import androidx.compose.runtime.getValue`（`var` 还要 `setValue`）
2. `graphicsLayer` 在 `androidx.compose.ui.graphics`（不是 `ui.draw`）
3. `PaddingValues.calculateBottomPadding()` 是成员方法，不可 import
4. 公开 `inline` 函数引用的字段/常量须 `@PublishedApi internal`
5. `Bubble`/`GameEvent` 是 sealed——新增变体后，**全仓库搜索 exhaustive `when`** 补分支（app 侧 CI 才会报）

## 不可破坏的核心约定

1. **确定性**：同种子 + 同输入序列 ⇒ 完全相同对局。引擎内禁止 `kotlin.random`/时间源，只用 `level/Rng.kt`（SplitMix64）。任何 bug 都可用「关卡号+输入脚本」写成 JVM 回归测试。
2. **预览不说谎**：`AimPath`（瞄准线）与 `ProjectileSim`（真实弹道）共享 `CollisionModel` 步进器。加任何影响弹道的机制（如引力井、虫洞）必须改在共享层，并扩展 AimPathTest 的一致性角度表。
3. **黄金哈希**：`LevelGeneratorTest` 固定了抽样关卡的生成哈希，防生成器漂移。改生成器时：机关 gate 之前的关卡哈希应不变（保留一个低关卡作 stability witness），gate 之后重定并在注释里说明原因。
4. **存档前向兼容**：`SaveData` 走 kotlinx-serialization `ignoreUnknownKeys` + 全字段默认值。新字段必须带默认值；**枚举（如 Mutator）不可改名**（存档里序列化的是名字，改名会让旧档解码失败并被兜底清空）。
5. **可玩性护栏**：`GreedyBotPlayabilityTest` 要求抽样生成关 ≥90% 在 1.5× 弹药预算内可解、50 手工关 100% 可解。生成器调参以让它变绿为准，别放松阈值。
6. **渲染热路径零分配**：单 Canvas 每帧一次失效（`frameTick`）；粒子/掉落球是 SoA 池；不给每个泡泡建 composable。

## 新增一个网格机关的标准清单（照 Supernova/Pulsar 的先例）

1. `grid/Bubble.kt` 加 sealed 变体 + `matchableColor()` 语义
2. 行为接入：`GameEngine`（消解流程）/ `ObstacleRules`（副作用）/ `BubbleGrid.anchored()`（若影响锚定）/ CollisionModel（若影响弹道）
3. `LevelDsl` 加 token（现有：`.` 空、颜色 `R B G Y P O`、`S` 石头、`I<c>` 冰、`F<c>` 雾、`C<c>` 锁链、`N<c>` 超新星、`U<c>` 脉冲星、`G` 引力井、`W1/W2` 虫洞对）
4. `DifficultyCurve` 加 gate 与密度、`LevelGenerator` 放置规则、`GeneratorSanityTest` 加规则、黄金哈希按第 3 条约定处理
5. `GreedyBot` 估值（多数情况走 `matchableColor()` 自动正确）
6. `:app`：BubbleSprites 精灵分支、Effects 事件特效、GameSessionHolder 事件 `when` 分支、星图预览弹窗机关 chips（GalaxyMapScreen）
7. 引擎脚本化测试 + 确定性回归

## 目录导航

```
core/src/main/kotlin/com/populong/bubbleshooter/core/
  grid/      Bubble 类型、六边形网格（odd-r offset，行可为负，floor-mod 奇偶）、几何
  match/     匹配/悬浮 BFS、机关副作用
  physics/   AimPath + ProjectileSim（共享 CollisionModel）
  engine/    GameEngine 纯 reducer、GameState/Event/Input/Config、计分
  mode/      Level/Endless/Daily、Mutator（无尽加倍项）
  level/     Rng、DSL、50 手工关、难度曲线、生成器、AimGuide（引导线递减）、GreedyBot、LevelCatalog
  progress/  SaveData/SaveCodec、成就、生涯统计
app/src/main/kotlin/com/populong/bubbleshooter/
  game/      GameScreen（布局/手势/接线）、GameSessionHolder（固定步进桥 + 事件分发）
  render/    GameRenderer（单帧全绘制）、BubbleSprites（烘焙精灵）、BackgroundRenderer（星空/流星）
  fx/        ParticlePool、FallingBubbles（掉落物理球）、Effects（弹簧/泼溅/震屏/闪光协调器）
  audio/     SoundSynth（合成音效）、MusicSynth（三首 BGM）、SfxPlayer、MusicPlayer（流式循环）
  ui/        App（导航中枢 navigateBack + BackHandler 统一返回）、theme/（Neon + GalaxyTheme 六套星系配色）、menu/ 各屏
  data/      SaveRepository（文件 + StateFlow）
```

## UI 约定

- 全部界面套安全区（statusBars/navigationBars padding），背景全出血
- 返回统一：每屏左上角 48dp 返回 = 系统手势返回（`App.navigateBack()` + 各屏 BackHandler；对局内返回=暂停/退出确认；弹窗打开时返回=先关弹窗）
- 触控热区 ≥48dp 且所见即热区；中文文案；深空霓虹视觉语言（Neon tokens + NeonPanel）
- 音频：全部程序合成零素材；BGM 用 MODE_STREAM 流式循环（MODE_STATIC 会超设备缓冲上限——已踩坑）

## 工具链

Kotlin 2.1.0 / Compose BOM 2024.12.01 / AGP 8.7.3 / Gradle 8.14.3（wrapper 入库）。版本成套锁定，只整体升级。

## 当前进度与待办

见 git log 与 `.github/workflows/build.yml`。截至本文更新：三轮迭代完成（基础游戏 → 安全区/导航/BGM → 引导线递减/超新星/动画手感/引力井/虫洞/脉冲星）。计划中的后续：星云迷雾视觉升级、无尽彗星彩蛋、星尘+星座图鉴（设计见 git 历史中的 plan 讨论）。
