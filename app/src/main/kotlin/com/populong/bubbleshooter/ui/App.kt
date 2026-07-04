package com.populong.bubbleshooter.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

    /**
     * Single source of truth for "go back" from any menu-tier screen (galaxy map, the two mode
     * setup screens, settings, achievements, stats): all of them return straight to [Screen.Menu].
     * [Screen.Game] is unreachable here — [BackHandler] below is disabled while it's showing, since
     * `GameScreen` owns its own back handling and in-game exit-confirm dialog. Falling through to
     * [Screen.Menu] returns `Unit` so the system's default back behavior (leave the app) applies,
     * i.e. we deliberately don't intercept back from the home screen.
     */
    fun navigateBack() {
        screen = when (val s = screen) {
            is Screen.Game -> s
            Screen.Menu -> return
            else -> Screen.Menu
        }
    }

    // Duck background music while a game run is active; restore it the moment we leave one.
    // AppContainer.music/setScene land with Agent C's changes.
    LaunchedEffect(screen) { container.music.setScene(screen is Screen.Game) }

    // Unified Android gesture-/button-back for every non-Game, non-Menu screen. `GameScreen`
    // registers its own BackHandler for its in-game exit-confirm dialog; the OnBackPressedDispatcher
    // is a LIFO stack, and Compose composes GameScreen's content (and therefore its BackHandler)
    // *after* this one, so while a Game screen is showing its handler is registered later and wins
    // regardless of this one's `enabled` state — but we still gate on `screen !is Screen.Game`
    // here for clarity and to avoid ever having two enabled back callbacks active at once.
    BackHandler(enabled = screen != Screen.Menu && screen !is Screen.Game) { navigateBack() }

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
                onBack = { navigateBack() },
            )

            is Screen.Achievements -> AchievementsScreen(
                container = container,
                onBack = { navigateBack() },
            )

            is Screen.Stats -> StatsScreen(
                container = container,
                onBack = { navigateBack() },
            )

            is Screen.GalaxyMap -> GalaxyMapScreen(
                container = container,
                onPick = { level ->
                    screen = Screen.Game(GameMode.Level(LevelCatalog.spec(level)), origin = Screen.GalaxyMap)
                },
                onBack = { navigateBack() },
            )

            is Screen.EndlessSetup -> EndlessSetupScreen(
                container = container,
                onStart = { mutators ->
                    screen = Screen.Game(GameMode.Endless(mutators), origin = Screen.EndlessSetup)
                },
                onBack = { navigateBack() },
            )

            is Screen.DailySetup -> DailyScreen(
                container = container,
                onStart = { mode -> screen = Screen.Game(mode, origin = Screen.DailySetup) },
                onBack = { navigateBack() },
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
