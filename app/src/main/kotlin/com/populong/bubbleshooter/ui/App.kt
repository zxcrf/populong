package com.populong.bubbleshooter.ui

import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.populong.bubbleshooter.AppContainer
import com.populong.bubbleshooter.core.level.LevelCatalog
import com.populong.bubbleshooter.core.mode.GameMode
import com.populong.bubbleshooter.game.GameScreen
import com.populong.bubbleshooter.ui.menu.AchievementsScreen
import com.populong.bubbleshooter.ui.menu.DailyScreen
import com.populong.bubbleshooter.ui.menu.EndlessSetupScreen
import com.populong.bubbleshooter.ui.menu.GalaxyMapScreen
import com.populong.bubbleshooter.ui.menu.MainMenuScreen
import com.populong.bubbleshooter.ui.menu.SettingsScreen
import com.populong.bubbleshooter.ui.menu.StatsScreen

/** Top-level navigation state: which screen is currently shown. */
sealed interface Screen {
    /** The main menu / home screen. */
    data object Menu : Screen

    /** The constellation level map, reached from Menu's「关卡模式」row. */
    data object GalaxyMap : Screen

    /** Daily-challenge streak/calendar screen, reached from Menu's「每日挑战」row. */
    data object DailySetup : Screen

    /** Endless-mode mutator picker, reached from Menu's「无尽模式」row. */
    data object EndlessSetup : Screen

    /** The audio/haptics + about screen, reached from Menu's「⚙ 设置」button. */
    data object Settings : Screen

    /** The achievement list, reached from Menu's「🏆 成就」button. */
    data object Achievements : Screen

    /** The career-stats screen, reached from Menu's「📊 统计」button. */
    data object Stats : Screen

    /** An active game run in [mode]; [origin] is the screen to return to on exit. */
    data class Game(val mode: GameMode, val origin: Screen) : Screen
}

/**
 * The app's root composable. Owns screen-level navigation state and crossfades between the
 * main menu, the mode-setup screens, and an active game. [container] carries the process-lifetime
 * services (sound, haptics, save data) shared by every screen.
 */
@Composable
fun App(container: AppContainer) {
    var screen by remember { mutableStateOf<Screen>(Screen.Menu) }

    Crossfade(targetState = screen, label = "screen-crossfade") { current ->
        when (current) {
            is Screen.Menu -> MainMenuScreen(
                container = container,
                onPlayLevel = { screen = Screen.GalaxyMap },
                onPlayEndless = { screen = Screen.EndlessSetup },
                onPlayDaily = { screen = Screen.DailySetup },
                onOpenSettings = { screen = Screen.Settings },
                onOpenAchievements = { screen = Screen.Achievements },
                onOpenStats = { screen = Screen.Stats },
            )

            is Screen.Settings -> SettingsScreen(
                container = container,
                onBack = { screen = Screen.Menu },
            )

            is Screen.Achievements -> AchievementsScreen(
                container = container,
                onBack = { screen = Screen.Menu },
            )

            is Screen.Stats -> StatsScreen(
                container = container,
                onBack = { screen = Screen.Menu },
            )

            is Screen.GalaxyMap -> GalaxyMapScreen(
                container = container,
                onPick = { level ->
                    screen = Screen.Game(GameMode.Level(LevelCatalog.spec(level)), origin = Screen.GalaxyMap)
                },
                onBack = { screen = Screen.Menu },
            )

            is Screen.EndlessSetup -> EndlessSetupScreen(
                container = container,
                onStart = { mutators ->
                    screen = Screen.Game(GameMode.Endless(mutators), origin = Screen.EndlessSetup)
                },
                onBack = { screen = Screen.Menu },
            )

            is Screen.DailySetup -> DailyScreen(
                container = container,
                onStart = { mode -> screen = Screen.Game(mode, origin = Screen.DailySetup) },
                onBack = { screen = Screen.Menu },
            )

            is Screen.Game -> {
                val mode = current.mode
                val onNext: (() -> Unit)? = if (mode is GameMode.Level && mode.spec.id < LevelCatalog.TOTAL) {
                    {
                        screen = Screen.Game(
                            mode = GameMode.Level(LevelCatalog.spec(mode.spec.id + 1)),
                            origin = current.origin,
                        )
                    }
                } else {
                    null
                }
                GameScreen(
                    mode = mode,
                    container = container,
                    onExit = { screen = current.origin },
                    onNext = onNext,
                )
            }
        }
    }
}
