# 泡泡星河 Nebula Bubbles

一款现代化、精致的泡泡龙（Bubble Shooter）Android 游戏：深空霓虹视觉、2000 关星系征程、程序合成音效，完全本地离线。

## 玩法特性

- **关卡模式（2000 关）**：前 50 关手工设计、渐进式引导每个机关登场；51 关起由种子化程序生成（确定性——同一关卡永远相同），每 20 关一个星系章节，每关 1–3 星评价，星座星图选关。
- **无尽模式**：天花板逐步下压拼生存高分，可叠加变异修饰符（加速下压/第五色/短瞄准线/窄场地）换取分数倍率。
- **每日挑战**：按日期种子生成的每日专属关卡 + 连签日历。
- **手感**：反弹路径完整预览瞄准线、点击发射器交换泡泡、长按进入慢动作精准瞄准、反弹入库（bank shot）加分、Fever 连击燃烧模式。
- **特殊泡泡**：炸弹泡、彩虹泡（弹药）；石头、冰冻（两次解冻）、迷雾（靠近显色）、锁链（锚定网格，需相邻消除解锁）（机关）。
- **元游戏**：19 项成就、生涯统计、本地最高分，全部持久化到本地存档。
- **音频/触感**：全部音效由代码合成（AudioTrack PCM，无任何版权素材），连击音阶逐级爬升；震动反馈按 API 级别优雅降级。

## 技术架构

| 层 | 内容 |
|---|---|
| `:core`（纯 Kotlin JVM） | 六边形网格（odd-r offset + floor-mod 奇偶）、匹配/悬浮 BFS、确定性游戏引擎（固定 1/120s tick 的纯 reducer，瞄准预览与弹道模拟共享同一碰撞模型）、SplitMix64 RNG、关卡 DSL 与程序生成器、GreedyBot 求解器、存档模型与成就 |
| `:app`（Jetpack Compose） | 单 Activity；`withFrameNanos` 驱动固定步进循环，每帧只递增一个 `MutableLongState` 失效单个 Canvas；泡泡精灵预烘焙 ImageBitmap；零分配粒子池；星系星图用 LazyColumn×100 个 Canvas 绘制 2000 节点 |

工程约定：

- `:app` 仅在检测到 Android SDK 时参与构建（`settings.gradle.kts` 条件 include），无 SDK 环境可直接 `./gradlew test` 跑全部核心逻辑测试（约 240 项）。
- 关卡质量由测试护栏保证：生成器黄金哈希（防漂移）、结构合理性检查、GreedyBot 可玩性验证（抽样关卡必须在 1.5× 弹药预算内可解，boss 关不得过易）；50 手工关全部逐关验证可解。
- 同种子 + 同输入序列 ⇒ 完全相同的对局，任何 bug 可用「关卡号 + 输入脚本」在 JVM 测试中精确复现。

## 构建

```bash
# 核心逻辑测试（无需 Android SDK）
./gradlew :core:test

# 构建可安装的 release APK（需 Android SDK；CI 自动执行并上传 artifact）
./gradlew :app:assembleRelease
```

CI（GitHub Actions）在每次 push 时运行核心测试并产出 debug 签名的 release APK（minSdk 26 / targetSdk 35）。

## 版本

- Kotlin 2.1.0 · Compose BOM 2024.12.01 · AGP 8.7.3 · Gradle 8.14.3
